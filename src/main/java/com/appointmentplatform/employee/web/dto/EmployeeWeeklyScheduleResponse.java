package com.appointmentplatform.employee.web.dto;

import com.appointmentplatform.employee.application.EmployeeWeeklyScheduleDetails;
import java.util.List;

public record EmployeeWeeklyScheduleResponse(boolean usesBranchHours, List<EmployeeWeeklyIntervalResponse> workingHours) {
    public static EmployeeWeeklyScheduleResponse from(EmployeeWeeklyScheduleDetails details) {
        return new EmployeeWeeklyScheduleResponse(details.usesBranchHours(), details.workingHours().stream()
                .map(EmployeeWeeklyIntervalResponse::from).toList());
    }
}
