package com.appointmentplatform.employee.domain;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.*;
@Entity
@Table(name = "employee_schedule_overrides", schema = "appointment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "date"}))
public class EmployeeScheduleOverride {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID employeeId;
    @Column(nullable = false)
    private LocalDate date;
    @ElementCollection
    @CollectionTable(name = "employee_override_hours", schema = "appointment", joinColumns = @JoinColumn(name = "override_id"))
    private List<EmployeeTimeInterval> intervals = new ArrayList<>();

    protected EmployeeScheduleOverride() {}

    public EmployeeScheduleOverride(UUID employeeId, LocalDate date) {
        this.id = UUID.randomUUID(); this.employeeId = employeeId; this.date = date;
    }

    public List<EmployeeTimeInterval> intervals() {
        return intervals;
    }

    public void replace(List<EmployeeTimeInterval> values) {
        intervals.clear();
        intervals.addAll(values);
    }
}
