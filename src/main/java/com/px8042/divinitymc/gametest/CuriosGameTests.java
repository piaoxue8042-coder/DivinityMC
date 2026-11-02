package com.px8042.divinitymc.gametest;

import com.mojang.authlib.GameProfile;
import com.px8042.divinitymc.api.ArmorKind;
import com.px8042.divinitymc.armor.*;
import com.px8042.divinitymc.compat.CuriosIntegration;
import com.px8042.divinitymc.config.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("divinitymc")
public final class CuriosGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        if (!CuriosIntegration.loaded()) return List.of();
        return List.of(test("slots", CuriosGameTests::slots), test("cosmetics", CuriosGameTests::cosmetics),
            test("commands", CuriosGameTests::commands), test("conversion", CuriosGameTests::conversion),
            test("tooltip", CuriosGameTests::tooltip), test("persistence", CuriosGameTests::persistence));
    }
    private static TestFunction test(String name, Consumer<GameTestHelper> body) {
        return new TestFunction("curios", "divinitymc.curios." + name, "divinitymc:test_empty", 100, 0, true, body);
    }
    private static ServerPlayer wearer(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "CuriosTest"));
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
        var slots = new CurioStacksHandler(inventory, "curio", 2, true, true, true, ICurio.DropRule.DEFAULT);
        inventory.setCurios(new HashMap<>(Map.of("curio", slots)));
        ArmorService.state(player);
        return player;
    }
    private static ItemStack ring() { return new ItemStack(BuiltInRegistries.ITEM.get(CuriosFixtures.RING)); }
    private static void equip(ServerPlayer p, int slot, ItemStack stack) {
        CuriosApi.getCuriosInventory(p).orElseThrow().getCurios().get("curio").getStacks().setStackInSlot(slot, stack);
    }
    private static void expect(GameTestHelper h, ServerPlayer p, double physical, double magic) {
        var s = ArmorService.state(p).snapshot();
        h.assertTrue(s.maxPhysical() == physical && s.maxMagic() == magic, "expected capacity " + physical + "/" + magic + ", got " + s);
    }
    private static void slots(GameTestHelper h) {
        var p = wearer(h); var ring = ring(); p.setItemSlot(EquipmentSlot.MAINHAND, ring);
        expect(h, p, 0, 0);
        p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); equip(p, 0, ring);
        expect(h, p, 6, 3);
        h.assertTrue(ArmorService.state(p).current(ArmorKind.PHYSICAL) == 0, "equipping cannot refill initialized armor");
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        p.getAttribute(Attributes.ARMOR).addTransientModifier(new AttributeModifier(ResourceLocation.parse("divinitymc_test:external"), 30, AttributeModifier.Operation.ADD_VALUE));
        expect(h, p, 18, 9);
        equip(p, 1, ring.copy()); expect(h, p, 24, 12);
        equip(p, 0, ItemStack.EMPTY); equip(p, 1, ItemStack.EMPTY); expect(h, p, 12, 6);
        h.succeed();
    }
    private static void cosmetics(GameTestHelper h) {
        var p = wearer(h); var slots = CuriosApi.getCuriosInventory(p).orElseThrow().getCurios().get("curio");
        var cosmetic = ring(); cosmetic.set(EquipmentArmor.OVERRIDE.get(), new ArmorConfig.Capacity(100, 100));
        slots.getCosmeticStacks().setStackInSlot(0, cosmetic); expect(h, p, 0, 0);
        equip(p, 0, ring()); slots.getRenders().set(0, false); expect(h, p, 6, 3);
        slots.getActiveStates().set(0, false); expect(h, p, 0, 0);
        slots.getActiveStates().set(0, true); expect(h, p, 6, 3);
        h.succeed();
    }
    private static void commands(GameTestHelper h) {
        var p = wearer(h); var stack = ring(); stack.setDamageValue(17);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Same ring"));
        var other = stack.copy(); var source = p.createCommandSourceStack().withPermission(2);
        var path = ArmorConfig.path();
        try {
            var original = Files.readString(path);
            try {
                p.setItemSlot(EquipmentSlot.MAINHAND, stack);
                var dispatcher = h.getLevel().getServer().getCommands().getDispatcher();
                h.assertTrue(dispatcher.execute("divinitymc held set 2.5 20", source) == 1, "held accepts Curios-only item");
                h.assertTrue(Files.readString(path).equals(original) && !other.has(EquipmentArmor.OVERRIDE.get()), "single ring edits do not affect config or copy");
                h.assertTrue(stack.getDamageValue() == 17 && stack.getHoverName().getString().equals("Same ring"), "ring vanilla data preserved");
                p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); equip(p, 0, stack); expect(h, p, 2.5, 20);
                ArmorService.state(p).set(ArmorKind.PHYSICAL, 2);
                h.assertTrue(dispatcher.execute("divinitymc equipment " + CuriosFixtures.RING + " set 7 9", source) == 1, "type config accepts Curios item");
                expect(h, p, 2.5, 20);
                equip(p, 0, ItemStack.EMPTY); p.setItemSlot(EquipmentSlot.MAINHAND, stack);
                dispatcher.execute("divinitymc held reset", source);
                p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); equip(p, 0, stack); expect(h, p, 7, 9);
                h.assertTrue(ArmorService.state(p).current(ArmorKind.PHYSICAL) == 0, "removal/re-equip cannot refill");
                stack.set(EquipmentArmor.OVERRIDE.get(), new ArmorConfig.Capacity(0, 0)); expect(h, p, 0, 0);
                stack.remove(EquipmentArmor.OVERRIDE.get());
                dispatcher.execute("divinitymc equipment " + CuriosFixtures.RING + " reset", source); expect(h, p, 6, 3);
            } finally { Files.writeString(path, original); ArmorConfig.reload(h.getLevel().getServer()); }
        } catch (Exception error) { throw new RuntimeException(error); }
        h.succeed();
    }
    private static void conversion(GameTestHelper h) {
        var p = wearer(h); var stack = ring(); equip(p, 0, stack);
        var root = ArmorConfig.defaultJson(); root.getAsJsonObject("autoEquipment").addProperty("physicalMultiplier", 3);
        root.getAsJsonObject("autoEquipment").addProperty("magicMultiplier", 0.5);
        h.assertTrue(CuriosIntegration.capacity(p, ArmorConfig.parse(root)).equals(new ArmorConfig.Capacity(9, 1.5)), "only flat armor converted, ignoring percent and toughness");
        root.getAsJsonObject("autoEquipment").addProperty("enabled", false);
        h.assertTrue(CuriosIntegration.capacity(p, ArmorConfig.parse(root)).equals(new ArmorConfig.Capacity(0, 0)), "automatic switch disables curio conversion");
        stack.set(EquipmentArmor.OVERRIDE.get(), new ArmorConfig.Capacity(4, 20));
        h.assertTrue(CuriosIntegration.capacity(p, ArmorConfig.parse(root)).equals(new ArmorConfig.Capacity(4, 20)), "explicit item properties still apply");
        equip(p, 0, new ItemStack(BuiltInRegistries.ITEM.get(CuriosFixtures.EMPTY))); expect(h, p, 0, 0);
        stack.remove(EquipmentArmor.OVERRIDE.get());
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putDouble("armor", Double.MAX_VALUE);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); equip(p, 0, stack); expect(h, p, 0, 0);
        h.succeed();
    }
    private static void tooltip(GameTestHelper h) {
        var p = wearer(h); var stack = ring(); equip(p, 0, stack);
        var lines = stack.getTooltipLines(Item.TooltipContext.of(h.getLevel()), p, TooltipFlag.NORMAL);
        h.assertTrue(lines.stream().anyMatch(c -> c.getString().contains("+6 Physical Armor Capacity")), "curio tooltip uses automatic physical conversion");
        h.assertTrue(lines.stream().anyMatch(c -> c.getString().contains("+3 Magic Armor Capacity")), "curio tooltip uses automatic magic conversion");
        stack.set(EquipmentArmor.OVERRIDE.get(), new ArmorConfig.Capacity(0, 8.5));
        lines = stack.getTooltipLines(Item.TooltipContext.of(h.getLevel()), p, TooltipFlag.NORMAL);
        h.assertTrue(lines.stream().anyMatch(c -> c.getString().contains("Individual armor properties")), "curio override marker");
        h.assertTrue(lines.stream().anyMatch(c -> c.getString().contains("+8.5 Magic Armor Capacity")), "curio override capacity");
        h.succeed();
    }
    private static void persistence(GameTestHelper h) {
        var p = wearer(h); var stack = ring(); stack.set(EquipmentArmor.OVERRIDE.get(), new ArmorConfig.Capacity(4, 20));
        equip(p, 0, stack); expect(h, p, 4, 20);
        var slots = CuriosApi.getCuriosInventory(p).orElseThrow().getCurios().get("curio");
        var saved = slots.serializeNBT();
        equip(p, 0, ItemStack.EMPTY); expect(h, p, 0, 0);
        slots.deserializeNBT(saved); expect(h, p, 4, 20);
        h.succeed();
    }
}
