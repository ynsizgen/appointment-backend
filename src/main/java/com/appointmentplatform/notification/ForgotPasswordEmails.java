package com.appointmentplatform.notification;

public interface ForgotPasswordEmails {
    void enqueue(ForgotPasswordEmail email);
}
