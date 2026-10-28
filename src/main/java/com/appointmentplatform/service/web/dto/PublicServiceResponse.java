package com.appointmentplatform.service.web.dto;

import com.appointmentplatform.service.ServiceDetails;

import java.util.UUID;

public record PublicServiceResponse(UUID id, String displayName, int durationMinutes) {

    public static PublicServiceResponse from(ServiceDetails service) {
        return new PublicServiceResponse(service.id(), service.displayName(), service.durationMinutes());
    }
}
