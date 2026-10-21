package com.appointmentplatform.notification.persistence;

import com.appointmentplatform.notification.domain.SmsJob;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SmsJobRepository extends JpaRepository<SmsJob, UUID> {

    @Query(value = """
        SELECT * FROM appointment.sms_jobs
        WHERE status = :status AND next_attempt_at <= :now
        ORDER BY next_attempt_at, created_at, id
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<SmsJob> findNextDue(
            @Param("status") String status,
            @Param("now") Instant now
    );
}
