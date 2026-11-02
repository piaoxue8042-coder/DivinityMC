package com.px8042.divinitymc.client;

import com.px8042.divinitymc.config.ArmorConfig.Capacity;
import com.px8042.divinitymc.config.EquipmentDefaults;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Client display data only. Never consult the client's local server configuration. */
public final class EquipmentTooltipRules {
    private static Map<ResourceLocation, Capacity> equipment;
    private static EquipmentDefaults.Automatic automatic;
    public static Map<ResourceLocation, Capacity> equipment() { return equipment; }
    public static EquipmentDefaults.Automatic automatic() { return automatic; }
    public static void accept(Map<ResourceLocation, Capacity> values) { accept(values, new EquipmentDefaults.Automatic(true, 2, 1)); }
    public static void accept(Map<ResourceLocation, Capacity> values, EquipmentDefaults.Automatic conversion) {
        equipment = Map.copyOf(values); automatic = conversion;
    }
    public static void clear() { equipment = null; automatic = null; }
}
