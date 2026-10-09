package com.appointmentplatform.employee.persistence;

import com.appointmentplatform.employee.domain.Employee;
import com.appointmentplatform.employee.web.dto.EmployeeResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
     List<Employee> findAllByBranchId(UUID branchId);
}
