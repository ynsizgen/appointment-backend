package com.appointmentplatform.shared.internal.ratelimit;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RateLimitId implements Serializable {
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "bucket_key", columnDefinition = "char(64)")
    private String bucketKey;
    @Column(name = "window_start")
    private long windowStart;

    protected RateLimitId() {}
    public RateLimitId(String bucketKey, long windowStart) {
        this.bucketKey = bucketKey;
        this.windowStart = windowStart;
    }
    @Override public boolean equals(Object other) {
        return other instanceof RateLimitId id && windowStart == id.windowStart && Objects.equals(bucketKey, id.bucketKey);
    }
    @Override public int hashCode() { return Objects.hash(bucketKey, windowStart); }
}
