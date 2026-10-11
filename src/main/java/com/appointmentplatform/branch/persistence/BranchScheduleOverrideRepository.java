package com.appointmentplatform.branch.persistence;
import com.appointmentplatform.branch.domain.BranchScheduleOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;
public interface BranchScheduleOverrideRepository extends JpaRepository<BranchScheduleOverride, UUID> {
    Optional<BranchScheduleOverride> findByBranchIdAndDate(UUID branchId, LocalDate date);
}
