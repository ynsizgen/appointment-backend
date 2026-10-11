package com.appointmentplatform.branch.domain;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.*;
@Entity
@Table(name = "branch_schedule_overrides", schema = "appointment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"branch_id", "date"}))
public class BranchScheduleOverride {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID branchId;
    @Column(nullable = false)
    private LocalDate date;
    @ElementCollection
    @CollectionTable(name = "branch_override_hours", schema = "appointment", joinColumns = @JoinColumn(name = "override_id"))
    private List<BranchTimeRange> intervals = new ArrayList<>();

    protected BranchScheduleOverride() {}

    public BranchScheduleOverride(UUID branchId, LocalDate date) {
        this.id = UUID.randomUUID(); this.branchId = branchId; this.date = date;
    }

    public List<BranchTimeRange> intervals() {
        return intervals;
    }

    public void replace(List<BranchTimeRange> values) {
        intervals.clear();
        intervals.addAll(values);
    }
}
