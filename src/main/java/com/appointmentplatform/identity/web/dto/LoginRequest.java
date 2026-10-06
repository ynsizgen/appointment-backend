package com.appointmentplatform.identity.web.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotBlank(message="E-posta zorunludur.")
        @Email(message="Geçerli bir e-posta adresi girilmelidir.")
        @Size(max=254, message="E-posta en fazla 254 karakter olabilir.")
        String email,

        @NotBlank(message="Parola zorunludur.")
        @Size(max=128, message="Parola en fazla 128 karakter olabilir.")
        String password,

        @Pattern(regexp="[0-9]{6}", message="MFA kodu 6 rakamdan oluşmalıdır.")
        String mfaCode) {}
