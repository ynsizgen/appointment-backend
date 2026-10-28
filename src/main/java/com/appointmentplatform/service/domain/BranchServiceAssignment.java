package com.appointmentplatform.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "branch_services", schema = "appointment")
@IdClass(BranchServiceAssignmentId.class)
public class BranchServiceAssignment {
    @Id
    private UUID branchId;
    @Id
    private UUID serviceId;
    @Column(nullable = false)
    private UUID brandId;

    protected BranchServiceAssignment() {
    }

    public BranchServiceAssignment(UUID branchId, UUID serviceId, UUID brandId) {
        this.branchId = branchId;
        this.serviceId = serviceId;
        this.brandId = brandId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public UUID getBrandId() {
        return brandId;
    }
}
