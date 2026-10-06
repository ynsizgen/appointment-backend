package com.appointmentplatform.notification;

import java.time.Instant;
import java.util.UUID;

/** Module command, not a persistence entity. */
public record VerificationEmail(UUID registrationId, String recipient, String body, Instant expiresAt) {}
