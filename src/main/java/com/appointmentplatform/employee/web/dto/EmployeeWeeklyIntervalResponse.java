package com.appointmentplatform.employee.web.dto;

import com.appointmentplatform.employee.application.EmployeeWeeklyIntervalDetails;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record EmployeeWeeklyIntervalResponse(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
    public static EmployeeWeeklyIntervalResponse from(EmployeeWeeklyIntervalDetails details) {
        return new EmployeeWeeklyIntervalResponse(details.dayOfWeek(), details.startTime(), details.endTime());
    }
}
