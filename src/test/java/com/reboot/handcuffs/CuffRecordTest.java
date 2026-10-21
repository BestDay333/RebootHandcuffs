package com.reboot.handcuffs;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CuffRecordTest {

    private final UUID victim = UUID.randomUUID();
    private final UUID judge = UUID.randomUUID();

    @Test
    void fieldsAreStoredAsPassed() {
        CuffRecord record = new CuffRecord(
                victim, "Steve", judge, "Admin", 1234567890L, null, null);

        assertEquals(victim, record.getVictim());
        assertEquals("Steve", record.getVictimName());
        assertEquals(judge, record.getJudge());
        assertEquals("Admin", record.getJudgeName());
        assertEquals(1234567890L, record.getCuffedAt());
        assertNull(record.getMainHand());
        assertNull(record.getOffHand());
    }

    @Test
    void nullVictimNameFallsBackToUuid() {
        CuffRecord record = new CuffRecord(victim, null, judge, "Admin", 0L, null, null);
        assertEquals(victim.toString(), record.getVictimName());
    }

    @Test
    void nullJudgeNameFallsBackToQuestionMark() {
        CuffRecord record = new CuffRecord(victim, "Steve", judge, null, 0L, null, null);
        assertEquals("?", record.getJudgeName());
    }

    @Test
    void nullJudgeMeansConsoleCuffs() {
        CuffRecord record = new CuffRecord(victim, "Steve", null, "console", 0L, null, null);
        assertNull(record.getJudge());
    }

    @Test
    void setVictimNameUpdatesName() {
        CuffRecord record = new CuffRecord(victim, "OldName", judge, "Admin", 0L, null, null);
        record.setVictimName("NewName");
        assertEquals("NewName", record.getVictimName());
    }

    @Test
    void setVictimNameIgnoresBlankValues() {
        CuffRecord record = new CuffRecord(victim, "OldName", judge, "Admin", 0L, null, null);
        record.setVictimName(null);
        assertEquals("OldName", record.getVictimName());
        record.setVictimName("   ");
        assertEquals("OldName", record.getVictimName());
    }
}
