package com.appointmentplatform.service.persistence;

import com.appointmentplatform.service.domain.BranchServiceAssignment;
import com.appointmentplatform.service.domain.BranchServiceAssignmentId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchServiceAssignmentRepository extends JpaRepository<BranchServiceAssignment, BranchServiceAssignmentId> {
}
