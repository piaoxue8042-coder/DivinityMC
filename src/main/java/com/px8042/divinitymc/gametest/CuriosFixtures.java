package com.px8042.divinitymc.gametest;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.registries.RegisterEvent;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Only registered by the dedicated GameTest run; excluded from release jars. */
public final class CuriosFixtures {
    public static final ResourceLocation RING = ResourceLocation.parse("divinitymc_test:armor_ring");
    public static final ResourceLocation EMPTY = ResourceLocation.parse("divinitymc_test:empty_ring");
    public static void register(RegisterEvent event) {
        event.register(Registries.ITEM, RING, () -> new Ring(3));
        event.register(Registries.ITEM, EMPTY, () -> new Ring(0));
    }
    private static final class Ring extends Item implements ICurioItem {
        private final double armor;
        Ring(double armor) { super(new Properties().durability(100)); this.armor = armor; }
        @Override public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext context, ResourceLocation id, ItemStack stack) {
            var nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            double value = nbt.contains("armor") ? nbt.getDouble("armor") : armor;
            Multimap<Holder<Attribute>, AttributeModifier> result = LinkedHashMultimap.create();
            result.put(Attributes.ARMOR, new AttributeModifier(id.withSuffix("/flat"), value, AttributeModifier.Operation.ADD_VALUE));
            result.put(Attributes.ARMOR, new AttributeModifier(id.withSuffix("/percent"), 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            result.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id.withSuffix("/toughness"), 20, AttributeModifier.Operation.ADD_VALUE));
            result.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(id.withSuffix("/speed"), 0.01, AttributeModifier.Operation.ADD_VALUE));
            return result;
        }
    }
}
