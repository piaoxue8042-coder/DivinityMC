# DivinityMC 0.1.0 验证记录

验证日期：2026-10-29（Asia/Seoul）。

环境：Minecraft 1.21.1、NeoForge 21.1.265、Java 21.0.9、Gradle 9.2.1。系统默认 Java 25 未修改。

## 已通过

- `gradlew build`：编译、资源打包、12 项 JUnit 单元测试通过。
- `gradlew runGameTestServer`：15 项真实 Minecraft 游戏集成测试全部通过。
- `gradlew runClient`：客户端成功加载并进入单人世界，未访问已有的“新的世界”存档。
- 独立世界 `DivinityMC HUD Test`：铁胸甲提供 12 物甲 / 6 魔甲；穿上后逐渐恢复，不直接补满。
- 14 点物理测试伤害：物甲耗尽、魔甲不变、2 点溢出进入生命，显示破甲提示。
- 570×350 小窗口：玩家双护甲位于左上角，目标双护甲居中且错开，不遮挡原版生命条。
- 静止尸壳：准星正确显示 4 物甲 / 2 魔甲，玩家与目标两套数值独立显示。
- `/divinitymc reload` 在实际客户端成功执行。
- 发布 JAR 不包含 GameTest 类、场景或模板示例方块／物品。

## 自动测试覆盖

纯状态测试：独立伤害池、精确破甲、少量护甲仍免控、穿透、换装防刷、容量下降截断、脱战计时与恢复、再次交战中断恢复、恢复上限、异常数据隔离、非法数值与冷却保持。

游戏集成测试：天然护甲与装备不重复计入；有效受击的仇恨和无敌帧；溢出与当次击退；魔法破甲当次缓慢；回甲不清除既有控制；吸收生命顺序；抗性；目标天然免疫；实体 NBT 往返；非法配置候选；冲击附魔箭及药水箭；爆炸击退；玩家耐久；盾牌完整格挡；死亡与末地返回的复制规则；网络负载编码往返。

## AutoJS Maven 接入验证（2026-10-29）

- 从官方 Maven 成功解析 AutoJS 1.0.0 与 Rhino 2101.2.8-build.91；开发测试服务器成功加载两者，AutoJS 监听 `127.0.0.1:25586`。
- `build runGameTestServer generatePomFileForMavenJavaPublication` 成功，15 项游戏集成测试在加载 AutoJS 后全部通过；本次 JUnit 任务复用此前 12 项通过的结果（UP-TO-DATE）。
- 发布 POM 未声明 AutoJS / Rhino 依赖；发布 JAR 不含两者的类或嵌套依赖。日志：`work/autojs-maven-verification.log`。

## 装备双护甲说明修复（2026-10-29）

- `build runGameTestServer`：12 项单元测试、18 项游戏集成测试通过。新增覆盖装备说明替换、保留共用修饰符 ID 的击退抗性、保留武器属性、自定义及零容量、隐藏属性、装备配置包编解码与客户端缓存清理。
- 开发客户端进入独立世界 `DivinityMC Tooltip Test`，实际收到 30 条服务器装备配置；中文铁胸甲提示为 12 物甲 / 6 魔甲，下界合金胸甲为 16 / 8 并保留 10% 击退抗性，钻石剑仍显示攻击伤害及攻击速度。
- 日志：`work/tooltip-verification.log`；客户端读取结果：`work/tooltip-live-result.json`。复查命令：`python scripts/autojs/call.py --port 25586 --side client --file scripts/autojs/tooltip-check.js`。

## 标准模组防具与配置迁移（2026-10-29）

- `build runGameTestServer`：12 项单元测试、24 项游戏集成测试通过。
- 开发环境注册独立命名空间防具 `divinitymc_test:standard_chestplate`，覆盖真实物品注册、正确穿戴栏、自动换算、装备说明、卸装与防刷甲；该测试物品及所有测试类不进入发布 JAR。
- 新增配置测试覆盖自动开关及倍率、类型配置与 0 / 0、金套和海龟壳内置值、旧配置精确备份与重复加载、被省略的原版装备、无效旧配置不迁移、未安装模组条目保留，以及真实重载后的容量截断和无效更新保留当前规则。
- AutoJS 实测客户端收到自动容量 16 / 8；热重载类型配置后变为 7.25 / 20；无效更新保留 7.25 / 20；恢复原配置后回到 16 / 8。中文说明与服务器结果一致。
- 日志：`work/automatic-equipment-verification.log`；客户端结果：`work/equipment-live-results.json`。开发配置已自动迁移为版本 2，原文件保存在 `run/config/divinitymc.json.v1.bak`。
- 本阶段用独立注册的标准防具验证通用路径，没有据此宣称已测试某个具体第三方模组的套装技能、动态词条、饰品或护盾机制。

## 游戏内装备容量编辑命令（2026-10-29）

此节记录早期按类型编辑的行为；`held` 的当前行为以以下“物品独立护甲属性”记录为准。

- `build runGameTestServer`：12 项单元测试、28 项游戏集成测试通过。
- 使用实际命令调度器和服务端假玩家验证 `held` 查看、设置、恢复默认，以及控制台按物品 ID 编辑。修改保存后重新读取仍保持一致，同类装备的多个实体立即更新容量，上限增加不补甲、归零立即截断。
- 验证零值、小数、原版 ID 简写、海龟壳及金套默认值；空手、武器、未知物品、负数、非数值、超范围数值和权限不足均不写入配置。
- 配置文件本身损坏时命令拒绝编辑，保留磁盘原文及当前有效规则；错误日志是此测试的预期输出。测试结束后恢复原配置。
- 日志：`work/equipment-command-verification.log`。本轮验证了真实服务端命令路径；客户端同步沿用此前已实测的装备规则同步流程。

## 物品独立护甲属性（2026-10-29）

- `build runGameTestServer` 成功：12 项单元测试（本轮复用已通过结果）、33 项游戏集成测试全部通过。日志：`work/item-armor-verification.log`。
- 命令测试通过实际调度器验证两件同类型同名铁胸甲互不影响；名称、附魔和耐久保留，配置文件字节不变。覆盖零值、小数、空手、非装备、堆叠数量限制、非法数值及权限不足。
- 新增组件测试验证 `divinitymc:armor_capacity_override` 的持久化和原版物品网络编解码，非法组件值被拒绝；独立属性优先于类型配置，重载不覆盖独立属性，移除后回退到最新类型配置／内置值。
- 实际铁砧菜单验证改名和材料修复；物品实体 NBT 保存读取、丢出拾取、容器转移和复制均保留独立属性，修改副本不会影响原件。合成与升级未增加通用继承规则。
- 开发客户端在 `DivinityMC Tooltip Test` 中实测：两件同名铁胸甲，一件使用 `held set 4.5 20` 后显示“独立护甲属性”及 4.5 / 20，另一件保持 12 / 6；耐久不变，配置文件未写入。
- 客户端实际收到穿戴状态 2 / 4.5 物甲、3 / 20 魔甲；改为 0 / 0 后立即截断；`held reset` 后上限恢复 12 / 6，当前值仍为 0 / 0。中文客户端说明及英文测试说明均通过。结果：`work/item-armor-live-results.json`。测试后恢复背包和护甲状态。
- 开发配置原先铁胸甲 42 / 50 类型覆盖已备份至 `work/backups/divinitymc-before-item-properties-20261029.json` 并移除；普通铁胸甲恢复 12 / 6，不自动向任何改名物品写入旧值。
- 通讯协议升级为 3，服务器和客户端须同时更新。
- 客户端实测后再次执行 `build` 成功；发布 JAR 包含组件实现，不包含测试类、场景或 AutoJS / Rhino。已备份并更新 `E:\SJMCL\.minecraft\versions\DivinityMC_TEST\mods\divinitymc-0.1.0.jar`，安装文件与构建文件 SHA-256 一致：`E7EFF7747FDB643C7B6D55F44261EA1602B5745CDEF9D1B13639DBD05D7F64BF`。旧 JAR 保存在 `work/backups/divinitymc-before-item-properties-20261029.jar`。

## 同机双客户端局域网测试（2026-10-29）

- 房主 `Piao_Xue8042` 与加入者 `px8042` 使用启动器实例联机。通过 AutoJS 操作房主及逻辑服务器，加入者由用户操作并确认客户端显示；本轮不是专用服务器测试。
- 两件同名铁胸甲通过掉落拾取交给加入者，一件独立属性 4 / 20，另一件默认 12 / 6。类型配置未改变，用户确认双方物品说明、独立属性标记及换装后的容量显示正常。
- 三次实际玩家空手攻击：物甲 12 → 11 时生命不变且无位移；物甲 1 → 0 当次即发生击退，生命不变；无甲时生命 20 → 19。魔甲均保持 6。房主客户端收到的目标护甲与服务器一致，用户确认击退表现。测试后恢复生命与房主原模式。
- 魔甲阻挡缓慢且纯控制不扣甲；魔法伤害耗尽魔甲后，同次附带缓慢可施加，物甲及生命不变。恢复少量魔甲不移除已有缓慢，并阻挡延长持续时间。用户确认客户端出现短暂缓慢。
- 第一轮重连因用户换装和复制物品而不作为持久化结论。第二轮保持装备不动，确认登录创建了新的玩家实体，物甲 3 / 4、魔甲 2 / 20 与退出前完全一致；四件测试胸甲的槽位、数值、独立属性、数量与耐久逐项一致。
- 重连测试临时将该玩家的脱战等待设为 6000 tick，以避免自然回甲干扰；核对后已恢复为正常 200 tick，未修改配置。没有以本轮结果声称完整验证离线等待不推进或服务端重启。
- 结果保存在 `work/multiplayer-combat-live-results.json`、`work/multiplayer-magic-control-results.json`、`work/multiplayer-reconnect-before-2.json` 和 `work/multiplayer-reconnect-after-2.json`。
- 随后房主完整关闭并重新打开世界，两名玩家重新进入。四件测试胸甲的槽位、容量、独立属性、数量与耐久仍一致，护甲数据已初始化且未隔离。关闭时的玩家 `.dat` 与 `.dat_old` 均记录物甲 4 / 4、魔甲 20 / 20，与加载后相符；本轮关闭前已回满，因此不作为非满甲重开防补满的验证。记录：`work/multiplayer-world-reopen-results.json`、`work/multiplayer-world-saved-armor-results.json`。
- 补测非满甲世界重开：重新设置物甲 3 / 4、魔甲 2 / 20，临时等待 6000 tick；房主保存退出并重新打开世界、加入者进入后，数值仍为 3 / 4 和 2 / 20，四件测试胸甲逐项与基线一致，没有加载补满或丢失独立属性。核对时剩余等待 2860 tick，随后恢复正常 200 tick。此项验证世界重开持久化，不等同于专用服务端进程重启测试。记录：`work/multiplayer-world-reopen-before-partial.json`、`work/multiplayer-world-reopen-after-partial.json`。

- 多人类型配置热更新：先备份实际服务器配置，使用正式 `equipment minecraft:iron_chestplate set 18 9` 命令。服务器与房主客户端普通铁胸甲变为 18 / 9，加入者确认其客户端也更新；已穿戴的独立属性版始终为 4 / 20，当前护甲不变，所有测试物品数量和耐久保留。随后恢复测试前配置（字节完全一致）并重载，服务器和房主客户端普通胸甲回到 12 / 6，独立属性版仍为 4 / 20。记录和备份路径见 `work/multiplayer-type-sync-session.json`。

## 开发专用服务器准备（2026-10-30）

- 将测试胸甲注册限制为 JVM 属性 `divinitymc.testFixtures=true`，仅 `runGameTestServer` 设置此属性；普通 `runClient` / `runServer` 不再注册该物品，避免正式 JAR 客户端缺少测试注册项。
- 修改后 `build runGameTestServer` 成功，33 项游戏集成测试通过，12 项单元测试结果无失败。日志：`work/server-isolation-verification.log`。
- `runServer` 使用独立的 `run-server/` 工作目录与 AutoJS 25587 端口。首次实际启动达到 `Done`，AutoJS 读取确认 `dedicated=true`、测试胸甲未注册、游戏端口 25565，且服务端可正常保存退出。
- 实测该开发环境直接生成配置和世界，以离线模式启动，没有生成 `eula.txt`；已修正此前按发行服务器流程编写的说明。此测试实例设置 `server-ip=127.0.0.1`，只用于本机联机。
- 本阶段验证开发专用服务器启动和注册表隔离；尚未据此宣称两个客户端已加入该服务器，也没有验证独立发行服务端安装。初次启动日志：`work/dedicated-server-first-start.log`，运行时结果：`work/dedicated-server-inspect-result.json`。

## 尚未验证的范围

- 两台独立客户端连接真实专用服务器时的延迟、掉线与重连压力测试。
- 第三方技能、装备和伤害改写模组的兼容性。
- 大量实体的长期性能／网络压力；当前使用变化检测、5 tick 同步节流和周期校正。

日志位于工作目录的 `work/verification.log`、`work/client-verification.log`、`work/client-verification-final.log`；JUnit 报告位于 `build/reports/tests/test/index.html`。开发日志和测试世界不包含在发布 JAR 中。
