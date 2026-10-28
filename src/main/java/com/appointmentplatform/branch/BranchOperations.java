package com.appointmentplatform.branch;
import com.appointmentplatform.brand.domain.Brand;

import java.util.UUID;
import  java.util.*;
/** Branch ownership checks for other modules. */
public interface BranchOperations {
    List<PublicBranchDetails> acceptingBranches(UUID brandId);

    List<BranchWorkingInterval> weeklyHours(UUID branchId, UUID brandId);

    BranchDailySchedule dailyHours(UUID branchId, UUID brandId, java.time.LocalDate date);

    UUID brandId(UUID branchId);

    void requireAcceptingAppointments(UUID branchId, UUID brandId);

    void requireOwnedBy(UUID branchId, UUID brandId);
}
