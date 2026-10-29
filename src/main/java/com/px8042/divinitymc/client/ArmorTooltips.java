package com.px8042.divinitymc.client;

import com.px8042.divinitymc.config.ArmorConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ArmorTooltips {
    public static void onTooltip(ItemTooltipEvent event) {
        var player = event.getEntity();
        var equipment = player != null && !player.level().isClientSide
            ? ArmorConfig.rules().equipment() : EquipmentTooltipRules.equipment();
        update(event.getItemStack(), event.getToolTip(), equipment);
    }

    public static void update(ItemStack stack, List<Component> lines, Map<ResourceLocation, ArmorConfig.Capacity> equipment) {
        // Inspect translation components, never localized text or modifier IDs: netherite armor
        // shares modifier IDs with knockback resistance, whose tooltip must remain visible.
        lines.removeIf(line -> containsKey(line, Attributes.ARMOR.value().getDescriptionId())
            || containsKey(line, Attributes.ARMOR_TOUGHNESS.value().getDescriptionId()));
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).getContents() instanceof TranslatableContents text
                && text.getKey().startsWith("item.modifiers.")
                && (i + 1 == lines.size() || lines.get(i + 1).getString().isEmpty())) {
                lines.remove(i);
                if (i > 0 && lines.get(i - 1).getString().isEmpty()) lines.remove(--i);
            }
        }
        if (!(stack.getItem() instanceof Equipable equipable)
            || !stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).showInTooltip()) return;

        String header = "item.modifiers." + equipable.getEquipmentSlot().getName();
        int index = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getContents() instanceof TranslatableContents text && text.getKey().equals(header)) {
                index = i + 1; break;
            }
        }
        if (index < 0) {
            // Put the new group directly below the item name, ahead of durability and debug IDs.
            index = Math.min(1, lines.size());
            lines.add(index++, Component.empty());
            lines.add(index++, Component.translatable(header).withStyle(ChatFormatting.GRAY));
            if (index < lines.size()) lines.add(index, Component.empty());
        }
        if (equipment == null) {
            lines.add(index, Component.translatable("tooltip.divinitymc.syncing").withStyle(ChatFormatting.GRAY));
            return;
        }
        var capacity = equipment.getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()), new ArmorConfig.Capacity(0, 0));
        lines.add(index++, Component.translatable("tooltip.divinitymc.physical", number(capacity.physical())).withColor(0xCE9B55));
        lines.add(index, Component.translatable("tooltip.divinitymc.magic", number(capacity.magic())).withColor(0x648FE8));
    }

    private static String number(double value) { return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    private static boolean containsKey(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents text) {
            if (text.getKey().equals(key)) return true;
            for (Object argument : text.getArgs())
                if (argument instanceof Component child && containsKey(child, key)) return true;
        }
        for (Component sibling : component.getSiblings()) if (containsKey(sibling, key)) return true;
        return false;
    }
}
