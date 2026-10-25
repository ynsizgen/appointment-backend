package com.appointmentplatform.appointment.web.dto;

import com.appointmentplatform.appointment.application.DailyBranchAppointmentsDetails;

import java.util.List;

public record DailyBranchAppointmentsResponse(List<DailyBranchAppointmentsDetails> details) {
    public static DailyBranchAppointmentsResponse from(List<DailyBranchAppointmentsDetails> details) {
        return new DailyBranchAppointmentsResponse(details);
    }
}
