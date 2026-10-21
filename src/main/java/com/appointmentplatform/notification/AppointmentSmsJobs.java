package com.appointmentplatform.notification;

public interface AppointmentSmsJobs {

    void enqueue(AppointmentSmsJob appointmentSmsJob);
}
