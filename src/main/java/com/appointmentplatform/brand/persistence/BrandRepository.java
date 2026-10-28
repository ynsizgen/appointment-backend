package com.appointmentplatform.brand.persistence;

import com.appointmentplatform.brand.domain.Brand;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BrandRepository extends JpaRepository<Brand, UUID> {
    Optional<Brand> findBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Brand t where t.id = :id")
    Optional<Brand> findForUpdate(@Param("id") UUID id);
}
