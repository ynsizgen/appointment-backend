package com.appointmentplatform.notification;

import java.util.UUID;

public interface AppointmentReminderEligibility {
    boolean canSendReminder(UUID appointmentId);
}