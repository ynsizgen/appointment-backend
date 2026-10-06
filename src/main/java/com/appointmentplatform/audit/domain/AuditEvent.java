package com.appointmentplatform.audit.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events", schema = "appointment")
public class AuditEvent {
    @Id
    private UUID id;
    
    private UUID actorId;
    
    private UUID brandId;
    @Column(nullable = false, length = 64)
    private String action;
    @Column(nullable = false, length = 16)
    private String outcome;
    @org.hibernate.annotations.CreationTimestamp(source = org.hibernate.annotations.SourceType.DB)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {}
    public AuditEvent(UUID id, UUID actorId, UUID brandId, String action, String outcome) {
        this.id = id;
        this.actorId = actorId;
        this.brandId = brandId;
        this.action = action;
        this.outcome = outcome;
    }

    public UUID id() { return id; }
    public UUID actorId() { return actorId; }
    public UUID brandId() { return brandId; }
    public String action() { return action; }
    public String outcome() { return outcome; }
    public Instant createdAt() { return createdAt; }

}
