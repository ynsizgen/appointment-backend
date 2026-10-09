package com.appointmentplatform.branch.persistence;
import com.appointmentplatform.branch.domain.Branch;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface BranchRepository extends JpaRepository<Branch, UUID> {
    List<Branch> findByBrandIdOrderByCreatedAtAscIdAsc(UUID brandId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Branch b where b.id = :id and b.brandId = :brandId")
    Optional<Branch> findForUpdate(@Param("id") UUID id, @Param("brandId") UUID brandId);

    boolean existsByIdAndBrandId(UUID brandId,UUID id);
}
