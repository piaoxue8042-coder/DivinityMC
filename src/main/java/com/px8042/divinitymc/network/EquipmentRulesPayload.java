package com.px8042.divinitymc.network;

import com.px8042.divinitymc.DivinityMC;
import com.px8042.divinitymc.client.EquipmentTooltipRules;
import com.px8042.divinitymc.config.ArmorConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.Map;

public record EquipmentRulesPayload(Map<ResourceLocation, ArmorConfig.Capacity> equipment) implements CustomPacketPayload {
    public EquipmentRulesPayload { equipment = Map.copyOf(equipment); }
    public static final Type<EquipmentRulesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DivinityMC.MODID, "equipment_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EquipmentRulesPayload> CODEC = StreamCodec.of(
        (buf, p) -> buf.writeMap(p.equipment, (b, id) -> b.writeResourceLocation(id),
            (b, value) -> { b.writeDouble(value.physical()); b.writeDouble(value.magic()); }),
        buf -> new EquipmentRulesPayload(buf.readMap(b -> b.readResourceLocation(),
            b -> new ArmorConfig.Capacity(b.readDouble(), b.readDouble()))));
    @Override public Type<EquipmentRulesPayload> type() { return TYPE; }
    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new EquipmentRulesPayload(ArmorConfig.rules().equipment()));
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(TYPE, CODEC, (payload, context) ->
            context.enqueueWork(() -> EquipmentTooltipRules.accept(payload.equipment)));
    }
}
