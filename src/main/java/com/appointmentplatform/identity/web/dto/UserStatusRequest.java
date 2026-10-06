package com.appointmentplatform.identity.web.dto;
import jakarta.validation.constraints.NotNull;
public record UserStatusRequest(@NotNull Boolean enabled, @NotNull Boolean subscriptionActive) {}
