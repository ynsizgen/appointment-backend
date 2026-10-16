package com.appointmentplatform.identity.web;

import com.appointmentplatform.identity.application.PhoneVerificationService;
import com.appointmentplatform.identity.web.dto.PhoneCodeRequest;
import com.appointmentplatform.identity.web.dto.PhoneCodeVerificationRequest;
import com.appointmentplatform.identity.web.dto.PhoneVerificationResponse;
import com.appointmentplatform.shared.RateLimits;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/phone-verifications")
public class PhoneVerificationController {
    private final PhoneVerificationService service;
    private final RateLimits limits;

    public PhoneVerificationController(PhoneVerificationService service, RateLimits limits) {
        this.service = service;
        this.limits = limits;
    }

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void request(@Valid @RequestBody PhoneCodeRequest body, HttpServletRequest request) {
        limits.check("phone-otp-send-ip", request.getRemoteAddr(), 20, 900);
        service.request(body.phone());
    }

    @PostMapping("/verify")
    public PhoneVerificationResponse verify(@Valid @RequestBody PhoneCodeVerificationRequest body, HttpServletRequest request) {
        limits.check("phone-otp-verify-ip", request.getRemoteAddr(), 60, 900);
        var verified = service.verify(body.phone(), body.code());
        return new PhoneVerificationResponse(verified.verificationToken(), verified.expiresAt());
    }
}
