package com.appointmentplatform.branch.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "branches", schema = "appointment")
public class Branch {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID brandId;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 1000)
    private String address;
    @Column(length = 2048)
    private String googleMapsUrl;
    @Column(nullable = false)
    private boolean acceptingAppointments;
    @org.hibernate.annotations.CreationTimestamp(source = org.hibernate.annotations.SourceType.DB)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Branch() {

    }
    public Branch(UUID id, UUID brandId, String name, String address, String googleMapsUrl) {
        this.id = id; this.brandId = brandId; this.name = name;
        this.address = address; this.googleMapsUrl = googleMapsUrl;
        this.acceptingAppointments = false;
    }

    public UUID id() {
        return id;
    }
    public UUID brandId() {
        return brandId;
    }
    public String name() {
        return name;
    }
    public String address() {
        return address;
    }
    public String googleMapsUrl() {
        return googleMapsUrl;
    }
    public boolean acceptingAppointments() {
        return acceptingAppointments;
    }
    public Instant createdAt() {
        return createdAt;
    }
    public void changeAppointmentAcceptance(boolean accepting) {
        this.acceptingAppointments = accepting;
    }
}
