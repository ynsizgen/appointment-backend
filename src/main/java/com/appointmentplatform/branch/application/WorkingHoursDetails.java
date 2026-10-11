package com.appointmentplatform.branch.application;
import java.time.DayOfWeek;
import java.time.LocalTime;
public record WorkingHoursDetails(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {}
