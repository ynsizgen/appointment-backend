package com.appointmentplatform.service.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class BranchServiceAssignmentId implements Serializable {
    private UUID branchId;
    private UUID serviceId;

    public BranchServiceAssignmentId() {
    }

    public BranchServiceAssignmentId(UUID branchId, UUID serviceId) {
        this.branchId = branchId;
        this.serviceId = serviceId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BranchServiceAssignmentId that)) {
            return false;
        }
        return Objects.equals(branchId, that.branchId) && Objects.equals(serviceId, that.serviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(branchId, serviceId);
    }
}
