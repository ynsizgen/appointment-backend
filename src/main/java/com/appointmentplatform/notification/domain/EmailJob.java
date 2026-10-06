package com.appointmentplatform.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_jobs", schema = "appointment")
public class EmailJob {
    @Id
    private UUID id;
    @Column(unique = true)
    private UUID registrationId;
    @Column(unique = true)
    private UUID passwordResetId;
    @Column(nullable = false, length = 254)
    private String recipient;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(columnDefinition = "text")
    private String encryptedBody;
    @Column(nullable = false, length = 16)
    private String state;
    
    private Instant completedAt;
    @org.hibernate.annotations.CreationTimestamp(source = org.hibernate.annotations.SourceType.DB)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected EmailJob() {}
    public EmailJob(UUID id, UUID registrationId, String recipient, Instant expiresAt, String encryptedBody, String state, Instant completedAt) {
        this.id = id;
        this.registrationId = registrationId;
        this.recipient = recipient;
        this.expiresAt = expiresAt;
        this.encryptedBody = encryptedBody;
        this.state = state;
        this.completedAt = completedAt;
    }

    public static EmailJob passwordReset(UUID id, UUID passwordResetId, String recipient, Instant expiresAt,
                                         String encryptedBody) {
        var job = new EmailJob(id, null, recipient, expiresAt, encryptedBody, "PENDING", null);
        job.passwordResetId = passwordResetId;
        return job;
    }

    public UUID id() { return id; }
    public UUID registrationId() { return registrationId; }
    public UUID passwordResetId() { return passwordResetId; }
    public String recipient() { return recipient; }
    public Instant expiresAt() { return expiresAt; }
    public String encryptedBody() { return encryptedBody; }
    public String state() { return state; }
    public Instant completedAt() { return completedAt; }
    public Instant createdAt() { return createdAt; }
    public void finish(String state, Instant when) {
        this.state = state;
        this.completedAt = when;
        this.encryptedBody = null;
    }
}
