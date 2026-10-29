// Run on the client after entering a test world. Reads real generated tooltips; changes no items.
var mc = Java.loadClass('net.minecraft.client.Minecraft').getInstance();
var Items = Java.loadClass('net.minecraft.world.item.Items');
var Stack = Java.loadClass('net.minecraft.world.item.ItemStack');
var Context = Java.loadClass('net.minecraft.world.item.Item$TooltipContext');
var Flag = Java.loadClass('net.minecraft.world.item.TooltipFlag');
var Rules = Java.loadClass('com.px8042.divinitymc.client.EquipmentTooltipRules');
if (mc.player == null || Rules.equipment() == null) throw new Error('Enter a world and wait for server rules first');
function tooltip(item) {
    var components = new Stack(item).getTooltipLines(Context.of(mc.level), mc.player, Flag.NORMAL);
    var result = [];
    for (var i = 0; i < components.size(); i++) result.push(String(components.get(i).getString()));
    return result;
}
JSON.stringify({
    serverEquipmentEntries: Rules.equipment().size(),
    ironChestplate: tooltip(Items.IRON_CHESTPLATE),
    netheriteChestplate: tooltip(Items.NETHERITE_CHESTPLATE),
    diamondSword: tooltip(Items.DIAMOND_SWORD),
    turtleHelmet: tooltip(Items.TURTLE_HELMET),
    goldenChestplate: tooltip(Items.GOLDEN_CHESTPLATE)
});
