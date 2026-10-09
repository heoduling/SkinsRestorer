package audit.sr;

import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.SkinProperty;
import net.skinsrestorer.bukkit.gui.BukkitGUIHolder;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local isolated regression only. Not included in the production plugin. */
public final class SkinHolderProbe extends JavaPlugin {
    private Method reset, counts;
    private RegisteredListener listener;
    private boolean fixed;
    private int passed, guiClicks;
    private final Map<String, Inventory> containers = new LinkedHashMap<>();

    @Override public void onEnable() {
        try {
            Class<?> counters = Class.forName("audit.holder.SnapshotCounters", true, ClassLoader.getSystemClassLoader());
            reset = counters.getMethod("reset"); counts = counters.getMethod("counts");
            for (RegisteredListener candidate : InventoryClickEvent.getHandlerList().getRegisteredListeners()) {
                if (candidate.getListener().getClass().getName().equals("net.skinsrestorer.bukkit.listener.InventoryListener")) listener = candidate;
            }
            require(listener != null, "Registered skin listener missing");
            fixed = listener.getPlugin().getDescription().getVersion().equals("15.12.6-1");
            getLogger().info("PROBE_READY fixed=" + fixed + " version=" + listener.getPlugin().getDescription().getVersion());
            Bukkit.getAsyncScheduler().runNow(this, task -> {
                try {
                    String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/c10591e6909e6a281b371836e462d67a2c78fa0952e910f32b41a26c48c1757c\"}}}";
                    SkinProperty skin = SkinProperty.of(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)), "isolated-test");
                    for (int i = 0; i < 40; i++) SkinsRestorerProvider.get().getSkinStorage().setCustomSkinData("audit" + String.format("%02d", i), skin);
                    getLogger().info("SKINS_SEEDED count=40");
                } catch (Throwable e) { fail(e); }
            });
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equals("stop")) {
            Bukkit.getGlobalRegionScheduler().execute(this, Bukkit::shutdown); return true;
        }
        if (!(sender instanceof Player player)) return true;
        player.getScheduler().execute(this, () -> {
            try {
                switch (args.length == 0 ? "run" : args[0]) {
                    case "run" -> run(player);
                    case "diagnose" -> Bukkit.getAsyncScheduler().runNow(this, task -> {
                        try {
                            Object hook = listener.getPlugin().getClass().getMethod("getShutdownHook").invoke(listener.getPlugin());
                            var captured = hook.getClass().getDeclaredFields()[0]; captured.setAccessible(true);
                            Object plugin = captured.get(hook);
                            var field = plugin.getClass().getDeclaredField("injector"); field.setAccessible(true);
                            var injector = (net.skinsrestorer.shadow.injector.Injector) field.get(plugin);
                            var wrapper = injector.getSingleton(net.skinsrestorer.bukkit.wrapper.WrapperBukkit.class);
                            var commandHandler = injector.getSingleton(net.skinsrestorer.shared.commands.GUICommand.class);
                            var method = commandHandler.getClass().getDeclaredMethod("onDefault", net.skinsrestorer.shared.subjects.SRPlayer.class);
                            method.setAccessible(true); method.invoke(commandHandler, wrapper.player(player));
                            getLogger().info("DIAGNOSE_RETURNED enabled=" + listener.getPlugin().isEnabled());
                            player.getScheduler().run(listener.getPlugin(), scheduled -> getLogger().info("SR_ENTITY_RUN top=" + player.getOpenInventory().getTopInventory().getHolder(false)), null);
                            player.getScheduler().run(this, scheduled -> {
                                try {
                                    var gui = injector.getSingleton(net.skinsrestorer.bukkit.gui.BukkitGUI.class);
                                    var model = new net.skinsrestorer.shared.gui.SRInventory(6, new net.skinsrestorer.shared.subjects.messages.ComponentString("{\"text\":\"Native GUI\"}"), Map.of());
                                    Inventory actual = gui.createGUI(model);
                                    var opened = player.openInventory(actual);
                                    getLogger().info("DIRECT_GUI opened=" + (opened != null) + " dead=" + player.isDead() + " health=" + player.getHealth());
                                } catch (Throwable e) { fail(e); }
                            }, null);
                        } catch (Throwable e) { fail(e); }
                    });
                    case "wire" -> {
                        Inventory inv = containers.get(args[1]);
                        inv.clear(); inv.setItem(0, new ItemStack(Material.STONE, 32));
                        player.getInventory().clear(); player.setItemOnCursor(null); player.openInventory(inv);
                        reset.invoke(null); getLogger().info("WIRE_READY " + args[1]);
                    }
                    case "report" -> {
                        Inventory top = player.getOpenInventory().getTopInventory();
                        getLogger().info("WIRE_REPORT " + Arrays.toString(args) + " counts=" + counts.invoke(null)
                                + " slot=" + top.getItem(0) + " cursor=" + player.getItemOnCursor());
                    }
                    case "menu" -> {
                        Inventory top = player.getOpenInventory().getTopInventory();
                        require(top.getHolder(false) instanceof BukkitGUIHolder, "Real skin GUI holder");
                        getLogger().info("MENU_REPORT size=" + top.getSize() + " counts=" + counts.invoke(null));
                        for (int i = 0; i < top.getSize(); i++) {
                            ItemStack item = top.getItem(i);
                            if (item != null) getLogger().info("MENU_SLOT " + i + " type=" + item.getType() + " name=" + item.getItemMeta().getDisplayName());
                        }
                        reset.invoke(null);
                    }
                    case "profile" -> {
                        UUID id = player.getUniqueId();
                        Bukkit.getAsyncScheduler().runNow(this, task -> getLogger().info("PROFILE_REPORT skin=" + SkinsRestorerProvider.get().getPlayerStorage().getSkinIdOfPlayer(id)));
                    }
                    case "reset" -> { player.closeInventory(); player.setItemOnCursor(null); player.getInventory().clear(); reset.invoke(null); getLogger().info("RESET_DONE"); }
                    default -> throw new IllegalArgumentException(Arrays.toString(args));
                }
            } catch (Throwable e) { fail(e); }
        }, null, 0L);
        return true;
    }
    private void fail(Throwable e) { getLogger().severe("FAIL " + e); e.printStackTrace(); }
    private void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private void pass(String message) { passed++; getLogger().info("PASS " + message); }
    @SuppressWarnings("unchecked") private Map<String, Long> measured() throws Exception { return (Map<String, Long>) counts.invoke(null); }
    private long count(Map<String, Long> map, String key) { return map.getOrDefault(key, 0L); }
    private void run(Player p) throws Exception {
        require(!p.isDead(), "Regression client must be alive");
        p.setInvulnerable(true);
        p.setGameMode(GameMode.CREATIVE);
        passed = 0;
        var permission = p.addAttachment(this);
        for (String node : List.of("skinsrestorer.command.gui", "skinsrestorer.command", "skinsrestorer.command.set")) permission.setPermission(node, true);
        require(Bukkit.isOwnedByCurrentRegion(p), "Player owner"); setup(p);
        for (boolean nested : List.of(false, true)) {
            for (var entry : containers.entrySet()) {
                Inventory inv = entry.getValue(); inv.clear(); inv.setItem(0, nested ? nested() : new ItemStack(Material.STONE, 32));
                byte[] before = inv.getItem(0).serializeAsBytes();
                p.openInventory(inv);
                for (int slot : new int[]{0, inv.getSize()}) {
                    for (ClickType type : List.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT,
                            ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK, ClickType.DROP, ClickType.CONTROL_DROP, ClickType.SWAP_OFFHAND, ClickType.MIDDLE)) {
                        InventoryClickEvent event = event(p, slot, type);
                        reset.invoke(null); listener.callEvent(event); var observed = measured();
                        require(!event.isCancelled(), "Unrelated container cancellation");
                        require(Arrays.equals(before, inv.getItem(0).serializeAsBytes()), "Item changed by holder query");
                        if (fixed) require(count(observed, "sr.snapshot") == 0, "Fixed snapshot: " + observed);
                        else if (!entry.getKey().equals("double")) require(count(observed, "sr.snapshot") == 1, "Missing baseline snapshot: " + observed);
                        pass("container=" + entry.getKey() + " nested=" + nested + " slot=" + slot + " click=" + type + " counts=" + observed);
                    }
                }
            }
        }
        Inventory barrel = containers.get("barrel"); p.openInventory(barrel);
        InventoryClickEvent cancelled = event(p, 0, ClickType.LEFT); cancelled.setCancelled(true);
        reset.invoke(null); listener.callEvent(cancelled); require(measured().isEmpty(), "ignoreCancelled changed"); pass("already-cancelled");
        BukkitGUIHolder holder = new BukkitGUIHolder(null, null) {
            @Override public void onClick(InventoryClickEvent event) { guiClicks++; }
        };
        Inventory gui = Bukkit.createInventory(holder, 54, "Identity regression"); holder.setInventory(gui);
        gui.setItem(0, new ItemStack(Material.PLAYER_HEAD)); p.openInventory(gui);
        require(gui.getHolder(false) == holder, "Custom holder identity");
        for (int slot : new int[]{0, 54}) {
            for (ClickType type : List.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT,
                    ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK, ClickType.DROP, ClickType.CONTROL_DROP, ClickType.SWAP_OFFHAND, ClickType.MIDDLE)) {
                InventoryClickEvent event = event(p, slot, type); int before = guiClicks;
                reset.invoke(null); listener.callEvent(event);
                require(event.isCancelled(), "GUI cancellation missing " + slot + " " + type);
                require(guiClicks - before == (slot == 0 ? 1 : 0), "Wrong callback count");
                require(count(measured(), "sr.snapshot") == 0, "GUI snapshot"); pass("gui slot=" + slot + " click=" + type);
            }
        }
        p.closeInventory();
        getLogger().info("SUITE_PASS cases=" + passed + " fixed=" + fixed);
    }
    private InventoryClickEvent event(Player p, int slot, ClickType click) {
        InventoryAction action = switch (click) {
            case SHIFT_LEFT, SHIFT_RIGHT -> InventoryAction.MOVE_TO_OTHER_INVENTORY;
            case NUMBER_KEY, SWAP_OFFHAND -> InventoryAction.HOTBAR_SWAP;
            case DOUBLE_CLICK -> InventoryAction.COLLECT_TO_CURSOR;
            case DROP -> InventoryAction.DROP_ONE_SLOT;
            case CONTROL_DROP -> InventoryAction.DROP_ALL_SLOT;
            case MIDDLE -> InventoryAction.CLONE_STACK;
            default -> InventoryAction.PICKUP_ALL;
        };
        return new InventoryClickEvent(p.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot, click, action, 0);
    }
    private void setup(Player p) throws Exception {
        Location base = p.getLocation().getBlock().getLocation().add(2, 0, 0);
        for (int i = 0; i < 5; i++) require(Bukkit.isOwnedByCurrentRegion(base.clone().add(i, 0, 0)), "Block owner");
        Block barrel = base.getBlock(); barrel.setType(Material.BARREL, false);
        containers.put("barrel", ((Barrel) barrel.getState(false)).getInventory());
        Block chest = base.clone().add(1, 0, 0).getBlock(); chest.setType(Material.CHEST, false);
        containers.put("chest", ((Chest) chest.getState(false)).getBlockInventory());
        Block left = base.clone().add(3, 0, 0).getBlock(), right = base.clone().add(4, 0, 0).getBlock();
        left.setType(Material.CHEST, false); right.setType(Material.CHEST, false);
        var l = (org.bukkit.block.data.type.Chest) left.getBlockData();
        var r = (org.bukkit.block.data.type.Chest) right.getBlockData();
        l.setFacing(BlockFace.NORTH); r.setFacing(BlockFace.NORTH);
        l.setType(org.bukkit.block.data.type.Chest.Type.LEFT); r.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);
        left.setBlockData(l, false); right.setBlockData(r, false);
        Inventory joined = ((Chest) left.getState(false)).getInventory(); require(joined.getSize() == 54, "Double chest");
        containers.put("double", joined);
        for (var entry : containers.entrySet()) {
            reset.invoke(null); InventoryHolder snapshot = entry.getValue().getHolder(); var positive = measured();
            reset.invoke(null); InventoryHolder live = entry.getValue().getHolder(false);
            require(live != null && live.getClass() == snapshot.getClass(), "Holder type");
            require(count(measured(), "total.snapshot") == 0, "false core contract");
            if (!entry.getKey().equals("double")) require(count(positive, "total.snapshot") == 1, "Positive snapshot control");
            pass("core-contract " + entry.getKey());
        }
    }
    private ItemStack nested() {
        ItemStack item = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        ShulkerBox box = (ShulkerBox) meta.getBlockState();
        for (int i = 0; i < 27; i++) {
            ItemStack child = new ItemStack(Material.STONE, 64); var cm = child.getItemMeta();
            cm.getPersistentDataContainer().set(new NamespacedKey(this, "sample"), PersistentDataType.STRING, "generated-audit-data-".repeat(500));
            child.setItemMeta(cm); box.getInventory().setItem(i, child);
        }
        meta.setBlockState(box); item.setItemMeta(meta); return item;
    }
}
