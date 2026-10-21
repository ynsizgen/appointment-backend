package com.appointmentplatform.notification;

import java.time.Instant;
import java.util.UUID;

public record AppointmentSmsJob(
        UUID appointmentId,
        String recipient,
        String body,
        SmsJobType type,
        Instant nextAttemptAt
) {}
