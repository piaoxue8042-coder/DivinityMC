package com.px8042.divinitymc.gametest;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.px8042.divinitymc.api.ArmorKind;
import com.px8042.divinitymc.armor.ArmorService;
import com.px8042.divinitymc.armor.EquipmentArmor;
import com.px8042.divinitymc.config.ArmorConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import java.nio.file.Files;
import java.util.UUID;

@GameTestHolder("divinitymc")
@PrefixGameTestTemplate(false)
public final class EquipmentCommandGameTests {
    private static int execute(CommandSourceStack source, String command) throws CommandSyntaxException {
        return source.getServer().getCommands().getDispatcher().execute(command, source);
    }
    private static void expectRejected(GameTestHelper h, CommandSourceStack source, String command) {
        boolean rejected = false;
        try { rejected = execute(source, command) == 0; } catch (CommandSyntaxException expected) { rejected = true; }
        h.assertTrue(rejected, "command rejected: " + command);
    }
    @GameTest(template = "test_empty")
    public static void heldEditsOnlyOneItemAndNeverWritesConfig(GameTestHelper h) throws Exception {
        var server = h.getLevel().getServer(); var path = ArmorConfig.path(); var original = Files.readString(path);
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ArmorEditor"));
        var item = Items.IRON_CHESTPLATE;
        var held = new ItemStack(item); player.setItemSlot(EquipmentSlot.MAINHAND, held);
        held.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Same name"));
        held.setDamageValue(35);
        held.enchant(h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
            .getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION), 1);
        var sameName = held.copy();
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        var first = h.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        var second = h.spawnWithNoFreeWill(EntityType.COW, 3, 2, 3);
        first.setItemSlot(EquipmentSlot.CHEST, sameName.copy()); second.setItemSlot(EquipmentSlot.CHEST, sameName);
        var a = ArmorService.state(first); var b = ArmorService.state(second);
        a.set(ArmorKind.PHYSICAL, 5); b.set(ArmorKind.PHYSICAL, 6);
        try {
            h.assertTrue(execute(source, "divinitymc held") == 1, "held query works");
            h.assertTrue(execute(source, "divinitymc held set 10.5 20") == 1, "held edit succeeds");
            h.assertTrue(a.maximum(ArmorKind.PHYSICAL) == 12 && b.maximum(ArmorKind.PHYSICAL) == 12, "other same-name items remain unchanged");
            h.assertFalse(sameName.has(EquipmentArmor.OVERRIDE.get()), "same name does not link items");
            h.assertTrue(held.getDamageValue() == 35 && held.isEnchanted() && held.getHoverName().getString().equals("Same name"), "damage enchantments and name preserved");
            var copy = held.copy();
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); first.setItemSlot(EquipmentSlot.CHEST, held);
            ArmorService.state(first);
            h.assertTrue(a.maximum(ArmorKind.PHYSICAL) == 10.5 && a.maximum(ArmorKind.MAGIC) == 20, "independent values used when equipped");
            h.assertTrue(a.current(ArmorKind.PHYSICAL) == 5 && b.current(ArmorKind.PHYSICAL) == 6, "equipping does not refill armor");
            first.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.MAINHAND, held);
            h.assertTrue(execute(source, "divinitymc held set 0 0") == 1, "explicit zero capacity supported");
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); first.setItemSlot(EquipmentSlot.CHEST, held); ArmorService.state(first);
            h.assertTrue(a.maximum(ArmorKind.PHYSICAL) == 0 && a.current(ArmorKind.MAGIC) == 0, "zero override clamps existing armor");
            h.assertTrue(copy.get(EquipmentArmor.OVERRIDE.get()).physical() == 10.5, "editing source does not change copied item");
            first.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.MAINHAND, held);
            h.assertTrue(execute(source, "divinitymc held reset") == 1, "held reset succeeds");
            first.setItemSlot(EquipmentSlot.CHEST, held.copy()); ArmorService.state(first);
            h.assertTrue(a.maximum(ArmorKind.PHYSICAL) == 12 && a.current(ArmorKind.PHYSICAL) == 0, "reset restores type limit without refilling");
            h.assertFalse(held.has(EquipmentArmor.OVERRIDE.get()), "reset removes only item component");
            h.assertTrue(Files.readString(path).equals(original), "held commands never write type configuration");
            h.assertTrue(player.getMainHandItem() == held && held.getCount() == 1, "edit does not replace or consume held stack");
        } finally { player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); Files.writeString(path, original); ArmorConfig.reload(server); }
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void equipmentIdEditsAndResetsUseBuiltInDefaults(GameTestHelper h) throws Exception {
        var server = h.getLevel().getServer(); var path = ArmorConfig.path(); var original = Files.readString(path);
        var source = server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        var id = ResourceLocation.parse("minecraft:turtle_helmet");
        try {
            var json = JsonParser.parseString(original).getAsJsonObject();
            var entries = json.getAsJsonArray("equipmentOverrides");
            entries.asList().removeIf(e -> ResourceLocation.parse(e.getAsJsonObject().get("id").getAsString()).equals(id));
            var shortId = new com.google.gson.JsonObject(); shortId.addProperty("id", "turtle_helmet");
            shortId.addProperty("physical", 1); shortId.addProperty("magic", 1); entries.add(shortId);
            Files.writeString(path, json.toString());
            h.assertTrue(execute(source, "divinitymc equipment minecraft:turtle_helmet set 2 30") == 1, "console can edit by item ID");
            h.assertTrue(new ArmorConfig.Capacity(2, 30).equals(ArmorConfig.rules().equipment().get(id)), "requested values applied");
            h.assertTrue(execute(source, "divinitymc equipment minecraft:turtle_helmet reset") == 1, "reset by ID succeeds");
            h.assertTrue(new ArmorConfig.Capacity(12, 0).equals(ArmorConfig.rules().equipment().get(id)), "reset uses special turtle default");
            h.assertTrue(execute(source, "divinitymc equipment minecraft:golden_chestplate reset") == 1, "gold override can be removed");
            h.assertTrue(new ArmorConfig.Capacity(5, 10).equals(ArmorConfig.rules().equipment().get(ResourceLocation.parse("minecraft:golden_chestplate"))), "gold default preserved");
        } finally { Files.writeString(path, original); ArmorConfig.reload(server); }
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void editPermissionsAndInvalidInputsDoNotWrite(GameTestHelper h) throws Exception {
        var server = h.getLevel().getServer(); var path = ArmorConfig.path(); var original = Files.readString(path);
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ArmorNoPerm"));
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_CHESTPLATE));
        expectRejected(h, source.withPermission(1), "divinitymc held set 99 99");
        expectRejected(h, source.withPermission(0), "divinitymc equipment minecraft:iron_chestplate reset");
        expectRejected(h, source, "divinitymc held set -1 5");
        expectRejected(h, source, "divinitymc held set NaN 5");
        expectRejected(h, source, "divinitymc held set 999999999999999999999999999999999999999999999 5");
        expectRejected(h, source, "divinitymc equipment minecraft:diamond_sword set 5 5");
        expectRejected(h, source, "divinitymc equipment nonexistent:helmet set 5 5");
        var stacked = new ItemStack(BuiltInRegistries.ITEM.get(TestEquipment.CHEST), 2);
        player.setItemSlot(EquipmentSlot.MAINHAND, stacked);
        expectRejected(h, source, "divinitymc held set 5 5");
        stacked.set(EquipmentArmor.OVERRIDE.get(), new ArmorConfig.Capacity(3, 7));
        expectRejected(h, source, "divinitymc held reset");
        h.assertTrue(stacked.getCount() == 2 && stacked.get(EquipmentArmor.OVERRIDE.get()).magic() == 7, "rejected stack edit/reset preserves data");
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        expectRejected(h, source, "divinitymc held set 5 5");
        h.assertTrue(Files.readString(path).equals(original), "rejected commands leave file unchanged");
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void editRefusesMalformedDiskConfig(GameTestHelper h) throws Exception {
        var server = h.getLevel().getServer(); var path = ArmorConfig.path(); var original = Files.readString(path);
        var active = ArmorConfig.rules();
        try {
            Files.writeString(path, "{invalid-json");
            expectRejected(h, server.createCommandSourceStack().withSuppressedOutput(), "divinitymc equipment minecraft:iron_chestplate set 5 5");
            h.assertTrue(ArmorConfig.rules() == active, "invalid file does not replace live rules");
            h.assertTrue(Files.readString(path).equals("{invalid-json"), "command does not overwrite invalid user file");
        } finally { Files.writeString(path, original); ArmorConfig.reload(server); }
        h.succeed();
    }
}
