package com.appointmentplatform.appointment.application;

import com.appointmentplatform.appointment.AppointmentStatus;

import java.time.Instant;

public record CustomerAppointmentDetails (
        String serviceName,
        int durationMinutes,
        String customerFirstName,
        String customerLastName,
        String customerPhone,
        String customerEmail,
        Instant startsAt,
        AppointmentStatus status,
        Instant accessTokenExpiresAt

){
}
