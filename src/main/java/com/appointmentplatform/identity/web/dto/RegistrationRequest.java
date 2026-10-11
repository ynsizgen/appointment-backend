package com.appointmentplatform.identity.web.dto;

import jakarta.validation.constraints.*;

public record RegistrationRequest(
        @NotBlank(message="E-posta zorunludur.")
        @Email(message="Geçerli bir e-posta adresi girilmelidir.")
        @Size(max=254, message="E-posta en fazla 254 karakter olabilir.")
        String email,
        @NotBlank(message="Parola zorunludur.")
        @Size(min=12, max=128, message="Parola 12 ile 128 karakter arasında olmalıdır.")
        String password,
        @NotBlank(message="Ad zorunludur.")
        @Size(max=100, message="Ad en fazla 100 karakter olabilir.")
        String firstName,
        @NotBlank(message="Soyad zorunludur.")
        @Size(max=100, message="Soyad en fazla 100 karakter olabilir.")
        String lastName,
        @NotBlank(message="Telefon numarası zorunludur.")
        @Pattern(regexp="\\+[1-9][0-9]{7,14}", message="Telefon numarası +905551112233 biçiminde olmalıdır.")
        String phone,
        @NotBlank(message="Marka adı zorunludur.")
        @Size(max=200, message="Marka adı en fazla 200 karakter olabilir.")
        String displayName) {}
