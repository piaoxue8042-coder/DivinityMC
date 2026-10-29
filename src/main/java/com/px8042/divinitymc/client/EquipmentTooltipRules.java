package com.px8042.divinitymc.client;

import com.px8042.divinitymc.config.ArmorConfig.Capacity;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Client display data only. Never consult the client's local server configuration. */
public final class EquipmentTooltipRules {
    private static Map<ResourceLocation, Capacity> equipment;
    public static Map<ResourceLocation, Capacity> equipment() { return equipment; }
    public static void accept(Map<ResourceLocation, Capacity> values) { equipment = Map.copyOf(values); }
    public static void clear() { equipment = null; }
}
