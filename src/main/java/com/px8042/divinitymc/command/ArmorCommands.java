package com.px8042.divinitymc.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.px8042.divinitymc.api.*;
import com.px8042.divinitymc.armor.*;
import com.px8042.divinitymc.config.ArmorConfig;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.Locale;
import static net.minecraft.commands.Commands.*;

public final class ArmorCommands {
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("divinitymc").requires(source -> source.hasPermission(2))
            .then(literal("get").then(argument("targets", EntityArgument.entities()).executes(c -> each(c, "get"))))
            .then(literal("fill").then(argument("targets", EntityArgument.entities()).executes(c -> each(c, "fill"))))
            .then(literal("reset").then(argument("targets", EntityArgument.entities()).executes(c -> each(c, "reset"))))
            .then(literal("set").then(argument("targets", EntityArgument.entities())
                .then(argument("physical", DoubleArgumentType.doubleArg(0, Float.MAX_VALUE))
                    .then(argument("magic", DoubleArgumentType.doubleArg(0, Float.MAX_VALUE)).executes(c -> each(c, "set"))))))
            .then(literal("reload").executes(c -> {
                try {
                    ArmorConfig.reload(c.getSource().getServer());
                    for (var level : c.getSource().getServer().getAllLevels())
                        for (var entity : level.getAllEntities()) if (entity instanceof LivingEntity living) ArmorService.sync(living);
                    c.getSource().sendSuccess(() -> Component.literal("DivinityMC: configuration reloaded."), true); return 1;
                } catch (Exception error) {
                    c.getSource().sendFailure(Component.literal("Configuration rejected; previous rules retained: " + error.getMessage())); return 0;
                }
            }))
            .then(literal("classify").then(argument("damage_type", StringArgumentType.greedyString()).executes(c -> {
                var id = ResourceLocation.tryParse(StringArgumentType.getString(c, "damage_type"));
                if (id == null) { c.getSource().sendFailure(Component.literal("Invalid resource ID")); return 0; }
                var kind = ArmorConfig.rules().damage().getOrDefault(id, ArmorKind.PHYSICAL);
                c.getSource().sendSuccess(() -> Component.literal(id + " -> " + kind), false); return 1;
            }))));
    }
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
