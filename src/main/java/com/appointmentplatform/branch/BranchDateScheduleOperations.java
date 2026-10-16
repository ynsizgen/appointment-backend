package com.appointmentplatform.branch;

import java.time.LocalDate;
import java.util.UUID;

public interface BranchDateScheduleOperations {
    BranchDailySchedule daily(UUID brandId, UUID branchId, LocalDate date);
}