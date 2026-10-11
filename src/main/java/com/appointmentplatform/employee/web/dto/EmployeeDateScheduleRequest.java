package com.appointmentplatform.employee.web.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record EmployeeDateScheduleRequest(
        @NotNull @Size(max = 100)
        List<@NotNull @Valid EmployeeDateHoursRequest> workingHours) {}
