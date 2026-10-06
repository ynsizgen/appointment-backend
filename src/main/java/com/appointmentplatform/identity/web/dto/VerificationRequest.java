package com.appointmentplatform.identity.web.dto;

import jakarta.validation.constraints.*;

public record VerificationRequest(
        @NotBlank(message="Doğrulama anahtarı zorunludur.")
        @Pattern(regexp="[A-Za-z0-9_-]{43}", message="Doğrulama anahtarı geçersiz biçimde.")
        String token) {}
