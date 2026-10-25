package com.appointmentplatform.employee;

import java.util.List;
import java.util.UUID;

public interface EmployeeOperations {
    UUID branchId(UUID employeeId);

    void requireServiceAssigned(UUID brandId, UUID employeeId, UUID serviceId);

    List<EmployeeDetails> findAllEmployeeByBranchId(UUID branchId);
}
