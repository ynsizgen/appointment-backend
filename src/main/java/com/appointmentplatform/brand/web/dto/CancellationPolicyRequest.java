package com.appointmentplatform.brand.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CancellationPolicyRequest(
        @NotNull
        @Min(0)
        @Max(168)
        Integer cancellationNoticeHours
) {}
