package com.appointmentplatform.appointment.application;

import com.appointmentplatform.appointment.AppointmentSource;
import com.appointmentplatform.appointment.AppointmentStatus;

import java.time.Instant;
import java.util.UUID;

public record DailyBranchAppointmentsDetails(
        UUID appointmentId,
        UUID branchId,
        UUID employeeId,
        String employeeFirstName,
        String employeeLastName,
        String serviceName,
        int durationMinutes,
        String customerFirstName,
        String customerLastName,
        String customerPhone,
        Instant startsAt,
        Instant endsAt,
        AppointmentStatus appointmentStatus,
        AppointmentSource appointmentSource
        ) {}
