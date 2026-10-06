package com.appointmentplatform.identity.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
public class VerificationPage {
    @GetMapping(value="/verify-email",produces=MediaType.TEXT_HTML_VALUE)
    public ClassPathResource page() {
        return new ClassPathResource("static/verify-email.html");
    }

    @GetMapping(value="/reset-password", produces=MediaType.TEXT_HTML_VALUE)
    public ClassPathResource resetPasswordPage() {
        return new ClassPathResource("static/reset-password.html");
    }
}
