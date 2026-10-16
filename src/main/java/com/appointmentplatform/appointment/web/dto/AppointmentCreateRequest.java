package com.appointmentplatform.appointment.web.dto;

import jakarta.validation.constraints.*;


import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentCreateRequest(
        @NotNull UUID employeeId,
        @NotNull UUID serviceId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @NotBlank @Size(max = 100) String customerFirstName,
        @NotBlank @Size(max = 100) String customerLastName,
        @NotBlank
        @Pattern(regexp = "\\+[1-9][0-9]{7,14}")
        String customerPhone,
        @Email @Size(max = 254) String customerEmail
) {}
