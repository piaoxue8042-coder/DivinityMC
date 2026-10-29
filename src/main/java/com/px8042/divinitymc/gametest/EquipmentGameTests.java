package com.px8042.divinitymc.gametest;

import com.google.gson.*;
import com.px8042.divinitymc.api.ArmorKind;
import com.px8042.divinitymc.armor.ArmorService;
import com.px8042.divinitymc.config.ArmorConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import java.nio.file.*;

@GameTestHolder("divinitymc")
@PrefixGameTestTemplate(false)
public final class EquipmentGameTests {
    private static void capacity(GameTestHelper h, ArmorConfig.Rules rules, String id, double physical, double magic) {
        h.assertTrue(new ArmorConfig.Capacity(physical, magic).equals(rules.equipment().get(ResourceLocation.parse(id))), id + " capacity");
    }
    private static void override(JsonObject root, String id, double physical, double magic) {
        var entries = root.getAsJsonArray("equipmentOverrides");
        entries.asList().removeIf(e -> e.getAsJsonObject().get("id").getAsString().equals(id));
        JsonObject row = new JsonObject(); row.addProperty("id", id);
        row.addProperty("physical", physical); row.addProperty("magic", magic); entries.add(row);
    }
    private static Path temporaryConfig() throws java.io.IOException {
        var parent = Path.of("config-tests"); Files.createDirectories(parent);
        return Files.createTempDirectory(parent, "equipment-").resolve("divinitymc.json");
    }
    private static JsonObject legacy() {
        var root = ArmorConfig.defaultJson(); root.addProperty("schemaVersion", 1);
        root.remove("autoEquipment");
        root.add("equipment", root.remove("equipmentOverrides"));
        return root;
    }

    @GameTest(template = "test_empty")
    public static void standardModArmorWorksInItsSlotAndTooltip(GameTestHelper h) {
        var cow = h.spawnWithNoFreeWill(EntityType.COW, 2, 2, 2); cow.setNoGravity(true);
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(TestEquipment.CHEST));
        cow.setItemSlot(EquipmentSlot.MAINHAND, stack);
        h.assertTrue(ArmorService.state(cow).maximum(ArmorKind.PHYSICAL) == 0, "holding armor does not grant capacity");
        cow.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        cow.setItemSlot(EquipmentSlot.CHEST, stack);
        var state = ArmorService.state(cow);
        h.assertTrue(state.maximum(ArmorKind.PHYSICAL) == 16 && state.maximum(ArmorKind.MAGIC) == 8, "foreign namespace standard armor automatically resolved");
        h.assertTrue(state.current(ArmorKind.PHYSICAL) == 0, "equipping does not refill initialized pool");
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var lines = stack.getTooltipLines(Item.TooltipContext.of(h.getLevel()), player, TooltipFlag.NORMAL);
        h.assertTrue(lines.stream().anyMatch(c -> c.getString().contains("+16 Physical Armor Capacity")), "actual tooltip uses resolved capacity");
        cow.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        h.assertTrue(ArmorService.state(cow).maximum(ArmorKind.MAGIC) == 0, "removal updates capacity");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void defaultsOverridesAndAutomaticToggle(GameTestHelper h) {
        var root = ArmorConfig.defaultJson();
        h.assertTrue(root.getAsJsonArray("equipmentOverrides").isEmpty(), "fresh config does not freeze built-in equipment values");
        var defaults = ArmorConfig.parse(root);
        capacity(h, defaults, "minecraft:turtle_helmet", 12, 0);
        capacity(h, defaults, "minecraft:golden_helmet", 2, 4);
        capacity(h, defaults, "minecraft:golden_chestplate", 5, 10);
        capacity(h, defaults, "minecraft:golden_leggings", 3, 6);
        capacity(h, defaults, "minecraft:golden_boots", 1, 2);
        capacity(h, defaults, "minecraft:golden_horse_armor", 14, 7);
        root.getAsJsonObject("autoEquipment").addProperty("physicalMultiplier", 3);
        root.getAsJsonObject("autoEquipment").addProperty("magicMultiplier", 0.5);
        var scaled = ArmorConfig.parse(root);
        capacity(h, scaled, TestEquipment.CHEST.toString(), 24, 4);
        capacity(h, scaled, "minecraft:iron_chestplate", 12, 6);
        override(root, TestEquipment.CHEST.toString(), 0, 0);
        capacity(h, ArmorConfig.parse(root), TestEquipment.CHEST.toString(), 0, 0);
        root.getAsJsonObject("autoEquipment").addProperty("enabled", false);
        override(root, TestEquipment.CHEST.toString(), 7.25, 20);
        var manual = ArmorConfig.parse(root);
        capacity(h, manual, TestEquipment.CHEST.toString(), 7.25, 20);
        h.assertTrue(manual.equipmentSource(TestEquipment.CHEST).equals("override"), "override wins even with automatic disabled");
        root.getAsJsonArray("equipmentOverrides").asList().clear();
        h.assertFalse(ArmorConfig.parse(root).equipment().containsKey(TestEquipment.CHEST), "automatic off disables unconfigured mod armor");
        root.getAsJsonObject("autoEquipment").addProperty("enabled", true);
        capacity(h, ArmorConfig.parse(root), TestEquipment.CHEST.toString(), 24, 4);
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void legacyMigrationPreservesValuesAndBacksUpOnce(GameTestHelper h) throws java.io.IOException {
        var root = legacy();
        var row = new JsonObject(); row.addProperty("id", "minecraft:iron_chestplate");
        row.addProperty("physical", 3.5); row.addProperty("magic", 19);
        root.getAsJsonArray("equipment").add(row);
        var path = temporaryConfig(); var original = root.toString(); Files.writeString(path, original);
        var active = ArmorConfig.rules();
        var migrated = ArmorConfig.load(h.getLevel().getServer(), path);
        capacity(h, migrated, "minecraft:iron_chestplate", 3.5, 19);
        capacity(h, migrated, "minecraft:turtle_helmet", 0, 0); // Omitted in v1: preserve exclusion.
        capacity(h, migrated, TestEquipment.CHEST.toString(), 16, 8); // Added mod armor needs no legacy entry.
        h.assertTrue(Files.readString(path.resolveSibling("divinitymc.json.v1.bak")).equals(original), "exact legacy file retained");
        String updated = Files.readString(path);
        h.assertTrue(JsonParser.parseString(updated).getAsJsonObject().get("schemaVersion").getAsInt() == 2, "schema migrated on disk");
        ArmorConfig.load(h.getLevel().getServer(), path);
        h.assertTrue(Files.readString(path).equals(updated), "second load leaves user config unchanged");
        try (var files = Files.list(path.getParent())) { h.assertTrue(files.count() == 2, "only one backup created"); }
        h.assertTrue(active == ArmorConfig.rules(), "candidate load never changes active rules");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void invalidLegacyConfigurationDoesNotMigrate(GameTestHelper h) throws java.io.IOException {
        var root = legacy(); root.addProperty("recoveryDelayTicks", -1);
        var path = temporaryConfig(); var original = root.toString(); Files.writeString(path, original);
        boolean failed = false;
        try { ArmorConfig.load(h.getLevel().getServer(), path); } catch (IllegalArgumentException expected) { failed = true; }
        h.assertTrue(failed, "invalid legacy rules rejected");
        h.assertTrue(Files.readString(path).equals(original), "invalid input not overwritten");
        h.assertFalse(Files.exists(path.resolveSibling("divinitymc.json.v1.bak")), "failed validation does not write backup/migration");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void absentModOverridesRemainInactive(GameTestHelper h) throws java.io.IOException {
        var root = ArmorConfig.defaultJson(); override(root, "not_installed:helmet", 4, 18);
        var path = temporaryConfig(); Files.writeString(path, root.toString());
        var rules = ArmorConfig.load(h.getLevel().getServer(), path);
        var missing = ResourceLocation.parse("not_installed:helmet");
        h.assertTrue(rules.equipmentOverrides().containsKey(missing), "absent-mod override retained for reinstall");
        h.assertFalse(rules.equipment().containsKey(missing), "absent item does not enter effective values or network table");
        capacity(h, rules, TestEquipment.CHEST.toString(), 16, 8);
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void realReloadUpdatesCapacityWithoutRefillAndRejectsInvalidRules(GameTestHelper h) throws java.io.IOException {
        var path = ArmorConfig.path(); String saved = Files.readString(path);
        var cow = h.spawnWithNoFreeWill(EntityType.COW, 2, 2, 2);
        cow.setItemSlot(EquipmentSlot.CHEST, new ItemStack(BuiltInRegistries.ITEM.get(TestEquipment.CHEST)));
        var state = ArmorService.state(cow); state.set(ArmorKind.PHYSICAL, 9);
        try {
            var root = JsonParser.parseString(saved).getAsJsonObject();
            override(root, TestEquipment.CHEST.toString(), 30, 10);
            Files.writeString(path, root.toString()); ArmorConfig.reload(h.getLevel().getServer());
            h.assertTrue(state.maximum(ArmorKind.PHYSICAL) == 30 && state.current(ArmorKind.PHYSICAL) == 9, "reload raises limit without refill");
            override(root, TestEquipment.CHEST.toString(), 3, 0);
            Files.writeString(path, root.toString()); ArmorConfig.reload(h.getLevel().getServer());
            h.assertTrue(state.maximum(ArmorKind.PHYSICAL) == 3 && state.current(ArmorKind.PHYSICAL) == 3, "reload clamps immediately");
            var active = ArmorConfig.rules();
            root.getAsJsonObject("autoEquipment").addProperty("magicMultiplier", -1);
            Files.writeString(path, root.toString());
            boolean failed = false;
            try { ArmorConfig.reload(h.getLevel().getServer()); } catch (IllegalArgumentException expected) { failed = true; }
            h.assertTrue(failed && active == ArmorConfig.rules(), "invalid reload retains live rules");
            h.assertTrue(state.maximum(ArmorKind.PHYSICAL) == 3, "invalid reload does not change entity capacity");
        } finally { Files.writeString(path, saved); ArmorConfig.reload(h.getLevel().getServer()); }
        h.succeed();
    }
}
