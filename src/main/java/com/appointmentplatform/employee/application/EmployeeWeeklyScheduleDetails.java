package com.appointmentplatform.employee.application;

import java.util.List;

public record EmployeeWeeklyScheduleDetails(boolean usesBranchHours, List<EmployeeWeeklyIntervalDetails> workingHours) {}
