package com.px8042.divinitymc.gametest;

import com.px8042.divinitymc.api.*;
import com.px8042.divinitymc.armor.*;
import com.px8042.divinitymc.config.ArmorConfig;
import com.px8042.divinitymc.network.ArmorPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder("divinitymc")
@PrefixGameTestTemplate(false)
public final class ArmorGameTests {
    private static Husk armored(GameTestHelper h) {
        Husk mob = h.spawnWithNoFreeWill(EntityType.HUSK, 2, 2, 2);
        mob.setNoGravity(true); mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        var state = ArmorService.state(mob);
        state.set(ArmorKind.PHYSICAL, state.maximum(ArmorKind.PHYSICAL));
        state.set(ArmorKind.MAGIC, state.maximum(ArmorKind.MAGIC));
        return mob;
    }
    private static void eq(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < 0.0001, message + ": expected " + expected + ", got " + actual);
    }
    @GameTest(template = "test_empty")
    public static void equipmentAndNaturalArmorAreNotDoubleCounted(GameTestHelper h) {
        var mob = armored(h); var s = DivinityArmorApi.armor(mob);
        // Husk's base armor is 2; chestplate supplies 6. Toughness/vanilla defense must not apply twice.
        eq(h, s.maxPhysical(), 16, "physical capacity"); eq(h, s.maxMagic(), 8, "magic capacity");
        mob.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        var stripped = DivinityArmorApi.armor(mob);
        eq(h, stripped.physical(), 4, "removal clamps");
        mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        eq(h, DivinityArmorApi.armor(mob).physical(), 4, "re-equipping must not refill");
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void absorbedHitKeepsAggroIFramesAndDurability(GameTestHelper h) {
        var mob = armored(h);
        var attacker = h.spawnWithNoFreeWill(EntityType.HUSK, 4, 2, 2);
        float health = mob.getHealth();
        h.assertTrue(mob.hurt(mob.damageSources().mobAttack(attacker), 5), "armor-only hit must be accepted");
        eq(h, mob.getHealth(), health, "health unchanged");
        eq(h, DivinityArmorApi.armor(mob).physical(), 11, "armor takes unmitigated damage");
        h.assertTrue(mob.getLastHurtByMob() == attacker, "aggro retained");
        h.assertTrue(mob.invulnerableTime > 0 && mob.hurtTime > 0, "hurt timers retained");
        h.assertFalse(mob.hurt(mob.damageSources().mobAttack(attacker), 5), "same hit blocked by iframe");
        eq(h, DivinityArmorApi.armor(mob).physical(), 11, "no double debit");
        eq(h, mob.getDeltaMovement().lengthSqr(), 0, "physical armor blocks knockback");
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void breakHitOverflowAndKnockback(GameTestHelper h) {
        var mob = armored(h); var attacker = h.spawnWithNoFreeWill(EntityType.HUSK, 4, 2, 2);
        DivinityArmorApi.set(mob, ArmorKind.PHYSICAL, 3);
        mob.hurt(mob.damageSources().mobAttack(attacker), 5);
        eq(h, mob.getHealth(), mob.getMaxHealth() - 2, "overflow");
        eq(h, DivinityArmorApi.armor(mob).physical(), 0, "broken");
        h.assertTrue(mob.getDeltaMovement().lengthSqr() > 0, "same hit knockback allowed");
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void magicControlRespectsPostDamageArmor(GameTestHelper h) {
        var mob = armored(h);
        h.assertFalse(mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100)), "magic blocks slow");
        eq(h, DivinityArmorApi.armor(mob).magic(), 8, "control does not consume armor");
        DivinityArmorApi.set(mob, ArmorKind.MAGIC, 3);
        h.assertTrue(DivinityArmorApi.attack(mob, mob.damageSources().magic(), 3,
            List.of(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100))), "accepted magic hit");
        h.assertTrue(mob.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "exact-break slow applied");
        DivinityArmorApi.restore(mob, ArmorKind.MAGIC, 8);
        h.assertTrue(mob.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "recovery does not cleanse");
        h.assertFalse(mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 500)), "refresh blocked");
        eq(h, mob.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration(), 100, "old duration preserved");
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void survivalDamageBypassesAndAbsorptionFollowsArmor(GameTestHelper h) {
        var mob = armored(h);
        mob.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(4);
        mob.setAbsorptionAmount(4);
        DivinityArmorApi.set(mob, ArmorKind.PHYSICAL, 3);
        mob.hurt(mob.damageSources().generic(), 5);
        eq(h, mob.getAbsorptionAmount(), 2, "yellow hearts absorb only overflow");
        eq(h, mob.getHealth(), 20, "no health lost");
        mob.invulnerableTime = 0;
        mob.hurt(mob.damageSources().fall(), 4);
        eq(h, DivinityArmorApi.armor(mob).magic(), 8, "fall does not consume magic");
        eq(h, mob.getHealth(), 18, "fall uses absorption then health");
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void resistanceStillPrecedesArmor(GameTestHelper h) {
        var mob = armored(h); mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
        mob.hurt(mob.damageSources().generic(), 5);
        eq(h, DivinityArmorApi.armor(mob).physical(), 12, "20 percent resistance before armor");
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void naturalImmunityDoesNotStartCombat(GameTestHelper h) {
        var mob = armored(h);
        mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        mob.knockback(1, 1, 0);
        eq(h, DivinityArmorApi.armor(mob).recoveryDelay(), 0, "natural immunity does not refresh combat");
        h.assertFalse(mob.addEffect(new MobEffectInstance(MobEffects.POISON, 100)), "undead immunity preserved");
        eq(h, DivinityArmorApi.armor(mob).recoveryDelay(), 0, "poison immunity does not refresh combat");
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void nbtRoundTripPreservesDepletedArmor(GameTestHelper h) {
        var mob = armored(h); mob.hurt(mob.damageSources().generic(), 5);
        var before = DivinityArmorApi.armor(mob);
        CompoundTag saved = new CompoundTag(); mob.saveWithoutId(saved);
        var copy = EntityType.HUSK.create(h.getLevel()); copy.load(saved);
        h.assertTrue(before.equals(DivinityArmorApi.armor(copy)), "entity save/load must retain current and delay");
        CompoundTag malformed = ArmorAttachments.encode(ArmorService.state(mob)); malformed.remove("physical");
        boolean rejected = false;
        try { ArmorAttachments.decode(malformed); } catch (IllegalArgumentException expected) { rejected = true; }
        h.assertTrue(rejected, "malformed data not silently accepted");
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void configurationRejectsDuplicateAndNegativeValues(GameTestHelper h) {
        var original = ArmorConfig.rules(); var json = ArmorConfig.defaultJson();
        json.getAsJsonArray("damageTypes").add(json.getAsJsonArray("damageTypes").get(0).deepCopy());
        boolean duplicate = false;
        try { ArmorConfig.parse(json); } catch (IllegalArgumentException expected) { duplicate = true; }
        h.assertTrue(duplicate, "duplicate damage classification rejected");
        json = ArmorConfig.defaultJson(); json.addProperty("recoveryDelayTicks", -1);
        boolean negative = false;
        try { ArmorConfig.parse(json); } catch (IllegalArgumentException expected) { negative = true; }
        h.assertTrue(negative, "negative values rejected");
        h.assertTrue(original == ArmorConfig.rules(), "invalid candidates do not replace active rules");
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void arrowPunchAndTippedEffectUseSeparatePools(GameTestHelper h) {
        var mob = armored(h);
        var owner = h.spawnWithNoFreeWill(EntityType.HUSK, 4, 2, 2);
        ItemStack bow = new ItemStack(Items.BOW);
        bow.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PUNCH), 2);
        class TestArrow extends Arrow {
            TestArrow() { super(h.getLevel(), owner, new ItemStack(Items.TIPPED_ARROW), bow); }
            void hit(LivingEntity target) { onHitEntity(new EntityHitResult(target)); }
        }
        TestArrow arrow = new TestArrow();
        arrow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200));
        arrow.setBaseDamage(2); arrow.setDeltaMovement(1, 0, 0);
        arrow.hit(mob);
        eq(h, DivinityArmorApi.armor(mob).physical(), 14, "arrow debits once");
        eq(h, DivinityArmorApi.armor(mob).magic(), 8, "slow does not debit magic");
        eq(h, mob.getDeltaMovement().lengthSqr(), 0, "punch impulse blocked");
        h.assertFalse(mob.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "tipped slow blocked");
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void explosionImpulseUsesArmorAfterDamage(GameTestHelper h) {
        var mob = armored(h);
        var pos = mob.position().add(0.5, 0, 0);
        // A tiny blast deals at least one damage without exhausting a full pool.
        var explosion = new Explosion(h.getLevel(), null, pos.x, pos.y, pos.z, 0.5f, false, Explosion.BlockInteraction.KEEP);
        explosion.explode();
        h.assertTrue(DivinityArmorApi.armor(mob).physical() < 16, "explosion dealt armor damage");
        eq(h, mob.getDeltaMovement().lengthSqr(), 0, "explosion impulse blocked");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void playerDurabilityAndArmorOnlyDamage(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        var state = ArmorService.state(player);
        state.set(ArmorKind.PHYSICAL, state.maximum(ArmorKind.PHYSICAL));
        var attacker = h.makeMockPlayer(GameType.SURVIVAL);
        h.assertTrue(player.hurt(player.damageSources().playerAttack(attacker), 5), "player accepts armor-only hit");
        eq(h, player.getHealth(), 20, "player health unchanged");
        eq(h, DivinityArmorApi.armor(player).physical(), 7, "player uses same pool rules");
        h.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() > 0, "player armor durability retained");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void shieldDoesNotDebitArmorOrApplyAttachedControl(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(h.absoluteVec(new Vec3(2, 2, 2))); player.setYRot(0);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHIELD));
        player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        for (int i = 0; i < 6; i++) player.tick();
        var state = ArmorService.state(player);
        state.set(ArmorKind.PHYSICAL, state.maximum(ArmorKind.PHYSICAL)); state.set(ArmorKind.MAGIC, 0);
        var attacker = h.spawnWithNoFreeWill(EntityType.HUSK, 2, 2, 4);
        h.assertTrue(player.isBlocking(), "shield active");
        h.assertTrue(player.isDamageSourceBlocked(player.damageSources().mobAttack(attacker)), "attacker is in front of shield");
        h.assertFalse(DivinityArmorApi.attack(player, player.damageSources().mobAttack(attacker), 5,
            List.of(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100))), "fully blocked hit rejected");
        eq(h, DivinityArmorApi.armor(player).physical(), 12, "shield preserves armor");
        h.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "blocked attack does not append slow");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void cloneDistinguishesDeathFromEndReturn(GameTestHelper h) {
        var original = h.makeMockPlayer(GameType.SURVIVAL);
        original.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        ArmorService.state(original).set(ArmorKind.PHYSICAL, 3); ArmorService.state(original).combat(137);
        var returned = h.makeMockPlayer(GameType.SURVIVAL);
        returned.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        ArmorEvents.clone(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(returned, original, false));
        eq(h, DivinityArmorApi.armor(returned).physical(), 3, "End return preserves armor");
        eq(h, DivinityArmorApi.armor(returned).recoveryDelay(), 137, "End return preserves cooldown");
        var respawned = h.makeMockPlayer(GameType.SURVIVAL);
        respawned.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        ArmorEvents.clone(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(respawned, original, true));
        eq(h, DivinityArmorApi.armor(respawned).physical(), 12, "death initializes against new equipment");
        eq(h, DivinityArmorApi.armor(respawned).recoveryDelay(), 0, "death does not copy cooldown");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void networkPayloadRoundTrip(GameTestHelper h) {
        var mob = armored(h); mob.hurt(mob.damageSources().generic(), 5);
        var packet = ArmorPayload.of(mob, ArmorService.state(mob));
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ArmorPayload.CODEC.encode(buf, packet);
            h.assertTrue(packet.equals(ArmorPayload.CODEC.decode(buf)), "entity identity, dimension and armor survive encoding");
        } finally { buf.release(); }
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void equipmentTooltipsReplaceArmorButKeepOtherAttributes(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var context = Item.TooltipContext.of(h.getLevel());
        var iron = new ItemStack(Items.IRON_CHESTPLATE).getTooltipLines(context, player, TooltipFlag.NORMAL);
        h.assertTrue(iron.stream().anyMatch(c -> c.getString().contains("+12 Physical Armor Capacity")), "iron physical capacity shown");
        h.assertTrue(iron.stream().anyMatch(c -> c.getString().contains("+6 Magic Armor Capacity")), "iron magic capacity shown");
        var netherite = new ItemStack(Items.NETHERITE_CHESTPLATE).getTooltipLines(context, player, TooltipFlag.ADVANCED);
        h.assertFalse(netherite.toString().contains("attribute.name.generic.armor"), "armor and toughness descriptions removed");
        h.assertTrue(netherite.toString().contains("attribute.name.generic.knockback_resistance"), "shared modifier ID must not hide knockback resistance");
        var sword = new ItemStack(Items.DIAMOND_SWORD).getTooltipLines(context, player, TooltipFlag.NORMAL);
        h.assertTrue(sword.toString().contains("attribute.name.generic.attack_damage"), "weapon attributes retained");
        h.assertFalse(sword.toString().contains("tooltip.divinitymc"), "weapons do not gain equipment capacity descriptions");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void equipmentTooltipsUseSyncedValuesAndRespectHiddenAttributes(GameTestHelper h) {
        var stack = new ItemStack(Items.IRON_CHESTPLATE);
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        var rules = java.util.Map.of(id, new ArmorConfig.Capacity(7.25, 0));
        var lines = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        lines.add(stack.getHoverName());
        com.px8042.divinitymc.client.ArmorTooltips.update(stack, lines, rules);
        h.assertTrue(lines.stream().anyMatch(c -> c.getString().contains("+7.25 Physical Armor Capacity")), "use configured capacity, not vanilla conversion");
        h.assertTrue(lines.stream().anyMatch(c -> c.getString().contains("+0 Magic Armor Capacity")), "zero capacity visible");
        lines.clear(); lines.add(stack.getHoverName());
        com.px8042.divinitymc.client.ArmorTooltips.update(stack, lines, java.util.Map.of());
        h.assertTrue(lines.stream().anyMatch(c -> c.getString().contains("+0 Physical Armor Capacity")), "unconfigured equipment has zero capacity");
        stack.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
            stack.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS).withTooltip(false));
        var hidden = stack.getTooltipLines(Item.TooltipContext.of(h.getLevel()), h.makeMockPlayer(GameType.SURVIVAL), TooltipFlag.NORMAL);
        h.assertFalse(hidden.toString().contains("tooltip.divinitymc"), "hidden attributes respected");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void equipmentRulesPacketAndDisconnectReset(GameTestHelper h) {
        var packet = new com.px8042.divinitymc.network.EquipmentRulesPayload(java.util.Map.of(
            ResourceLocation.parse("minecraft:iron_chestplate"), new ArmorConfig.Capacity(7.25, 0)));
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            com.px8042.divinitymc.network.EquipmentRulesPayload.CODEC.encode(buf, packet);
            var decoded = com.px8042.divinitymc.network.EquipmentRulesPayload.CODEC.decode(buf);
            h.assertTrue(packet.equals(decoded), "configured equipment values survive network encoding");
            com.px8042.divinitymc.client.EquipmentTooltipRules.accept(decoded.equipment());
            h.assertTrue(decoded.equipment().equals(com.px8042.divinitymc.client.EquipmentTooltipRules.equipment()), "client retains received values");
        } finally {
            buf.release();
            com.px8042.divinitymc.client.EquipmentTooltipRules.clear();
        }
        h.assertTrue(com.px8042.divinitymc.client.EquipmentTooltipRules.equipment() == null, "disconnect clears server rules instead of leaking into next server");
        h.succeed();
    }
}
