package com.appointmentplatform.notification;

import java.time.Instant;
import java.util.UUID;

/** Module command, not a persistence entity. */
public record ForgotPasswordEmail(UUID forgotId, String recipient, String body, Instant expiresAt) {}
