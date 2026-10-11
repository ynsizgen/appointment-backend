package com.appointmentplatform.branch;
import java.time.LocalTime;
public record BranchTimeInterval(LocalTime startTime, LocalTime endTime) {}
