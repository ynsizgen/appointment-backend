package com.appointmentplatform.appointment.persistence;

import com.appointmentplatform.appointment.domain.Appointment;
import com.appointmentplatform.appointment.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @Query("""
    SELECT a FROM Appointment a
    WHERE a.employeeId = :employeeId
      AND a.status IN :statuses
      AND a.startsAt < :rangeEnd
      AND a.endsAt > :rangeStart
    ORDER BY a.startsAt
    """)
    List<Appointment> findBlockingInRange(
            @Param("employeeId") UUID employeeId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            @Param("statuses") List<AppointmentStatus> statuses);

    @Query("""
    SELECT COUNT(a) > 0
    FROM Appointment a
    WHERE a.employeeId = :employeeId
      AND a.status IN :statuses
      AND a.startsAt < :endsAt
      AND a.endsAt > :startsAt
    """)
    boolean existsOverlapping(
            @Param("employeeId") UUID employeeId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("statuses") List<AppointmentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT a from Appointment a WHERE a.id = :id
    """)
    Optional<Appointment> findByIdForUpdate(
            @Param("id") UUID id
    );

    Optional<Appointment> findAllByAccessTokenHash(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT a FROM Appointment a
    WHERE a.accessTokenHash = :tokenHash
    """)
    Optional<Appointment> findByAccessTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Query("""
    SELECT a
    FROM Appointment a
    where a.branchId=:branchId
    and a.startsAt >= :rangeStart
    and a.startsAt < :rangeEnd
    order by a.startsAt,a.id
    """)
    List<Appointment> findByBranchIdAndDate(
            @Param("branchId") UUID branchId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd
            );

}
