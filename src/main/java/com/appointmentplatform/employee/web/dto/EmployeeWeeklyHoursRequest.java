package com.appointmentplatform.employee.web.dto;
import jakarta.validation.constraints.NotNull;
import java.time.*;

public record EmployeeWeeklyHoursRequest(
        @NotNull
        DayOfWeek dayOfWeek,
        @NotNull
        LocalTime startTime,
        @NotNull
        LocalTime endTime
) {}
