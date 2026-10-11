package com.appointmentplatform.identity.application;

public record RegistrationCommand(String email, String password, String firstName, String lastName,
        String phone, String displayName) {}
