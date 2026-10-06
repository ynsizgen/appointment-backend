package com.appointmentplatform.notification.application;

import com.appointmentplatform.notification.ForgotPasswordEmail;
import com.appointmentplatform.notification.ForgotPasswordEmails;
import com.appointmentplatform.notification.domain.EmailJob;
import com.appointmentplatform.notification.persistence.EmailJobRepository;
import com.appointmentplatform.shared.Crypto;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForgotPasswordEmailService implements ForgotPasswordEmails {
    private final EmailJobRepository repository;
    private final Crypto crypto;

    public ForgotPasswordEmailService(EmailJobRepository repository, Crypto crypto) {
        this.repository = repository;
        this.crypto = crypto;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(ForgotPasswordEmail email) {
        repository.save(EmailJob.passwordReset(UUID.randomUUID(), email.forgotId(), email.recipient(),
                email.expiresAt(), crypto.encrypt(email.body())));
    }
}
