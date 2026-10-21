package com.appointmentplatform.appointment.domain;

import com.appointmentplatform.appointment.AppointmentSource;
import com.appointmentplatform.appointment.AppointmentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointments", schema = "appointment")
public class Appointment {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID branchId;
    @Column(nullable = false)
    private UUID employeeId;
    @Column(nullable = false)
    private UUID serviceId;
    @Column(nullable = false)
    private String serviceName;
    @Column(nullable = false)
    private int durationMinutes;
    @Column(nullable = false)
    private String customerFirstName;
    @Column(nullable = false)
    private String customerLastName;
    @Column(nullable = false)
    private String customerPhone;
    @Column @Email
    private String customerEmail;
    @Column(nullable = false)
    private Instant startsAt;
    @Column(nullable = false)
    private Instant endsAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentSource source;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false, updatable = false)
    private Instant cancellationDeadline;
    @Column(unique = true, length = 64)
    private String accessTokenHash;
    @Column(nullable = false, updatable = false)
    private Instant accessTokenExpiresAt;


    protected Appointment() {}

    public Appointment(
            UUID id,
            UUID branchId,
            UUID employeeId,
            UUID serviceId,
            String serviceName,
            int durationMinutes,
            String customerFirstName,
            String customerLastName,
            String customerPhone,
            String customerEmail,
            Instant startsAt,
            Instant endsAt,
            AppointmentStatus status,
            AppointmentSource source,
            Instant createdAt,
            Instant cancellationDeadline,
            String accessTokenHash,
            Instant accessTokenExpiresAt
    ) {
        this.id = id;
        this.branchId = branchId;
        this.employeeId = employeeId;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.durationMinutes = durationMinutes;
        this.customerFirstName = customerFirstName;
        this.customerLastName = customerLastName;
        this.customerPhone = customerPhone;
        this.customerEmail = customerEmail;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status;
        this.source = source;
        this.createdAt = createdAt;
        this.cancellationDeadline = cancellationDeadline;
        this.accessTokenHash = accessTokenHash;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getCustomerFirstName() {
        return customerFirstName;
    }

    public void setCustomerFirstName(String customerFirstName) {
        this.customerFirstName = customerFirstName;
    }

    public String getCustomerLastName() {
        return customerLastName;
    }

    public void setCustomerLastName(String customerLastName) {
        this.customerLastName = customerLastName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public @Email String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(@Email String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public AppointmentSource getSource() {
        return source;
    }

    public void setSource(AppointmentSource source) {
        this.source = source;
    }

    public Instant getCancellationDeadline() {
        return cancellationDeadline;
    }

    public void setCancellationDeadline(Instant cancellationDeadline) {
        this.cancellationDeadline = cancellationDeadline;
    }

    public String getAccessTokenHash() {
        return accessTokenHash;
    }

    public void setAccessTokenHash(String accessTokenHash) {
        this.accessTokenHash = accessTokenHash;
    }

    public Instant getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public void setAccessTokenExpiresAt(Instant accessTokenExpiresAt) {
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }
}
