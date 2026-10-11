package com.appointmentplatform.branch.persistence;
import com.appointmentplatform.branch.domain.BranchWorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BranchWorkingHoursRepository extends JpaRepository<BranchWorkingHours, UUID> {
    List<BranchWorkingHours> findAllByBranchId(UUID branchId);
    void deleteByBranchId(UUID branchId);
}
