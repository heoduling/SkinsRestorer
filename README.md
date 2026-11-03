<img align="right" src="https://github.com/SkinsRestorer/skinsrestorer.net/blob/main/public/logo.png?raw=true" height="150" width="150">

[![modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg)](https://modrinth.com/plugin/skinsrestorer) [![hangar](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/hangar_vector.svg)](https://hangar.papermc.io/SRTeam/SkinsRestorer)

[![documentation](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/documentation/ghpages_vector.svg)](https://skinsrestorer.net/docs) [![discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg)](https://skinsrestorer.net/discord) [![crowdin](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/translate/crowdin_vector.svg)](https://translate.skinsrestorer.net/)

# SkinsRestorer

### Folia 维护版 15.12.6-2

本分支修复 UUID 缓存把“分钟”当作“秒”处理的问题：配置 `storage.uuidExpiresAfter: 60` 时，缓存按 60 分钟有效。保留 15.12.6-1 的容器 Holder 快照修复；玩家已选皮肤、缓存文件格式与现有配置无需迁移。

已完成旧版失败对照、17 项新增回归测试、完整干净构建，以及隔离 Shiroha 的升级和重启读取验证。详细结果、测试边界和升级方法见 [UUID 缓存修复验证报告](UUID-CACHE-REGRESSION.md)。

**🎨 The most popular skin plugin for Minecraft**

## 📥 How to install? / Installation / Setup

You can [read on our website](https://skinsrestorer.net/docs/installation) how to
install SkinsRestorer. Make sure to follow all steps before reporting issues!

## :telescope: Compatibility

![bukkit](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/bukkit_vector.svg) ![spigot](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/spigot_vector.svg) ![paper](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg) ![purpur](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg)

![velocity](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/velocity_vector.svg) ![bungeecord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/bungeecord_vector.svg) ![waterfall](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/waterfall_vector.svg)

![Modrinth Game Versions](https://img.shields.io/modrinth/game-versions/skinsrestorer?style=for-the-badge)

## 🌈 Community

Feel free to join our Discord community server:

[![Discord Banner](https://discord.com/api/guilds/186794372468178944/widget.png?style=banner2)](https://skinsrestorer.net/discord)

## :building_construction: SkinsRestorer API

You can find instructions on how to use the SkinsRestorer API on [our website](https://skinsrestorer.net/docs/development/api).

## :link: Links

- [Modrinth](https://modrinth.com/plugin/skinsrestorer)
- [SpigotMC](https://www.spigotmc.org/resources/2124)
- [Hangar](https://hangar.papermc.io/SRTeam/SkinsRestorer)
- [Jenkins](https://ci.codemc.io/job/SkinsRestorer/job/SkinsRestorer/)
- [Discord](https://skinsrestorer.net/discord)
- [Website](https://skinsrestorer.net)
- [Documentation](https://skinsrestorer.net/docs)
- [Javadocs](https://jd.skinsrestorer.net)
- [Crowdin](https://translate.skinsrestorer.net)

## :family: Authors

See [Contributors](https://skinsrestorer.net/contributors) for a list of people that have
supported this project by contributing.
