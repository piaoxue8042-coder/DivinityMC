package com.px8042.divinitymc.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.px8042.divinitymc.api.*;
import com.px8042.divinitymc.armor.*;
import com.px8042.divinitymc.config.ArmorConfig;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Equipable;
import java.math.BigDecimal;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.Locale;
import static net.minecraft.commands.Commands.*;

public final class ArmorCommands {
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("divinitymc").requires(source -> source.hasPermission(2))
            .executes(c -> {
                c.getSource().sendSuccess(() -> Component.translatable("command.divinitymc.equipment.help"), false); return 1;
            })
            .then(literal("get").then(argument("targets", EntityArgument.entities()).executes(c -> each(c, "get"))))
            .then(literal("fill").then(argument("targets", EntityArgument.entities()).executes(c -> each(c, "fill"))))
            .then(literal("reset").then(argument("targets", EntityArgument.entities()).executes(c -> each(c, "reset"))))
            .then(literal("set").then(argument("targets", EntityArgument.entities())
                .then(argument("physical", DoubleArgumentType.doubleArg(0, Float.MAX_VALUE))
                    .then(argument("magic", DoubleArgumentType.doubleArg(0, Float.MAX_VALUE)).executes(c -> each(c, "set"))))))
            .then(literal("reload").executes(c -> {
                try {
                    ArmorConfig.reload(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("DivinityMC: configuration reloaded."), true); return 1;
                } catch (Exception error) {
                    c.getSource().sendFailure(Component.literal("Configuration rejected; previous rules retained: " + error.getMessage())); return 0;
                }
            }))
            .then(literal("held").executes(c -> equipment(c, true, "get"))
                .then(equipmentSet(true))
                .then(literal("reset").executes(c -> equipment(c, true, "reset"))))
            .then(literal("equipment").then(argument("item", ItemArgument.item(event.getBuildContext()))
                .executes(c -> equipment(c, false, "get"))
                .then(equipmentSet(false))
                .then(literal("reset").executes(c -> equipment(c, false, "reset")))))
            .then(literal("classify").then(argument("damage_type", StringArgumentType.greedyString()).executes(c -> {
                var id = ResourceLocation.tryParse(StringArgumentType.getString(c, "damage_type"));
                if (id == null) { c.getSource().sendFailure(Component.literal("Invalid resource ID")); return 0; }
                var kind = ArmorConfig.rules().damage().getOrDefault(id, ArmorKind.PHYSICAL);
                c.getSource().sendSuccess(() -> Component.literal(id + " -> " + kind), false); return 1;
            }))));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> equipmentSet(boolean held) {
        return literal("set").then(argument("physical", DoubleArgumentType.doubleArg(0, Float.MAX_VALUE))
            .then(argument("magic", DoubleArgumentType.doubleArg(0, Float.MAX_VALUE))
                .executes(c -> equipment(c, held, "set"))));
    }
    private static int equipment(CommandContext<CommandSourceStack> c, boolean held, String operation) throws CommandSyntaxException {
        if (held) return heldEquipment(c, operation);
        var source = c.getSource();
        var item = ItemArgument.getItem(c, "item").getItem();
        var id = BuiltInRegistries.ITEM.getKey(item);
        if (!operation.equals("get") && !(item instanceof Equipable)) {
            source.sendFailure(Component.translatable("command.divinitymc.equipment.unsupported", id.toString())); return 0;
        }
        if (!operation.equals("get")) {
            var value = operation.equals("reset") ? null : new ArmorConfig.Capacity(
                DoubleArgumentType.getDouble(c, "physical"), DoubleArgumentType.getDouble(c, "magic"));
            try { ArmorConfig.editEquipment(source.getServer(), id, value); }
            catch (Exception error) {
                com.px8042.divinitymc.DivinityMC.LOGGER.warn("Equipment configuration edit failed for {}", id, error);
                source.sendFailure(Component.translatable("command.divinitymc.equipment.failed", error.getMessage())); return 0;
            }
        }
        var rules = ArmorConfig.rules();
        var capacity = rules.equipment().getOrDefault(id, new ArmorConfig.Capacity(0, 0));
        var origin = Component.translatable("command.divinitymc.equipment.source." + rules.equipmentSource(id));
        source.sendSuccess(() -> Component.translatable("command.divinitymc.equipment." + operation,
            id.toString(), number(capacity.physical()), number(capacity.magic()), origin), !operation.equals("get"));
        return 1;
    }
    private static int heldEquipment(CommandContext<CommandSourceStack> c, String operation) throws CommandSyntaxException {
        var source = c.getSource();
        var player = source.getPlayerOrException();
        var stack = player.getMainHandItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof Equipable)) {
            source.sendFailure(Component.translatable("command.divinitymc.equipment.hold")); return 0;
        }
        if (!operation.equals("get")) {
            if (stack.getCount() != 1) {
                source.sendFailure(Component.translatable("command.divinitymc.held.split")); return 0;
            }
            if (operation.equals("reset")) stack.remove(EquipmentArmor.OVERRIDE.get());
            else stack.set(EquipmentArmor.OVERRIDE.get(), new ArmorConfig.Capacity(
                DoubleArgumentType.getDouble(c, "physical"), DoubleArgumentType.getDouble(c, "magic")));
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
            ArmorService.sync(player);
        }
        var rules = ArmorConfig.rules();
        var capacity = EquipmentArmor.resolve(stack, rules.equipment());
        var origin = Component.translatable("command.divinitymc.equipment.source." + EquipmentArmor.source(stack, rules));
        source.sendSuccess(() -> Component.translatable("command.divinitymc.held." + operation,
            stack.getHoverName(), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
            number(capacity.physical()), number(capacity.magic()), origin), !operation.equals("get"));
        return 1;
    }
    private static String number(double value) { return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    private static int each(CommandContext<CommandSourceStack> c, String operation) throws CommandSyntaxException {
        int count = 0;
        for (var entity : EntityArgument.getEntities(c, "targets")) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (operation.equals("reset")) living.setData(ArmorAttachments.ARMOR, new ArmorState());
            var state = ArmorService.state(living);
            if (operation.equals("fill")) {
                state.set(ArmorKind.PHYSICAL, state.maximum(ArmorKind.PHYSICAL));
                state.set(ArmorKind.MAGIC, state.maximum(ArmorKind.MAGIC));
            } else if (operation.equals("set")) {
                state.set(ArmorKind.PHYSICAL, DoubleArgumentType.getDouble(c, "physical"));
                state.set(ArmorKind.MAGIC, DoubleArgumentType.getDouble(c, "magic"));
            }
            if (!operation.equals("get")) ArmorService.sync(living);
            var s = state.snapshot();
            String text = String.format(Locale.ROOT, "%s: physical %.2f/%.2f, magic %.2f/%.2f, delay %d ticks%s",
                living.getName().getString(), s.physical(), s.maxPhysical(), s.magic(), s.maxMagic(), s.recoveryDelay(), s.quarantined() ? " [QUARANTINED]" : "");
            c.getSource().sendSuccess(() -> Component.literal(text), false); count++;
        }
        return count;
    }
}
