package com.appointmentplatform.employee.persistence;

import com.appointmentplatform.employee.domain.Employee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findForUpdate(@Param("id") UUID id);
    List<Employee> findAllByBranchId(UUID branchId);

    @Query("""
        SELECT e
        FROM Employee e
        WHERE e.branchId = :branchId
          AND e.active = true
          AND EXISTS (
              SELECT a
              FROM EmployeeServiceAssignment a
              WHERE a.employeeId = e.id
                AND a.serviceId = :serviceId
          )
        ORDER BY e.firstName, e.lastName, e.id
        """)
    List<Employee> findAvailableEmployees(
            @Param("serviceId") UUID serviceId,
            @Param("branchId") UUID branchId
    );
}
