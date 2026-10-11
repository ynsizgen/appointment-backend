package com.appointmentplatform.branch.web.dto;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
public record BranchDateHoursRequest(@NotNull LocalTime startTime, @NotNull LocalTime endTime) {}
