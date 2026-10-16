package com.appointmentplatform.notification.infrastructure;

import com.appointmentplatform.notification.SmsSender;
import com.appointmentplatform.shared.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev | prod")
public class UnavailableSmsSender implements SmsSender {
    @Override
    public void sendVerificationCode(String phone, String code) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS sağlayıcısı henüz yapılandırılmadı.");
    }
}
