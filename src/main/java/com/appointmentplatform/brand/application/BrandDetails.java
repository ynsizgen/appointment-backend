package com.appointmentplatform.brand.application;
import java.util.UUID;
public record BrandDetails(UUID id, String displayName, int cancellationNoticeHours, String slug) {}
