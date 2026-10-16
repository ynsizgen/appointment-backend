package com.appointmentplatform.employee.application;
import java.time.DayOfWeek;
import java.util.List;
public record EmployeeWeeklyScheduleDetails(boolean usesBranchHours,
        List<EmployeeWeeklyIntervalDetails> workingHours,
        List<EmployeeWeeklyIntervalDetails> configuredHours,
        List<DayOfWeek> closedDays,
        List<DayOfWeek> inheritedDays) {}
