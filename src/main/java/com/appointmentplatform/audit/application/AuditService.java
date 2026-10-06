package com.appointmentplatform.audit.application;

import com.appointmentplatform.audit.Audit;
import com.appointmentplatform.audit.domain.AuditEvent;
import com.appointmentplatform.audit.persistence.AuditEventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional
public class AuditService implements Audit {
    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(UUID actor, UUID brand, String action, String outcome) {
        repository.save(new AuditEvent(UUID.randomUUID(), actor, brand, action, outcome));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(UUID actor, String action) {
        record(actor, null, action, "DENIED");
    }
}
