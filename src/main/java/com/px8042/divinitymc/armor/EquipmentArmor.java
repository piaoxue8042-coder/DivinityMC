package com.px8042.divinitymc.armor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.px8042.divinitymc.DivinityMC;
import com.px8042.divinitymc.config.ArmorConfig;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.Map;
import java.util.function.Supplier;

public final class EquipmentArmor {
    private static final Codec<Double> VALUE = Codec.DOUBLE.validate(value ->
        Double.isFinite(value) && value >= 0 && value <= Float.MAX_VALUE
            ? DataResult.success(value) : DataResult.error(() -> "Invalid armor capacity: " + value));
    public static final Codec<ArmorConfig.Capacity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        VALUE.fieldOf("physical").forGetter(ArmorConfig.Capacity::physical),
        VALUE.fieldOf("magic").forGetter(ArmorConfig.Capacity::magic)).apply(instance, ArmorConfig.Capacity::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorConfig.Capacity> STREAM_CODEC = StreamCodec.of(
        (buf, value) -> { buf.writeDouble(value.physical()); buf.writeDouble(value.magic()); },
        buf -> new ArmorConfig.Capacity(buf.readDouble(), buf.readDouble()));
    public static final DeferredRegister<DataComponentType<?>> TYPES = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, DivinityMC.MODID);
    public static final Supplier<DataComponentType<ArmorConfig.Capacity>> OVERRIDE = TYPES.register("armor_capacity_override", () ->
        DataComponentType.<ArmorConfig.Capacity>builder().persistent(CODEC).networkSynchronized(STREAM_CODEC).build());

    /** Null means the client is still waiting for type rules; a stack override is already self-contained. */
    public static ArmorConfig.Capacity resolve(ItemStack stack, Map<ResourceLocation, ArmorConfig.Capacity> typeRules) {
        var independent = stack.get(OVERRIDE.get());
        if (independent != null) return independent;
        return typeRules == null ? null : typeRules.getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()), new ArmorConfig.Capacity(0, 0));
    }
    public static String source(ItemStack stack, ArmorConfig.Rules rules) {
        return stack.has(OVERRIDE.get()) ? "stack" : rules.equipmentSource(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
