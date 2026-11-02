package com.px8042.divinitymc.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import java.util.HashMap;
import java.util.Map;

/** Only fixed values belong in the synchronized type table. */
public final class EquipmentDefaults {
    public record Automatic(boolean enabled, double physicalMultiplier, double magicMultiplier) {
        public Automatic {
            com.px8042.divinitymc.armor.ArmorState.valid(physicalMultiplier);
            com.px8042.divinitymc.armor.ArmorState.valid(magicMultiplier);
        }
        public ArmorConfig.Capacity convert(double flatArmor) {
            double armor = Math.max(0, flatArmor);
            double physical = armor * physicalMultiplier;
            double magic = armor * magicMultiplier;
            if (!enabled || !Double.isFinite(physical) || !Double.isFinite(magic)
                    || physical > Float.MAX_VALUE || magic > Float.MAX_VALUE) return new ArmorConfig.Capacity(0, 0);
            return new ArmorConfig.Capacity(physical, magic);
        }
    }
    public static Map<ResourceLocation, ArmorConfig.Capacity> resolve(
            Map<ResourceLocation, ArmorConfig.Capacity> overrides) {
        Map<ResourceLocation, ArmorConfig.Capacity> result = new HashMap<>();
        BuiltInRegistries.ITEM.forEach(item -> {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (overrides.containsKey(id)) {
                result.put(id, overrides.get(id)); // Explicit 0/0 disables capacity; never fall back.
            } else if (id.getNamespace().equals("minecraft") && item instanceof ArmorItem armor) {
                result.put(id, vanilla(id, armor));
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
