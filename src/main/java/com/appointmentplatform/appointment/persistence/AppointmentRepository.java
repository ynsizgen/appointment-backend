package com.appointmentplatform.appointment.persistence;

import com.appointmentplatform.appointment.domain.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @Query("""
    SELECT a FROM Appointment a
    WHERE a.employeeId = :employeeId
      AND a.startsAt < :rangeEnd
      AND a.endsAt > :rangeStart
    ORDER BY a.startsAt
    """)
    List<Appointment> findBlockingInRange(
            @Param("employeeId") UUID employeeId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd);
}
