package com.appointmentplatform.employee.persistence;
import com.appointmentplatform.employee.domain.EmployeeWeeklySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface EmployeeWeeklyScheduleRepository extends JpaRepository<EmployeeWeeklySchedule, UUID> {

}
