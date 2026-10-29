package com.px8042.divinitymc;

import com.mojang.logging.LogUtils;
import com.px8042.divinitymc.armor.ArmorAttachments;
import com.px8042.divinitymc.armor.ArmorEvents;
import com.px8042.divinitymc.command.ArmorCommands;
import com.px8042.divinitymc.config.ArmorConfig;
import com.px8042.divinitymc.network.ArmorPayload;
import com.px8042.divinitymc.network.EquipmentRulesPayload;
import com.px8042.divinitymc.client.ArmorTooltips;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(DivinityMC.MODID)
public final class DivinityMC {
    public static final String MODID = "divinitymc";
    public static final Logger LOGGER = LogUtils.getLogger();
    public DivinityMC(IEventBus modBus) {
        ArmorAttachments.TYPES.register(modBus);
        com.px8042.divinitymc.armor.EquipmentArmor.TYPES.register(modBus);
        modBus.addListener(ArmorPayload::register);
        modBus.addListener(EquipmentRulesPayload::register);
        NeoForge.EVENT_BUS.addListener(ArmorTooltips::onTooltip);
        NeoForge.EVENT_BUS.register(ArmorEvents.class);
        NeoForge.EVENT_BUS.addListener(ArmorCommands::register);
        NeoForge.EVENT_BUS.addListener(ArmorConfig::onServerStarting);
    }
}
