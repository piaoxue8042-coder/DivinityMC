package com.px8042.divinitymc.compat;

import com.px8042.divinitymc.armor.EquipmentArmor;
import com.px8042.divinitymc.config.ArmorConfig;
import com.px8042.divinitymc.config.EquipmentDefaults;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import java.util.ArrayList;
import java.util.List;

/** Called only behind the optional-mod guard. Cosmetics never enter the functional stack list. */
final class CuriosArmor {
    private record Equipped(ItemStack stack, SlotContext context) {}

    static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CuriosArmor::changed);
    }
    private static void changed(top.theillusivec4.curios.api.event.CurioChangeEvent event) {
        com.px8042.divinitymc.armor.ArmorService.sync(event.getEntity());
    }

    static boolean supports(ItemStack stack, boolean client) {
        return !stack.isEmpty() && (CuriosApi.getCurio(stack).isPresent()
            || stack.getTags().anyMatch(tag -> tag.location().getNamespace().equals("curios"))
            || !CuriosApi.getItemStackSlots(stack, client).isEmpty());
    }

    private static List<Equipped> equipped(LivingEntity entity) {
        List<Equipped> result = new ArrayList<>();
        CuriosApi.getCuriosInventory(entity).ifPresent(inventory -> inventory.getCurios().forEach((id, handler) -> {
            var stacks = handler.getStacks();
            var active = handler.getActiveStates();
            var renders = handler.getRenders();
            for (int i = 0; i < stacks.getSlots(); i++) {
                var stack = stacks.getStackInSlot(i);
                if (stack.isEmpty() || i >= active.size() || !active.get(i)) continue;
                result.add(new Equipped(stack, new SlotContext(id, entity, i, false, i < renders.size() && renders.get(i))));
            }
        }));
        return result;
    }

    static ArmorConfig.Capacity capacity(LivingEntity entity, ArmorConfig.Rules rules) {
        var total = new ArmorConfig.Capacity(0, 0);
        for (var slot : equipped(entity)) {
            // Individual data and type/default capacities take precedence over Curios attribute conversion.
            var fixed = EquipmentArmor.explicitOrDefault(slot.stack(), rules.equipment());
            total = total.plus(fixed != null ? fixed : automatic(slot.stack(), slot.context(), rules.autoEquipment()));
        }
        return total;
    }

    static ArmorConfig.Capacity preview(ItemStack stack, LivingEntity wearer, EquipmentDefaults.Automatic automatic) {
        if (wearer != null) {
            for (var entry : equipped(wearer))
                if (entry.stack() == stack) return automatic(stack, entry.context(), automatic);
        }
        boolean client = wearer != null && wearer.level().isClientSide;
        if (net.minecraft.world.item.Equipable.get(stack) != null || !supports(stack, client)) return null;
        String slot = CuriosApi.getItemStackSlots(stack, client).keySet().stream().sorted().findFirst().orElse("curio");
        return automatic(stack, new SlotContext(slot, wearer, 0, false, true), automatic);
    }

    private static ArmorConfig.Capacity automatic(ItemStack stack, SlotContext context, EquipmentDefaults.Automatic automatic) {
        if (!automatic.enabled()) return new ArmorConfig.Capacity(0, 0);
        double armor = 0;
        // Ask Curios for this item and slot, never read the entity's already-aggregated armor value.
        var modifiers = CuriosApi.getAttributeModifiers(context, CuriosApi.getSlotId(context), stack);
        for (var modifier : modifiers.get(Attributes.ARMOR)) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE && Double.isFinite(modifier.amount()))
                armor += modifier.amount();
        }
        return automatic.convert(armor);
    }
}
