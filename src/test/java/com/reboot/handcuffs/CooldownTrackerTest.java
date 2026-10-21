package com.reboot.handcuffs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownTrackerTest {

    private static final long COOLDOWN_MS = 1_000L;

    private final AtomicLong clock = new AtomicLong(100_000L);
    private final CooldownTracker tracker = new CooldownTracker(clock::get);
    private final UUID target = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tracker.setCooldownMillis(COOLDOWN_MS);
    }

    @Test
    void firstActionIsAllowed() {
        assertTrue(tracker.tryStart(target));
    }

    @Test
    void secondActionInsideCooldownIsBlocked() {
        assertTrue(tracker.tryStart(target));
        clock.addAndGet(COOLDOWN_MS - 1L);
        assertFalse(tracker.tryStart(target));
    }

    @Test
    void actionAfterCooldownIsAllowed() {
        assertTrue(tracker.tryStart(target));
        clock.addAndGet(COOLDOWN_MS);
        assertTrue(tracker.tryStart(target));
    }

    @Test
    void cooldownIsTrackedPerTarget() {
        assertTrue(tracker.tryStart(target));
        assertTrue(tracker.tryStart(UUID.randomUUID()));
    }

    @Test
    void zeroCooldownDisablesTracking() {
        tracker.setCooldownMillis(0L);
        for (int i = 0; i < 100; i++) {
            assertTrue(tracker.tryStart(target));
        }
    }

    @Test
    void negativeCooldownTreatedAsDisabled() {
        tracker.setCooldownMillis(-5L);
        assertTrue(tracker.tryStart(target));
        assertTrue(tracker.tryStart(target));
    }

    @Test
    void staleEntriesAreCleanedUpAfterThreshold() {
        // Заполняем карту больше порога чистки, но только устаревшими записями.
        for (int i = 0; i <= CooldownTracker.CLEANUP_THRESHOLD; i++) {
            tracker.tryStart(UUID.randomUUID());
        }
        clock.addAndGet(COOLDOWN_MS * CooldownTracker.CLEANUP_MULTIPLIER + 1L);
        assertTrue(tracker.tryStart(target)); // триггерит чистку
        assertEquals(1, tracker.size());
    }

    @Test
    void clearEmptiesTracker() {
        tracker.tryStart(target);
        tracker.clear();
        assertEquals(0, tracker.size());
    }

    @Test
    void nullClockFallsBackToSystemClock() {
        CooldownTracker fallback = new CooldownTracker(null);
        fallback.setCooldownMillis(0L);
        assertTrue(fallback.tryStart(target));
    }
}
