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

## 尚未验证的范围

- 两台独立客户端连接真实专用服务器时的延迟、掉线与重连压力测试。
- 第三方技能、装备和伤害改写模组的兼容性。
- 大量实体的长期性能／网络压力；当前使用变化检测、5 tick 同步节流和周期校正。

日志位于工作目录的 `work/verification.log`、`work/client-verification.log`、`work/client-verification-final.log`；JUnit 报告位于 `build/reports/tests/test/index.html`。开发日志和测试世界不包含在发布 JAR 中。
