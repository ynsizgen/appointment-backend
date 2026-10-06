package com.appointmentplatform.identity.persistence;

import com.appointmentplatform.identity.domain.PendingForgotPassword;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PendingForgotBrandPasswordRepository extends JpaRepository<PendingForgotPassword, UUID> {
    Optional<PendingForgotPassword> findByUserId(UUID userId);

    @Modifying
    @Query(value = "delete from appointment.pending_password_resets where user_id = :userId", nativeQuery = true)
    void deleteByUserId(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from PendingForgotPassword f where f.tokenHash = :hash")
    Optional<PendingForgotPassword> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying(flushAutomatically = true)
    @Query("delete from PendingForgotPassword f where f.expiresAt <= :now")
    void deleteExpired(@Param("now") Instant now);

    // Serialize reset requests for the same email across application instances.
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtextextended(:email, 0))", nativeQuery = true)
    int lockEmail(@Param("email") String email);
}
