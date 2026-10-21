package com.appointmentplatform.notification;

public interface SmsSender {
    void sendVerificationCode(String phone, String code);
    void sendMessage(String phone, String body);          // Randevu SMS’i için
}
