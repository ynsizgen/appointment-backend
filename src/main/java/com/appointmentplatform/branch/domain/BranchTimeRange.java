package com.appointmentplatform.branch.domain;
import jakarta.persistence.*;
import java.time.*;
@Embeddable
public class BranchTimeRange {
    @Column(nullable = false) private LocalTime startTime;
    @Column(nullable = false) private LocalTime endTime;
    protected BranchTimeRange() {}
    public BranchTimeRange(LocalTime startTime, LocalTime endTime) {
        
        this.startTime = startTime; this.endTime = endTime;
    }
    
    public LocalTime startTime() { return startTime; }
    public LocalTime endTime() { return endTime; }
}
