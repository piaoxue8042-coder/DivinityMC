package com.px8042.divinitymc.armor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.px8042.divinitymc.DivinityMC;
import com.px8042.divinitymc.config.ArmorConfig;
import com.px8042.divinitymc.config.EquipmentDefaults;
import com.px8042.divinitymc.compat.CuriosIntegration;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Equipable;
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
    private static final ResourceLocation TRAVELLERS_ARMOR = ResourceLocation.parse("twilightforest:travellers_armor");
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
    public static ArmorConfig.Capacity explicitOrDefault(ItemStack stack, Map<ResourceLocation, ArmorConfig.Capacity> typeRules) {
        var independent = stack.get(OVERRIDE.get());
        return independent != null ? independent : typeRules == null ? null : typeRules.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
    public static ArmorConfig.Capacity resolve(ItemStack stack, Map<ResourceLocation, ArmorConfig.Capacity> typeRules,
            EquipmentDefaults.Automatic automatic, LivingEntity wearer) {
        var fixed = explicitOrDefault(stack, typeRules);
        if (fixed != null || typeRules == null) return fixed;
        var curio = CuriosIntegration.preview(stack, wearer, automatic);
        if (curio != null) return curio;
        if ((wearer == null || wearer.level().isClientSide) && brokenTravellerPreview(stack))
            return new ArmorConfig.Capacity(0, 0);
        var wearable = Equipable.get(stack);
        return wearable == null ? new ArmorConfig.Capacity(0, 0) : fromSlot(stack, wearable.getEquipmentSlot(), automatic);
    }
    // Twilight Forest disables these modifiers in a server-only event. Remote tooltip generation
    // falls back to the item's intact attributes even after receiving the broken stack.
    private static boolean brokenTravellerPreview(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage() - 1) return false;
        var marker = BuiltInRegistries.DATA_COMPONENT_TYPE.get(TRAVELLERS_ARMOR);
        return marker != null && stack.has(marker);
    }
    /** Called for the actual equipment slot; never substitute a Curios preview on this path. */
    public static ArmorConfig.Capacity worn(ItemStack stack, EquipmentSlot slot, ArmorConfig.Rules rules) {
        var fixed = explicitOrDefault(stack, rules.equipment());
        return fixed != null ? fixed : fromSlot(stack, slot, rules.autoEquipment());
    }
    private static ArmorConfig.Capacity fromSlot(ItemStack stack, EquipmentSlot slot, EquipmentDefaults.Automatic automatic) {
        if (!automatic.enabled() || BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft"))
            return new ArmorConfig.Capacity(0, 0);
        double[] armor = {0};
        stack.forEachModifier(slot, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ARMOR) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE
                    && Double.isFinite(modifier.amount())) armor[0] += modifier.amount();
        });
        return automatic.convert(armor[0]);
    }
    public static boolean supports(ItemStack stack, boolean client) {
        return Equipable.get(stack) != null || CuriosIntegration.supports(stack, client);
    }
    public static String source(ItemStack stack, ArmorConfig.Rules rules) {
        if (stack.has(OVERRIDE.get())) return "stack";
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!rules.equipment().containsKey(id) && rules.autoEquipment().enabled()) {
            if (!id.getNamespace().equals("minecraft") && Equipable.get(stack) != null) return "automatic";
            if (CuriosIntegration.supports(stack, false)) return "curios";
        }
        return rules.equipmentSource(id);
    }
}
