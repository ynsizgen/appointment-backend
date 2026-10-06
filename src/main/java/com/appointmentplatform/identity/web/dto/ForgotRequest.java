package com.appointmentplatform.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotRequest(
        @NotBlank(message="E-posta zorunludur.")
        @Email(message="Geçerli bir e-posta adresi girilmelidir.")
        @Size(max=254, message="E-posta en fazla 254 karakter olabilir.")
        String email
) {}
