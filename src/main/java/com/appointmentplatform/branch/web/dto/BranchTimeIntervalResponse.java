package com.appointmentplatform.branch.web.dto;
import com.appointmentplatform.branch.BranchTimeInterval;
import java.time.LocalTime;
public record BranchTimeIntervalResponse(LocalTime startTime, LocalTime endTime) {
    public static BranchTimeIntervalResponse from(BranchTimeInterval h) {
        return new BranchTimeIntervalResponse(h.startTime(), h.endTime());
    }
}
