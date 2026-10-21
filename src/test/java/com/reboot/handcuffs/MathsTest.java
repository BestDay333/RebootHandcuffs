package com.reboot.handcuffs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MathsTest {

    // ------------------------------------------------------------------
    //  clampLong
    // ------------------------------------------------------------------

    @Test
    void clampLong_returnsValueInsideRange() {
        assertEquals(5L, Maths.clampLong(5L, 0L, 10L));
        assertEquals(0L, Maths.clampLong(0L, 0L, 10L));
        assertEquals(10L, Maths.clampLong(10L, 0L, 10L));
    }

    @Test
    void clampLong_clampsBelowMin() {
        assertEquals(0L, Maths.clampLong(-1L, 0L, 10L));
        assertEquals(1200L, Maths.clampLong(-9999L, 1200L, 5000L));
    }

    @Test
    void clampLong_clampsAboveMax() {
        assertEquals(10L, Maths.clampLong(11L, 0L, 10L));
        assertEquals(1200L, Maths.clampLong(Long.MAX_VALUE, 0L, 1200L));
    }

    // ------------------------------------------------------------------
    //  clampDouble
    // ------------------------------------------------------------------

    @Test
    void clampDouble_returnsValueInsideRange() {
        assertEquals(1.5D, Maths.clampDouble(1.5D, 0.1D, 32.0D));
    }

    @Test
    void clampDouble_clampsToEdges() {
        assertEquals(0.1D, Maths.clampDouble(0.0D, 0.1D, 32.0D));
        assertEquals(32.0D, Maths.clampDouble(100.0D, 0.1D, 32.0D));
    }

    @Test
    void clampDouble_nanFallsBackToMin() {
        assertEquals(0.1D, Maths.clampDouble(Double.NaN, 0.1D, 32.0D));
    }

    // ------------------------------------------------------------------
    //  applyPlaceholders
    // ------------------------------------------------------------------

    @Test
    void applyPlaceholders_replacesEveryPair() {
        assertEquals("Steve закован Admin",
                Maths.applyPlaceholders("%player% закован %judge%",
                        "%player%", "Steve", "%judge%", "Admin"));
    }

    @Test
    void applyPlaceholders_replacesRepeatedPlaceholder() {
        assertEquals("Steve и Steve",
                Maths.applyPlaceholders("%p% и %p%", "%p%", "Steve"));
    }

    @Test
    void applyPlaceholders_keepsInputWhenNoPairs() {
        assertEquals("%player%", Maths.applyPlaceholders("%player%"));
        assertEquals("%player%", Maths.applyPlaceholders("%player%", "Steve"));
        assertNull(Maths.applyPlaceholders(null, "%p%", "x"));
    }

    // ------------------------------------------------------------------
    //  formatDuration
    // ------------------------------------------------------------------

    @Test
    void formatDuration_seconds() {
        assertEquals("0с", Maths.formatDuration(0L));
        assertEquals("5с", Maths.formatDuration(5_000L));
        assertEquals("59с", Maths.formatDuration(59_999L));
    }

    @Test
    void formatDuration_minutes() {
        assertEquals("1м", Maths.formatDuration(60_000L));
        assertEquals("59м", Maths.formatDuration(59L * 60_000L));
    }

    @Test
    void formatDuration_hours() {
        assertEquals("1ч", Maths.formatDuration(3_600_000L));
        assertEquals("1ч 5м", Maths.formatDuration(3_900_000L));
        assertEquals("23ч 59м", Maths.formatDuration(23L * 3_600_000L + 59L * 60_000L));
    }

    @Test
    void formatDuration_days() {
        assertEquals("1д", Maths.formatDuration(86_400_000L));
        assertEquals("2д 3ч", Maths.formatDuration(2L * 86_400_000L + 3L * 3_600_000L));
    }

    @Test
    void formatDuration_negativeTreatedAsZero() {
        assertEquals("0с", Maths.formatDuration(-1000L));
    }
}
