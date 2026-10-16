package com.appointmentplatform.identity.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "phone_verifications", schema = "appointment")
public class PhoneVerification {
    @Id
    @Column(length = 64)
    private String phoneHash;

    @Column(length = 64)
    private String codeHash;

    private Instant expiresAt;
    private Instant sentAt;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 64)
    private String proofHash;

    private Instant proofExpiresAt;

    @ElementCollection
    @CollectionTable(name = "phone_verification_sends", schema = "appointment",
            joinColumns = @JoinColumn(name = "phone_hash"))
    @Column(name = "sent_at", nullable = false)
    private List<Instant> sends = new ArrayList<>();

    protected PhoneVerification() {
    }

    public List<Instant> sends() {
        return sends;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public String codeHash() {
        return codeHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public int attempts() {
        return attempts;
    }

    public String proofHash() {
        return proofHash;
    }

    public Instant proofExpiresAt() {
        return proofExpiresAt;
    }

    public void issue(String hash, Instant now) {
        codeHash = hash;
        sentAt = now;
        expiresAt = now.plusSeconds(120);
        attempts = 0;
        proofHash = null;
        proofExpiresAt = null;
        sends.add(now);
    }

    public void failAttempt() {
        attempts++;
    }

    public void verify(String hash, Instant expiry) {
        codeHash = null;
        proofHash = hash;
        proofExpiresAt = expiry;
    }

    public void consume() {
        proofHash = null;
        proofExpiresAt = null;
    }
}
