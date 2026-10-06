package com.appointmentplatform.notification.application;

import com.appointmentplatform.notification.persistence.EmailJobRepository;
import com.appointmentplatform.notification.infrastructure.VerificationMailSender;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.mail.worker-enabled", havingValue = "true", matchIfMissing = true)
public class VerificationMailWorker {
    private final EmailJobRepository repository;
    private final VerificationMailSender sender;
    private final Clock clock;

    public VerificationMailWorker(EmailJobRepository repository, VerificationMailSender sender, Clock clock) {
        this.repository = repository;
        this.sender = sender;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.mail.poll-delay:5000}")
    @Transactional
    public void deliver() {
        var jobs = repository.findNext(clock.instant(), PageRequest.of(0, 1));
        if (jobs.isEmpty())
            return;
        var job = jobs.getFirst();
        String outcome;
        try {
            sender.send(job.recipient(), job.encryptedBody());
            outcome = "SENT";
        } catch (RuntimeException exception) {
            outcome = "FAILED";
        }
        job.finish(outcome, clock.instant());
    }


}
