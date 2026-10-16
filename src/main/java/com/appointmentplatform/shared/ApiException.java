package com.appointmentplatform.shared;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final long retryAfterSeconds;
    public ApiException(HttpStatus status, String message) {
        this(status, message, 900);
    }

    public ApiException(HttpStatus status, String message, long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public HttpStatus status() { return status; }
}
