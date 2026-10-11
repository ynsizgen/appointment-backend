package com.appointmentplatform.employee.application;

import java.time.LocalDate;
import java.util.List;

public record EmployeeDailyScheduleDetails(LocalDate date, String source, boolean active,
        List<EmployeeTimeIntervalDetails> configuredHours, List<EmployeeTimeIntervalDetails> workingHours) {}
