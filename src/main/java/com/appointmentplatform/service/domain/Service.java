package com.appointmentplatform.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "services", schema = "appointment")
public class Service {
    @Id
    private UUID id;
    @Column(nullable = false)
    private  UUID brandId;
    @Column(nullable = false, length = 200)
    private String displayName;
    @Column(nullable = false)
    private int durationMinutes;

    protected Service() {}

    public Service(UUID id, UUID brandId, String displayName, int durationMinutes) {
        this.id = id;
        this.brandId = brandId;
        this.displayName = displayName;
        this.durationMinutes = durationMinutes;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBrandId() {
        return brandId;
    }

    public void setBrandId(UUID brandId) {
        this.brandId = brandId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

}
