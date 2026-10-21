package com.reboot.handcuffs;

/**
 * Чистая геометрия «поводка»: удержание жертвы в радиусе, сила притяжения
 * и число частиц на линии.
 *
 * <p>Никаких зависимостей от Bukkit — вся логика покрыта юнит-тестами
 * ({@code LeashMathTest}).
 */
public final class LeashMath {

    /** Сила притяжения не превышает эту величину, даже если судья летит. */
    static final double JUDGE_SPEED_FACTOR = 1.2D;

    private LeashMath() {
    }

    /**
     * Прижимает позицию жертвы к границе сферы радиуса {@code maxDistance} вокруг судьи.
     *
     * @return null, если жертва уже внутри радиуса (перемещать не нужно),
     *         иначе — массив {@code {x, y, z}} точки на границе сферы
     */
    public static double[] clampToLeash(double victimX, double victimY, double victimZ,
                                        double judgeX, double judgeY, double judgeZ,
                                        double maxDistance) {
        double dx = victimX - judgeX;
        double dy = victimY - judgeY;
        double dz = victimZ - judgeZ;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double maxDistanceSq = maxDistance * maxDistance;
        if (distanceSq <= maxDistanceSq) {
            return null;
        }

        double scale = maxDistance / Math.sqrt(distanceSq);
        return new double[]{
                judgeX + dx * scale,
                judgeY + dy * scale,
                judgeZ + dz * scale};
    }

    /**
     * Сила притяжения: скорость судьи (жертва идёт ровно за ним) плюс растяжение
     * поводка за каждый блок превышения радиуса, но не больше {@code maxPullStrength}.
     */
    public static double pullStrength(double maxPullStrength, double judgeSpeed,
                                      double excessDistance, double pullStrengthPerBlock) {
        double strength = judgeSpeed * JUDGE_SPEED_FACTOR
                + Math.max(0.0D, excessDistance) * pullStrengthPerBlock;
        return Math.min(maxPullStrength, strength);
    }

    /** Число частиц на линии: {@code distance * density}, ограничено {@code [1, maxPoints]}. */
    public static int particlePoints(double distance, double density, int maxPoints) {
        int points = (int) Math.ceil(Math.max(0.0D, distance) * Math.max(0.0D, density));
        if (points < 1) {
            return 1;
        }
        return Math.min(points, Math.max(1, maxPoints));
    }
}
