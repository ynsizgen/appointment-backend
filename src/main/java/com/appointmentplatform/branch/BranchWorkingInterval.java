package com.appointmentplatform.branch;
import java.time.DayOfWeek;
import java.time.LocalTime;
public record BranchWorkingInterval(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {}
