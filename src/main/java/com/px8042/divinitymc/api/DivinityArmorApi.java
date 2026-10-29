package com.px8042.divinitymc.api;

import com.px8042.divinitymc.armor.*;
import com.px8042.divinitymc.config.ArmorConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;

/** Register extensions during mod setup. All mutations and control callbacks run on the logical server. */
public final class DivinityArmorApi {
    public static final ResourceLocation KNOCKBACK = ResourceLocation.parse("divinitymc:knockback");
    private static final Map<ResourceLocation, ArmorKind> CONTROLS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Function<LivingEntity, ArmorConfig.Capacity>> CAPACITIES = new ConcurrentHashMap<>();

    public static void registerControl(ResourceLocation id, ArmorKind kind) {
        if (kind == ArmorKind.BYPASS || CONTROLS.putIfAbsent(id, kind) != null)
            throw new IllegalArgumentException("Duplicate/invalid control: " + id);
    }
    public static boolean registeredControl(ResourceLocation id) { return CONTROLS.containsKey(id); }
    public static void registerCapacityProvider(ResourceLocation id, Function<LivingEntity, ArmorConfig.Capacity> provider) {
        Objects.requireNonNull(provider);
        if (CAPACITIES.putIfAbsent(id, provider) != null) throw new IllegalArgumentException("Duplicate capacity provider: " + id);
    }
    public static ArmorConfig.Capacity additionalCapacity(LivingEntity entity) {
        var result = new ArmorConfig.Capacity(0, 0);
        for (var entry : CAPACITIES.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
            result = result.plus(Objects.requireNonNull(entry.getValue().apply(entity)));
        return result;
    }
    public static ArmorKind damageKind(DamageSource source) {
        var id = source.typeHolder().unwrapKey().orElseThrow().location();
        return ArmorConfig.rules().damage().getOrDefault(id, ArmorKind.PHYSICAL);
    }
    public static ArmorKind controlKind(ResourceLocation control) {
        return ArmorConfig.rules().controls().getOrDefault(control, CONTROLS.get(control));
    }
    public static ArmorState.Snapshot armor(LivingEntity entity) { return ArmorService.state(entity).snapshot(); }
    public static boolean blocked(LivingEntity entity, ResourceLocation control) {
        var kind = controlKind(control);
        return kind != null && ArmorService.state(entity).blocks(kind);
    }
    public static double consume(LivingEntity entity, ArmorKind kind, double amount) {
        server(entity); ArmorState.valid(amount);
        double overflow = ArmorService.state(entity).absorb(kind, amount);
        if (amount > overflow) ArmorService.combat(entity, null);
        ArmorService.sync(entity); return overflow;
    }
    public static void restore(LivingEntity entity, ArmorKind kind, double amount) {
        server(entity); ArmorService.state(entity).restore(kind, amount); ArmorService.sync(entity);
    }
    public static void set(LivingEntity entity, ArmorKind kind, double amount) {
        server(entity); ArmorService.state(entity).set(kind, amount); ArmorService.sync(entity);
    }
    public static boolean tryControl(LivingEntity target, Entity source, ResourceLocation control,
                                     Predicate<LivingEntity> naturallyApplicable, Consumer<LivingEntity> action) {
        server(target);
        if (!target.isAlive() || target instanceof Player p && (p.isSpectator() || p.getAbilities().invulnerable)
            || !naturallyApplicable.test(target)) return false;
        ArmorService.combat(target, source);
        if (blocked(target, control)) {
            ArmorService.state(target).feedback |= 4;
            return false;
        }
        action.accept(target); return true;
    }
    /** The hurt result already accounts for invulnerability, cancellation and full shield blocking. */
    public static boolean attack(LivingEntity target, DamageSource source, float damage, List<MobEffectInstance> effects) {
        server(target); ArmorState.valid(damage);
        boolean accepted = target.hurt(source, damage);
        if (accepted && target.isAlive()) for (var effect : List.copyOf(effects))
            target.addEffect(new MobEffectInstance(effect), source.getEntity());
        return accepted;
    }
    public static boolean applyEffect(LivingEntity target, Entity source, MobEffectInstance effect) {
        server(target); return target.isAlive() && target.addEffect(new MobEffectInstance(effect), source);
    }
    private static void server(LivingEntity entity) {
        if (entity.level().isClientSide || entity.getServer() == null || !entity.getServer().isSameThread())
            throw new IllegalStateException("DivinityMC mutations require the server thread");
    }
}
