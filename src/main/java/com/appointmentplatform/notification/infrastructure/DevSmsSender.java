package com.appointmentplatform.notification.infrastructure;

import com.appointmentplatform.notification.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev & !prod")
public class DevSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(DevSmsSender.class);

    @Override
    public void sendVerificationCode(String phone, String code) {
        log.info("DEV SMS — phone ending {} — verification code: {}", phone.substring(phone.length() - 4), code);
    }

    @Override
    public void sendMessage(String phone, String body) {
        log.info("DEV SMS — phone ending {} — notificatioın: {}", phone.substring(phone.length() - 4), body);
    }
}
