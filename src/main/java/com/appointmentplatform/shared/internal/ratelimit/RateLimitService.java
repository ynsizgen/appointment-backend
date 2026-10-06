package com.appointmentplatform.shared.internal.ratelimit;

import com.appointmentplatform.shared.*;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class RateLimitService implements RateLimits {
    private final RateLimitRepository repository;
    private final Clock clock;

    public RateLimitService(RateLimitRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void check(String scope, String subject, int limit, long windowSeconds) {
        long window = clock.instant().getEpochSecond() / windowSeconds * windowSeconds;
        String key = Crypto.hash(scope + ":" + subject);
        repository.increment(key, window);
        int attempts = repository.findById(new RateLimitId(key, window)).orElseThrow().attempts();
        if (attempts > limit)
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Çok fazla deneme yapıldı. Daha sonra tekrar deneyin.");
    }

    @Scheduled(fixedDelayString = "${app.mail.cleanup-delay:3600000}")
    @Transactional
    public void cleanup() {
        repository.deleteExpired(clock.instant().getEpochSecond() - 86400);
    }
}
