package com.px8042.divinitymc.mixin;

import com.px8042.divinitymc.api.DivinityArmorApi;
import com.px8042.divinitymc.armor.ArmorService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {
    /** Punch uses push(), bypassing LivingKnockBackEvent. Intercept only this combat impulse. */
    @Redirect(method = "doKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;push(DDD)V"))
    private void divinitymc$arrowImpulse(LivingEntity target, double x, double y, double z,
                                        LivingEntity originalTarget, DamageSource source) {
        if (target.level().isClientSide) { target.push(x, y, z); return; }
        DivinityArmorApi.tryControl(target, source.getEntity(), DivinityArmorApi.KNOCKBACK,
            entity -> true, entity -> entity.push(x, y, z));
    }
}
