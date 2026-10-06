package com.appointmentplatform.identity.persistence;

import com.appointmentplatform.identity.domain.PendingRegistration;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, UUID> {
    Optional<PendingRegistration> findByEmail(String email);
    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PendingRegistration r where r.tokenHash = :hash")
    Optional<PendingRegistration> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying(flushAutomatically = true)
    @Query("delete from PendingRegistration r where r.email = :email and r.expiresAt <= :now")
    void deleteExpiredEmail(@Param("email") String email, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("delete from PendingRegistration r where r.expiresAt <= :now")
    void deleteExpired(@Param("now") Instant now);

    // Registration has no row to lock yet. PostgreSQL serializes competing requests
    // for the same email across application instances. All access remains via JPA.
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtextextended(:email, 0))", nativeQuery = true)
    int lockEmail(@Param("email") String email);
}
