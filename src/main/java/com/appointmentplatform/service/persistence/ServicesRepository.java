package com.appointmentplatform.service.persistence;

import com.appointmentplatform.service.domain.Service;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServicesRepository extends JpaRepository<Service, UUID> {
    List<Service> findByBrandId(UUID brandId);

    @Query("""
            SELECT s FROM Service s
            WHERE EXISTS (
                SELECT assignment FROM BranchServiceAssignment assignment
                WHERE assignment.serviceId = s.id AND assignment.branchId = :branchId
            )
            ORDER BY s.displayName, s.id
            """)
    List<Service> findAssignedToBranch(@Param("branchId") UUID branchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Service s WHERE s.id = :serviceId AND s.brandId = :brandId")
    Optional<Service> findOwnedByForUpdate(@Param("serviceId") UUID serviceId,
                                          @Param("brandId") UUID brandId);

    boolean existsByIdAndBrandId(UUID id, UUID brandId);
}
