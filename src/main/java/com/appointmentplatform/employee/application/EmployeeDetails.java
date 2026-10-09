package com.appointmentplatform.employee.application;

import java.util.UUID;

public record EmployeeDetails(UUID id, UUID branchId, String firstName, String lastName, boolean active) {
}
