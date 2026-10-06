package com.appointmentplatform.notification.persistence;

import com.appointmentplatform.notification.domain.EmailJob;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EmailJobRepository extends JpaRepository<EmailJob, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select j from EmailJob j where j.state = 'PENDING' and j.expiresAt > :now order by j.createdAt, j.id")
    List<EmailJob> findNext(@Param("now") Instant now, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from EmailJob j where j.id = :id")
    Optional<EmailJob> findForUpdate(@Param("id") UUID id);

    Optional<EmailJob> findByRegistrationId(UUID registrationId);
    Optional<EmailJob> findByPasswordResetId(UUID passwordResetId);
}
