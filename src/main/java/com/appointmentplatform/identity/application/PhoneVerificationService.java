package com.appointmentplatform.identity.application;

import com.appointmentplatform.identity.PhoneVerifications;
import com.appointmentplatform.identity.domain.PhoneVerification;
import com.appointmentplatform.identity.persistence.PhoneVerificationRepository;
import com.appointmentplatform.notification.SmsSender;
import com.appointmentplatform.shared.ApiException;
import com.appointmentplatform.shared.Crypto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhoneVerificationService implements PhoneVerifications {
    private final PhoneVerificationRepository repository;
    private final SmsSender sender;
    private final Crypto crypto;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PhoneVerificationService(PhoneVerificationRepository repository, SmsSender sender, Crypto crypto, Clock clock) {
        this.repository = repository;
        this.sender = sender;
        this.crypto = crypto;
        this.clock = clock;
    }

    @Transactional
    public void request(String phone) {
        String phoneHash = phoneHash(phone);
        repository.ensureExists(phoneHash);
        var verification = repository.findForUpdate(phoneHash).orElseThrow();
        Instant now = clock.instant();

        if (verification.sentAt() != null && now.isBefore(verification.sentAt().plusSeconds(60))) {
            throw limited("Yeni kod için 60 saniye beklemelisiniz.", verification.sentAt().plusSeconds(60), now);
        }

        verification.sends().removeIf(sent -> !sent.isAfter(now.minusSeconds(3600)));
        if (verification.sends().size() >= 3) {
            Instant oldest = verification.sends().stream().min(Instant::compareTo).orElseThrow();
            throw limited("Bir telefon numarasına saatte en fazla 3 kod gönderilebilir.", oldest.plusSeconds(3600), now);
        }

        String code;
        String hash;
        do {
            code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
            hash = codeHash(phone, code);
        } while (hash.equals(verification.codeHash()));

        verification.issue(hash, now);
        sender.sendVerificationCode(phone, code);
    }

    // Failed guesses must commit their attempt counter, even though the API returns an error.
    @Transactional(noRollbackFor = ApiException.class)
    public PhoneVerificationDetails verify(String phone, String code) {
        var verification = repository.findForUpdate(phoneHash(phone)).orElseThrow(this::invalidCode);
        Instant now = clock.instant();

        if (verification.codeHash() == null
                || !verification.expiresAt().isAfter(now)
                || verification.attempts() >= 3) {
            throw invalidCode();
        }

        if (!equal(verification.codeHash(), codeHash(phone, code))) {
            verification.failAttempt();
            throw invalidCode();
        }

        String token = crypto.token();
        Instant expires = now.plusSeconds(300);
        verification.verify(Crypto.hash(token), expires);
        return new PhoneVerificationDetails(token, expires);
    }

    // Consumption and appointment insertion share one transaction: failed bookings retain the proof.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(String phone, String verificationToken) {
        if (verificationToken == null || verificationToken.isBlank()) {
            throw invalidProof();
        }

        PhoneVerification verification = repository.findForUpdate(phoneHash(phone)).orElseThrow(this::invalidProof);
        if (verification.proofHash() == null
                || !verification.proofExpiresAt().isAfter(clock.instant())
                || !equal(verification.proofHash(), Crypto.hash(verificationToken))) {
            throw invalidProof();
        }

        verification.consume();
    }

    private String phoneHash(String phone) {
        return crypto.keyedHash("phone-verification:" + phone);
    }

    private String codeHash(String phone, String code) {
        return crypto.keyedHash("phone-code:" + phone + ":" + code);
    }

    private boolean equal(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private ApiException limited(String message, Instant until, Instant now) {
        long seconds = Math.max(1, (Duration.between(now, until).toMillis() + 999) / 1000);
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message, seconds);
    }

    private ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Doğrulama kodu geçersiz, süresi dolmuş veya deneme hakkı tükenmiş.");
    }

    private ApiException invalidProof() {
        return new ApiException(HttpStatus.FORBIDDEN, "Randevu oluşturmadan önce bu telefon numarasını doğrulamalısınız.");
    }
}
