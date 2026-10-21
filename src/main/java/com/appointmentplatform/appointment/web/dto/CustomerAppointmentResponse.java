package com.appointmentplatform.appointment.web.dto;

import com.appointmentplatform.appointment.AppointmentStatus;
import com.appointmentplatform.appointment.application.CustomerAppointmentDetails;

import java.time.Instant;


public record CustomerAppointmentResponse (String serviceName, int durationMinutes, String customerFirstName,
                                            String customerLastName, String customerPhone, String customerEmail,
                                            Instant startsAt, AppointmentStatus status, Instant accessTokenExpiresAt
                                           ) {
    public static CustomerAppointmentResponse from(CustomerAppointmentDetails customerAppointmentDetails) {
        return new CustomerAppointmentResponse(
                customerAppointmentDetails.serviceName(),
                customerAppointmentDetails.durationMinutes(),
                customerAppointmentDetails.customerFirstName(),
                customerAppointmentDetails.customerLastName(),
                customerAppointmentDetails.customerPhone(),
                customerAppointmentDetails.customerEmail(),
                customerAppointmentDetails.startsAt(),
                customerAppointmentDetails.status(),
                customerAppointmentDetails.accessTokenExpiresAt());
    }
}
