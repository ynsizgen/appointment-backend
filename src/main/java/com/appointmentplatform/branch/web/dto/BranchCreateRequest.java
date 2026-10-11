package com.appointmentplatform.branch.web.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record BranchCreateRequest(
        @NotBlank @Size(max = 200)
        String name,
        @NotBlank @Size(max = 1000)
        String address,
        @Size(max = 2048)
        String googleMapsUrl,
        @NotEmpty @Size(max = 100)
        List<@NotNull @Valid WorkingHoursRequest> workingHours) {}
