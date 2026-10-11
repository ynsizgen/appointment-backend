package com.appointmentplatform.employee.web.dto;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record EmployeeDateHoursRequest(
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime) {}
