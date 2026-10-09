# Folia container holder fix (15.12.6-1)

Based on the official `15.12.6` tag, commit
`c8c321f0b075505ee3ad47a8c1b7fdbf1aa6ee2f`.

The Bukkit inventory click listener previously called `Inventory.getHolder()`
before checking whether an inventory was a SkinsRestorer menu. On the supplied
Shiroha 26.2 core, a normal barrel or single chest therefore saved and decoded a
block entity snapshot for each uncancelled click, including clicks in the lower
player inventory. The production watchdog sample contains this exact path.

The listener now uses the existing Paper module to call `getHolder(false)`.
The holder type check, click dispatch and cancellation logic are unchanged.
There are no new tasks, caches, configuration keys or database migrations.

This maintenance branch targets modern Folia/Shiroha with the boolean holder API.
It does not promise compatibility with old Spigot versions. The existing proxy
implementation is unchanged, but proxy deployment is a separate validation scope.

## Build

Use JDK 25 and the checked-in Gradle wrapper:

```powershell
.\gradlew.bat clean check :skinsrestorer:build --no-daemon --console=plain
```

The universal plugin output is `build/libs/SkinsRestorer.jar`, with descriptor
version `15.12.6-1`. Use a distinct filename such as
`SkinsRestorer-15.12.6-1.jar` for delivery. Do not install it alongside the old JAR.

## Regression evidence

`regression/holder-snapshot` contains the isolated test source. The agent only
counts core holder/snapshot calls and attributes calls to the actual registered
SkinsRestorer click listener; it does not change arguments, results or control
flow. The probe and agent must never be installed on the production server.

The same test suite is run against the official original JAR and the fresh build:

- Positive controls for both holder overloads on the supplied core.
- Plain and generated nested-item barrels, single chests and double chests.
- Top/lower clicks, shift, hotbar keys, double clicks, drops and offhand swap.
- Already-cancelled events and real SkinsRestorer holder identity/cancellation.
- Protocol-776 client item transfers, `/skins`, 36+4 entries, next/previous page,
  selecting and persisting a local test skin, closing and reopening the menu.

The server uses an isolated FILE database, local sample skins, disabled skin
network fetches and a loopback-only listener. No production configuration, player
data, database credentials or world files are part of the test source.

The generated item sample does not reproduce the original barrel NBT. Zero
snapshot calls proves removal of this listener's expensive snapshot path; it
does not establish that all 15.94 seconds of the original watchdog delay were
spent in that path or that all production lag is fixed.
