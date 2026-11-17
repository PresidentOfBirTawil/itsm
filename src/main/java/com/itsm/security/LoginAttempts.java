package com.itsm.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Protection against password guessing: after {@value #MAX_FAILURES} wrong
 * passwords for one email address within {@link #WINDOW}, sign-in for that
 * address is paused for {@link #LOCK}. Then it works again by itself.
 *
 * <p>Addresses that have no account are counted the same way, so the answer
 * never tells an attacker whether an account exists. The counts are kept in
 * memory: a restart forgets them, which is fine for a pause of minutes.</p>
 */
@Component
public class LoginAttempts {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);
    public static final Duration LOCK = Duration.ofMinutes(15);
    /** A guard against someone filling the memory with made-up addresses. */
    static final int MAX_TRACKED = 50_000;

    private final Map<String, Record> records = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public LoginAttempts() {
        this(Clock.systemUTC());
    }

    /** With another clock, for example in tests. */
    public LoginAttempts(Clock clock) {
        this.clock = clock;
    }

    /** Whether sign-in for this address is paused right now. */
    public boolean isLocked(String email) {
        Record record = records.get(key(email));
        return record != null && record.isLocked(clock.instant());
    }

    /**
     * A wrong password (or an address without an account).
     *
     * @return true if this failure paused sign-in for the address
     */
    public boolean failed(String email) {
        Instant now = clock.instant();
        if (records.size() >= MAX_TRACKED) {
            forgetOld();
        }
        Record record = records.computeIfAbsent(key(email), ignored -> new Record());
        return record.fail(now);
    }

    /** A successful sign-in: the count starts again from zero. */
    public void succeeded(String email) {
        records.remove(key(email));
    }

    /** Drops what no longer matters, every few minutes. */
    @Scheduled(fixedDelay = 300_000)
    public void forgetOld() {
        Instant now = clock.instant();
        records.values().removeIf(record -> record.isForgettable(now));
    }

    private static String key(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    /** The recent failures for one address, and until when it is paused. */
    private static final class Record {

        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant lockedUntil;

        synchronized boolean isLocked(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }

        synchronized boolean fail(Instant now) {
            if (isLocked(now)) {
                return false;
            }
            failures.addLast(now);
            while (!failures.isEmpty() && failures.peekFirst().isBefore(now.minus(WINDOW))) {
                failures.removeFirst();
            }
            if (failures.size() >= MAX_FAILURES) {
                failures.clear();
                lockedUntil = now.plus(LOCK);
                return true;
            }
            return false;
        }

        synchronized boolean isForgettable(Instant now) {
            boolean lockOver = lockedUntil == null || !now.isBefore(lockedUntil);
            boolean failuresOld = failures.isEmpty() || failures.peekLast().isBefore(now.minus(WINDOW));
            return lockOver && failuresOld;
        }
    }
}
