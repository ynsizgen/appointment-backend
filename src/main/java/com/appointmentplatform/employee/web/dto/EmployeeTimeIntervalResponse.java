package com.appointmentplatform.employee.web.dto;

import com.appointmentplatform.employee.EmployeeTimeIntervalDetails;
import java.time.LocalTime;

public record EmployeeTimeIntervalResponse(LocalTime startTime, LocalTime endTime) {
    public static EmployeeTimeIntervalResponse from(EmployeeTimeIntervalDetails details) {
        return new EmployeeTimeIntervalResponse(details.startTime(), details.endTime());
    }
}
