package com.appointmentplatform.notification;

public interface SmsSender {
    void sendVerificationCode(String phone, String code);
}
