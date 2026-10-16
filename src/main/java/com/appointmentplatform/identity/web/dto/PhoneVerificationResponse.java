package com.appointmentplatform.identity.web.dto;

import java.time.Instant;

public record PhoneVerificationResponse(String verificationToken, Instant expiresAt) {
}
