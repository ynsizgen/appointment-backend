package com.appointmentplatform.identity.application;

import com.appointmentplatform.identity.persistence.PendingRegistrationRepository;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationCleanup {
    private final PendingRegistrationRepository repository;
    private final Clock clock;
    
    public RegistrationCleanup(PendingRegistrationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }
    
    @Scheduled(fixedDelayString = "${app.mail.cleanup-delay:3600000}")
    @Transactional
    public void cleanup() { 
        repository.deleteExpired(clock.instant());
    }
}
