package com.appointmentplatform.branch;
import java.time.LocalDate;
import java.util.List;
public record BranchDailySchedule(LocalDate date, boolean dateOverride, List<BranchTimeInterval> workingHours) {}
