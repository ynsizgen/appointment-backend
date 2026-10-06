package com.appointmentplatform.notification.application;

import com.appointmentplatform.notification.*;
import com.appointmentplatform.notification.domain.EmailJob;
import com.appointmentplatform.notification.persistence.EmailJobRepository;
import com.appointmentplatform.shared.Crypto;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class VerificationEmailService implements VerificationEmails {
    private final EmailJobRepository repository;
    private final Crypto crypto;

    public VerificationEmailService(EmailJobRepository repository, Crypto crypto) {
        this.repository = repository;
        this.crypto = crypto;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(VerificationEmail email) {
        repository.save(new EmailJob(UUID.randomUUID(), email.registrationId(), email.recipient(),
                email.expiresAt(), crypto.encrypt(email.body()), "PENDING", null));
    }
}
