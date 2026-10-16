package com.appointmentplatform.identity.persistence;

import com.appointmentplatform.identity.domain.PhoneVerification;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, String> {
    @Modifying
    @Query(value = """
            INSERT INTO appointment.phone_verifications(phone_hash, attempts)
            VALUES (:phoneHash, 0)
            ON CONFLICT (phone_hash) DO NOTHING
            """, nativeQuery = true)
    void ensureExists(@Param("phoneHash") String phoneHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PhoneVerification p WHERE p.phoneHash = :phoneHash")
    Optional<PhoneVerification> findForUpdate(@Param("phoneHash") String phoneHash);
}
