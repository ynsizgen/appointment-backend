package com.appointmentplatform.employee.application;

import java.time.LocalTime;

public record EmployeeTimeIntervalDetails(LocalTime startTime, LocalTime endTime) {}
