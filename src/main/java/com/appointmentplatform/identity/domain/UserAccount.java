package com.appointmentplatform.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "appointment")
public class UserAccount {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(nullable = false, length = 512)
    private String passwordHash;
    @Column(nullable = false, length = 100)
    private String firstName;
    @Column(nullable = false, length = 100)
    private String lastName;
    @Column(length = 32)
    private String phone;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;
    @Column(unique = true)
    private UUID brandId;
    @Column(nullable = false)
    private boolean subscriptionActive;
    @Column(nullable = false)
    private boolean enabled;
    @Column(columnDefinition = "text")
    private String mfaSecret;
    @Column(nullable = false)
    private long lastMfaStep;
    private Instant passwordChangedAt;
    private Instant sessionsRevokedAt;
    @org.hibernate.annotations.CreationTimestamp(source = org.hibernate.annotations.SourceType.DB)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAccount() {}
    public UserAccount(UUID id, String email, String passwordHash, String firstName, String lastName, String phone, Role role, UUID brandId, boolean subscriptionActive,boolean enabled,  String mfaSecret, long lastMfaStep) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.role = role;
        this.brandId = brandId;
        this.subscriptionActive = subscriptionActive;
        this.enabled = enabled;
        this.mfaSecret = mfaSecret;
        this.lastMfaStep = lastMfaStep;
    }

    public UUID id() { return id; }
    public String email() { return email; }
    public String passwordHash() { return passwordHash; }
    public String firstName() { return firstName; }
    public String lastName() { return lastName; }
    public String phone() { return phone; }
    public Role role() { return role; }
    public UUID brandId() { return brandId; }
    public boolean isSubscriptionActive() { return subscriptionActive; }
    public boolean enabled() { return enabled; }
    public String mfaSecret() { return mfaSecret; }
    public long lastMfaStep() { return lastMfaStep; }
    public Instant createdAt() { return createdAt; }
    public Instant passwordChangedAt() { return passwordChangedAt; }


    public Instant sessionsRevokedAt() { return sessionsRevokedAt; }

    public void changeStatus(boolean enabled, boolean subscriptionActive, Instant changedAt) {
        if (this.enabled && !enabled) this.sessionsRevokedAt = changedAt;
        this.enabled = enabled;
        this.subscriptionActive = subscriptionActive;
    }

    public void changePassword(String newPasswordHash, Instant changedAt) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = changedAt;
    }

}
