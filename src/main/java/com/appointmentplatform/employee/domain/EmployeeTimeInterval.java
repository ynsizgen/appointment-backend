package com.appointmentplatform.employee.domain;
import jakarta.persistence.*;
import java.time.*;
@Embeddable
public class EmployeeTimeInterval {
    @Column(nullable = false) private LocalTime startTime;
    @Column(nullable = false) private LocalTime endTime;
    protected EmployeeTimeInterval() {}
    public EmployeeTimeInterval(LocalTime startTime, LocalTime endTime) {
        
        this.startTime = startTime; this.endTime = endTime;
    }
    
    public LocalTime startTime() { return startTime; }
    public LocalTime endTime() { return endTime; }
}
