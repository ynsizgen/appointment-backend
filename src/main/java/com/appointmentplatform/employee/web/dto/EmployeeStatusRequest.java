package com.appointmentplatform.employee.web.dto;

import jakarta.validation.constraints.NotNull;

public record EmployeeStatusRequest(
    @NotNull
    Boolean active
) {}