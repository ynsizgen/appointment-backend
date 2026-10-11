package com.appointmentplatform.employee.persistence;
import com.appointmentplatform.employee.domain.EmployeeScheduleOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface EmployeeScheduleOverrideRepository extends JpaRepository<EmployeeScheduleOverride, UUID> {
    java.util.Optional<EmployeeScheduleOverride> findByEmployeeIdAndDate(UUID employeeId, java.time.LocalDate date);
}
