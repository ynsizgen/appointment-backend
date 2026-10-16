package com.appointmentplatform.employee.web.dto;
import com.appointmentplatform.employee.application.EmployeeWeeklyScheduleDetails;
import java.time.DayOfWeek;
import java.util.List;
public record EmployeeWeeklyScheduleResponse(boolean usesBranchHours,
        List<EmployeeWeeklyIntervalResponse> workingHours,
        List<EmployeeWeeklyIntervalResponse> configuredHours,
        List<DayOfWeek> closedDays, List<DayOfWeek> inheritedDays) {
    public static EmployeeWeeklyScheduleResponse from(EmployeeWeeklyScheduleDetails details) {
        return new EmployeeWeeklyScheduleResponse(details.usesBranchHours(),
                details.workingHours().stream().map(EmployeeWeeklyIntervalResponse::from).toList(),
                details.configuredHours().stream().map(EmployeeWeeklyIntervalResponse::from).toList(),
                details.closedDays(), details.inheritedDays());
    }
}
