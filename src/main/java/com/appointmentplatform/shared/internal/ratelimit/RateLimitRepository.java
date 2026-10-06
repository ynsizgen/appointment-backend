package com.appointmentplatform.shared.internal.ratelimit;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RateLimitRepository extends JpaRepository<RateLimitBucket, RateLimitId> {
    // An atomic upsert is required for the first concurrent requests in a window.
    @Modifying
    @Query(value = """
        insert into appointment.rate_limits(bucket_key, window_start, attempts) values (:key, :window, 1)
        on conflict (bucket_key, window_start)
        do update set attempts = appointment.rate_limits.attempts + 1
        """, nativeQuery = true)
    void increment(@Param("key") String key, @Param("window") long window);

    @Modifying
    @Query("delete from RateLimitBucket b where b.id.windowStart < :before")
    void deleteExpired(@Param("before") long before);
}
