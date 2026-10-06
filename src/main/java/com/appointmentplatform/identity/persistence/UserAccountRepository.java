package com.appointmentplatform.identity.persistence;

import com.appointmentplatform.identity.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmail(String email);

    org.springframework.data.domain.Page<UserAccount> findByRole(com.appointmentplatform.identity.domain.Role role, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findForUpdate(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query("update UserAccount u set u.lastMfaStep = :step where u.id = :id and u.lastMfaStep < :step")
    int consumeMfa(@Param("id") UUID id, @Param("step") long step);
}
