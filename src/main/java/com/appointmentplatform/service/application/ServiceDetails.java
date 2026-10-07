package com.appointmentplatform.service.application;

import java.util.UUID;

public record ServiceDetails(UUID id, UUID brandId, String displayName, int durationMinutes) {
}
