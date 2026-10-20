package com.reboot.handcuffs;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Состояние одного закованного игрока.
 * Все поля неизменны, кроме имени жертвы (оно обновляется при входе игрока).
 */
public final class CuffRecord {

    private final UUID victim;
    private String victimName;
    private final UUID judge;      // null - наручники надеты консолью/системой
    private final String judgeName;
    private final long cuffedAt;
    private final ItemStack mainHand;
    private final ItemStack offHand;

    public CuffRecord(UUID victim, String victimName, UUID judge, String judgeName,
                      long cuffedAt, ItemStack mainHand, ItemStack offHand) {
        this.victim = victim;
        this.victimName = victimName == null ? victim.toString() : victimName;
        this.judge = judge;
        this.judgeName = judgeName == null ? "?" : judgeName;
        this.cuffedAt = cuffedAt;
        this.mainHand = mainHand;
        this.offHand = offHand;
    }

    public UUID getVictim() {
        return victim;
    }

    public String getVictimName() {
        return victimName;
    }

    public void setVictimName(String victimName) {
        if (victimName != null && !victimName.isBlank()) {
            this.victimName = victimName;
        }
    }

    public UUID getJudge() {
        return judge;
    }

    public String getJudgeName() {
        return judgeName;
    }

    public long getCuffedAt() {
        return cuffedAt;
    }

    public ItemStack getMainHand() {
        return mainHand;
    }

    public ItemStack getOffHand() {
        return offHand;
    }
}
