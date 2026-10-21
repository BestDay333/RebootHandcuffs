package com.reboot.handcuffs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Простой кулдаун действий по цели (защита от спама кликами наручниками).
 *
 * <p>Чистая логика без зависимостей от Bukkit: время берётся из {@link LongSupplier},
 * поэтому класс полностью покрыт юнит-тестами ({@code CooldownTrackerTest}).
 *
 * <p>Потокобезопасность: рассчитан на использование только из главного потока сервера.
 */
public final class CooldownTracker {

    /** После превышения этого числа записей выполняется чистка устаревших. */
    static final int CLEANUP_THRESHOLD = 64;
    /** Записи старше {@code cooldown * MULTIPLIER} удаляются при чистке. */
    static final long CLEANUP_MULTIPLIER = 4L;

    private final Map<UUID, Long> lastActionAt = new HashMap<>();
    private final LongSupplier clock;

    private long cooldownMillis;

    public CooldownTracker(LongSupplier clock) {
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    /** Обновляется при старте и при {@code /cuffreload}. */
    public void setCooldownMillis(long cooldownMillis) {
        this.cooldownMillis = Math.max(0L, cooldownMillis);
    }

    /**
     * @return true, если действие по цели разрешено (и кулдаун при этом засчитан)
     */
    public boolean tryStart(UUID targetId) {
        if (cooldownMillis <= 0L) {
            return true;
        }

        long now = clock.getAsLong();
        if (lastActionAt.size() > CLEANUP_THRESHOLD) {
            lastActionAt.entrySet().removeIf(
                    entry -> now - entry.getValue() > cooldownMillis * CLEANUP_MULTIPLIER);
        }

        Long last = lastActionAt.get(targetId);
        if (last != null && now - last < cooldownMillis) {
            return false;
        }
        lastActionAt.put(targetId, now);
        return true;
    }

    public void clear() {
        lastActionAt.clear();
    }

    /** Только для тестов/диагностики. */
    int size() {
        return lastActionAt.size();
    }
}
