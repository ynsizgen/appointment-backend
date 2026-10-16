package com.appointmentplatform.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneCodeRequest(
        @NotBlank @Pattern(regexp = "\\+[1-9][0-9]{7,14}") String phone) {
}
