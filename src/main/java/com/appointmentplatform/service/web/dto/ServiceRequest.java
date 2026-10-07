package com.appointmentplatform.service.web.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ServiceRequest(
        @NotBlank(message = "Hizmet adı zorunludur.")
        @Size(max = 200, message = "Hizmet adı en fazla 200 karakter olabilir.")
        String displayName,

        @Positive(message = "Hizmet süresi sıfırdan büyük olmalıdır.")
        int durationMinutes
) {}
