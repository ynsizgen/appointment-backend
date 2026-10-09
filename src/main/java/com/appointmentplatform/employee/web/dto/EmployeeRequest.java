package com.appointmentplatform.employee.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmployeeRequest(
        @NotBlank(message = "İsim zorunludur.")
        @Size(max = 20, message = "İsim 20 karakterden büyük olamaz.")
        String firstName,
        @NotBlank(message = "Soyisim zorunludur.")
        @Size(max = 30, message = "Soyadı 30 karakterden büyük olamaz.")
        String lastName,
        boolean active
) {}
