package com.appointmentplatform.notification.infrastructure;

import com.appointmentplatform.shared.Crypto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class VerificationMailSender {
    private final JavaMailSender sender;
    private final Crypto crypto;
    private final String from;

    public VerificationMailSender(JavaMailSender sender, Crypto crypto, @Value("${app.mail.from}") String from) {
        this.sender = sender;
        this.crypto = crypto;
        this.from = from;
    }

    public void send(String recipient, String encryptedBody) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Brand e-posta doğrulaması");
        message.setText(crypto.decrypt(encryptedBody));
        sender.send(message);
    }
}
