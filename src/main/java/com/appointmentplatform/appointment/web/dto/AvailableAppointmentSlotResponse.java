package com.appointmentplatform.appointment.web.dto;

import com.appointmentplatform.appointment.application.AvailableAppointmentSlotDetails;

public record AvailableAppointmentSlotResponse(AvailableAppointmentSlotDetails availableAppointmentSlotDetails) {
    public static AvailableAppointmentSlotResponse from(AvailableAppointmentSlotDetails availableAppointmentSlotDetails) {
        return new AvailableAppointmentSlotResponse(availableAppointmentSlotDetails);
    }
}
