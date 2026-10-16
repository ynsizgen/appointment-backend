package com.appointmentplatform.identity;

public interface PhoneVerifications {
    void consume(String phone, String verificationToken);
}
