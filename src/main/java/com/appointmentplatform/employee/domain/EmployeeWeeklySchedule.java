package com.appointmentplatform.employee.domain;
import jakarta.persistence.*;
import java.util.*;
@Entity
@Table(name = "employee_weekly_schedules", schema = "appointment")
public class EmployeeWeeklySchedule {

    @Id
    private UUID employeeId;

    @ElementCollection
    @CollectionTable(name = "employee_weekly_hours", schema = "appointment", joinColumns = @JoinColumn(name = "employee_id"))
    private List<EmployeeWeeklyInterval> intervals = new ArrayList<>();

    protected EmployeeWeeklySchedule() {}

    public EmployeeWeeklySchedule(UUID employeeId) {
        this.employeeId = employeeId;
    }
    public List<EmployeeWeeklyInterval> intervals() {
        return intervals;
    }
    public void replace(List<EmployeeWeeklyInterval> values) {
        intervals.clear(); intervals.addAll(values);
    }
}
