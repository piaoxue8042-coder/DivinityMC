package com.px8042.divinitymc.armor;

import com.px8042.divinitymc.api.DivinityArmorApi;
import com.px8042.divinitymc.config.ArmorConfig;
import com.px8042.divinitymc.network.ArmorPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Equipable;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ArmorService {
    public static ArmorState state(LivingEntity entity) {
        var state = entity.getData(ArmorAttachments.ARMOR);
        if (!entity.level().isClientSide) updateCapacity(entity, state);
        return state;
    }
    public static void updateCapacity(LivingEntity entity, ArmorState state) {
        var rules = ArmorConfig.rules();
        var capacity = rules.natural().get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        if (capacity == null) {
            // Base only: equipment and other mods' attribute modifiers must never be counted twice.
            double base = entity.getAttributes().hasAttribute(Attributes.ARMOR) ? Math.max(0, entity.getAttributeBaseValue(Attributes.ARMOR)) : 0;
            capacity = new ArmorConfig.Capacity(base * rules.naturalPhysicalMultiplier(), base * rules.naturalMagicMultiplier());
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            var stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof Equipable equipable && equipable.getEquipmentSlot() == slot) {
                var extra = rules.equipment().get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                if (extra != null) capacity = capacity.plus(extra);
            }
        }
        capacity = capacity.plus(DivinityArmorApi.additionalCapacity(entity));
        state.capacity(capacity.physical(), capacity.magic());
    }
    public static void combat(LivingEntity target, Entity source) {
        state(target).combat(ArmorConfig.rules().recoveryDelayTicks());
        if (source instanceof Projectile projectile) source = projectile.getOwner();
        if (source instanceof LivingEntity attacker && attacker != target && !attacker.level().isClientSide)
            state(attacker).combat(ArmorConfig.rules().recoveryDelayTicks());
    }
    public static void sync(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        var state = state(entity);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, ArmorPayload.of(entity, state));
        state.sentRevision = state.revision(); state.feedback = 0;
    }
    public static void syncTo(LivingEntity entity, ServerPlayer watcher) {
        PacketDistributor.sendToPlayer(watcher, ArmorPayload.of(entity, state(entity)));
    }
}
