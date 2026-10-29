package com.px8042.divinitymc;

import com.px8042.divinitymc.armor.ArmorAttachments;
import com.px8042.divinitymc.client.EquipmentTooltipRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import java.util.Locale;

@Mod(value = DivinityMC.MODID, dist = Dist.CLIENT)
public final class DivinityMCClient {
    public DivinityMCClient(IEventBus modBus) {
        modBus.addListener(DivinityMCClient::layers);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> EquipmentTooltipRules.clear());
    }
    private static void layers(RegisterGuiLayersEvent event) {
        event.replaceLayer(VanillaGuiLayers.ARMOR_LEVEL, (graphics, delta) -> {});
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(DivinityMC.MODID, "armor_hud"), (graphics, delta) -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
            // Keep clear of the health/food/hotbar stack even at Minecraft's minimum window size.
            draw(graphics, mc.player, 8, 8, false);
            if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target)
                draw(graphics, target, graphics.guiWidth() / 2 - 80, 48, true);
        });
    }
    private static void draw(GuiGraphics graphics, LivingEntity entity, int x, int y, boolean name) {
        if (!entity.hasData(ArmorAttachments.ARMOR)) return;
        var s = entity.getData(ArmorAttachments.ARMOR).snapshot();
        var font = Minecraft.getInstance().font;
        if (name) { graphics.drawString(font, entity.getDisplayName(), x, y, 0xFFFFFF); y += 11; }
        bar(graphics, x, y, s.physical(), s.maxPhysical(), 0xFFCE9B55, "hud.divinitymc.physical");
        bar(graphics, x, y + 13, s.magic(), s.maxMagic(), 0xFF648FE8, "hud.divinitymc.magic");
        var data = entity.getPersistentData();
        if (s.quarantined()) graphics.drawString(font, Component.translatable("hud.divinitymc.quarantined"), x, y - 11, 0xFF5555);
        else if (data.getLong("divinitymc:feedback_until") > entity.level().getGameTime()) {
            int flags = data.getInt("divinitymc:feedback");
            String key = (flags & 3) != 0 ? "hud.divinitymc.broken" : "hud.divinitymc.blocked";
            graphics.drawString(font, Component.translatable(key), x + 162, y, 0xFFFFFF);
        }
    }
    private static void bar(GuiGraphics graphics, int x, int y, double current, double maximum, int color, String key) {
        graphics.fill(x, y, x + 158, y + 11, 0xCC181C23);
        int width = maximum <= 0 ? 0 : (int)Math.round(156 * Math.min(1, current / maximum));
        graphics.fill(x + 1, y + 1, x + 1 + width, y + 10, color);
        String values = String.format(Locale.ROOT, " %.1f / %.1f", current, maximum);
        graphics.drawString(Minecraft.getInstance().font, Component.translatable(key).append(values), x + 3, y + 1, 0xFFFFFF);
    }
}
