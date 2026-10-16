package com.appointmentplatform.employee.web.dto;

import com.appointmentplatform.employee.EmployeeDailyScheduleDetails;
import java.time.LocalDate;
import java.util.List;

public record EmployeeDailyScheduleResponse(LocalDate date, String source, boolean active,
        List<EmployeeTimeIntervalResponse> configuredHours, List<EmployeeTimeIntervalResponse> workingHours) {
    public static EmployeeDailyScheduleResponse from(EmployeeDailyScheduleDetails details) {
        return new EmployeeDailyScheduleResponse(details.date(), details.source(), details.active(),
                details.configuredHours().stream().map(EmployeeTimeIntervalResponse::from).toList(),
                details.workingHours().stream().map(EmployeeTimeIntervalResponse::from).toList());
    }
}
