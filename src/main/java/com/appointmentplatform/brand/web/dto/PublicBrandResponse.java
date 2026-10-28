package com.appointmentplatform.brand.web.dto;

import com.appointmentplatform.brand.application.PublicBrandDetails;
import com.appointmentplatform.branch.PublicBranchDetails;
import java.util.List;
import java.util.UUID;

public record PublicBrandResponse(UUID id, String displayName, String slug, List<PublicBranchDetails> branches) {
    public static PublicBrandResponse from(PublicBrandDetails details) {
        return new PublicBrandResponse(details.id(), details.displayName(), details.slug(), details.branches());
    }
}
