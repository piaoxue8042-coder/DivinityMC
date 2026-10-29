package com.px8042.divinitymc.network;

import com.px8042.divinitymc.DivinityMC;
import com.px8042.divinitymc.armor.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;

public record ArmorPayload(int entityId, UUID uuid, ResourceLocation dimension,
                           ArmorState.Snapshot snapshot, int feedback) implements CustomPacketPayload {
    public static final Type<ArmorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DivinityMC.MODID, "armor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeVarInt(p.entityId); buf.writeUUID(p.uuid); buf.writeResourceLocation(p.dimension);
            var s = p.snapshot;
            buf.writeDouble(s.physical()); buf.writeDouble(s.magic()); buf.writeDouble(s.maxPhysical()); buf.writeDouble(s.maxMagic());
            buf.writeVarInt(s.recoveryDelay()); buf.writeBoolean(s.initialized()); buf.writeBoolean(s.quarantined()); buf.writeVarInt(p.feedback);
        },
        buf -> new ArmorPayload(buf.readVarInt(), buf.readUUID(), buf.readResourceLocation(),
            new ArmorState.Snapshot(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readVarInt(), buf.readBoolean(), buf.readBoolean()), buf.readVarInt()));
    @Override public Type<ArmorPayload> type() { return TYPE; }
    public static ArmorPayload of(LivingEntity entity, ArmorState state) {
        return new ArmorPayload(entity.getId(), entity.getUUID(), entity.level().dimension().location(), state.snapshot(), state.feedback);
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> {
            var level = context.player().level();
            if (!level.dimension().location().equals(payload.dimension)) return;
            var entity = level.getEntity(payload.entityId);
            if (!(entity instanceof LivingEntity living) || !entity.getUUID().equals(payload.uuid)) return;
            living.setData(ArmorAttachments.ARMOR, ArmorState.restore(payload.snapshot));
            if (payload.feedback != 0) {
                var data = living.getPersistentData();
                data.putInt("divinitymc:feedback", payload.feedback);
                data.putLong("divinitymc:feedback_until", level.getGameTime() + 15);
            }
        }));
    }
}
