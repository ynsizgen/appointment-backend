package com.appointmentplatform.service.web.dto;

import com.appointmentplatform.service.application.ServiceDetails;

import java.util.UUID;

public record ServiceResponse (UUID id, UUID brandId, String displayName, int durationMinutes){
    public static ServiceResponse from(ServiceDetails serviceDetails) {
        return new ServiceResponse(serviceDetails.id(), serviceDetails.brandId(), serviceDetails.displayName(), serviceDetails.durationMinutes());
    }
}
