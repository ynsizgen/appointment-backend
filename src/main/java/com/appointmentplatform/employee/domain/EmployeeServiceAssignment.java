package com.appointmentplatform.employee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "employee_services", schema = "appointment")
public class EmployeeServiceAssignment {

    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID employeeId;
    @Column(nullable = false)
    private UUID serviceId;


    protected EmployeeServiceAssignment() {
    }

    public EmployeeServiceAssignment(UUID id, UUID employeeId, UUID serviceId) {
        this.id = id;
        this.employeeId = employeeId;
        this.serviceId = serviceId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

}
