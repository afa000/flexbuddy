package com.angel.flexbuddy.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.angel.flexbuddy.security.AttemptLimiter.Policy;

class AttemptLimiterTest {

    private static final Policy FIVE_PER_QUARTER_HOUR = new Policy(5, Duration.ofMinutes(15), Duration.ofMinutes(15));

    private MutableClock clock;
    private AttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-01T12:00:00Z"));
        limiter = limiter(100);
    }

    private AttemptLimiter limiter(int maxKeys) {
        return new AttemptLimiter(Map.of(
                "login-email", FIVE_PER_QUARTER_HOUR,
                "login-ip", new Policy(20, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                "register-ip", new Policy(10, Duration.ofHours(1), null)), clock, maxKeys);
    }

    private void fail(String bucket, String key, int times) {
        for (int i = 0; i < times; i++) {
            limiter.record(bucket, key);
        }
    }

    @Test
    void fourFailuresStayUnlockedAndTheFifthLocks() {
        fail("login-email", "a@example.com", 4);
        assertThat(limiter.isLocked("login-email", "a@example.com")).isFalse();

        limiter.record("login-email", "a@example.com");

        assertThat(limiter.isLocked("login-email", "a@example.com")).isTrue();
    }

    @Test
    void failuresOutsideTheWindowDontCount() {
        fail("login-email", "a@example.com", 4);
        clock.advance(Duration.ofMinutes(16));

        limiter.record("login-email", "a@example.com");

        assertThat(limiter.isLocked("login-email", "a@example.com")).isFalse();
    }

    @Test
    void theLockLastsItsDurationThenLifts() {
        fail("login-email", "a@example.com", 5);
        assertThat(limiter.lockedFor("login-email", "a@example.com")).contains(Duration.ofMinutes(15));

        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        assertThat(limiter.isLocked("login-email", "a@example.com")).isTrue();
        assertThat(limiter.lockedFor("login-email", "a@example.com")).contains(Duration.ofSeconds(1));

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.isLocked("login-email", "a@example.com")).isFalse();
        assertThat(limiter.lockedFor("login-email", "a@example.com")).isEmpty();
    }

    @Test
    void moreAttemptsDuringALockDoNotExtendIt() {
        fail("login-email", "a@example.com", 5);
        clock.advance(Duration.ofMinutes(10));

        fail("login-email", "a@example.com", 3);

        assertThat(limiter.lockedFor("login-email", "a@example.com")).contains(Duration.ofMinutes(5));
    }

    @Test
    void clearRemovesFailuresAndLock() {
        fail("login-email", "a@example.com", 5);
        limiter.clear("login-email", "a@example.com");

        assertThat(limiter.isLocked("login-email", "a@example.com")).isFalse();
        fail("login-email", "a@example.com", 4);
        assertThat(limiter.isLocked("login-email", "a@example.com")).isFalse();
    }

    @Test
    void bucketsAndKeysAreIndependent() {
        fail("login-email", "a@example.com", 5);

        assertThat(limiter.isLocked("login-email", "a@example.com")).isTrue();
        assertThat(limiter.isLocked("login-email", "b@example.com")).isFalse();
        assertThat(limiter.isLocked("login-ip", "a@example.com")).isFalse();
    }

    @Test
    void anUnlimitedLockLastsUntilTheWindowOfTheFirstAttemptEnds() {
        // Sign-ups lock for what is left of the hour that began with the first counted attempt.
        fail("register-ip", "203.0.113.9", 1);
        clock.advance(Duration.ofMinutes(20));
        fail("register-ip", "203.0.113.9", 9);

        assertThat(limiter.isLocked("register-ip", "203.0.113.9")).isTrue();
        assertThat(limiter.lockedFor("register-ip", "203.0.113.9")).contains(Duration.ofMinutes(40));
    }

    @Test
    void sweepRemovesExpiredEntries() {
        fail("login-email", "a@example.com", 2);
        fail("login-email", "b@example.com", 5);
        assertThat(limiter.size()).isEqualTo(2);

        clock.advance(Duration.ofMinutes(16));
        limiter.sweep();

        assertThat(limiter.size()).isZero();
    }

    @Test
    void sweepKeepsALiveLock() {
        fail("login-email", "a@example.com", 5);
        clock.advance(Duration.ofMinutes(5));

        limiter.sweep();

        assertThat(limiter.isLocked("login-email", "a@example.com")).isTrue();
    }

    @Test
    void theKeyCountIsCapped() {
        AttemptLimiter small = limiter(3);
        small.record("login-email", "first@example.com");
        clock.advance(Duration.ofSeconds(1));
        small.record("login-email", "second@example.com");
        clock.advance(Duration.ofSeconds(1));
        small.record("login-email", "third@example.com");
        clock.advance(Duration.ofSeconds(1));

        small.record("login-email", "fourth@example.com");

        assertThat(small.size()).isEqualTo(3);
        // The oldest key was the one dropped: three more failures would not lock it, but it starts again from none.
        small.record("login-email", "second@example.com");
        small.record("login-email", "second@example.com");
        small.record("login-email", "second@example.com");
        small.record("login-email", "second@example.com");
        assertThat(small.isLocked("login-email", "second@example.com")).isTrue();
    }

    @Test
    void aFloodOfNewKeysDoesNotPushOutALockThatIsDoingItsJob() {
        AttemptLimiter small = limiter(10);
        for (int i = 0; i < 5; i++) {
            small.record("login-email", "target@example.com");
        }
        for (int i = 0; i < 30; i++) {
            clock.advance(Duration.ofSeconds(1));
            small.record("login-email", "made-up-" + i + "@example.com");
        }

        assertThat(small.isLocked("login-email", "target@example.com")).isTrue();
        assertThat(small.size()).isLessThanOrEqualTo(10);
    }

    @Test
    void concurrentFailuresNeverExceedTheLimitUnnoticed() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                limiter.record("login-email", "race@example.com");
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertThat(limiter.isLocked("login-email", "race@example.com")).isTrue();
    }

    @Test
    void emailsShareOneCountWhateverTheirCaseOrSpacing() {
        assertThat(AttemptLimiter.emailKey("  Angel@Example.COM ")).isEqualTo("angel@example.com");
        assertThat(AttemptLimiter.emailKey(null)).isEmpty();
    }

    @Test
    void anUnknownBucketIsAProgrammingError() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> limiter.record("nope", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
