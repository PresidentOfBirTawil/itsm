package com.itsm.api;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * How many API requests one key may make: {@code itsm.api.requests-per-minute}
 * (120 by default), counted per calendar minute. Protects the service desk
 * from a runaway script; well-behaved integrations never notice it.
 */
@Component
public class ApiRateLimiter {

    /** The answer for one request: allowed or not, how many are left, and when the count starts again. */
    public record Decision(boolean allowed, int limit, int remaining, long resetSeconds) {
    }

    private final Map<Long, int[]> counts = new ConcurrentHashMap<>();   // key id -> {minute, count}
    private final int limit;
    private final Clock clock;

    @Autowired
    public ApiRateLimiter(@Value("${itsm.api.requests-per-minute:120}") int limit) {
        this(limit, Clock.systemUTC());
    }

    public ApiRateLimiter(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    public Decision take(Long tokenId) {
        long now = clock.millis();
        int minute = (int) (now / 60_000);
        long resetSeconds = 60 - (now / 1000) % 60;
        int[] window = counts.compute(tokenId, (id, current) -> {
            if (current == null || current[0] != minute) {
                return new int[]{minute, 1};
            }
            current[1]++;
            return current;
        });
        if (counts.size() > 10_000) {
            counts.values().removeIf(old -> old[0] != minute);
        }
        int used = window[1];
        return new Decision(used <= limit, limit, Math.max(0, limit - used), resetSeconds);
    }
}
