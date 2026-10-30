package com.px8042.divinitymc.gametest;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.px8042.divinitymc.api.ArmorKind;
import com.px8042.divinitymc.armor.*;
import com.px8042.divinitymc.client.ArmorTooltips;
import com.px8042.divinitymc.config.ArmorConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;
import java.nio.file.Files;
import java.util.ArrayList;

@GameTestHolder("divinitymc")
@PrefixGameTestTemplate(false)
public final class ItemArmorGameTests {
    private static ItemStack armor() {
        var stack = new ItemStack(Items.IRON_CHESTPLATE);
        stack.set(EquipmentArmor.OVERRIDE.get(), new ArmorConfig.Capacity(4.5, 20));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Special armor"));
        return stack;
    }
    private static void check(GameTestHelper h, ItemStack stack) {
        h.assertTrue(new ArmorConfig.Capacity(4.5, 20).equals(stack.get(EquipmentArmor.OVERRIDE.get())), "individual properties retained");
    }
    @GameTest(template = "test_empty")
    public static void itemDataSurvivesStorageNetworkAndCopy(GameTestHelper h) {
        var stack = armor(); stack.setDamageValue(23);
        var loaded = ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElseThrow();
        check(h, loaded);
        h.assertTrue(loaded.getDamageValue() == 23 && loaded.getHoverName().getString().equals("Special armor"), "vanilla data retained across save/load");
        var buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        try { ItemStack.STREAM_CODEC.encode(buf, loaded); check(h, ItemStack.STREAM_CODEC.decode(buf)); }
        finally { buf.release(); }
        var copy = loaded.copy(); loaded.set(EquipmentArmor.OVERRIDE.get(), new ArmorConfig.Capacity(0, 0));
        check(h, copy);
        h.assertTrue(EquipmentArmor.resolve(loaded, ArmorConfig.rules().equipment()).physical() == 0, "zero overrides type value");
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void componentRejectsInvalidValues(GameTestHelper h) {
        for (double bad : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE}) {
            JsonObject root = new JsonObject(); root.addProperty("physical", bad); root.addProperty("magic", 1);
            h.assertTrue(EquipmentArmor.CODEC.parse(JsonOps.INSTANCE, root).error().isPresent(), "invalid persistent component rejected");
            var buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                buf.writeDouble(bad); buf.writeDouble(1);
                boolean rejected = false;
                try { EquipmentArmor.STREAM_CODEC.decode(buf); } catch (IllegalArgumentException expected) { rejected = true; }
                h.assertTrue(rejected, "invalid network component rejected");
            } finally { buf.release(); }
        }
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void anvilRenameAndMaterialRepairRetainProperties(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL); player.experienceLevel = 100;
        var rename = new AnvilMenu(1, player.getInventory());
        rename.getSlot(0).set(armor()); rename.setItemName("Renamed armor"); rename.createResult();
        var renamed = rename.getSlot(2).getItem().copy(); check(h, renamed);
        h.assertTrue(renamed.getHoverName().getString().equals("Renamed armor"), "real anvil rename succeeds");
        renamed.setDamageValue(100);
        var repair = new AnvilMenu(2, player.getInventory());
        repair.getSlot(0).set(renamed); repair.getSlot(1).set(new ItemStack(Items.IRON_INGOT)); repair.createResult();
        var repaired = repair.getSlot(2).getItem(); check(h, repaired);
        h.assertTrue(repaired.getDamageValue() < 100, "real material repair succeeds");
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void containerDropReloadAndPickupRetainProperties(GameTestHelper h) {
        var container = new SimpleContainer(1); container.setItem(0, armor());
        var drop = new ItemEntity(h.getLevel(), 2, 2, 2, container.removeItemNoUpdate(0));
        h.assertTrue(container.getItem(0).isEmpty(), "item moved out of container");
        var nbt = new CompoundTag(); drop.saveWithoutId(nbt);
        var restored = EntityType.ITEM.create(h.getLevel()); restored.load(nbt); restored.setNoPickUpDelay();
        h.getLevel().addFreshEntity(restored); check(h, restored.getItem());
        var receiver = h.makeMockPlayer(GameType.SURVIVAL);
        restored.playerTouch(receiver);
        h.assertTrue(restored.isRemoved(), "dropped item picked up");
        var received = receiver.getInventory().items.stream().filter(s -> !s.isEmpty()).findFirst().orElseThrow();
        check(h, received);
        var transferred = new SimpleContainer(1); transferred.setItem(0, received);
        receiver.getInventory().removeItemNoUpdate(receiver.getInventory().findSlotMatchingItem(received));
        check(h, transferred.getItem(0));
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void independentCapacitySurvivesTypeReloadAndTooltipsMatch(GameTestHelper h) throws Exception {
        var server = h.getLevel().getServer(); var path = ArmorConfig.path(); var original = Files.readString(path);
        var stack = armor(); var cow = h.spawnWithNoFreeWill(EntityType.COW, 2, 2, 2);
        cow.setItemSlot(EquipmentSlot.CHEST, stack);
        var state = ArmorService.state(cow); state.set(ArmorKind.PHYSICAL, 2);
        try {
            var id = ResourceLocation.parse("minecraft:iron_chestplate");
            ArmorConfig.editEquipment(server, id, new ArmorConfig.Capacity(30, 40));
            h.assertTrue(state.maximum(ArmorKind.PHYSICAL) == 4.5 && state.current(ArmorKind.PHYSICAL) == 2, "type reload cannot replace item override");
            var lines = new ArrayList<Component>(); lines.add(stack.getHoverName());
            ArmorTooltips.update(stack, lines, null);
            h.assertTrue(lines.stream().anyMatch(c -> c.getString().contains("Individual armor properties")), "independent source is visible");
            h.assertTrue(lines.stream().anyMatch(c -> c.getString().contains("+4.5 Physical Armor Capacity")), "component can be displayed before type rules arrive");
            stack.remove(EquipmentArmor.OVERRIDE.get()); ArmorService.sync(cow);
            h.assertTrue(state.maximum(ArmorKind.PHYSICAL) == 30 && state.current(ArmorKind.PHYSICAL) == 2, "removing override uses current type configuration without refill");
            ArmorConfig.editEquipment(server, id, null);
            h.assertTrue(state.maximum(ArmorKind.PHYSICAL) == 12, "removing type override restores built-in value");
        } finally { Files.writeString(path, original); ArmorConfig.reload(server); }
        h.succeed();
    }
}
