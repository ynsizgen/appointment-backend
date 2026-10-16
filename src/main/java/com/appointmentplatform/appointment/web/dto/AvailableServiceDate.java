package com.appointmentplatform.appointment.web.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record AvailableServiceDate(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
}
