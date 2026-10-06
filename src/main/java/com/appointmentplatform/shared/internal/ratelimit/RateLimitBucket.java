package com.appointmentplatform.shared.internal.ratelimit;

import jakarta.persistence.*;

@Entity
@Table(name = "rate_limits", schema = "appointment")
public class RateLimitBucket {
    @EmbeddedId private RateLimitId id;
    @Column(nullable = false) private int attempts;
    protected RateLimitBucket() {}
    public int attempts() { return attempts; }
}
