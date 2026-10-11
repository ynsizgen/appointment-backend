package com.appointmentplatform.branch.web.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record BranchDateScheduleRequest(
        @NotNull @Size(max = 100) List<@NotNull
        @Valid BranchDateHoursRequest> workingHours) {}
