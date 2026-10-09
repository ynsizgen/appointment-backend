package com.appointmentplatform.employee.application;

import java.util.UUID;

public record EmployeeServiceAssignmentDetails(UUID id, UUID employeeId, UUID serviceId) {
}
