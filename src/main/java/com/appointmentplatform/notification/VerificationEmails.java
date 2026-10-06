package com.appointmentplatform.notification;

public interface VerificationEmails {
    void enqueue(VerificationEmail email);
}
