package com.appointmentplatform.identity.security;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record AccountPrincipal(UUID id, String role, UUID brandId, Instant authenticatedAt) implements Serializable {}
