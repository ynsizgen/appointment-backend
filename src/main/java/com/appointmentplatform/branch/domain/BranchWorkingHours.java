package com.appointmentplatform.branch.domain;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "branch_working_hours", schema = "appointment")
public class BranchWorkingHours {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID branchId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private DayOfWeek dayOfWeek;
    @Column(nullable = false)
    private LocalTime startTime;
    @Column(nullable = false)
    private LocalTime endTime;

    protected BranchWorkingHours() {}

    public BranchWorkingHours(UUID id, UUID branchId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.id = id; this.branchId = branchId; this.dayOfWeek = dayOfWeek;
        this.startTime = startTime; this.endTime = endTime;
    }

    public DayOfWeek dayOfWeek() {
        return dayOfWeek; }
    public LocalTime startTime() {
        return startTime; }
    public LocalTime endTime() {
        return endTime; }
}
