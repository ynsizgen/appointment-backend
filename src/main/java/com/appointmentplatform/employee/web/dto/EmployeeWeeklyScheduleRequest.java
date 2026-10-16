package com.appointmentplatform.employee.web.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Set;
import java.time.DayOfWeek;
public record EmployeeWeeklyScheduleRequest(
        @NotNull
        @Size(max = 100)
        List<@NotNull @Valid EmployeeWeeklyHoursRequest> workingHours,
        @Size(max = 7) Set<@NotNull DayOfWeek> closedDays
) {
    public EmployeeWeeklyScheduleRequest {
        if (closedDays == null) closedDays = Set.of();
    }
}
