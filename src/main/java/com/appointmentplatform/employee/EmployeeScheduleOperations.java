package com.appointmentplatform.employee;



import java.time.LocalDate;
import java.util.UUID;

public interface EmployeeScheduleOperations {
    EmployeeDailyScheduleDetails daily(UUID brandId, UUID employeeId, LocalDate date);
}
