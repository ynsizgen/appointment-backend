package com.appointmentplatform.brand.application;

import com.appointmentplatform.branch.PublicBranchDetails;
import java.util.List;
import java.util.UUID;

public record PublicBrandDetails(UUID id, String displayName, String slug, List<PublicBranchDetails> branches) {}
