package com.reboot.handcuffs;

/**
 * Чистые математические/строковые утилиты плагина.
 *
 * <p>Класс намеренно не зависит от Bukkit: вся логика здесь покрыта юнит-тестами
 * ({@code MathsTest}).
 */
public final class Maths {

    private Maths() {
    }

    public static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clampDouble(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** Подстановка пар плейсхолдеров: {@code ("%player%", "Steve")}. */
    public static String applyPlaceholders(String input, String... placeholders) {
        if (input == null || placeholders == null || placeholders.length < 2) {
            return input;
        }
        String result = input;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace(placeholders[i], placeholders[i + 1]);
        }
        return result;
    }

    /**
     * Человекочитаемая длительность: {@code 30с}, {@code 5м}, {@code 1ч 5м},
     * {@code 2д 3ч}. Значение меньше секунды — {@code 0с}.
     */
    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        if (totalSeconds < 60L) {
            return totalSeconds + "с";
        }
        long totalMinutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (totalMinutes < 60L) {
            return totalMinutes + "м";
        }
        long totalHours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (totalHours < 24L) {
            return minutes == 0L ? totalHours + "ч" : totalHours + "ч " + minutes + "м";
        }
        long days = totalHours / 24L;
        long hours = totalHours % 24L;
        return hours == 0L ? days + "д" : days + "д " + hours + "ч";
    }
}
