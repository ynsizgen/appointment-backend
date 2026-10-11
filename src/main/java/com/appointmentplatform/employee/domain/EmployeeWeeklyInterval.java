package com.appointmentplatform.employee.domain;
import jakarta.persistence.*;
import java.time.*;
@Embeddable
public class EmployeeWeeklyInterval {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private DayOfWeek dayOfWeek;
    @Column(nullable = false)
    private LocalTime startTime;
    @Column(nullable = false)
    private LocalTime endTime;

    protected EmployeeWeeklyInterval() {}

    public EmployeeWeeklyInterval(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime; this.endTime = endTime;
    }
    public DayOfWeek dayOfWeek() {
        return dayOfWeek;
    }
    public LocalTime startTime() {
        return startTime;
    }
    public LocalTime endTime() {
        return endTime;
    }
}
