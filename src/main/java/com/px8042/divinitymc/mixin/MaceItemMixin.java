package com.px8042.divinitymc.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.px8042.divinitymc.api.DivinityArmorApi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MaceItem.class)
public abstract class MaceItemMixin {
    // The smash pushes nearby entities directly. Zero power skips both the push and its motion packet.
    @ModifyReturnValue(method = "getKnockbackPower", at = @At("RETURN"))
    private static double divinitymc$smashKnockback(double power, Player attacker, LivingEntity neighbor, Vec3 offset) {
        if (power <= 0 || neighbor.level().isClientSide) return power;
        return DivinityArmorApi.tryControl(neighbor, attacker, DivinityArmorApi.KNOCKBACK, entity -> true) ? power : 0;
    }
}
