package com.appointmentplatform.identity.application;

import java.time.Instant;
import java.util.UUID;

public record AccountDetails(UUID id, String role, UUID brandId, String firstName, String lastName, boolean subscriptionActive, boolean enabled, Instant passwordChangedAt, Instant sessionsRevokedAt) {}
