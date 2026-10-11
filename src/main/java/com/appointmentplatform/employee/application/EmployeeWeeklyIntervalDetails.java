package com.appointmentplatform.employee.application;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record EmployeeWeeklyIntervalDetails(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {}
