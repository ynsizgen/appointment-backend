package com.appointmentplatform.employee;

import java.time.LocalTime;

public record EmployeeTimeIntervalDetails(LocalTime startTime, LocalTime endTime) {}
