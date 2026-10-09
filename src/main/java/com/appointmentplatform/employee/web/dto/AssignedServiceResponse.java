package com.appointmentplatform.employee.web.dto;

import com.appointmentplatform.employee.application.AssignedServiceDetails;
import com.appointmentplatform.service.ServiceDetails;

import java.util.UUID;

public record AssignedServiceResponse(UUID employeeId, ServiceDetails serviceDetails) {
    public static AssignedServiceResponse from(AssignedServiceDetails assignedServiceDetails) {
        return new AssignedServiceResponse(assignedServiceDetails.employeeId(), assignedServiceDetails.serviceDetails());
    }
}
