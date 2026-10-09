package com.appointmentplatform.employee.web.dto;


import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EmployeeServiceAssignmentRequest(
        @NotNull
        UUID employeeId,
        @NotNull
        UUID serviceId) {
}
