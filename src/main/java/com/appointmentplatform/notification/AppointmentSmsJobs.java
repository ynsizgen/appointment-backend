package com.appointmentplatform.notification;

import java.util.UUID;

public interface AppointmentSmsJobs {
    void enqueue(AppointmentSmsJob appointmentSmsJob);
}
