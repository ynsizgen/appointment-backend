package com.appointmentplatform.branch.web.dto;
import com.appointmentplatform.branch.BranchDailySchedule;
import java.time.LocalDate;
import java.util.List;
public record BranchDailyScheduleResponse(LocalDate date, boolean dateOverride, List<BranchTimeIntervalResponse> workingHours) {
    public static BranchDailyScheduleResponse from(BranchDailySchedule s) {
        return new BranchDailyScheduleResponse(s.date(), s.dateOverride(),
                s.workingHours().stream().map(BranchTimeIntervalResponse::from).toList());
    }
}
