package com.appointmentplatform.appointment.web.dto;

import com.appointmentplatform.appointment.application.AppointmentDetails;
import com.appointmentplatform.appointment.AppointmentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentCreationResponse(
        UUID id,
        UUID branchId,
        UUID employeeId,
        UUID serviceId,
        String serviceName,
        int durationMinutes,
        Instant startsAt,
        Instant endsAt,
        AppointmentStatus status
) {
    public static AppointmentCreationResponse from(AppointmentDetails details) {
        return new AppointmentCreationResponse(
                details.id(),
                details.branchId(),
                details.employeeId(),
                details.serviceId(),
                details.serviceName(),
                details.durationMinutes(),
                details.startsAt(),
                details.endsAt(),
                details.status()
        );
    }
}