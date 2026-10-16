package com.appointmentplatform.branch;
import java.util.UUID;
import  java.util.*;
/** Branch ownership checks for other modules. */
public interface BranchOperations {

    List<BranchWorkingInterval> weeklyHours(UUID branchId, UUID brandId);

    BranchDailySchedule dailyHours(UUID branchId, UUID brandId, java.time.LocalDate date);

    UUID brandId(UUID branchId);

    void requireOwnedBy(UUID branchId, UUID brandId);
}
