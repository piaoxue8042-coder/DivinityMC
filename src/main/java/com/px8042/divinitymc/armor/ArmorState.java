package com.px8042.divinitymc.armor;

import com.px8042.divinitymc.api.ArmorKind;

/** Pure state machine. One point is one damage point (half a heart). */
public final class ArmorState {
    private double physical, magic, maxPhysical, maxMagic;
    private int recoveryDelay;
    private boolean initialized, quarantined;
    private long revision;
    public long sentRevision = -1;
    public int feedback;

    public record Snapshot(double physical, double magic, double maxPhysical, double maxMagic,
                           int recoveryDelay, boolean initialized, boolean quarantined) {}
    public Snapshot snapshot() {
        return new Snapshot(physical, magic, maxPhysical, maxMagic, recoveryDelay, initialized, quarantined);
    }
    public static ArmorState restore(Snapshot s) {
        valid(s.physical); valid(s.magic); valid(s.maxPhysical); valid(s.maxMagic);
        if (s.physical > s.maxPhysical || s.magic > s.maxMagic || s.recoveryDelay < 0)
            throw new IllegalArgumentException("Invalid armor bounds or recovery delay");
        ArmorState state = new ArmorState();
        state.physical = s.physical; state.magic = s.magic;
        state.maxPhysical = s.maxPhysical; state.maxMagic = s.maxMagic;
        state.recoveryDelay = s.recoveryDelay; state.initialized = s.initialized;
        state.quarantined = s.quarantined;
        return state;
    }
    public static ArmorState quarantine() {
        return restore(new Snapshot(0, 0, 0, 0, 0, true, true));
    }
    public void capacity(double physicalMax, double magicMax) {
        valid(physicalMax); valid(magicMax);
        var before = snapshot();
        maxPhysical = physicalMax; maxMagic = magicMax;
        if (!initialized) {
            physical = physicalMax; magic = magicMax; initialized = true;
        } else {
            physical = Math.min(physical, physicalMax); magic = Math.min(magic, magicMax);
        }
        changed(before);
    }
    /** Return overflow without cancelling the underlying hit. */
    public double absorb(ArmorKind kind, double damage) {
        valid(damage);
        if (kind == ArmorKind.BYPASS || quarantined) return damage;
        double before = current(kind), absorbed = Math.min(before, damage);
        set(kind, before - absorbed);
        if (before > 0 && current(kind) == 0) feedback |= kind == ArmorKind.PHYSICAL ? 1 : 2;
        return damage - absorbed;
    }
    public boolean blocks(ArmorKind kind) { return !quarantined && current(kind) > 0; }
    public double current(ArmorKind kind) {
        return switch (kind) { case PHYSICAL -> physical; case MAGIC -> magic; case BYPASS -> 0; };
    }
    public double maximum(ArmorKind kind) {
        return switch (kind) { case PHYSICAL -> maxPhysical; case MAGIC -> maxMagic; case BYPASS -> 0; };
    }
    public void set(ArmorKind kind, double amount) {
        valid(amount);
        var before = snapshot();
        switch (kind) {
            case PHYSICAL -> physical = Math.min(amount, maxPhysical);
            case MAGIC -> magic = Math.min(amount, maxMagic);
            case BYPASS -> throw new IllegalArgumentException("BYPASS has no armor pool");
        }
        changed(before);
    }
    public void restore(ArmorKind kind, double amount) { valid(amount); set(kind, Math.min(maximum(kind), current(kind) + amount)); }
    public void combat(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("Negative cooldown");
        if (recoveryDelay != ticks) { recoveryDelay = ticks; revision++; }
    }
    public void tick(double fractionPerSecond) {
        valid(fractionPerSecond);
        if (quarantined) return;
        if (recoveryDelay > 0) { recoveryDelay--; revision++; return; }
        set(ArmorKind.PHYSICAL, Math.min(maxPhysical, physical + maxPhysical * fractionPerSecond / 20));
        set(ArmorKind.MAGIC, Math.min(maxMagic, magic + maxMagic * fractionPerSecond / 20));
    }
    public long revision() { return revision; }
    private void changed(Snapshot before) { if (!before.equals(snapshot())) revision++; }
    public static void valid(double value) {
        if (!Double.isFinite(value) || value < 0 || value > Float.MAX_VALUE)
            throw new IllegalArgumentException("Armor values must be finite and nonnegative: " + value);
    }
}
