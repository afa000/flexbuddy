package com.angel.flexbuddy.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Counts attempts in memory and locks a key when too many land inside a window. It guards the sign-in and sign-up
 * forms, where a lock stops guessing. The counts hold no passwords, are not stored, and reset when the server
 * restarts, which suits the single instance the app runs on. Every change to one key is atomic, so two requests at
 * once cannot both slip under a limit.
 */
@Component
public class AttemptLimiter {

    /** {@code lockFor} may be null, meaning the lock lasts until the window of the first counted attempt ends. */
    public record Policy(int maxAttempts, Duration window, Duration lockFor) {
    }

    public static final String LOGIN_EMAIL = "login-email";
    public static final String LOGIN_IP = "login-ip";
    public static final String REGISTER_IP = "register-ip";

    static final int DEFAULT_MAX_KEYS = 50_000;

    private static final class Entry {
        private final Deque<Instant> attempts = new ArrayDeque<>();
        private volatile Instant lockedUntil;
        private volatile Instant lastAttempt;
        private final Duration window;

        Entry(Duration window) {
            this.window = window;
        }

        boolean isLocked(Instant now) {
            Instant until = lockedUntil;
            return until != null && until.isAfter(now);
        }

        /** Forgets attempts that have left the window. */
        void prune(Instant now) {
            Instant start = now.minus(window);
            while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(start)) {
                attempts.removeFirst();
            }
        }
    }

    private final Map<String, Policy> policies;
    private final Clock clock;
    private final int maxKeys;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Autowired
    public AttemptLimiter(SecurityLimitsProperties properties, Clock clock) {
        this(Map.of(
                LOGIN_EMAIL, new Policy(properties.login().maxFailuresPerEmail(), properties.login().window(),
                        properties.login().lock()),
                LOGIN_IP, new Policy(properties.login().maxFailuresPerIp(), properties.login().window(),
                        properties.login().lock()),
                REGISTER_IP, new Policy(properties.registration().maxPerIp(), properties.registration().window(), null)),
                clock, DEFAULT_MAX_KEYS);
    }

    AttemptLimiter(Map<String, Policy> policies, Clock clock, int maxKeys) {
        this.policies = Map.copyOf(policies);
        this.clock = clock;
        this.maxKeys = maxKeys;
    }

    /** An email as the limiter keys it, so "Angel@Example.com " and "angel@example.com" share one count. */
    public static String emailKey(String rawEmail) {
        return rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isLocked(String bucket, String key) {
        policy(bucket);
        Entry entry = entries.get(id(bucket, key));
        return entry != null && entry.isLocked(clock.instant());
    }

    /** The time left on a lock, for logs and tests. */
    public Optional<Duration> lockedFor(String bucket, String key) {
        policy(bucket);
        Entry entry = entries.get(id(bucket, key));
        Instant now = clock.instant();
        if (entry == null || !entry.isLocked(now)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, entry.lockedUntil));
    }

    /** Records one attempt, and locks the key when the window holds the most the policy allows. */
    public void record(String bucket, String key) {
        Policy policy = policy(bucket);
        String id = id(bucket, key);
        if (!entries.containsKey(id) && entries.size() >= maxKeys) {
            evict();
        }
        Instant now = clock.instant();
        entries.compute(id, (ignored, existing) -> {
            Entry entry = existing != null ? existing : new Entry(policy.window());
            if (entry.isLocked(now)) {
                // More attempts during a lock neither extend it nor count toward the next one.
                return entry;
            }
            entry.lockedUntil = null;
            entry.prune(now);
            entry.attempts.addLast(now);
            entry.lastAttempt = now;
            if (entry.attempts.size() >= policy.maxAttempts()) {
                entry.lockedUntil = policy.lockFor() != null
                        ? now.plus(policy.lockFor())
                        : entry.attempts.peekFirst().plus(policy.window());
                entry.attempts.clear();
            }
            return entry;
        });
    }

    /** Forgets a key's attempts and any lock on it, for example after a successful sign-in or a password reset. */
    public void clear(String bucket, String key) {
        policy(bucket);
        entries.remove(id(bucket, key));
    }

    /** Forgets everything. Meant for tests that share one Spring context. */
    public void clearAll() {
        entries.clear();
    }

    /** Whole minutes a lock in this bucket lasts, for the message the sign-in page shows. */
    public long lockMinutes(String bucket) {
        Policy policy = policy(bucket);
        return (policy.lockFor() != null ? policy.lockFor() : policy.window()).toMinutes();
    }

    /** Drops entries whose attempts and lock have both passed. */
    @Scheduled(fixedDelay = 600_000)
    public void sweep() {
        Instant now = clock.instant();
        for (String id : entries.keySet()) {
            entries.computeIfPresent(id, (ignored, entry) -> {
                if (entry.isLocked(now)) {
                    return entry;
                }
                entry.prune(now);
                return entry.attempts.isEmpty() ? null : entry;
            });
        }
    }

    int size() {
        return entries.size();
    }

    /**
     * Makes room when the map is full: unlocked keys with the oldest last attempt go first, so a flood of made-up
     * emails cannot use up memory or push out a lock that is doing its job.
     */
    private synchronized void evict() {
        int keep = maxKeys - Math.max(1, maxKeys / 10);
        int excess = entries.size() - keep;
        if (excess <= 0) {
            return;
        }
        Instant now = clock.instant();
        entries.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Entry> e) -> e.getValue().isLocked(now))
                        .thenComparing(e -> e.getValue().lastAttempt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .limit(excess)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(entries::remove);
    }

    private Policy policy(String bucket) {
        Policy policy = policies.get(bucket);
        if (policy == null) {
            throw new IllegalArgumentException("Unknown attempt bucket: " + bucket);
        }
        return policy;
    }

    private static String id(String bucket, String key) {
        return bucket + '|' + key;
    }
}
