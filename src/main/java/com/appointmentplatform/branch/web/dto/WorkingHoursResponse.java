package com.appointmentplatform.branch.web.dto;
import com.appointmentplatform.branch.application.WorkingHoursDetails;
import java.time.DayOfWeek;
import java.time.LocalTime;
public record WorkingHoursResponse(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
    public static WorkingHoursResponse from(WorkingHoursDetails value) {
        return new WorkingHoursResponse(value.dayOfWeek(), value.startTime(), value.endTime());
    }
}
