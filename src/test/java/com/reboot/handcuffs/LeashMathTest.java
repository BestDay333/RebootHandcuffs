package com.reboot.handcuffs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LeashMathTest {

    private static final double EPS = 1e-9;

    // ------------------------------------------------------------------
    //  clampToLeash
    // ------------------------------------------------------------------

    @Test
    void pointInsideLeashIsNotClamped() {
        assertNull(LeashMath.clampToLeash(0, 0, 0, 1, 0, 0, 1.5));
        assertNull(LeashMath.clampToLeash(1.5, 0, 0, 0, 0, 0, 1.5)); // ровно на границе
    }

    @Test
    void pointOutsideLeashIsPulledToBoundary() {
        double[] clamped = LeashMath.clampToLeash(10, 0, 0, 0, 0, 0, 1.5);
        assertNotNull(clamped);
        assertArrayEquals(new double[]{1.5, 0.0, 0.0}, clamped, EPS);
    }

    @Test
    void clampedPointKeepsDirectionAndDistance() {
        // Жертва "северо-восточнее" судьи: направление должно сохраниться.
        double[] clamped = LeashMath.clampToLeash(3, 4, 0, 0, 0, 0, 2.0);
        assertNotNull(clamped);

        double distance = Math.sqrt(clamped[0] * clamped[0] + clamped[1] * clamped[1]);
        assertEquals(2.0, distance, EPS);

        // Отношение сторон сохраняется (3:4 -> 1.2:1.6).
        assertEquals(1.2, clamped[0], EPS);
        assertEquals(1.6, clamped[1], EPS);
    }

    @Test
    void clampHandlesVerticalOffset() {
        double[] clamped = LeashMath.clampToLeash(0, 10, 0, 0, 0, 0, 1.0);
        assertNotNull(clamped);
        assertArrayEquals(new double[]{0.0, 1.0, 0.0}, clamped, EPS);
    }

    @Test
    void clampReturnsJudgePositionForDegenerateDistance() {
        // maxDistance = 0: любая точка прижимается к судье.
        double[] clamped = LeashMath.clampToLeash(5, 5, 5, 1, 1, 1, 0.0);
        assertNotNull(clamped);
        assertArrayEquals(new double[]{1.0, 1.0, 1.0}, clamped, EPS);
    }

    // ------------------------------------------------------------------
    //  pullStrength
    // ------------------------------------------------------------------

    @Test
    void pullStrengthFollowsJudgeSpeed() {
        // Жертва идёт за судьёй: сила = скорость судьи с коэффициентом (без растяжения).
        assertEquals(0.3 * LeashMath.JUDGE_SPEED_FACTOR,
                LeashMath.pullStrength(0.8, 0.3, 0.0, 0.15), EPS);
    }

    @Test
    void pullStrengthGrowsWithExcessDistance() {
        // Судья на месте, но жертва далеко: тянем за растяжение поводка.
        assertEquals(0.5, LeashMath.pullStrength(10.0, 0.0, 5.0, 0.1), EPS);
    }

    @Test
    void pullStrengthIsCappedByMax() {
        assertEquals(0.8, LeashMath.pullStrength(0.8, 10.0, 100.0, 1.0), EPS);
    }

    @Test
    void pullStrengthNeverNegativeForNegativeExcess() {
        assertEquals(0.0, LeashMath.pullStrength(0.8, 0.0, -3.0, 0.15), EPS);
    }

    // ------------------------------------------------------------------
    //  particlePoints
    // ------------------------------------------------------------------

    @Test
    void particlePointsScaleWithDistanceAndDensity() {
        assertEquals(10, LeashMath.particlePoints(10.0, 1.0, 100));
        assertEquals(20, LeashMath.particlePoints(10.0, 2.0, 100));
        assertEquals(5, LeashMath.particlePoints(10.0, 0.5, 100));
    }

    @Test
    void particlePointsAreRoundedUp() {
        assertEquals(3, LeashMath.particlePoints(2.2, 1.0, 100));
    }

    @Test
    void particlePointsAreCappedByMaxPoints() {
        assertEquals(12, LeashMath.particlePoints(100.0, 2.0, 12));
    }

    @Test
    void particlePointsAreAtLeastOne() {
        assertEquals(1, LeashMath.particlePoints(0.0, 1.0, 12));
        assertEquals(1, LeashMath.particlePoints(-5.0, 1.0, 12));
        assertEquals(1, LeashMath.particlePoints(10.0, 0.0, 0));
    }
}
