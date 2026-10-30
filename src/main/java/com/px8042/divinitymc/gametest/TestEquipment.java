package com.px8042.divinitymc.gametest;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** A foreign-namespace standard armor fixture. This entire package is excluded from release jars. */
@EventBusSubscriber(modid = "divinitymc", bus = EventBusSubscriber.Bus.MOD)
public final class TestEquipment {
    public static final ResourceLocation CHEST = ResourceLocation.parse("divinitymc_test:standard_chestplate");
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        // Normal dev clients/servers must have the same registry as the released jar.
        if (!Boolean.getBoolean("divinitymc.testFixtures")) return;
        event.register(Registries.ITEM, CHEST,
            () -> new ArmorItem(ArmorMaterials.DIAMOND, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    }
}
