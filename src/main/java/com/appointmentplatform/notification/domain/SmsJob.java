package com.appointmentplatform.notification.domain;

import com.appointmentplatform.notification.SmsJobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

@Entity
@Table(name = "sms_jobs", schema = "appointment")
public class SmsJob {
    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID appointmentId;

    @Column(nullable = false, length = 16)
    private String recipient;

    @Column(columnDefinition = "text")
    private String encryptedBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private SmsJobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SmsJobStatus status;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @CreationTimestamp(source = SourceType.DB)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;

    protected SmsJob() {
    }

    public SmsJob(UUID id, UUID appointmentId, String recipient, String encryptedBody,
                  SmsJobType type, Instant nextAttemptAt) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.recipient = recipient;
        this.encryptedBody = encryptedBody;
        this.type = type;
        this.nextAttemptAt = nextAttemptAt;
        this.status = SmsJobStatus.PENDING;
        this.attemptCount = 0;
    }

    public void markSent(Instant now) {
        this.status = SmsJobStatus.SENT;
        this.completedAt = now;
        this.attemptCount++;
        this.encryptedBody = null;
    }

    public void markAttempt(Instant now) {
        this.attemptCount++;
        this.status = SmsJobStatus.PENDING;
        this.nextAttemptAt = now.plus(Duration.ofMinutes(10));
    }

    public void markFailure(Instant now) {
        this.attemptCount++;
        this.status = SmsJobStatus.FAILED;
        this.completedAt = now;
        this.encryptedBody = null;
    }

    public void markSkipped(Instant now) {
        this.status = SmsJobStatus.SKIPPED;
        this.completedAt = now;
        this.encryptedBody = null;
    }


    public UUID id() {
        return id;
    }

    public UUID appointmentId() {
        return appointmentId;
    }

    public String recipient() {
        return recipient;
    }

    public String encryptedBody() {
        return encryptedBody;
    }

    public SmsJobType type() {
        return type;
    }

    public SmsJobStatus status() {
        return status;
    }

    public void setStatus(SmsJobStatus status) {
        this.status = status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant completedAt() {
        return completedAt;
    }
}
