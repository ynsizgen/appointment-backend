package com.appointmentplatform.identity.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pending_registrations", schema = "appointment")
public class PendingRegistration {
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
    @Column(nullable = false, length = 32)
    private String phone;
    @Column(nullable = false, length = 200)
    private String displayName;
    @Column(nullable = false, length = 1000)
    private String address;
    @Column(length = 2048)
    private String googleMapsUrl;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, unique = true, columnDefinition = "char(64)")
    private String tokenHash;
    @Column(nullable = false)
    private Instant expiresAt;
    @org.hibernate.annotations.CreationTimestamp(source = org.hibernate.annotations.SourceType.DB)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected PendingRegistration() {}
    public PendingRegistration(UUID id, String email, String passwordHash, String firstName, String lastName, String phone, String displayName, String address, String googleMapsUrl, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.displayName = displayName;
        this.address = address;
        this.googleMapsUrl = googleMapsUrl;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UUID id() { return id; }
    public String email() { return email; }
    public String passwordHash() { return passwordHash; }
    public String firstName() { return firstName; }
    public String lastName() { return lastName; }
    public String phone() { return phone; }
    public String displayName() { return displayName; }
    public String address() { return address; }
    public String googleMapsUrl() { return googleMapsUrl; }
    public String tokenHash() { return tokenHash; }
    public Instant expiresAt() { return expiresAt; }
    public Instant createdAt() { return createdAt; }

}
