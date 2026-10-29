# DivinityMC

Minecraft **1.21.1** · NeoForge **21.1.265** · Java **21** · Mod ID `divinitymc`

将双护甲与控制免疫引入 Minecraft 即时战斗的底层模组。不包含职业、技能树、回合制、新方块或新物品。

## 安装与开发

客户端和服务器都安装 `build/libs/divinitymc-0.1.0.jar`，使用 NeoForge 21.1.265 或兼容的后续 1.21.1 版本。开发基线固定为 21.1.265；未声称已测试后续版本。

本目录是 Gradle 工程根目录。系统默认 Java 25 可以保留，编译工具链指定 Java 21。PowerShell 下可仅对当前终端选择已有 JDK：

```powershell
$env:JAVA_HOME = 'E:\jdk-21.0.9_windows-x64_bin\jdk-21.0.9'
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat runClient
```

`build` 包含纯护甲状态单元测试。`runGameTestServer` 在开发运行目录运行无界面 Minecraft 集成测试，不需要打开现有玩家存档。GameTest 类与场景不进入发布 JAR。

## 规则

- 物理伤害先扣物甲，魔法伤害先扣魔甲，超出部分进入吸收生命／生命结算；生存穿透伤害直接进入吸收生命／生命结算。
- 原版护甲及韧性减伤被移除；盾牌、附魔、抗性、适用的耐久损耗、受伤无敌帧、仇恨和目标自身免疫保留。
- 本模组不将所有攻击标为原版穿甲伤害；原版绕过护甲的伤害也可能按配置被双甲吸收。
- 当前对应护甲大于 0 即阻挡注册控制；纯控制不扣甲。先扣伤害再判断当次控制。回甲不会解除已有控制，已有控制刷新会被新护甲阻挡。
- 默认物甲抵挡近战、附魔和爆炸击退；魔甲抵挡缓慢、失明、黑暗、漂浮。目标自身免疫与击退抗性仍有效。
- 中毒、凋零可施加，其伤害归魔甲；保留原版中毒致死限制和免疫。流水、活塞、碰撞、蛛网、粉雪的移动效果不拦截。
- 默认火焰、岩浆、冰冻、雷击、魔法、凋零、龙息与火球归魔甲；摔落、溺水、干涸、窒息、挤压、饥饿、虚空、边界、鞘翅撞墙和强制死亡归穿透；其他伤害默认物理。
- 未注册的控制不作自动免疫。其他模组自行推动实体、直接改血或自定义控制可能绕过本模组，需要显式适配。

## 容量与恢复

初始配置为原版盔甲的护甲值 ×2 物甲、×1 魔甲。天然护甲只取实体护甲属性的基础值，不重复计算装备或附加属性修饰符。实体类型配置可覆盖天然容量。装备只能在自身对应装备槽提供容量，放在背包或拿在手上不生效。未配置的模组盔甲默认不给容量。

装备升档不补当前甲；降档和破损立即截断。首次初始化和死亡重生按当时容量满甲。重登、区块卸载、跨维度（包括末地返回）保留当前甲与剩余等待时间。

默认 200 个加载中的实体 tick 未参与伤害／有效控制后，每秒恢复上限的 10%。护甲吸收伤害或抵挡控制也会刷新计时；挥空与目标自身免疫不会。暂停、离线、区块不再 ticking 时不推进恢复。保存只按游戏 tick，不按现实时间补回。

数据读取异常会记录原始 NBT 和错误，并将双甲隔离为零且禁用恢复，不会静默补满。管理员确认后使用 `reset` 恢复初始化。

## 配置

装备悬停说明显示该装备提供的物理护甲、魔法护甲**容量**，例如默认铁胸甲为 12 / 6。原版护甲与护甲韧性说明被替换，击退抗性、武器伤害等仍保留。这里表示穿戴后增加的上限，当前剩余护甲看屏幕左上角双甲条。

海龟壳（`minecraft:turtle_helmet`）的默认容量为 **12 物理护甲 / 0 魔法护甲**。金制头盔、胸甲、护腿、靴子分别提供 **2 / 4、5 / 10、3 / 6、1 / 2**，整套合计 **11 物理护甲 / 22 魔法护甲**。已有配置优先于默认值，可修改对应物品条目后执行 `/divinitymc reload` 生效。

提示数值在登录时由服务器同步，成功执行 `/divinitymc reload` 后更新；无效更新保留旧值。未配置的可穿戴装备显示 0 / 0。客户端退出服务器会清除显示缓存，等待新服务器同步，避免串服。新增同步协议后客户端与服务器需要同时更新 JAR。

服务器首次启动创建 `config/divinitymc.json`；开发运行时是 `run/config/divinitymc.json`。客户端不自行决定战斗规则。

- `schemaVersion`：固定为 1。
- `recoveryDelayTicks`：非负整数，默认 200。
- `recoveryFractionPerSecond`：每秒恢复最大容量的比例，默认 0.1。
- `naturalPhysicalMultiplier` / `naturalMagicMultiplier`：天然基础护甲转换倍率，默认 2 / 1。
- `equipment`：`{"id":"minecraft:iron_chestplate","physical":12,"magic":6}` 形式的数组，每件物品只出现一次。
- `naturalArmor`：同样格式，ID 改为实体类型；配置的是该类型的天然容量，之后仍加装备容量。
- `damageTypes`：`{"id":"minecraft:magic","kind":"MAGIC"}`，支持 `PHYSICAL` / `MAGIC` / `BYPASS`。
- `controls`：同样格式，ID 是效果 ID 或 `divinitymc:knockback`；只支持 `PHYSICAL` / `MAGIC`。

完整示例由当前游戏注册表生成，避免丢失原版盔甲条目。数值必须有限且非负，ID 必须存在，重复或冲突分类会拒绝整个更新。修改后用 `/divinitymc reload` 原子重载；失败保留上一份有效规则。首次启动配置无效时记录错误并使用默认值，不覆盖错误文件。

控制扩展注册默认值可被同 ID 的配置覆盖。删除原版控制条目会停用相应免控；伤害条目缺省回落物理。

## 调试命令

需要权限等级 2（单人开启作弊或服务器管理员）。

```text
/divinitymc get @s
/divinitymc set @s 3 0
/divinitymc fill @s
/divinitymc reset @s
/divinitymc classify minecraft:magic
/divinitymc reload
```

`set` 会限制在当前容量内；`fill` 不解除数据隔离；`reset` 是显式的隔离解除和满甲重新初始化。选择器可指定多个生物。破甲与免控在 HUD 短暂提示，不刷聊天。

## 扩展 API

入口：`com.px8042.divinitymc.api.DivinityArmorApi`。在模组初始化时注册唯一命名空间 ID；所有数值修改、攻击和控制调用必须在逻辑服务器主线程执行。

```java
DivinityArmorApi.registerControl(ResourceLocation.parse("yourmod:stun"), ArmorKind.MAGIC);
DivinityArmorApi.registerCapacityProvider(ResourceLocation.parse("yourmod:bonus"),
    entity -> new ArmorConfig.Capacity(0, 10));

var snapshot = DivinityArmorApi.armor(target);
DivinityArmorApi.restore(target, ArmorKind.MAGIC, 5);
// consume 返回未被护甲吸收的量，只改护甲，不自行造成生命伤害。
var overflow = DivinityArmorApi.consume(target, ArmorKind.PHYSICAL, 5);

// 先调用原版 hurt，通过有效性判断后才追加控制；每笔伤害按配置选择一种类型。
DivinityArmorApi.attack(target, source, 5,
    List.of(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60)));

// 任意自定义控制必须提供自身可应用条件，底层不会强制取消目标天然免疫。
DivinityArmorApi.tryControl(target, caster, ResourceLocation.parse("yourmod:stun"),
    entity -> yourCanStun(entity), entity -> yourApplyStun(entity));
```

`armor` 返回不可变快照；`blocked` 只回答双甲是否阻挡，不代表控制本身能否生效。不要同时手动 `consume` 再对同一伤害调用 `hurt`，否则会重复扣甲。扩展容量提供器只读取状态，不应递归调用 `armor` 或改变实体。

## AutoJS 开发调试

Gradle 从 AutoJS 作者的 Maven 仓库及 Latvian Maven 仓库加载 AutoJS 1.0.0 和 Rhino 2101.2.8-build.91。它们只在 `localRuntime` 中使用，不打包进 DivinityMC，也不成为发布依赖；不需要 KubeJS。

重新加载 Gradle 工程后，`runClient` / `runServer` 会自动带上调试工具，无需向 `run/mods` 再复制同名 JAR。开发实例使用端口 **25586**，与启动器实例默认的 25585 区分。

```powershell
.\gradlew.bat runClient
python scripts/autojs/call.py --port 25586 --status
python scripts/autojs/call.py --port 25586 --side client --file scripts/autojs/client-info.js
```

可用 `-Pautojs_enabled=false` 临时关闭调试依赖，或用 `-Pautojs_port=25587` 为另一个开发实例指定端口；版本和默认开关在 `gradle.properties` 中配置。AutoJS 脚本能修改运行中的游戏状态，使用专门的测试世界。

## 验证边界

自动测试覆盖核心数值和原版战斗集成。仍需在目标整合包中验证第三方技能／装备兼容；已在开发客户端验证小窗口 HUD、装备更新、破甲显示与目标显示；两台独立客户端的网络延迟仍需验收。测试夹具不代表所有 Mod 的自定义伤害路径都自动兼容。

