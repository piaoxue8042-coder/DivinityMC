package com.px8042.divinitymc.armor;

import com.px8042.divinitymc.api.*;
import com.px8042.divinitymc.config.ArmorConfig;
import com.px8042.divinitymc.network.EquipmentRulesPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class ArmorEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void incoming(LivingIncomingDamageEvent event) {
        if (!event.getEntity().level().isClientSide)
            event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> 0);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent.Pre event) {
        var entity = event.getEntity();
        float amount = event.getNewDamage();
        if (entity.level().isClientSide || amount <= 0) return;
        ArmorService.combat(entity, event.getSource().getEntity());
        var state = ArmorService.state(entity);
        float overflow = (float)state.absorb(DivinityArmorApi.damageKind(event.getSource()), amount);
        event.setNewDamage(overflow);
        // Vanilla only emits this game event / records combat on health loss.
        // Keep zero-health-loss armor hits visible to combat tracking and vibration listeners.
        if (overflow == 0 && amount > 0) {
            entity.getCombatTracker().recordDamage(event.getSource(), 0);
            entity.gameEvent(GameEvent.ENTITY_DAMAGE);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void effect(MobEffectEvent.Applicable event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide || !event.getApplicationResult()) return;
        var id = BuiltInRegistries.MOB_EFFECT.getKey(event.getEffectInstance().getEffect().value());
        if (DivinityArmorApi.controlKind(id) == null) return;
        if (!entity.isAlive() || immunePlayer(entity)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY); return;
        }
        ArmorService.combat(entity, event.getEffectSource());
        if (DivinityArmorApi.blocked(entity, id)) {
            ArmorService.state(entity).feedback |= 4;
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void knockback(LivingKnockBackEvent event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide || !entity.isAlive() || immunePlayer(entity)) return;
        if (event.getStrength() * (1 - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) <= 0) return;
        ArmorService.combat(entity, entity.getLastHurtByMob());
        if (DivinityArmorApi.blocked(entity, DivinityArmorApi.KNOCKBACK)) {
            ArmorService.state(entity).feedback |= 4;
            event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void explosion(ExplosionKnockbackEvent event) {
        if (event.getLevel().isClientSide || !(event.getAffectedEntity() instanceof LivingEntity entity)
            || !entity.isAlive() || immunePlayer(entity) || event.getKnockbackVelocity().lengthSqr() == 0) return;
        ArmorService.combat(entity, event.getExplosion().getIndirectSourceEntity());
        if (DivinityArmorApi.blocked(entity, DivinityArmorApi.KNOCKBACK)) {
            event.setKnockbackVelocity(Vec3.ZERO);
            ArmorService.state(entity).feedback |= 4;
        }
    }
    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide || !entity.isAlive()) return;
        var state = ArmorService.state(entity);
        state.tick(ArmorConfig.rules().recoveryFractionPerSecond());
        if (state.feedback != 0 || entity.tickCount % 5 == 0 && state.sentRevision != state.revision() || entity.tickCount % 100 == 0)
            ArmorService.sync(entity);
    }
    @SubscribeEvent
    public static void equipment(LivingEquipmentChangeEvent event) {
        if (!event.getEntity().level().isClientSide) ArmorService.sync(event.getEntity());
    }
    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity entity && event.getEntity() instanceof ServerPlayer player)
            ArmorService.syncTo(entity, player);
    }
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) EquipmentRulesPayload.sendTo(player);
        ArmorService.sync(event.getEntity());
    }
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) { ArmorService.sync(event.getEntity()); }
    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { ArmorService.sync(event.getEntity()); }
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() && event.getOriginal().hasData(ArmorAttachments.ARMOR))
            event.getEntity().setData(ArmorAttachments.ARMOR, ArmorState.restore(event.getOriginal().getData(ArmorAttachments.ARMOR).snapshot()));
        // Death deliberately does not copy the old pool; initialize against the new inventory later.
    }
    private static boolean immunePlayer(LivingEntity entity) {
        return entity instanceof Player p && (p.isSpectator() || p.getAbilities().invulnerable);
    }
}
