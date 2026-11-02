package com.px8042.divinitymc.compat;

import com.px8042.divinitymc.config.ArmorConfig;
import com.px8042.divinitymc.config.EquipmentDefaults;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Keep optional API types out of classes that are loaded when Curios is absent. */
public final class CuriosIntegration {
    public static boolean loaded() { return ModList.get().isLoaded("curios"); }
    public static void register() { if (loaded()) CuriosArmor.register(); }
    public static boolean supports(ItemStack stack, boolean client) {
        return loaded() && CuriosArmor.supports(stack, client);
    }
    public static ArmorConfig.Capacity capacity(LivingEntity entity, ArmorConfig.Rules rules) {
        return loaded() ? CuriosArmor.capacity(entity, rules) : new ArmorConfig.Capacity(0, 0);
    }
    public static ArmorConfig.Capacity preview(ItemStack stack, LivingEntity wearer, EquipmentDefaults.Automatic automatic) {
        return loaded() && automatic.enabled() ? CuriosArmor.preview(stack, wearer, automatic) : null;
    }
}
