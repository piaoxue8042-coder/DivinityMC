// Read-only development check. The foreign-namespace fixture is excluded from release jars.
var mc = Java.loadClass('net.minecraft.client.Minecraft').getInstance();
var Registries = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries');
var Id = Java.loadClass('net.minecraft.resources.ResourceLocation');
var Stack = Java.loadClass('net.minecraft.world.item.ItemStack');
var Context = Java.loadClass('net.minecraft.world.item.Item$TooltipContext');
var Flag = Java.loadClass('net.minecraft.world.item.TooltipFlag');
var Rules = Java.loadClass('com.px8042.divinitymc.client.EquipmentTooltipRules');
var id = Id.parse('divinitymc_test:standard_chestplate');
if (mc.player == null || Rules.equipment() == null) throw new Error('Enter a world and wait for server synchronization');
if (!Registries.ITEM.containsKey(id)) throw new Error('This check requires the development-only armor fixture');
var capacity = Rules.equipment().get(id);
if (capacity == null) throw new Error('Automatic equipment entry missing from server packet');
var lines = new Stack(Registries.ITEM.get(id)).getTooltipLines(Context.of(mc.level), mc.player, Flag.NORMAL);
var text = [];
for (var i = 0; i < lines.size(); i++) text.push(String(lines.get(i).getString()));
JSON.stringify({id: String(id), physical: capacity.physical(), magic: capacity.magic(), tooltip: text});
