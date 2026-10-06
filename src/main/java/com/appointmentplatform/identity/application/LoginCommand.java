package com.appointmentplatform.identity.application;

public record LoginCommand(String email, String password, String mfaCode) {}
