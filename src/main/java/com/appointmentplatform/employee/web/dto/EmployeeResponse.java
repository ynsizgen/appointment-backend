package com.appointmentplatform.employee.web.dto;

import com.appointmentplatform.employee.application.EmployeeDetails;
import com.appointmentplatform.employee.domain.Employee;

import java.util.UUID;

public record EmployeeResponse(UUID id, UUID branchId, String firstName, String lastName, boolean active) {
    public static EmployeeResponse from (EmployeeDetails employee) {
         return new EmployeeResponse(employee.id(), employee.branchId(),employee.firstName(),employee.lastName(),employee.active());
     }
}
