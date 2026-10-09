package com.appointmentplatform.employee.web.dto;

import com.appointmentplatform.employee.application.EmployeeService;
import com.appointmentplatform.employee.application.EmployeeServiceAssignmentDetails;
import com.appointmentplatform.employee.domain.Employee;
import com.appointmentplatform.employee.domain.EmployeeServiceAssignment;
import com.appointmentplatform.service.domain.Service;

import java.util.UUID;

public record EmployeeServiceAssignmentResponse(UUID id, UUID employeeId, UUID serviceId) {
    public static EmployeeServiceAssignmentResponse from(EmployeeServiceAssignmentDetails employeeServiceAssignment) {
        return new EmployeeServiceAssignmentResponse(employeeServiceAssignment.id(), employeeServiceAssignment.employeeId(), employeeServiceAssignment.serviceId());
    }
}
