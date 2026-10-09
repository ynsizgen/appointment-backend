package com.appointmentplatform.employee.persistence;

import com.appointmentplatform.employee.application.EmployeeServiceAssignmentDetails;
import com.appointmentplatform.employee.domain.EmployeeServiceAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmployeeServiceAssignmentRepository extends JpaRepository<EmployeeServiceAssignment, UUID> {
    boolean existsByEmployeeIdAndServiceId(UUID employeeId, UUID serviceId);

    void deleteByEmployeeIdAndServiceId(UUID employeeId, UUID serviceId);

    List<EmployeeServiceAssignment> findAllByEmployeeId(UUID emmployeeId);
}
