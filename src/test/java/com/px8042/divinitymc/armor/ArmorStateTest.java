package com.px8042.divinitymc.armor;

import com.px8042.divinitymc.api.ArmorKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArmorStateTest {
    private ArmorState full() { var s = new ArmorState(); s.capacity(10, 6); return s; }
    @Test void damagePoolsAreIndependent() {
        var s = full();
        assertEquals(2, s.absorb(ArmorKind.PHYSICAL, 12));
        assertEquals(0, s.current(ArmorKind.PHYSICAL));
        assertEquals(6, s.current(ArmorKind.MAGIC));
        assertEquals(0, s.absorb(ArmorKind.MAGIC, 2));
        assertEquals(4, s.current(ArmorKind.MAGIC));
    }
    @Test void exactBreakAllowsSameHitControl() {
        var s = full(); assertTrue(s.blocks(ArmorKind.PHYSICAL));
        assertEquals(0, s.absorb(ArmorKind.PHYSICAL, 10));
        assertFalse(s.blocks(ArmorKind.PHYSICAL));
        assertTrue(s.blocks(ArmorKind.MAGIC));
    }
    @Test void fractionalArmorStillBlocks() {
        var s = full(); s.set(ArmorKind.PHYSICAL, 0.01);
        assertTrue(s.blocks(ArmorKind.PHYSICAL));
    }
    @Test void bypassPreservesBothPools() {
        var s = full(); var before = s.snapshot();
        assertEquals(100, s.absorb(ArmorKind.BYPASS, 100));
        assertEquals(before, s.snapshot());
    }
    @Test void swapsCannotRefill() {
        var s = full(); s.absorb(ArmorKind.PHYSICAL, 7);
        s.capacity(30, 20); assertEquals(3, s.current(ArmorKind.PHYSICAL));
        s.capacity(0, 0); s.capacity(30, 20);
        assertEquals(0, s.current(ArmorKind.PHYSICAL)); assertEquals(0, s.current(ArmorKind.MAGIC));
    }
    @Test void decreasingMaximumClamps() {
        var s = full(); s.capacity(3, 2);
        assertEquals(3, s.current(ArmorKind.PHYSICAL)); assertEquals(2, s.current(ArmorKind.MAGIC));
    }
    @Test void cooldownRequiresLoadedTicksAndPersists() {
        var s = full(); s.set(ArmorKind.PHYSICAL, 0); s.combat(200);
        for (int i = 0; i < 99; i++) s.tick(0.1);
        s = ArmorState.restore(s.snapshot()); s.capacity(10, 6);
        assertEquals(101, s.snapshot().recoveryDelay());
        for (int i = 0; i < 101; i++) s.tick(0.1);
        assertEquals(0, s.current(ArmorKind.PHYSICAL));
        for (int i = 0; i < 20; i++) s.tick(0.1);
        assertEquals(1, s.current(ArmorKind.PHYSICAL), 1e-9);
    }
    @Test void renewedCombatInterruptsRecovery() {
        var s = full(); s.set(ArmorKind.MAGIC, 0); s.tick(.1);
        double before = s.current(ArmorKind.MAGIC); s.combat(200); s.tick(.1);
        assertEquals(before, s.current(ArmorKind.MAGIC));
    }
    @Test void restoreNeverOverfills() {
        var s = full(); s.restore(ArmorKind.MAGIC, 100);
        assertEquals(6, s.current(ArmorKind.MAGIC));
    }
    @Test void corruptedStateDoesNotRegenerateOrReinitialize() {
        var s = ArmorState.quarantine(); s.capacity(100, 100);
        for (int i = 0; i < 1000; i++) s.tick(.1);
        assertEquals(0, s.current(ArmorKind.PHYSICAL));
        assertEquals(10, s.absorb(ArmorKind.MAGIC, 10));
    }
    @Test void malformedNumbersAreRejected() {
        for (double bad : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> full().set(ArmorKind.PHYSICAL, bad));
        assertThrows(IllegalArgumentException.class, () -> ArmorState.restore(new ArmorState.Snapshot(2,0,1,0,0,true,false)));
    }
    @Test void restorationDoesNotResetCooldown() {
        var s = full(); s.combat(132); s.set(ArmorKind.MAGIC, 0); s.restore(ArmorKind.MAGIC, 2);
        assertEquals(132, s.snapshot().recoveryDelay());
    }
}
