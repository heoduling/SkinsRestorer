# SkinsRestorer 15.12.6-1 修复验证报告

日期：2026-10-10（Asia/Tokyo）。结论：已在用户提供的准确核心上验证，修复消除了 SkinsRestorer 菜单身份判断引起的普通木桶/单箱快照，保留受测菜单行为。未操作生产服务器。

## 产物与源码

- 上游：SkinsRestorer 15.12.6，`c8c321f0b075505ee3ad47a8c1b7fdbf1aa6ee2f`。
- 构建提交：`5da43b53839fdecbae82c5c0352579eb6ef8ab38`。
- fork：https://github.com/heoduling/SkinsRestorer/tree/codex/folia-holder-snapshot-15.12.6 。
- 修复版：`SkinsRestorer-15.12.6-1.jar`，8,017,349 字节。
- SHA-256：`43f8c9088c0a6b4b3a3bd6af0644ab7d3055ffda0d78eb0cf4d11c58cff2a953`。
- 原版 SHA-256：`a85b4a370f988741c9a38f4d0498262edacbf3220314790ac1c127461996359c`，与官方发布文件逐字节相同。
- 核心：用户提供的 Shiroha 26.2-DEV-a8f1d54，bundler SHA-256 `837c7b12f6821311024b7c7c3cd203bc0f6ed9e70279e06af2b856598878d80d`。

## 修改范围

实际业务源码只改两个文件：`InventoryListener.java` 的 Holder 查询改为现有 Paper 模块的 `PaperUtil.getInventoryHolder()`；后者直接调用 `inventory.getHolder(false)`。`gradle.properties` 将版本标记为 15.12.6-1。另外保存回归测试源码与说明。

原有 Holder 类型识别、上下背包处理、点击取消、皮肤动作与网络协议保持原实现。没有新增调度、异步容器访问、缓存、线程、配置键或数据库迁移。生命周期资源未增加。这一维护分支以现代 Folia/Shiroha 的 boolean Holder API 为目标，不承诺旧 Spigot 兼容。

## 构建与独立校验

JDK：Eclipse Temurin 25.0.4.1+1；Gradle Wrapper：9.7.1。

最终命令：

```text
gradlew.bat clean check :skinsrestorer:build --no-daemon --console=plain --warning-mode=summary --max-workers=4 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dorg.gradle.internal.http.connectionTimeout=15000 -Dorg.gradle.internal.http.socketTimeout=15000 --no-configuration-cache
```

代理参数只用于本机依赖下载，不属于插件设置。最终退出码 0，BUILD SUCCESSFUL；350 个任务：281 执行、54 来自 Gradle 缓存、15 已是最新。正式产物来自该次 clean 构建。

JUnit 共发现 148 项，146 通过，2 跳过，0 失败、0 错误。跳过项为没有 Docker 环境时上游主动禁用的 MySQL/PostgreSQL Testcontainers 测试。Bukkit 7 项、Scissors 1 项、共享测试 140 项（其中 2 项跳过）。上游 `downgradedTest` 为 NO-SOURCE，不计为执行过的测试。

上游 SpotBugs 有未解决告警和部分分析缺失类型；其原有 `ignoreFailures=true` 允许构建通过。本次没有声称静态分析零告警，也没有为通过检查修改该设置。首次尝试中的网络连接和混合换行问题已解决，最终格式检查通过。

独立 verify-plugin-jar.py 校验通过：名称、版本、主类存在、Folia 声明、ZIP/CRC、重复/危险路径和 SHA-256。成品字节码确认调用 `getHolder(false)`；独立校验本身不等于实机验证，后者如下。

## 同核心运行时对照

使用 loopback 隔离服、全新测试世界、FILE 存储和本地生成皮肤，不使用生产配置或数据库。测试玩家必须存活；使用实体所属调度器执行测试，并校验方块区域所有权。计数 agent 仅在核心方法入口计数，不改变参数、返回值或分支；只把调用栈包含实际 SkinsRestorer 监听器的快照归到该插件。

每版 144 项事件/核心检查：3 项核心正反对照、120 项普通容器检查、1 项已取消事件检查、20 项真实 BukkitGUIHolder 身份/点击取消检查。普通容器覆盖木桶、单箱、双箱，普通物品与生成的潜影盒嵌套自定义数据，上下背包位置及十种点击类型。每次检查同时核对原物品序列化结果未被修改。

| 同一组检查 | 原版快照次数 | 修复版快照次数 |
|---|---:|---:|
| 木桶，40 项点击 | 40 | 0 |
| 单箱，40 项点击 | 40 | 0 |
| 双箱，40 项点击 | 0 | 0 |
| 合计，120 项点击 | 80 | 0 |

双箱在这个核心上原本就不走相同的快照路径，不能把它算成原版问题。

另外，每版 9 项协议 776 真实客户端流程通过：三个容器取物、/skins 选择页、36 个皮肤的第一页、剩余 4 个皮肤的第二页、返回上一页、选择皮肤保存并关窗、关闭后重开。木桶/单箱的真实取物点击也从 1 次快照降到 0 次，物品仍正确转移到光标。皮肤选择前先通过 API 清空记录，选中后确认从空记录变为 `audit00`，避免复用旧结果。

两版最终隔离日志均无测试 FAIL；修复版正常冷启动、完成流程并正常关闭。开发测试中曾因测试玩家已死亡而无法保持菜单，最终测试已增加存活断言和隔离保护，旧诊断记录没有计入最终通过结果。

## 证据与边界

完整本地证据目录：`audit/skinsrestorer-15.12.6-watchdog-20261010/`，包括 `build.log`、`jar-verification.txt`、修复前后 javap、`unit-test-counts.json`、`runtime/baseline-server.log`、`runtime/fixed-server.log` 和两个 `*-result.json`。fork 中保留回归源代码与摘要。

此次证明的是这条快照路径已消除，不是复现并消除了原现场完整的 15.941819091 秒停顿。没有原木桶 NBT，没有生产性能采样，也没有用户实际 Velocity 端 JAR，因此没有宣称完整 VC 链路或百人并发已验收。客户端流程为协议测试，未进行人工画面核验；样本皮肤只用于菜单、记录及调用流程验证，不证明真实远程皮肤服务可用。

本次不包含新的拖拽事件处理；测试没有证明上游所有拖拽情形均有防护，不把已有行为扩大承诺为全面 GUI 防复制审计。修复保持原有事件逻辑。

## 生存服替换与回滚

1. 完整停服，备份现用插件 JAR 和 SkinsRestorer 数据目录。
2. 将旧 SkinsRestorer JAR 移出插件扫描目录，只保留本次修复版；保留原配置和数据，不复制测试服内容。
3. 正常启动，确认版本 15.12.6-1，再检查普通木桶/箱子操作与真实 VC 路径下的皮肤菜单。
4. 回滚时完整停服，换回原版 JAR，再启动。没有配置结构或数据库迁移。

VC 端没有因这次 Bukkit 监听器修改而被更换。本次未部署到生产，勿把回归用 probe/agent 安装到生产。
