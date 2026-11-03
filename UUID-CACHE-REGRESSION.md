# SkinsRestorer 15.12.6-2：UUID 缓存单位修复

验证日期：2026-11-04，Asia/Tokyo。代码构建提交：`71c53cae466d653f184eddf3eff442fdf87c8abd`。本文件为构建完成后的文档记录。

## 结论与修改范围

`StorageConfig.UUID_EXPIRES_AFTER` 的配置注释定义单位为分钟，默认值为 60；`CacheStorageImpl.isValidUUIDTimestamp()` 原先直接将秒级缓存年龄与该整数比较。因此，当配置为 60、缓存年龄超过 60 秒时，旧代码必然将其判为过期，并使后续 UUID/按名称查询皮肤进入网络查询分支。

修复在这一共享判断处使用 `TimeUnit.MINUTES.toSeconds(expiresAfter)`；版本从 15.12.6-1 升为 15.12.6-2。没有修改缓存写入、数据库适配器、玩家皮肤选择、配置键、公共 API、调度和线程生命周期。原有非正数配置及 `allowExpired` 的代码行为保留。配置值为 0 的原代码行为与其注释存在出入，本次没有扩大范围调整它。

保留 15.12.6-1 的 `InventoryListener -> PaperUtil.getInventoryHolder -> inventory.getHolder(false)` 修复；最终 JAR 字节码已核对。前一修复的历史验证见 `REGRESSION-REPORT.md`，其中旧核心和旧 JAR 的测试结果不冒充本次运行结果。

## 自动回归与构建

环境：Oracle JDK 25.0.3，Gradle Wrapper 9.7.1。完整命令的任务部分为：

```text
gradlew.bat clean check :skinsrestorer:build --no-daemon --console=plain --max-workers=4 --no-configuration-cache
```

本机另指定 D 盘的 Gradle 缓存、临时目录、JDK 和依赖下载代理；这些参数不进入插件配置。退出码 0，`BUILD SUCCESSFUL in 6m 7s`，350 项任务：289 执行、51 来自构建缓存、10 已是最新。

| 验证 | 结果 |
| --- | --- |
| 修复前原有共享测试 | 140 项：138 通过、2 跳过 |
| 新增回归测试用于旧代码 | 17 项：7 通过、10 断言失败、0 错误 |
| 同一组测试用于修复代码 | 17 项全部通过 |
| 完整干净构建 | 165 项：163 通过、2 跳过、0 失败、0 错误 |

完整构建包括 Bukkit 7 项、Scissors 1 项、共享模块 157 项。2 项跳过的是无 Docker 环境下主动禁用的 MySQL/PostgreSQL Testcontainers 测试，不能算作数据库实测通过。Spotless 检查通过；上游 SpotBugs 仍存在告警及原有 `ignoreFailures=true`，不宣称静态分析零告警。

新增测试位于 `test/src/test/java/net/skinsrestorer/storage/UUIDCacheExpiryTest.java`。使用真实临时目录和 FileAdapter，写入并重新打开文件；仅模拟设置、外部 Mojang 边界和受控时钟。覆盖：60 分钟下的 30、90、1800、3590、3600、3601、3610 秒，1 分钟的 59/60/61 秒，最大正整数分钟的长整型换算，0 的既有行为，过期绕过，负缓存，UUID/皮肤读取不联网，真正过期后刷新并持久化结果。原文件中的秒级时间戳保持原值。

## 隔离服前后对照与重启

使用独立目录、独立世界、回环地址 `127.0.0.1` 和端口 25579。未启动、停止或修改生产服务器，也未读取生产数据库。只加载 SkinsRestorer 与测试用 UUIDCacheProbe，后者不属于交付插件。

实际核心来自本地 `shiroha-bundler-26.2-SNAPSHOT.jar`：Manifest 为 Shiroha `26.2-DEV-f10fd16`，API `26.2-SNAPSHOT`，Folia continuation 调度器；该构建的运行日志品牌为 mizuki。它并非前一事故测试使用的 `a8f1d54` 核心。

- Bundler SHA-256：`22db83248a4a51c4f8a28efbab481943e18504ab45c209734b37abcf884c3aa9`。
- 展开后的核心 SHA-256：`72b77075bb7bd63688e8279665410648502612b1e3c8a23159777b9f75d9eb7b`。
- 旧插件 SHA-256：`43f8c9088c0a6b4b3a3bd6af0644ab7d3055ffda0d78eb0cf4d11c58cff2a953`。

配置 `storage.uuidExpiresAfter: 60`、关闭默认皮肤和代理模式，通过 `advanced.noConnections: true` 在 SkinsRestorer HTTP 连接建立前禁用外部请求。因此缓存内查询成功不能由在线服务返回结果掩盖。

旧版先在真实 FileAdapter 中写入年龄为 30/90/1800/3700 秒的名称缓存、新鲜的正版皮肤记录、90 秒的负缓存及一个已选自定义皮肤。正常停服后，保留全部数据，先升级为修复版读取，再正常停服、重新启动修复版读取。修复版不重新播种、不修改时间戳；读取时实际年龄会随验证时间增长，精确边界由上述受控时钟测试验证。

| 同一组 13 项运行时检查 | 通过 | 失败 |
| --- | ---: | ---: |
| 原 15.12.6-1 | 6 | 7 |
| 升级 15.12.6-2，读取旧文件 | 13 | 0 |
| 重启 15.12.6-2，再读同一批文件 | 13 | 0 |

检查覆盖缓存有效/过期、`allowExpired`、UUID 获取、按名称查询皮肤、未选皮肤玩家的正常查找路径、负缓存、已选皮肤标识与属性、秒级时间戳。旧版的 90 秒与 1800 秒缓存提前失效，UUID、皮肤与玩家回退查询进入被禁用的网络路径；修复版从文件返回结果。三轮服务器均通过控制台 `stop` 正常退出，退出码均为 0。

旧版生成的 8 个 `.mojangcache`、`.playerskin`、`.customskin` 和 `.player` 文件，升级及重启后的路径集合和 SHA-256 全部一致。已选皮肤的标识及实际 SkinProperty 均保持一致，未作数据迁移。

本地证据目录为 `audit/skinsrestorer-uuid-cache-20261104/`，包含基线/失败/通过/完整构建日志、JUnit XML、测试探针源码与运行脚本、旧版/升级/重启服务器日志、数据校验清单及成品字节码记录。该目录不打包进插件。

## 产物与升级

成品：`SkinsRestorer-15.12.6-2.jar`，8,017,429 字节。

SHA-256：`ca945cf17b8c5ddfdae5d163ed3b46b967d5f8632947d09f5b3845b102cb09e1`。

来自本次干净构建的 `build/libs/SkinsRestorer.jar`。独立校验器通过插件名称、版本、描述文件、主类路径、Folia 声明、ZIP/CRC、重复及危险路径检查；最终字节码包含 `TimeUnit.MINUTES.toSeconds(long)`。隔离服实际加载版本为 15.12.6-2。

替换前备份现有 JAR 和插件数据；正常停服后只替换 JAR，保留原插件目录、配置、皮肤文件或数据库。生产环境不要复制本报告中的测试配置或测试探针。无需删除缓存或让玩家重新选皮肤；回滚时停服换回原 JAR 即可，本次没有改变数据格式。

实际玩家客户端皮肤显示、真实 Velocity 联服、外部服务限流/延迟以及生产并发未在本次验证。上述证据证明缓存单位错误得到修复，不证明它是此前机器人 4 秒超时或全部生产延迟的唯一根因。MySQL/PostgreSQL 未作运行时集成验证。本分支延续现代 Folia/Shiroha 目标，不新增旧 Spigot 兼容承诺。
