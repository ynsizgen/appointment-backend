package com.appointmentplatform.identity.application;

import java.time.Instant;

public record PhoneVerificationDetails(String verificationToken, Instant expiresAt) {
}
