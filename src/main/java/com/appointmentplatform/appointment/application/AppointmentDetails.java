package com.appointmentplatform.appointment.application;

import com.appointmentplatform.appointment.AppointmentStatus;

import java.time.Instant;
import java.util.UUID;

public record AppointmentDetails(
        UUID id,
        UUID branchId,
        UUID employeeId,
        UUID serviceId,
        String serviceName,
        int durationMinutes,
        Instant startsAt,
        Instant endsAt,
        AppointmentStatus status,
        Instant cancellationDeadline,
        String customerPhone
) {}
