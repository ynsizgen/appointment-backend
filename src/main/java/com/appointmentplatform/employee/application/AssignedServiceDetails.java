package com.appointmentplatform.employee.application;

import com.appointmentplatform.service.ServiceDetails;

import java.util.UUID;

public record  AssignedServiceDetails(UUID employeeId, ServiceDetails serviceDetails) {
}
