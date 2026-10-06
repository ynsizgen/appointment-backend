package com.appointmentplatform.identity.application;

import com.appointmentplatform.audit.Audit;
import com.appointmentplatform.identity.domain.*;
import com.appointmentplatform.identity.persistence.*;
import com.appointmentplatform.identity.security.Totp;
import com.appointmentplatform.shared.Crypto;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {
    private final UserAccountRepository users;
    private final PendingRegistrationRepository registrations;
    private final PasswordEncoder passwords;
    private final Crypto crypto;
    private final Totp totp;
    private final Audit audit;
    private final String email;
    private final String password;
    private final String secret;

    public AdminBootstrap(UserAccountRepository users, PendingRegistrationRepository registrations,
            PasswordEncoder passwords, Crypto crypto, Totp totp, Audit audit,
            @Value("${app.bootstrap.email}") String email, @Value("${app.bootstrap.password}") String password,
            @Value("${app.bootstrap.totp-secret}") String secret) {
        this.users = users;
        this.registrations = registrations;
        this.passwords = passwords;
        this.crypto = crypto;
        this.totp = totp;
        this.audit = audit;
        this.email = email;
        this.password = password;
        this.secret = secret;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || password.length() < 12 || password.length() > 128)
            throw new IllegalStateException("Valid bootstrap email and a 12–128 character password are required");
        totp.decode(secret);
        String normalized = IdentityService.normalizeEmail(email);
        registrations.lockEmail(normalized);
        var existing = users.findByEmail(normalized);
        if (existing.isPresent()) {
            if (existing.get().role() != Role.ADMIN)
                throw new IllegalStateException("Bootstrap address belongs to a non-admin account");
            return;
        }
        users.save(new UserAccount(UUID.randomUUID(), normalized, passwords.encode(password),
                "Platform", "Admin", null, Role.ADMIN, null, true,true, crypto.encrypt(secret), -1));
        audit.record(null, null, "ADMIN_BOOTSTRAPPED", "SUCCESS");
    }
}
