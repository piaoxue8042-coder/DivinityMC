package com.px8042.divinitymc.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Equipable;
import java.util.HashMap;
import java.util.Map;

/** Resolve once per rules reload. Never derive capacities from an entity's total armor attribute. */
public final class EquipmentDefaults {
    public record Automatic(boolean enabled, double physicalMultiplier, double magicMultiplier) {
        public Automatic {
            com.px8042.divinitymc.armor.ArmorState.valid(physicalMultiplier);
            com.px8042.divinitymc.armor.ArmorState.valid(magicMultiplier);
        }
    }
    public static Map<ResourceLocation, ArmorConfig.Capacity> resolve(
            Automatic automatic, Map<ResourceLocation, ArmorConfig.Capacity> overrides) {
        Map<ResourceLocation, ArmorConfig.Capacity> result = new HashMap<>();
        BuiltInRegistries.ITEM.forEach(item -> {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (!(item instanceof Equipable)) return;
            if (overrides.containsKey(id)) {
                result.put(id, overrides.get(id)); // Explicit 0/0 disables capacity; never fall back.
            } else if (item instanceof ArmorItem armor) {
                if (id.getNamespace().equals("minecraft")) {
                    result.put(id, vanilla(id, armor));
                } else if (automatic.enabled()) {
                    result.put(id, new ArmorConfig.Capacity(armor.getDefense() * automatic.physicalMultiplier(),
                        armor.getDefense() * automatic.magicMultiplier()));
                }
            }
        });
        return Map.copyOf(result);
    }
    private static ArmorConfig.Capacity vanilla(ResourceLocation id, ArmorItem armor) {
        return switch (id.getPath()) {
            case "turtle_helmet" -> new ArmorConfig.Capacity(12, 0);
            case "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots" ->
                new ArmorConfig.Capacity(armor.getDefense(), armor.getDefense() * 2);
            default -> new ArmorConfig.Capacity(armor.getDefense() * 2, armor.getDefense());
        };
    }
}
