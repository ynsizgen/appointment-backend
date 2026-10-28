package com.appointmentplatform.brand.web.dto;
import com.appointmentplatform.brand.application.BrandDetails;
import java.util.UUID;
public record BrandResponse(UUID id, String displayName, int cancellationNoticeHours, String slug) {
    public static BrandResponse from(BrandDetails details) {
        return new BrandResponse(details.id(), details.displayName(), details.cancellationNoticeHours(), details.slug());
    }
}
