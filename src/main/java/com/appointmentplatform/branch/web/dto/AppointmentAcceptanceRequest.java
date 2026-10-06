package com.appointmentplatform.branch.web.dto;
import jakarta.validation.constraints.NotNull;
public record AppointmentAcceptanceRequest(@NotNull Boolean acceptingAppointments) {}
