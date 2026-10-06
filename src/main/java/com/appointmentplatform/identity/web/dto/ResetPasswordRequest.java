package com.appointmentplatform.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message="Sıfırlama anahtarı zorunludur.")
        @Pattern(regexp="[A-Za-z0-9_-]{43}", message="Sıfırlama anahtarı geçersiz biçimde.") String token,
        @NotBlank(message="Yeni parola zorunludur.")
        @Size(min=12, max=128, message="Parola 12 ile 128 karakter arasında olmalıdır.") String password) {}
