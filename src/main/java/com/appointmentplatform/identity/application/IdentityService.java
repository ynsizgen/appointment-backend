package com.appointmentplatform.identity.application;

import com.appointmentplatform.audit.Audit;
import com.appointmentplatform.identity.domain.*;
import com.appointmentplatform.identity.persistence.*;
import com.appointmentplatform.identity.security.Totp;
import com.appointmentplatform.notification.*;
import com.appointmentplatform.shared.*;
import com.appointmentplatform.brand.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IdentityService {
    private final UserAccountRepository users;
    private final PendingRegistrationRepository registrations;
    private final PendingForgotBrandPasswordRepository forgotBrandPassword;
    private final BrandOperations brands;
    private final VerificationEmails emails;
    private final ForgotPasswordEmails forgotPasswordEmails;
    private final PasswordEncoder passwords;
    private final Crypto crypto;
    private final Clock clock;
    private final Audit audit;
    private final Totp totp;
    private final String publicUrl;
    private final Duration ttl;
    private final Duration passwordResetTtl;
    private final String dummyHash;

    public IdentityService(UserAccountRepository users, PendingRegistrationRepository registrations, PendingForgotBrandPasswordRepository forgotBrandPassword,
                           BrandOperations brands, VerificationEmails emails, ForgotPasswordEmails forgotPasswordEmails, PasswordEncoder passwords, Crypto crypto,
                           Clock clock, Audit audit, Totp totp, @Value("${app.public-url}") String publicUrl,
                           @Value("${app.verification-ttl}") Duration ttl,
                           @Value("${app.password-reset-ttl:15m}") Duration passwordResetTtl) {
        this.users = users;
        this.registrations = registrations;
        this.forgotBrandPassword = forgotBrandPassword;
        this.brands = brands;
        this.emails = emails;
        this.forgotPasswordEmails = forgotPasswordEmails;
        this.passwords = passwords;
        this.crypto = crypto;
        this.clock = clock;
        this.audit = audit;
        this.totp = totp;
        this.publicUrl = publicUrl;
        this.ttl = ttl;
        this.passwordResetTtl = passwordResetTtl;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public void  register(RegistrationCommand request) {
        String email = normalizeEmail(request.email());
        String passwordHash = passwords.encode(request.password());
        registrations.lockEmail(email);
        registrations.deleteExpiredEmail(email, clock.instant());
        if (users.findByEmail(email).isPresent() || registrations.existsByEmail(email))
            return;
        String token = crypto.token();
        UUID id = UUID.randomUUID();
        Instant expires = clock.instant().plus(ttl);
        var pending = new PendingRegistration(id, email, passwordHash, request.firstName().strip(),
                request.lastName().strip(), request.phone(), request.displayName().strip(),
                Crypto.hash(token), expires);
        registrations.saveAndFlush(pending);
        emails.enqueue(new VerificationEmail(id, email,
                "Brand üyeliğinizi tamamlamak için bağlantıyı açıp doğrulamayı onaylayın:\n"
                + publicUrl + "/verify-email#token=" + token, expires));
        audit.record(null, null, "BRAND_REGISTRATION_REQUESTED", "SUCCESS");
    }

    @Transactional
    public void forgotBrandPassword(String requestedEmail) {
        String email = normalizeEmail(requestedEmail);
        forgotBrandPassword.lockEmail(email);
        var brand = users.findByEmail(email).orElse(null);
        if (brand == null || brand.role() != Role.BRAND || !brand.enabled())
            return;

        forgotBrandPassword.deleteByUserId(brand.id());
        String token = crypto.token();
        UUID id = UUID.randomUUID();
        Instant expires = clock.instant().plus(passwordResetTtl);
        var pending = new PendingForgotPassword(id, brand.id(), Crypto.hash(token), expires);
        forgotBrandPassword.saveAndFlush(pending);
        forgotPasswordEmails.enqueue(new ForgotPasswordEmail(id, email,
                "Brand hesabınızın parolasını değiştirmek için bağlantıyı açın:\n"
                        + publicUrl + "/reset-password#token=" + token, expires));
        audit.record(null, brand.brandId(), "BRAND_FORGOT_REQUESTED", "SUCCESS");
    }

    @Transactional
    public void resetBrandPassword(String token, String newPassword) {
        var pending = forgotBrandPassword.findByTokenHashForUpdate(Crypto.hash(token))
                .orElseThrow(this::invalidPasswordReset);
        if (!pending.expiresAt().isAfter(clock.instant()))
            throw invalidPasswordReset();
        var brand = users.findById(pending.userId()).orElseThrow(this::invalidPasswordReset);
        if (brand.role() != Role.BRAND || !brand.enabled()) throw invalidPasswordReset();

        brand.changePassword(passwords.encode(newPassword), clock.instant());
        forgotBrandPassword.delete(pending);
        audit.record(brand.id(), brand.brandId(), "BRAND_PASSWORD_RESET", "SUCCESS");
    }

    @Transactional
    public void verify(String token) {
        var pending = registrations.findByTokenHashForUpdate(Crypto.hash(token)).orElseThrow(this::invalidVerification);
        if (!pending.expiresAt().isAfter(clock.instant()))
            throw invalidVerification();
        UUID brandId = brands.register(new BrandRegistration(pending.displayName()));
        var user = new UserAccount(UUID.randomUUID(), pending.email(), pending.passwordHash(),
                pending.firstName(), pending.lastName(), pending.phone(), Role.BRAND, brandId, false,true, null, -1);
        users.save(user);
        registrations.delete(pending);
        audit.record(user.id(), brandId, "EMAIL_VERIFIED", "SUCCESS");
    }

    @Transactional
    public AccountDetails login(LoginCommand input) {
        var user = users.findByEmail(normalizeEmail(input.email())).orElse(null);
        boolean valid = passwords.matches(input.password(), user == null ? dummyHash : user.passwordHash());
        if (!valid || user == null || !user.enabled())
            throw denied(user);
        if (user.role() == Role.ADMIN) {
            long step = totp.verify(crypto.decrypt(user.mfaSecret()), input.mfaCode(), clock.instant());
            if (step < 0 || users.consumeMfa(user.id(), step) != 1)
                throw denied(user);
        }
        audit.record(user.id(), user.brandId(), "LOGIN", "SUCCESS");
        return details(user);
    }

    public Optional<AccountDetails> findAccount(UUID id) {
        return users.findById(id).map(this::details);
    }

    private AccountDetails details(UserAccount user) {
        return new AccountDetails(user.id(), user.role().name(), user.brandId(), user.firstName(), user.lastName(), user.isSubscriptionActive(), user.enabled(), user.passwordChangedAt(), user.sessionsRevokedAt());
    }

    private ApiException denied(UserAccount user) {
        audit.failure(user == null ? null : user.id(), "LOGIN");
        return new ApiException(HttpStatus.UNAUTHORIZED, "Giriş bilgileri geçersiz.");
    }

    private ApiException invalidVerification() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Doğrulama bağlantısı geçersiz veya süresi dolmuş.");
    }

    private ApiException invalidPasswordReset() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Parola sıfırlama bağlantısı geçersiz, kullanılmış veya süresi dolmuş.");
    }

}
