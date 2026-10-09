package com.appointmentplatform.employee.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="employees", schema = "appointment")
public class Employee {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID branchId;
    @Column(nullable = false, length = 20)
    private String firstName;
    @Column(nullable = false, length = 30)
    private String lastName;
    @Column(nullable = false)
    private boolean active;


    protected Employee() {
    }

    public Employee(UUID id, UUID branchId, String firstName, String lastName, boolean active) {
        this.id = id;
        this.branchId = branchId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public boolean isActive() {
        return active;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
