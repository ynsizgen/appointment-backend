package com.appointmentplatform.shared;

public interface RateLimits {
    void check(String scope, String subject, int limit, long windowSeconds);
}
