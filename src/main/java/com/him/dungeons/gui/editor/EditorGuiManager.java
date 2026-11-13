package com.him.dungeons.gui.editor;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.ConfigUtil;
import com.him.dungeons.util.LanguageManager;
import com.him.dungeons.util.MessageUtil;
import com.him.dungeons.util.RoomUse;
import com.him.dungeons.util.YamlDoorUtil;
import com.him.dungeons.util.SelectionManager;
import com.him.dungeons.api.hook.DungeonHookRegistry;
import com.him.dungeons.api.hook.DungeonHookRegistry.HookType;
import com.him.dungeons.generator.RoomConfig;
import com.him.dungeons.generator.RoomConfigReader;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EditorGuiManager implements Listener {

    // ==================== 页面 ID ====================
    private static final String P_MAIN = "main";
    private static final String P_ROOT = "root";
    private static final String P_DUNGEON_LIST = "dungeonList";
    private static final String P_DUNGEON_EDITOR = "dungeonEditor";
    private static final String P_CONFIG_CAT = "configCat";
    private static final String P_CONFIG_SECTION = "configSection";
    private static final String P_ROOM_LIST = "roomList";
    private static final String P_ROOM_CONFIG = "roomConfig";
    private static final String P_DOOR_LIST = "doorList";
    private static final String P_SINGLE_DOOR = "singleDoor";
    private static final String P_CHEST_LIST = "chestList";
    private static final String P_SINGLE_CHEST = "singleChest";
    private static final String P_SPAWN = "spawn";
    private static final String P_CHECKPOINT = "checkpoint";
    private static final String P_CODE_FILES = "codeFiles";
    private static final String P_LIST_EDITOR = "listEditor";
    private static final String P_MAP_EDITOR = "mapEditor";
    private static final String P_HOOK_OVERVIEW = "hookOverview";
    private static final String P_HOOK_DETAIL   = "hookDetail";

    private final HimDungeons plugin;
    private final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();
    private final Map<UUID, ChestAddSession> chestAddSessions = new ConcurrentHashMap<>();

    private static class ChestAddSession {
        final String dungeonName;
        final int x, y, z;
        String baseName;
        final List<Map<String, Object>> chests = new ArrayList<>();
        ChestAddSession(String dungeonName, int x, int y, int z) {
            this.dungeonName = dungeonName;
            this.x = x; this.y = y; this.z = z;
        }
    }

    public EditorGuiManager(HimDungeons plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void handleResetToDefault(Player p, String dungeon) {
        beginInput(p,
                LanguageManager.get(p, "editor.msg.reset-confirm", "dungeon", dungeon),
                in -> {
                    if (!"confirm".equalsIgnoreCase(in)) {
                        MessageUtil.sendMessage(p, "editor.common.cancelled");
                        openDungeonEditor(p, dungeon);
                        return;
                    }
                    FileConfiguration def = plugin.buildDefaultInstanceConfig(dungeon);
                    boolean ok = ConfigUtil.saveDungeonConfig(dungeon, def);
                    session(p).working.remove(dungeon);
                    MessageUtil.sendMessage(p,
                            ok ? "editor.msg.reset-ok" : "editor.msg.reset-fail");
                    openDungeonEditor(p, dungeon);
                },
                () -> openDungeonEditor(p, dungeon));
    }

    // ==================== 聊天输入 ====================
    private static class PendingInput {
        final Consumer<String> apply;
        final Runnable cancel;
        PendingInput(Consumer<String> apply, Runnable cancel) {
            this.apply = apply;
            this.cancel = cancel;
        }
    }

    private void beginInput(Player p, String prompt, Consumer<String> apply, Runnable cancel) {
        p.closeInventory();
        p.sendMessage(LanguageManager.getPrefix() + prompt);
        p.sendMessage(LanguageManager.getPrefix()
                + LanguageManager.get(p, "editor.common.cancel-hint"));
        pendingInputs.put(p.getUniqueId(), new PendingInput(apply, cancel));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String msg = e.getMessage().trim();

        // ============ 箱子多步输入 ============
        ChestAddSession cs = chestAddSessions.get(uuid);
        if (cs != null) {
            e.setCancelled(true);
            if (msg.equalsIgnoreCase("cancel")) {
                chestAddSessions.remove(uuid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    MessageUtil.sendMessage(p, "editor.common.cancelled");
                    openDungeonEditor(p, cs.dungeonName);
                });
                return;
            }
            if (cs.baseName == null) {
                cs.baseName = msg;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    MessageUtil.sendMessage(p, "editor.msg.chest-path-set", "path", cs.baseName);
                    p.sendMessage(LanguageManager.getPrefix()
                            + LanguageManager.get(p, "editor.msg.chest-prob-hint"));
                });
                return;
            }
            if (msg.equalsIgnoreCase("done")) {
List<Map<?, ?>> existing = readChestMaps(cs.dungeonName, cs.baseName);
List<Map<?, ?>> chestList = new ArrayList<>(existing);
chestList.addAll(cs.chests);
writeChestMaps(cs.dungeonName, cs.baseName, chestList);
chestAddSessions.remove(uuid);
Bukkit.getScheduler().runTask(plugin, () -> {
    MessageUtil.sendMessage(p, "editor.msg.chest-saved",
            "path", roomFileSingle(cs.dungeonName, cs.baseName).getPath(),
            "count", cs.chests.size());
    openDungeonEditor(p, cs.dungeonName);
});
                return;
            }
            int prob;
            try { prob = Integer.parseInt(msg); } catch (Exception ex) {
                Bukkit.getScheduler().runTask(plugin,
                        () -> MessageUtil.sendError(p, "editor.msg.chest-invalid-prob"));
                return;
            }
            if (prob < 0 || prob > 100) {
                Bukkit.getScheduler().runTask(plugin,
                        () -> MessageUtil.sendError(p, "editor.msg.chest-prob-range"));
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                Bukkit.getScheduler().runTask(plugin,
                        () -> MessageUtil.sendError(p, "editor.msg.chest-need-hand"));
                return;
            }
            if (prob == 0) {
                Bukkit.getScheduler().runTask(plugin,
                        () -> MessageUtil.sendMessage(p, "editor.msg.chest-prob-zero"));
                return;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("pos", Arrays.asList(cs.x, cs.y, cs.z));
            entry.put("loot", prob + " " + hand.getType().name() + " " + hand.getAmount());
            cs.chests.add(entry);
            Bukkit.getScheduler().runTask(plugin, () -> {
                MessageUtil.sendMessage(p, "editor.msg.chest-added", "count", cs.chests.size());
                MessageUtil.sendMessage(p, "editor.msg.chest-continue");
            });
            return;
        }

        // ============ 常规单步输入 ============
        PendingInput pi = pendingInputs.remove(uuid);
        if (pi == null) return;
        e.setCancelled(true);
        if (msg.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, pi.cancel);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> pi.apply.accept(msg));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sessions.remove(e.getPlayer().getUniqueId());
        pendingInputs.remove(e.getPlayer().getUniqueId());
        chestAddSessions.remove(e.getPlayer().getUniqueId());
    }

    // ==================== 会话 ====================
    private EditorSession session(Player p) {
        return sessions.computeIfAbsent(p.getUniqueId(), EditorSession::new);
    }

    private FileConfiguration workingConfig(EditorSession s, String dungeon) {
        String key = dungeon == null ? "root" : dungeon;
        return s.working.computeIfAbsent(key, k -> {
            FileConfiguration c = dungeon == null
                    ? ConfigUtil.getGlobalConfig()
                    : ConfigUtil.getDungeonConfig(dungeon);
            return c != null ? c : new YamlConfiguration();
        });
    }

    private void saveWorking(Player p, EditorSession s, String dungeon) {
        String key = dungeon == null ? "root" : dungeon;
        FileConfiguration c = s.working.get(key);
        if (c == null) return;
        boolean ok = dungeon == null
                ? ConfigUtil.saveGlobalConfig(c)
                : ConfigUtil.saveDungeonConfig(dungeon, c);
        MessageUtil.sendMessage(p, ok ? "editor.common.save-ok" : "editor.common.save-fail");
    }

    // ==================== 物品构建 ====================
    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack cfgItem(Player player, Material mat, String nameKey,
                              String path, FileConfiguration cfg) {
        Object v = cfg.get(path);
        List<String> lore = new ArrayList<>();
        lore.add(LanguageManager.get(player, "editor.common.current",
                "value", v == null ? "null" : v));
        lore.add(LanguageManager.get(player, "editor.common.path", "path", path));
        lore.add(LanguageManager.get(player, "editor.common.click-edit"));
        return item(mat, LanguageManager.get(player, nameKey), lore);
    }

    // ==================== P1 主页面 ====================
    public void openMain(Player p) {
        String title = LanguageManager.get(p, "editor.title.main");
        Inventory inv = Bukkit.createInventory(new EditorHolder(P_MAIN, null, null, 0), 27, title);
        inv.setItem(10, item(Material.REPEATER,
                LanguageManager.get(p, "editor.main.hook"),
                List.of(
                        LanguageManager.get(p, "editor.main.hook-lore1"),
                        LanguageManager.get(p, "editor.main.hook-lore2",
                                "count", plugin.getHookRegistry().countAll()))));
        inv.setItem(9, item(Material.BOOK,
                LanguageManager.get(p, "editor.main.root"),
                List.of(LanguageManager.get(p, "editor.main.root-lore"))));
        inv.setItem(11, item(Material.CHEST,
                LanguageManager.get(p, "editor.main.dungeon"),
                List.of(LanguageManager.get(p, "editor.main.dungeon-lore"))));
        inv.setItem(13, item(Material.REDSTONE,
                LanguageManager.get(p, "editor.main.reload"),
                List.of(LanguageManager.get(p, "editor.main.reload-lore"))));
        inv.setItem(15, item(Material.COMPASS,
                LanguageManager.get(p, "editor.main.join"), null));
        p.openInventory(inv);
    }

    // ==================== P2 根配置 ====================
    public void openRoot(Player p) {
        EditorSession s = session(p);
        FileConfiguration c = workingConfig(s, null);
        String title = LanguageManager.get(p, "editor.title.root");
        Inventory inv = Bukkit.createInventory(new EditorHolder(P_ROOT, null, null, 0), 36, title);

        inv.setItem(0, cfgItem(p, Material.BOOK, "editor.root.lang", "lang", c));
        inv.setItem(1, cfgItem(p, Material.NAME_TAG, "editor.root.msg-prefix", "msgPrefix", c));
        inv.setItem(2, cfgItem(p, Material.REPEATER, "editor.root.scan", "scanAndRegisterNewRooms", c));
        inv.setItem(9, cfgItem(p, Material.CLOCK, "editor.root.cooldown", "generateCooldown", c));
        Object fmt = c.get("schematic.saveFormat", "sponge_v3");
        inv.setItem(10, item(Material.PAPER,
                LanguageManager.get(p, "editor.root.save-format"),
                List.of(
                        LanguageManager.get(p, "editor.root.save-format-lore1", "format", fmt),
                        LanguageManager.get(p, "editor.root.save-format-lore2"))));
inv.setItem(11, item(Material.END_PORTAL_FRAME,
        LanguageManager.get(p, "editor.root.world-pool.name"),
        List.of(LanguageManager.get(p, "editor.root.world-pool.value",
                "value", c.getInt("worldPool.size", 2)))));

boolean fastPaste = c.getBoolean("fastPaste.enabled", false);
inv.setItem(12, item(
        fastPaste ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
        LanguageManager.get(p, "editor.root.fast-paste.name"),
        List.of(
                LanguageManager.get(p, "editor.root.fast-paste.enabled",
                        "value", (fastPaste ? "§a" : "§c") + fastPaste),
                LanguageManager.get(p, "editor.root.fast-paste.path",
                        "path", "fastPaste.enabled"),
                LanguageManager.get(p, "editor.root.fast-paste.click-toggle"))));

String detected = c.getString("fastPaste.detectedPackage", "");
inv.setItem(13, item(Material.COMPASS,
        LanguageManager.get(p, "editor.root.detected-package.name"),
        List.of(
                LanguageManager.get(p, "editor.root.detected-package.value",
                        "value", detected.isEmpty()
                                ? LanguageManager.get(p, "editor.root.detected-package.not-detected")
                                : detected),
                LanguageManager.get(p, "editor.root.detected-package.path",
                        "path", "fastPaste.detectedPackage"),
                LanguageManager.get(p, "editor.root.detected-package.readonly"))));

        inv.setItem(18, item(Material.LIME_DYE,
                LanguageManager.get(p, "editor.common.save"), null));
        inv.setItem(25, item(Material.COMPASS,
                LanguageManager.get(p, "editor.main.join"), null));
        inv.setItem(26, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.back-main"), null));
        p.openInventory(inv);
    }

    // ==================== P3 实例选择 ====================
    public void openDungeonList(Player p, int page) {
        List<String> names = listDungeonNames();
        String title = LanguageManager.get(p, "editor.title.dungeon-list");
        Inventory inv = Bukkit.createInventory(new EditorHolder(P_DUNGEON_LIST, null, null, page),
                54, title);
        int start = page * 36;
        for (int i = 0; i < 36 && start + i < names.size(); i++) {
            String name = names.get(start + i);
            inv.setItem(9 + i, item(Material.CHEST,
                    LanguageManager.get(p, "editor.dungeon-list.entry", "name", name),
                    List.of(LanguageManager.get(p, "editor.dungeon-list.entry-lore"))));
        }
        inv.setItem(0, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-prev"), null));
        inv.setItem(4, item(Material.PAPER,
                LanguageManager.get(p, "editor.common.page-info", "page", page + 1), null));
        inv.setItem(8, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-next"), null));
        inv.setItem(52, item(Material.COMPASS,
                LanguageManager.get(p, "editor.main.join"), null));
        inv.setItem(53, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.back-main"), null));
        p.openInventory(inv);
    }

    private List<String> listDungeonNames() {
        List<String> out = new ArrayList<>();
        File[] dirs = plugin.getDataFolder().listFiles(File::isDirectory);
        if (dirs == null) return out;
        for (File d : dirs) if (new File(d, "config.yml").exists()) out.add(d.getName());
        out.sort(String::compareTo);
        return out;
    }

    // ==================== P4 实例修改选择 ====================
    public void openDungeonEditor(Player p, String dungeon) {
        String title = LanguageManager.get(p, "editor.title.dungeon-editor");
        Inventory inv = Bukkit.createInventory(new EditorHolder(P_DUNGEON_EDITOR, dungeon, null, 0),
                54, title);
        inv.setItem(9,  item(Material.LIME_DYE, LanguageManager.get(p, "editor.dungeon-editor.add-room"), null));
        inv.setItem(10, item(Material.LIME_DYE, LanguageManager.get(p, "editor.dungeon-editor.add-door"), null));
        inv.setItem(11, item(Material.LIME_DYE, LanguageManager.get(p, "editor.dungeon-editor.add-chest"), null));
        inv.setItem(12, item(Material.LIME_DYE, LanguageManager.get(p, "editor.dungeon-editor.add-spawn"), null));
        inv.setItem(13, item(Material.LIME_DYE, LanguageManager.get(p, "editor.dungeon-editor.add-checkpoint"), null));
        inv.setItem(18, item(Material.RED_DYE, LanguageManager.get(p, "editor.dungeon-editor.remove-room"), null));
        inv.setItem(19, item(Material.RED_DYE, LanguageManager.get(p, "editor.dungeon-editor.remove-door"), null));
        inv.setItem(20, item(Material.RED_DYE, LanguageManager.get(p, "editor.dungeon-editor.remove-chest"), null));
        inv.setItem(21, item(Material.RED_DYE, LanguageManager.get(p, "editor.dungeon-editor.remove-spawn"), null));
        inv.setItem(22, item(Material.RED_DYE, LanguageManager.get(p, "editor.dungeon-editor.remove-checkpoint"), null));
        inv.setItem(27, item(Material.BOOK, LanguageManager.get(p, "editor.dungeon-editor.config"), null));
        inv.setItem(28, item(Material.REPEATER,
                LanguageManager.get(p, "editor.dungeon-editor.refresh"),
                List.of(LanguageManager.get(p, "editor.dungeon-editor.refresh-lore"))));
        inv.setItem(29, item(Material.PAPER, LanguageManager.get(p, "editor.dungeon-editor.room-config"), null));
        inv.setItem(30, item(Material.BARRIER, LanguageManager.get(p, "editor.dungeon-editor.reset"), null));
        inv.setItem(31, item(Material.COMPARATOR, LanguageManager.get(p, "editor.dungeon-editor.scan"), null));
        inv.setItem(32, item(Material.WRITABLE_BOOK, LanguageManager.get(p, "editor.dungeon-editor.codefiles"), null));
        inv.setItem(49, item(Material.REDSTONE_TORCH, LanguageManager.get(p, "editor.dungeon-editor.test"), null));
        boolean hasTest = hasTestWorld(dungeon);
        inv.setItem(50, item(hasTest ? Material.TNT : Material.GRAY_DYE,
                LanguageManager.get(p, hasTest
                        ? "editor.dungeon-editor.delete-test"
                        : "editor.dungeon-editor.delete-test-none"), null));
        inv.setItem(51, item(Material.EMERALD, LanguageManager.get(p, "editor.dungeon-editor.join"), null));
        inv.setItem(52, item(Material.ARROW, LanguageManager.get(p, "editor.common.back-list"), null));
        inv.setItem(53, item(Material.ARROW, LanguageManager.get(p, "editor.common.back-main"), null));
        p.openInventory(inv);
    }

    private boolean hasTestWorld(String dungeon) {
        String prefix = "dungeon_" + dungeon + "_test_";
        for (World w : Bukkit.getWorlds()) if (w.getName().startsWith(prefix)) return true;
        File[] dirs = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (dirs != null) for (File d : dirs) if (d.getName().startsWith(prefix)) return true;
        return false;
    }

    // ==================== P5 配置分类 ====================
    public void openConfigCat(Player p, String dungeon) {
        String title = LanguageManager.get(p, "editor.title.config-cat");
        Inventory inv = Bukkit.createInventory(new EditorHolder(P_CONFIG_CAT, dungeon, null, 0),
                27, title);
        inv.setItem(10, item(Material.BOOK, LanguageManager.get(p, "editor.config-cat.root"), null));
        inv.setItem(11, item(Material.COMPARATOR, LanguageManager.get(p, "editor.config-cat.gen"), null));
        inv.setItem(12, item(Material.GRASS_BLOCK, LanguageManager.get(p, "editor.config-cat.world"), null));
        inv.setItem(13, item(Material.PLAYER_HEAD, LanguageManager.get(p, "editor.config-cat.player"), null));
        inv.setItem(14, item(Material.SKELETON_SKULL, LanguageManager.get(p, "editor.config-cat.boss"), null));
        inv.setItem(15, item(Material.GOLD_INGOT, LanguageManager.get(p, "editor.config-cat.economy"), null));
        inv.setItem(16, item(Material.SLIME_BALL, LanguageManager.get(p, "editor.config-cat.soft"), null));
        inv.setItem(26, item(Material.ARROW, LanguageManager.get(p, "editor.config-cat.back"), null));
        p.openInventory(inv);
    }

    // ==================== P5a–P5g 配置子页面 ====================
    /** Field 的 name 从硬编码中文改为语言 key（f.name 即 key） */
    record Field(Material mat, String name, String path) {}

    private Map<String, List<Field>> sectionFields() {
        Map<String, List<Field>> m = new LinkedHashMap<>();
        m.put("root", List.of(
                new Field(Material.LEVER, "editor.field.enable", "dungeonInstance.enable"),
                new Field(Material.NAME_TAG, "editor.field.display-name", "dungeonInstance.displayName"),
                new Field(Material.PAPER, "editor.field.file-name", "dungeonInstance.fileName"),
                new Field(Material.BOOK, "editor.field.msg-prefix", "dungeonInstance.msgPrefix"),
                new Field(Material.END_PORTAL_FRAME, "editor.field.lobby", "dungeonInstance.lobby")
        ));
        m.put("gen", List.of(
                new Field(Material.IRON_INGOT, "editor.field.min-room", "dungeonInstance.minRoomAmount"),
                new Field(Material.GOLD_INGOT, "editor.field.max-room", "dungeonInstance.maxRoomAmount"),
                new Field(Material.DIAMOND, "editor.field.dfs-max", "dungeonInstance.dfsMaxDeep"),
                new Field(Material.COMPARATOR, "editor.field.use-bfs", "dungeonInstance.useBfsCheck"),
                new Field(Material.OAK_FENCE, "editor.field.use-aabb", "dungeonInstance.useAabb"),
                new Field(Material.GLASS, "editor.field.disconnected-distance", "dungeonInstance.disconnectedRoomDistance"),
                new Field(Material.REPEATER, "editor.field.allow-room-reuse", "dungeonInstance.allowRoomReuse"),
                new Field(Material.REPEATER, "editor.field.allow-door-reuse", "dungeonInstance.allowDoorReuse"),
                new Field(Material.REPEATER, "editor.field.allow-fallback-reuse", "dungeonInstance.allowFallbackDoorReuse"),
                new Field(Material.LEAD, "editor.field.start-to-boss", "dungeonInstance.startToBossRoomAmmount"),
                new Field(Material.BARRIER, "editor.field.block-generate", "dungeonInstance.blockRoomGenerateBlocks"),
                // 新增
                new Field(Material.PLAYER_HEAD, "editor.field.scale-by-party", "dungeonInstance.scaleByPartySize"),
                new Field(Material.ZOMBIE_HEAD, "editor.field.mob-spawn-rate", "dungeonInstance.mobSpawnRate"),
                new Field(Material.CREEPER_HEAD, "editor.field.max-mobs-per-room", "dungeonInstance.maxMobsPerRoom")
        ));
        m.put("world", List.of(
                new Field(Material.CHEST, "editor.field.keep-inventory", "dungeonInstance.keepInventory"),
                new Field(Material.ENDER_CHEST, "editor.field.deep-clone", "dungeonInstance.deepCloneInventory"),
                new Field(Material.CLOCK, "editor.field.time", "dungeonInstance.time"),
                new Field(Material.CLOCK, "editor.field.dungeon-time", "dungeonInstance.dungeonTime"),
                new Field(Material.SUNFLOWER, "editor.field.advance-time", "dungeonInstance.advanceTime"),
                new Field(Material.WATER_BUCKET, "editor.field.weather", "dungeonInstance.weather"),
                new Field(Material.IRON_SWORD, "editor.field.difficulty", "dungeonInstance.difficulty"),
                new Field(Material.ZOMBIE_HEAD, "editor.field.allow-mobs", "dungeonInstance.allowMobs"),
                new Field(Material.TNT, "editor.field.tnt", "dungeonInstance.tntExplosion"),
                new Field(Material.CREEPER_HEAD, "editor.field.entity", "dungeonInstance.entityExplosion"),
                new Field(Material.RESPAWN_ANCHOR, "editor.field.block", "dungeonInstance.blockExplosion"),
                // 新增（P0 重生与死亡）
                new Field(Material.RED_BED, "editor.field.respawn-mode", "dungeonInstance.respawnMode"),
                new Field(Material.SKELETON_SKULL, "editor.field.death-cooldown", "dungeonInstance.deathCooldown"),
                new Field(Material.CLOCK, "editor.field.player-cooldown", "dungeonInstance.playerCooldown"),
                new Field(Material.IRON_SWORD, "editor.field.friendly-fire", "dungeonInstance.friendlyFire"),
                new Field(Material.HOPPER, "editor.field.auto-pickup", "dungeonInstance.autoPickup"),
                new Field(Material.GOLDEN_APPLE, "editor.field.teleport-invulnerable", "dungeonInstance.teleportInvulnerable"),
                // 新增（P1 体验）
                new Field(Material.BELL, "editor.field.countdown-warn", "dungeonInstance.countdownWarn"),
                new Field(Material.COMPARATOR, "editor.field.timeout-action", "dungeonInstance.timeoutAction"),
                new Field(Material.NAME_TAG, "editor.field.enter-title", "dungeonInstance.enterTitle"),
                new Field(Material.NAME_TAG, "editor.field.leave-title", "dungeonInstance.leaveTitle"),
                new Field(Material.MAP, "editor.field.room-progress", "dungeonInstance.roomProgress"),
                new Field(Material.COMPASS, "editor.field.boss-direction", "dungeonInstance.bossDirection"),
                new Field(Material.NOTE_BLOCK, "editor.field.sound-volume", "dungeonInstance.soundVolume"),
                new Field(Material.ENDER_EYE, "editor.field.allow-spectator", "dungeonInstance.allowSpectatorAfterDeath"),
                // 新增（P3 roomFog）
                new Field(Material.GLASS, "editor.field.room-fog", "dungeonInstance.roomFog")
        ));
        m.put("player", List.of(
                new Field(Material.BOOKSHELF, "editor.field.cmd-list-type", "dungeonInstance.commandsAllowListType"),
                new Field(Material.COMMAND_BLOCK, "editor.field.cmd-list", "dungeonInstance.commandsAllow"),
                new Field(Material.BRICK, "editor.field.build-list-type", "dungeonInstance.buildSettingsListType"),
                new Field(Material.STONE, "editor.field.build-list", "dungeonInstance.buildSettings"),
                new Field(Material.TNT, "editor.field.break-list-type", "dungeonInstance.breakSettingsListType"),
                new Field(Material.DIAMOND_PICKAXE, "editor.field.break-list", "dungeonInstance.breakSettings"),
                new Field(Material.OAK_BUTTON, "editor.field.interact-list-type", "dungeonInstance.interactionSettingsListType"),
                new Field(Material.LEVER, "editor.field.interact-list", "dungeonInstance.interactionSettings"),
                // 新增
                new Field(Material.RED_BANNER, "editor.field.boss-bar", "dungeonInstance.bossBar"),
                new Field(Material.GLOWSTONE_DUST, "editor.field.party-glow", "dungeonInstance.partyGlow")
        ));
        m.put("boss", List.of(
                new Field(Material.SLIME_BALL, "editor.field.use-mythic-boss", "dungeonInstance.useMythicMobForBoss"),
                new Field(Material.NAME_TAG, "editor.field.mythic-boss-name", "dungeonInstance.mythicMobBossName"),
                new Field(Material.SKELETON_SKULL, "editor.field.boss-type", "dungeonInstance.bossType"),
                new Field(Material.WITHER_SKELETON_SKULL, "editor.field.boss-nbt", "dungeonInstance.bossNbt"),
                // 新增
                new Field(Material.IRON_CHESTPLATE, "editor.field.boss-health-multiplier", "dungeonInstance.bossHealthMultiplierPerPlayer"),
                new Field(Material.WITHER_SKELETON_SKULL, "editor.field.boss-phase-enabled", "dungeonInstance.bossPhase.enabled"),
                new Field(Material.WRITABLE_BOOK, "editor.field.boss-phase-phases", "dungeonInstance.bossPhase.phases")
        ));
        m.put("economy", List.of(
                new Field(Material.GOLD_INGOT, "editor.field.vault-reward", "dungeonInstance.rewards.vault"),
                new Field(Material.EMERALD, "editor.field.pp-reward", "dungeonInstance.rewards.playerPoints"),
                new Field(Material.COMMAND_BLOCK_MINECART, "editor.field.cmd-reward", "dungeonInstance.rewards.consoleCommands"),
                new Field(Material.GOLD_NUGGET, "editor.field.vault-cost", "dungeonInstance.enterCost.vault"),
                new Field(Material.DIAMOND, "editor.field.pp-cost", "dungeonInstance.enterCost.playerPoints"),
                new Field(Material.COMMAND_BLOCK, "editor.field.cmd-cost", "dungeonInstance.enterCost.consoleCommands"),
                // 新增（P1 奖励）
                new Field(Material.GOLD_BLOCK, "editor.field.first-clear-vault", "dungeonInstance.rewards.firstClearBonus.vault"),
                new Field(Material.EMERALD_BLOCK, "editor.field.first-clear-pp", "dungeonInstance.rewards.firstClearBonus.playerPoints"),
                new Field(Material.COMMAND_BLOCK, "editor.field.first-clear-cmd", "dungeonInstance.rewards.firstClearBonus.consoleCommands"),
                new Field(Material.HOPPER, "editor.field.share-mode", "dungeonInstance.rewards.shareMode"),
                new Field(Material.DIAMOND, "editor.field.permission-multiplier", "dungeonInstance.rewards.permissionMultiplier"),
                new Field(Material.BARRIER, "editor.field.failure-vault", "dungeonInstance.failurePenalty.vault"),
                new Field(Material.BARRIER, "editor.field.failure-pp", "dungeonInstance.failurePenalty.playerPoints"),
                new Field(Material.BARRIER, "editor.field.failure-cmd", "dungeonInstance.failurePenalty.consoleCommands"),
                new Field(Material.CLOCK, "editor.field.track-fastest", "dungeonInstance.stats.trackFastestTime"),
                // 新增（P3 排行榜）
                new Field(Material.PAPER, "editor.field.leaderboard", "dungeonInstance.leaderboard"),
                // 新增（P3 战利品表）
                new Field(Material.CHEST, "editor.field.loot-table-enabled", "dungeonInstance.lootTable.enabled"),
                new Field(Material.CHEST_MINECART, "editor.field.loot-table-tables", "dungeonInstance.lootTable.tables")
        ));
        m.put("soft", List.of(
                new Field(Material.RED_BED, "editor.field.allow-parties", "dungeonInstance.allowParties"),
                new Field(Material.SLIME_BALL, "editor.field.allow-slimefun", "dungeonInstance.allowSlimefun"),
                new Field(Material.BLAZE_ROD, "editor.field.allow-mythic", "dungeonInstance.useMythicMobs")
        ));
        return m;
    }
    public void openConfigSection(Player p, String dungeon, String section, int page) {
        EditorSession s = session(p);
        FileConfiguration c = workingConfig(s, dungeon);
        List<Field> fields = sectionFields().getOrDefault(section, List.of());
        String title = LanguageManager.get(p, "editor.title.config-section", "section", section);
        Inventory inv = Bukkit.createInventory(new EditorHolder(P_CONFIG_SECTION, dungeon, section, page),
                54, title);
        int slot = 9;
        for (Field f : fields) {
            if (slot >= 45) break;
            Object v = c.get(f.path);
            List<String> lore = new ArrayList<>();
            lore.add(LanguageManager.get(p, "editor.common.current",
                    "value", formatFieldValue(p, f.path, v)));
            lore.add(LanguageManager.get(p, "editor.common.path", "path", f.path));
            lore.add(LanguageManager.get(p, isCycleField(f.path)
                    ? "editor.common.click-cycle"
                    : "editor.common.click-edit"));
            inv.setItem(slot++, item(f.mat, LanguageManager.get(p, f.name), lore));
        }
        inv.setItem(0, item(Material.ARROW,
                LanguageManager.get(p, "editor.config-cat.back"), null));
        inv.setItem(4, item(Material.PAPER, section, null));
        inv.setItem(8, item(Material.LIME_DYE,
                LanguageManager.get(p, "editor.common.save"), null));
        p.openInventory(inv);
    }

    private static boolean isExplosionField(String path) {
        return path.endsWith("tntExplosion")
                || path.endsWith("entityExplosion")
                || path.endsWith("blockExplosion");
    }
    private static boolean isCycleField(String path) {
        return isExplosionField(path)
                || path.endsWith("respawnMode")
                || path.endsWith("timeoutAction")
                || path.endsWith("shareMode")
                || path.endsWith("ListType");
    }

    private static boolean isComplexField(String path) {
        return path.endsWith("bossPhase.phases") || path.endsWith("lootTable.tables");
    }

    private static String cycleRespawnMode(String cur) {
        String s = cur == null ? "checkpoint" : cur.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "lobby" -> "checkpoint";
            case "checkpoint" -> "spectator";
            case "spectator" -> "lobby";
            default -> "checkpoint";
        };
    }

    private static String cycleTimeoutAction(String cur) {
        return "extend".equalsIgnoreCase(cur) ? "forceEnd" : "extend";
    }

    private static String cycleShareMode(String cur) {
        return "split".equalsIgnoreCase(cur) ? "each" : "split";
    }
    /** 原始值 → 用户可读展示（爆炸模式走 key，其余直接值） */
    private static String formatFieldValue(Player p, String path, Object v) {
        if (isExplosionField(path)) {
            String s = v == null ? "deny" : v.toString().toLowerCase(Locale.ROOT);
            String key = switch (s) {
                case "allow" -> "editor.explosion.allow";
                case "damage_only", "damage", "damage-only" -> "editor.explosion.damage";
                default -> "editor.explosion.deny";
            };
            return LanguageManager.get(p, key);
        }
        if (path.endsWith("respawnMode")) {
            String s = v == null ? "checkpoint" : v.toString().toLowerCase(Locale.ROOT);
            String key = switch (s) {
                case "lobby" -> "editor.respawn-mode.lobby";
                case "spectator" -> "editor.respawn-mode.spectator";
                default -> "editor.respawn-mode.checkpoint";
            };
            return LanguageManager.get(p, key);
        }
        if (path.endsWith("timeoutAction")) {
            String s = v == null ? "forceEnd" : v.toString();
            String key = "extend".equalsIgnoreCase(s)
                    ? "editor.timeout-action.extend"
                    : "editor.timeout-action.force-end";
            return LanguageManager.get(p, key);
        }
        if (path.endsWith("shareMode")) {
            String s = v == null ? "each" : v.toString();
            String key = "split".equalsIgnoreCase(s)
                    ? "editor.share-mode.split"
                    : "editor.share-mode.each";
            return LanguageManager.get(p, key);
        }
if (path.endsWith("ListType")) {
    String s = v == null ? "white" : v.toString();
    return LanguageManager.get(p, "black".equalsIgnoreCase(s)
            ? "editor.list-type.blacklist"
            : "editor.list-type.whitelist");
}
        return "§f" + (v == null ? "null" : v);
    }

    /** 循环切换：allow → damage_only → deny → allow */
    private static String cycleExplosion(String cur) {
        String s = cur == null ? "deny" : cur.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "allow" -> "damage_only";
            case "damage_only", "damage", "damage-only" -> "deny";
            default -> "allow";
        };
    }

    // ==================== P6 房间列表 ====================
    public void openRoomList(Player p, String dungeon, int page) {
        EditorSession s = session(p);
        FileConfiguration c = workingConfig(s, dungeon);
        List<String> keys = structureChooseKeys(c);
        String title = LanguageManager.get(p, "editor.title.room-list");
        Inventory inv = Bukkit.createInventory(new EditorHolder(P_ROOM_LIST, dungeon, null, page),
                54, title);
        int start = page * 36;
        for (int i = 0; i < 36 && start + i < keys.size(); i++) {
            String key = keys.get(start + i);
            int weight = c.getInt("dungeonInstance.structureChoose." + key, 50);
            inv.setItem(9 + i, item(Material.PAPER,
                    LanguageManager.get(p, "editor.room-list.entry", "name", key),
                    List.of(LanguageManager.get(p, "editor.room-list.entry-lore", "weight", weight))));
        }
        inv.setItem(0, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-prev"), null));
        inv.setItem(4, item(Material.PAPER,
                LanguageManager.get(p, "editor.common.page-info", "page", page + 1), null));
        inv.setItem(8, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-next"), null));
        inv.setItem(52, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.back-edit"), null));
        inv.setItem(53, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.back-main"), null));
        p.openInventory(inv);
    }

    private List<String> structureChooseKeys(FileConfiguration c) {
        ConfigurationSection sec = c.getConfigurationSection("dungeonInstance.structureChoose");
        if (sec == null) return new ArrayList<>();
        List<String> list = new ArrayList<>(sec.getKeys(false));
        list.sort(String::compareTo);
        return list;
    }

    // ==================== P6a 单房间 ====================
public void openRoomConfig(Player p, String dungeon, String baseName) {
    EditorSession s = session(p);
    FileConfiguration c = workingConfig(s, dungeon);
    String title = LanguageManager.get(p, "editor.title.room-config", "room", baseName);
    Inventory inv = Bukkit.createInventory(new EditorHolder(P_ROOM_CONFIG, dungeon, baseName, 0),
            27, title);

    int weight = c.getInt("dungeonInstance.structureChoose." + baseName, 50);
    RoomConfig roomCfg = RoomConfigReader.read(dungeon, baseName);
    RoomUse use = roomCfg.use;

    // 0：权重（旧）
    inv.setItem(0, item(Material.PAPER,
            LanguageManager.get(p, "editor.room-config.weight"),
            List.of(
                    LanguageManager.get(p, "editor.room-config.weight-value", "weight", weight),
                    LanguageManager.get(p, "editor.room-config.weight-click"))));

    // 1：type
    inv.setItem(1, item(Material.NAME_TAG,
            LanguageManager.get(p, "editor.room-config.type", "value", roomCfg.type),
            List.of(LanguageManager.get(p, "editor.room-config.type-lore"))));

    // 2-6：use 五项
    inv.setItem(2, useItem(p, "inStart",    use.inStart,    Material.RED_BED));
    inv.setItem(3, useItem(p, "inMainPath", use.inMainPath, Material.IRON_INGOT));
    inv.setItem(4, useItem(p, "inBranch",   use.inBranch,   Material.GOLD_INGOT));
    inv.setItem(5, useItem(p, "inBoss",     use.inBoss,     Material.SKELETON_SKULL));
    inv.setItem(6, useItem(p, "inFallback", use.inFallback, Material.BARRIER));

    // 门 / 箱 / 出生点 / 存档点
inv.setItem(9, item(Material.OAK_DOOR,
        LanguageManager.get(p, "editor.room-config.door-file"),
        List.of(displayFile(dungeon, baseName, "_door.yml"),LanguageManager.get(p, "editor.room-config.merged-file-hint"))));
inv.setItem(10, item(Material.CHEST,
        LanguageManager.get(p, "editor.room-config.chest-file"),
        List.of(displayFile(dungeon, baseName, "_chest.yml"))));
    inv.setItem(11, item(Material.COMPASS,
            LanguageManager.get(p, "editor.room-config.spawn-file"), null));
    inv.setItem(12, item(Material.BEACON,
            LanguageManager.get(p, "editor.room-config.checkpoint-file"), null));

    inv.setItem(26, item(Material.ARROW,
            LanguageManager.get(p, "editor.common.back-list"), null));
    plugin.getDebugVisualizer().showRoomElements(p, dungeon, baseName);
    p.openInventory(inv);
}
/** 统一目标文件：<base>.yml 存在 → 用它；否则退回旧 _door.yml */
private File yamlFile(String dungeon, String baseName, String legacySuffix) {
    File rooms = new File(plugin.getDataFolder(), dungeon + "/rooms");
    String rel = baseName.replace('/', File.separatorChar);
    File single = new File(rooms, rel + ".yml");
    if (single.exists()) return single;
    File legacy = new File(rooms, rel + legacySuffix);
    return legacy.exists() ? legacy : single;   // 都不存在就返回 single（新建）
}
private ItemStack useItem(Player p, String field, boolean on, Material mat) {
    String nameKey = "editor.room-config.use-" + field;
    String valKey = on ? "editor.room-config.use-on" : "editor.room-config.use-off";
    return item(mat,
            LanguageManager.get(p, nameKey),
            List.of(LanguageManager.get(p, valKey),
                    LanguageManager.get(p, "editor.common.click-cycle")));
}

    private String doorFileName(String base) { return base + "_door.yml"; }
    private String chestFileName(String base) { return base + "_chest.yml"; }
    private File roomFile(String dungeon, String baseName, String suffix) {
        File rooms = new File(plugin.getDataFolder(), dungeon + "/rooms");
        return new File(rooms, baseName.replace('/', File.separatorChar) + suffix);
    }

    // ==================== P6a-i 门列表 ====================
    public void openDoorList(Player p, String dungeon, String baseName, int page) {
        List<Map<?, ?>> doors = readDoorMaps(dungeon, baseName);
        String title = LanguageManager.get(p, "editor.title.door-list", "room", baseName);
        Inventory inv = Bukkit.createInventory(new EditorHolder(P_DOOR_LIST, dungeon, baseName, page),
                54, title);
        int start = page * 36;
        for (int i = 0; i < 36 && start + i < doors.size(); i++) {
            Map<?, ?> m = doors.get(start + i);
            inv.setItem(9 + i, item(Material.OAK_DOOR,
                    LanguageManager.get(p, "editor.door-list.entry", "index", start + i),
                    List.of(
                            LanguageManager.get(p, "editor.door-list.entry-lore", "data", m),
                            LanguageManager.get(p, "editor.door-list.entry-lore2"))));
        }
        inv.setItem(0, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-prev"), null));
        inv.setItem(4, item(Material.PAPER,
                LanguageManager.get(p, "editor.door-list.page-info",
                        "page", page + 1, "total", doors.size()), null));
        inv.setItem(8, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-next"), null));
        inv.setItem(45, item(Material.LIME_DYE,
                LanguageManager.get(p, "editor.door-list.add"),
                List.of(LanguageManager.get(p, "editor.door-list.add-lore"))));
        inv.setItem(53, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.back-list"), null));
        p.openInventory(inv);
    }

    // ==================== P6a-i-1 单门 ====================
public void openSingleDoor(Player p, String dungeon, String baseName, int index) {
    List<YamlDoorUtil.DoorAnchor> doors =
            RoomConfigReader.read(dungeon, baseName).doors;
    if (index < 0 || index >= doors.size()) { openDoorList(p, dungeon, baseName, 0); return; }
    YamlDoorUtil.DoorAnchor door = doors.get(index);

    String title = LanguageManager.get(p, "editor.title.single-door",
            "room", baseName + "#" + index);
    Inventory inv = Bukkit.createInventory(new EditorHolder(P_SINGLE_DOOR, dungeon,
            baseName + "#" + index, 0), 27, title);

    inv.setItem(10, item(Material.RED_STAINED_GLASS_PANE,
            LanguageManager.get(p, "editor.coord.x", "value", door.x), null));
    inv.setItem(11, item(Material.GREEN_STAINED_GLASS_PANE,
            LanguageManager.get(p, "editor.coord.y", "value", door.y), null));
    inv.setItem(12, item(Material.BLUE_STAINED_GLASS_PANE,
            LanguageManager.get(p, "editor.coord.z", "value", door.z), null));
    inv.setItem(13, item(Material.COMPASS,
            LanguageManager.get(p, "editor.single-door.facing", "value", door.facing),
            List.of(LanguageManager.get(p, "editor.single-door.facing-lore"))));
    inv.setItem(14, item(Material.NAME_TAG,
            LanguageManager.get(p, "editor.single-door.type", "value", door.type),
            List.of(LanguageManager.get(p, "editor.single-door.type-lore"))));
    inv.setItem(15, item(Material.PAPER,
            LanguageManager.get(p, "editor.single-door.weight", "value", door.weight), null));

    // ★ id 只读
    inv.setItem(16, item(Material.ITEM_FRAME,
            LanguageManager.get(p, "editor.single-door.id", "value", door.id),
            List.of(LanguageManager.get(p, "editor.single-door.id-lore"))));

    // ★ connect 摘要
    if (door.connect != null && !door.connect.isEmpty()) {
        List<String> lore = new ArrayList<>();
        lore.add(LanguageManager.get(p, "editor.single-door.connect-allow",
                "count", door.connect.allow.size()));
        lore.add(LanguageManager.get(p, "editor.single-door.connect-deny",
                "count", door.connect.deny.size()));
        lore.add(LanguageManager.get(p, "editor.single-door.connect-hint"));
        inv.setItem(17, item(Material.CHAIN,
                LanguageManager.get(p, "editor.single-door.connect"), lore));
    } else {
        inv.setItem(17, item(Material.GRAY_DYE,
                LanguageManager.get(p, "editor.single-door.connect-none"),
                List.of(LanguageManager.get(p, "editor.single-door.connect-hint"))));
    }

    inv.setItem(21, item(Material.BARRIER,
            LanguageManager.get(p, "editor.single-door.delete"), null));
    inv.setItem(22, item(Material.LIME_DYE,
            LanguageManager.get(p, "editor.common.save"), null));
    inv.setItem(26, item(Material.ARROW,
            LanguageManager.get(p, "editor.common.back-list"), null));
    p.openInventory(inv);
}

    // ==================== P6a-ii 箱子列表 ====================
    public void openChestList(Player p, String dungeon, String baseName, int page) {
        List<Map<?, ?>> chests = readChestMaps(dungeon, baseName);
        String title = LanguageManager.get(p, "editor.title.chest-list", "room", baseName);
        Inventory inv = Bukkit.createInventory(new EditorHolder(P_CHEST_LIST, dungeon, baseName, page),
                54, title);
        int start = page * 36;
        for (int i = 0; i < 36 && start + i < chests.size(); i++) {
            Map<?, ?> m = chests.get(start + i);
            inv.setItem(9 + i, item(Material.CHEST,
                    LanguageManager.get(p, "editor.chest-list.entry", "index", start + i),
                    List.of(
                            LanguageManager.get(p, "editor.chest-list.entry-lore", "data", m),
                            LanguageManager.get(p, "editor.chest-list.entry-lore2"))));
        }
        inv.setItem(0, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-prev"), null));
        inv.setItem(4, item(Material.PAPER,
                LanguageManager.get(p, "editor.chest-list.page-info",
                        "page", page + 1, "total", chests.size()), null));
        inv.setItem(8, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-next"), null));
        inv.setItem(45, item(Material.LIME_DYE,
                LanguageManager.get(p, "editor.chest-list.add"),
                List.of(LanguageManager.get(p, "editor.chest-list.add-lore"))));
        inv.setItem(53, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.back-list"), null));
        p.openInventory(inv);
    }

    // ==================== P6a-ii-1 单箱子 ====================
    public void openSingleChest(Player p, String dungeon, String baseName, int index) {
        List<Map<?, ?>> chests = readChestMaps(dungeon, baseName);
        if (index < 0 || index >= chests.size()) { openChestList(p, dungeon, baseName, 0); return; }
        Map<?, ?> m = chests.get(index);
        Object pos = m.get("pos");
        Object loot = m.get("loot");
        String title = LanguageManager.get(p, "editor.title.single-chest",
                "room", baseName + "#" + index);
        Inventory inv = Bukkit.createInventory(new EditorHolder(P_SINGLE_CHEST, dungeon,
                baseName + "#" + index, 0), 27, title);
        inv.setItem(10, item(Material.PAPER,
                LanguageManager.get(p, "editor.single-chest.pos"),
                List.of("§7" + pos)));
        inv.setItem(11, item(Material.NAME_TAG,
                LanguageManager.get(p, "editor.single-chest.loot"),
                List.of("§7" + loot)));
        inv.setItem(13, item(Material.CHEST,
                LanguageManager.get(p, "editor.single-chest.replace"),
                List.of(LanguageManager.get(p, "editor.single-chest.replace-lore"))));
        inv.setItem(21, item(Material.BARRIER,
                LanguageManager.get(p, "editor.single-chest.delete"), null));
        inv.setItem(22, item(Material.LIME_DYE,
                LanguageManager.get(p, "editor.common.save"), null));
        inv.setItem(26, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.back-list"), null));
        p.openInventory(inv);
    }
private org.bukkit.util.Vector readSpawnOrCheckpoint(File f, boolean checkpoint) {
    if (f == null || !f.exists()) return null;
    YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
    String key = checkpoint ? "checkpoint" : "spawn";
    ConfigurationSection sec = y.getConfigurationSection(key);
    if (sec != null && sec.contains("x")) {
        return new org.bukkit.util.Vector(
                sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"));
    }
    // 回退旧格式（根节点直写 / 老 spawn 段）
    return YamlDoorUtil.parseSpawnPoint(f);
}
    // ==================== P6a-iii / iv 出生点 / 存档点 ====================
    public void openSpawnOrCheckpoint(Player p, String dungeon, String baseName, boolean checkpoint) {
        org.bukkit.util.Vector v = readSpawnVec(dungeon, baseName, checkpoint);
        String pid = checkpoint ? P_CHECKPOINT : P_SPAWN;
        String titleKey = checkpoint ? "editor.title.checkpoint" : "editor.title.spawn";
        String title = LanguageManager.get(p, titleKey, "room", baseName);
        Inventory inv = Bukkit.createInventory(new EditorHolder(pid, dungeon, baseName, 0), 27, title);
        inv.setItem(10, item(Material.RED_STAINED_GLASS_PANE,
                LanguageManager.get(p, "editor.coord.x",
                        "value", v == null ? "?" : v.getX()), null));
        inv.setItem(11, item(Material.GREEN_STAINED_GLASS_PANE,
                LanguageManager.get(p, "editor.coord.y",
                        "value", v == null ? "?" : v.getY()), null));
        inv.setItem(12, item(Material.BLUE_STAINED_GLASS_PANE,
                LanguageManager.get(p, "editor.coord.z",
                        "value", v == null ? "?" : v.getZ()), null));
        String overwriteKey = checkpoint
                ? "editor.checkpoint.overwrite"
                : "editor.spawn.overwrite";
        inv.setItem(13, item(Material.EMERALD,
                LanguageManager.get(p, overwriteKey), null));
        inv.setItem(22, item(Material.LIME_DYE,
                LanguageManager.get(p, "editor.common.save"), null));
        inv.setItem(26, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.back-list"), null));
        p.openInventory(inv);
    }

    // ==================== P7 脚本文件 ====================
    public void openCodeFiles(Player p, String dungeon, String relDir, int page) {
        File base = new File(plugin.getDataFolder(), dungeon + "/code");
        File cur = relDir == null || relDir.isEmpty() ? base : new File(base, relDir);
        if (!cur.exists()) cur.mkdirs();
        File[] files = cur.listFiles();
        if (files == null) files = new File[0];
        Arrays.sort(files, Comparator
                .comparing(File::isDirectory).reversed()
                .thenComparing(File::getName));
        String rel = relDir == null ? "" : relDir;
        String title = rel.isEmpty()
                ? LanguageManager.get(p, "editor.title.code-files")
                : LanguageManager.get(p, "editor.title.code-files-sub", "dir", rel);
        Inventory inv = Bukkit.createInventory(new EditorHolder(P_CODE_FILES, dungeon, rel, page),
                54, title);
        int start = page * 36;
        for (int i = 0; i < 36 && start + i < files.length; i++) {
            File f = files[start + i];
            inv.setItem(9 + i, item(f.isDirectory() ? Material.CHEST : Material.PAPER,
                    (f.isDirectory() ? "§a" : "§e") + f.getName(),
                    List.of(f.isDirectory()
                            ? LanguageManager.get(p, "editor.code-files.dir")
                            : LanguageManager.get(p, "editor.code-files.file-size",
                                    "bytes", f.length()))));
        }
        inv.setItem(0, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-prev"), null));
        inv.setItem(4, item(Material.PAPER,
                LanguageManager.get(p, "editor.common.page-info", "page", page + 1), null));
        inv.setItem(8, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-next"), null));
        inv.setItem(52, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.back-parent"), null));
        inv.setItem(53, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.back-edit"), null));
        p.openInventory(inv);
    }

    // ==================== 事件分发 ====================
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof EditorHolder h)) return;
        e.setCancelled(true);
        ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType() == Material.AIR) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        switch (h.pageId) {
            case P_MAIN -> clickMain(p, slot);
            case P_ROOT -> clickRoot(p, slot, e);
            case P_DUNGEON_LIST -> clickDungeonList(p, h, slot);
            case P_DUNGEON_EDITOR -> clickDungeonEditor(p, h, slot);
            case P_CONFIG_CAT -> clickConfigCat(p, h, slot);
            case P_CONFIG_SECTION -> clickConfigSection(p, h, slot, e);
            case P_ROOM_LIST -> clickRoomList(p, h, slot);
            case P_ROOM_CONFIG -> clickRoomConfig(p, h, slot);
            case P_DOOR_LIST -> clickDoorList(p, h, slot);
            case P_SINGLE_DOOR -> clickSingleDoor(p, h, slot, e);
            case P_CHEST_LIST -> clickChestList(p, h, slot);
            case P_SINGLE_CHEST -> clickSingleChest(p, h, slot);
            case P_SPAWN, P_CHECKPOINT -> clickSpawnOrCheckpoint(p, h, slot);
            case P_CODE_FILES -> clickCodeFiles(p, h, slot);
            case P_LIST_EDITOR -> clickListEditor(p, h, slot);
            case P_MAP_EDITOR -> clickMapEditor(p, h, slot, e);
            case P_HOOK_OVERVIEW -> clickHookOverview(p, h, slot, e);
            case P_HOOK_DETAIL   -> clickHookDetail(p, h, slot, e);
        }
    }

    // ==================== 钩子管理 ====================
    public void openHookOverview(Player p) {
        DungeonHookRegistry reg = plugin.getHookRegistry();
        int total = reg.countAll();
        String title = LanguageManager.get(p, "editor.title.hook-overview");
        Inventory inv = Bukkit.createInventory(
                new EditorHolder(P_HOOK_OVERVIEW, null, null, 0),
                54, title);

        HookType[] types = HookType.values();
        for (int i = 0; i < types.length && i < 45; i++) {
            HookType t = types[i];
            int n = reg.count(t);
            Material mat = groupMaterial(t.group);
            List<String> lore = new ArrayList<>();
            lore.add(LanguageManager.get(p, "editor.hook-overview.group", "group", t.group));
            lore.add(LanguageManager.get(p, n > 0
                    ? "editor.hook-overview.count"
                    : "editor.hook-overview.count-empty", "count", n));
            lore.add(LanguageManager.get(p, "editor.hook-overview.id", "id", t.id));
            lore.add("");
            lore.add(LanguageManager.get(p, "editor.hook-overview.click-lore"));
            if (n > 0) lore.add(LanguageManager.get(p, "editor.hook-overview.clear-type-lore"));
            inv.setItem(i, item(mat, "§e" + t.displayName(), lore));
        }

        inv.setItem(45, item(Material.TNT,
                LanguageManager.get(p, "editor.hook-overview.clear-all"),
                List.of(
                        LanguageManager.get(p, "editor.hook-overview.clear-all-lore1",
                                "total", total),
                        LanguageManager.get(p, "editor.hook-overview.clear-all-lore2"),
                        LanguageManager.get(p, "editor.hook-overview.clear-all-lore3"))));
        inv.setItem(53, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.back-main"), null));
        p.openInventory(inv);
    }

    public void openHookDetail(Player p, HookType type, int page) {
        DungeonHookRegistry reg = plugin.getHookRegistry();
        List<?> hooks = reg.getHooks(type);

        int perPage = 36;
        int maxPage = Math.max(0, (hooks.size() - 1) / perPage);
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        String title = LanguageManager.get(p, "editor.title.hook-detail",
                "name", type.displayName());
        Inventory inv = Bukkit.createInventory(
                new EditorHolder(P_HOOK_DETAIL, null, type.name(), page),
                54, title);

        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < hooks.size(); i++) {
            Object hook = hooks.get(start + i);
            List<String> lore = new ArrayList<>();
            lore.add(LanguageManager.get(p, "editor.hook-detail.index",
                    "index", start + i));
            lore.add(LanguageManager.get(p, "editor.hook-detail.class",
                    "class", hook.getClass().getName()));
            lore.add("");
            lore.add(LanguageManager.get(p, "editor.hook-detail.delete-lore"));
            inv.setItem(9 + i, item(Material.PAPER, "§e" + hookSource(hook), lore));
        }

        inv.setItem(0, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-prev-short"), null));
        inv.setItem(4, item(Material.PAPER,
                LanguageManager.get(p, "editor.hook-detail.page-info",
                        "page", page + 1,
                        "total", maxPage + 1,
                        "count", hooks.size()), null));
        inv.setItem(8, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-next-short"), null));
        inv.setItem(45, item(Material.TNT,
                LanguageManager.get(p, "editor.hook-detail.clear-type"),
                List.of(
                        LanguageManager.get(p, "editor.hook-detail.clear-type-lore1",
                                "count", hooks.size()),
                        LanguageManager.get(p, "editor.hook-detail.clear-type-lore2"))));
        inv.setItem(53, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.back-parent"), null));
        p.openInventory(inv);
    }

    private static Material groupMaterial(String group) {
        return switch (group) {
            case "L1" -> Material.LIGHT_BLUE_DYE;
            case "L2" -> Material.LIME_DYE;
            case "L3" -> Material.YELLOW_DYE;
            case "L4" -> Material.RED_DYE;
            case "L5" -> Material.PURPLE_DYE;
            default   -> Material.GRAY_DYE;
        };
    }

    private static String hookSource(Object hook) {
        String n = hook.getClass().getName();
        int i = n.indexOf("$$Lambda");
        if (i > 0) n = n.substring(0, i);
        int dot = n.lastIndexOf('.');
        return dot >= 0 ? n.substring(dot + 1) : n;
    }

    private void clickHookOverview(Player p, EditorHolder h, int slot, InventoryClickEvent e) {
        if (slot == 53) { openMain(p); return; }
        if (slot == 45) {
            if (e.isShiftClick()) {
                int n = plugin.getHookRegistry().clearAllTypes();
                MessageUtil.sendMessage(p, "editor.msg.hook-cleared-all", "count", n);
                openHookOverview(p);
            } else {
                MessageUtil.sendError(p, "editor.msg.hook-confirm-all");
            }
            return;
        }
        if (slot >= 0 && slot < HookType.values().length) {
            openHookDetail(p, HookType.values()[slot], 0);
        }
    }

    private void clickHookDetail(Player p, EditorHolder h, int slot, InventoryClickEvent e) {
        HookType type;
        try { type = HookType.valueOf(h.contextKey); }
        catch (Exception ex) { openHookOverview(p); return; }

        if (slot == 53) { openHookOverview(p); return; }
        if (slot == 0)  { openHookDetail(p, type, Math.max(0, h.pageIndex - 1)); return; }
        if (slot == 8)  { openHookDetail(p, type, h.pageIndex + 1); return; }
        if (slot == 45) {
            if (e.isShiftClick()) {
                int n = plugin.getHookRegistry().clearType(type);
                MessageUtil.sendMessage(p, "editor.msg.hook-cleared-type",
                        "type", type.displayName(), "count", n);
                openHookDetail(p, type, 0);
            } else {
                MessageUtil.sendError(p, "editor.msg.hook-confirm-type");
            }
            return;
        }
        if (slot >= 9 && slot < 45) {
            int idx = h.pageIndex * 36 + (slot - 9);
            if (plugin.getHookRegistry().removeHook(type, idx)) {
                MessageUtil.sendMessage(p, "editor.msg.hook-deleted",
                        "type", type.displayName());
            }
            openHookDetail(p, type, h.pageIndex);
        }
    }

    // ==================== 各页面点击处理 ====================
    private void clickMain(Player p, int slot) {
        if (slot == 9) openRoot(p);
        else if (slot == 10) openHookOverview(p);
        else if (slot == 11) openDungeonList(p, 0);
        else if (slot == 13) {
            p.closeInventory();
            plugin.reloadConfig();
            LanguageManager.reload();
            MessageUtil.sendMessage(p, "editor.msg.plugin-reloaded");
            openMain(p);
        } else if (slot == 15) {
            p.closeInventory();
            plugin.getStartGUI().openStartMenu(p);
        }
    }

    private void clickRoot(Player p, int slot, InventoryClickEvent e) {
        EditorSession s = session(p);
        FileConfiguration c = workingConfig(s, null);
        if (slot == 11) {
            int cur = c.getInt("worldPool.size", 2);
            int next = e.isRightClick() ? Math.max(0, cur - 1) : Math.min(10, cur + 1);
            c.set("worldPool.size", next);
            // 立即写盘，无需额外点保存
            ConfigUtil.setWorldPoolSize(next);
ConfigUtil.setWorldPoolSize(next);
MessageUtil.sendMessage(p, "editor.msg.world-pool-updated", "size", next);
            openRoot(p);
            return;
        }
        if (slot == 12) {
            boolean cur = c.getBoolean("fastPaste.enabled", false);
            c.set("fastPaste.enabled", !cur);
            ConfigUtil.saveGlobalConfig(c);   // fastPaste 是全局配置，直接落盘
            openRoot(p);
            return;
        }
        if (slot == 18) { saveWorking(p, s, null); openRoot(p); return; }
        if (slot == 25) { p.closeInventory(); plugin.getStartGUI().openStartMenu(p); return; }
        if (slot == 26) { openMain(p); return; }
        if (slot == 10) {
            Object cur = c.get("schematic.saveFormat", "sponge_v3");
            String next = "sponge_v3".equals(cur) ? "sponge_v2" : "sponge_v3";
            c.set("schematic.saveFormat", next);
            openRoot(p);
            return;
        }
        String path = switch (slot) {
            case 0 -> "lang";
            case 1 -> "msgPrefix";
            case 2 -> "scanAndRegisterNewRooms";
            case 9 -> "generateCooldown";
            default -> null;
        };
        if (path == null) return;
        if ("scanAndRegisterNewRooms".equals(path)) {
            c.set(path, !c.getBoolean(path, true));
            openRoot(p);
            return;
        }
        if ("generateCooldown".equals(path)) {
            beginInput(p,
                    LanguageManager.get(p, "editor.msg.enter-value-int", "field", path),
                    input -> {
                        try { c.set(path, Integer.parseInt(input)); }
                        catch (Exception ex) { MessageUtil.sendError(p, "editor.common.invalid-int"); }
                        openRoot(p);
                    },
                    () -> openRoot(p));
            return;
        }
        beginInput(p,
                LanguageManager.get(p, "editor.msg.enter-value", "field", path),
                input -> { c.set(path, input); openRoot(p); },
                () -> openRoot(p));
    }

    private void clickDungeonList(Player p, EditorHolder h, int slot) {
        if (slot == 0) { openDungeonList(p, Math.max(0, h.pageIndex - 1)); return; }
        if (slot == 8) { openDungeonList(p, h.pageIndex + 1); return; }
        if (slot == 52) { p.closeInventory(); plugin.getStartGUI().openStartMenu(p); return; }
        if (slot == 53) { openMain(p); return; }
        if (slot >= 9 && slot < 45) {
            ItemStack it = p.getOpenInventory().getTopInventory().getItem(slot);
            if (it != null && it.hasItemMeta() && it.getItemMeta() != null) {
                String name = ChatColor.stripColor(it.getItemMeta().getDisplayName());
                openDungeonEditor(p, name);
            }
        }
    }

    private void clickDungeonEditor(Player p, EditorHolder h, int slot) {
        String d = h.dungeonName;
        switch (slot) {
            case 9  -> beginAddRoom(p, d);
            case 10 -> beginAddDoor(p, d);
            case 11 -> beginAddChest(p, d);
            case 12 -> beginAddSpawn(p, d);
            case 13 -> beginAddCheckpoint(p, d);
            case 18 -> beginRemoveRoom(p, d);
            case 19 -> beginRemoveDoor(p, d);
            case 20 -> beginRemoveChest(p, d);
            case 21 -> beginRemoveSpawn(p, d);
            case 22 -> beginRemoveCheckpoint(p, d);
            case 27 -> openConfigCat(p, d);
            case 28 -> {
                session(p).working.remove(d);
                MessageUtil.sendMessage(p, "editor.msg.reloaded-config");
                openDungeonEditor(p, d);
            }
            case 29 -> openRoomList(p, d, 0);
            case 30 -> handleResetToDefault(p, d);
            case 31 -> { plugin.scanAndRegisterRooms(d); openDungeonEditor(p, d); }
            case 32 -> openCodeFiles(p, d, "", 0);
            case 49 -> { p.closeInventory(); plugin.generateTestWorld(d, p); }
            case 50 -> {
                p.closeInventory();
                plugin.deleteTestWorld(d);
                MessageUtil.sendMessage(p, "editor.msg.test-world-deleted");
                Bukkit.getScheduler().runTaskLater(plugin, () -> openDungeonEditor(p, d), 5L);
            }
            case 51 -> { p.closeInventory(); plugin.startDungeon(p, d, "single"); }
            case 52 -> openDungeonList(p, 0);
            case 53 -> openMain(p);
            default -> { }
        }
    }

    private void clickConfigCat(Player p, EditorHolder h, int slot) {
        String d = h.dungeonName;
        String section = switch (slot) {
            case 10 -> "root";
            case 11 -> "gen";
            case 12 -> "world";
            case 13 -> "player";
            case 14 -> "boss";
            case 15 -> "economy";
            case 16 -> "soft";
            default -> null;
        };
        if (section != null) { openConfigSection(p, d, section, 0); return; }
        if (slot == 26) openDungeonEditor(p, d);
    }

    // ==================== 通用 List 编辑器 ====================
    public void openListEditor(Player p, String dungeon, String configPath, int page) {
        openListEditor(p, dungeon, configPath, page, null);
    }

    public void openListEditor(Player p, String dungeon, String configPath, int page, String sourceSection) {
        EditorSession s = session(p);
        FileConfiguration c = workingConfig(s, dungeon);
        List<String> list = new ArrayList<>(c.getStringList(configPath));
        String title = LanguageManager.get(p, "editor.title.list", "path", configPath);
        Inventory inv = Bukkit.createInventory(
                new EditorHolder(P_LIST_EDITOR, dungeon, configPath, page, sourceSection),
                54, title);
        int start = page * 36;
        for (int i = 0; i < 36 && start + i < list.size(); i++) {
            inv.setItem(9 + i, item(Material.PAPER,
                    LanguageManager.get(p, "editor.list-editor.entry", "index", start + i),
                    List.of(
                            LanguageManager.get(p, "editor.list-editor.entry-lore",
                                    "value", list.get(start + i)),
                            LanguageManager.get(p, "editor.common.click-delete"))));
        }
        inv.setItem(0, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-prev"), null));
        inv.setItem(4, item(Material.PAPER,
                LanguageManager.get(p, "editor.common.page-info", "page", page + 1), null));
        inv.setItem(8, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-next"), null));
        inv.setItem(45, item(Material.LIME_DYE,
                LanguageManager.get(p, "editor.common.add"), null));
        inv.setItem(53, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.back-parent"), null));
        p.openInventory(inv);
    }

    // ==================== 通用 Map 编辑器 ====================
    public void openMapEditor(Player p, String dungeon, String configPath, int page) {
        openMapEditor(p, dungeon, configPath, page, null);
    }

    public void openMapEditor(Player p, String dungeon, String configPath, int page, String sourceSection) {
        EditorSession s = session(p);
        FileConfiguration c = workingConfig(s, dungeon);
        ConfigurationSection sec = c.getConfigurationSection(configPath);
        if (sec == null) sec = c.createSection(configPath);
        List<String> keys = new ArrayList<>(sec.getKeys(false));
        keys.sort(String::compareTo);
        String title = LanguageManager.get(p, "editor.title.map", "path", configPath);
        Inventory inv = Bukkit.createInventory(
                new EditorHolder(P_MAP_EDITOR, dungeon, configPath, page, sourceSection),
                54, title);
        int start = page * 36;
        for (int i = 0; i < 36 && start + i < keys.size(); i++) {
            String k = keys.get(start + i);
            Object v = sec.get(k);
            inv.setItem(9 + i, item(Material.PAPER,
                    LanguageManager.get(p, "editor.map-editor.entry", "key", k),
                    List.of(
                            LanguageManager.get(p, "editor.map-editor.entry-lore", "value", v),
                            LanguageManager.get(p, "editor.common.click-edit-delete"))));
        }
        inv.setItem(0, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-prev"), null));
        inv.setItem(4, item(Material.PAPER,
                LanguageManager.get(p, "editor.common.page-info", "page", page + 1), null));
        inv.setItem(8, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-next"), null));
        inv.setItem(45, item(Material.LIME_DYE,
                LanguageManager.get(p, "editor.common.add"), null));
        inv.setItem(53, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.back-parent"), null));
        p.openInventory(inv);
    }

    private void clickConfigSection(Player p, EditorHolder h, int slot, InventoryClickEvent e) {
        EditorSession s = session(p);
        FileConfiguration c = workingConfig(s, h.dungeonName);
        if (slot == 0) { openConfigCat(p, h.dungeonName); return; }
        if (slot == 8) {
            saveWorking(p, s, h.dungeonName);
            openConfigSection(p, h.dungeonName, h.contextKey, 0);
            return;
        }
        List<Field> fields = sectionFields().getOrDefault(h.contextKey, List.of());
        int idx = slot - 9;
        if (idx < 0 || idx >= fields.size()) return;
        Field f = fields.get(idx);
        Object v = c.get(f.path);
        String fieldName = LanguageManager.get(p, f.name);

        // ---- 0. 枚举型（三选一，循环切换）----
        if (isExplosionField(f.path)) {
            c.set(f.path, cycleExplosion(v == null ? null : v.toString()));
            openConfigSection(p, h.dungeonName, h.contextKey, 0);
            return;
        }

        // ---- 1. 白/黑名单模式：点击切换 white <-> black ----
        if (f.path.endsWith("ListType")) {
            String cur = v == null ? "white" : v.toString();
            String next = "white".equalsIgnoreCase(cur) ? "black" : "white";
            c.set(f.path, next);
            openConfigSection(p, h.dungeonName, h.contextKey, 0);
            return;
        }

        // ---- 2. 配置节（Map）----
        ConfigurationSection sec = c.getConfigurationSection(f.path);
        if (sec != null || v instanceof ConfigurationSection
                || f.path.endsWith("vault") || f.path.endsWith("playerPoints")) {
            openMapEditor(p, h.dungeonName, f.path, 0, h.contextKey);
            return;
        }

        // ---- 3. 布尔 ----
        if (v instanceof Boolean b) {
            c.set(f.path, !b);
            openConfigSection(p, h.dungeonName, h.contextKey, 0);
            return;
        }

        // ---- 4. 数字 ----
        if (v instanceof Integer || v instanceof Long) {
            beginInput(p,
                    LanguageManager.get(p, "editor.msg.enter-value-int", "field", fieldName),
                    in -> {
                        try { c.set(f.path, Long.parseLong(in)); }
                        catch (Exception ex) { MessageUtil.sendError(p, "editor.common.invalid-int"); }
                        openConfigSection(p, h.dungeonName, h.contextKey, 0);
                    },
                    () -> openConfigSection(p, h.dungeonName, h.contextKey, 0));
            return;
        }

        // ---- 5. 列表 ----
        if (v instanceof List || f.path.endsWith("Allow")
                || f.path.endsWith("Settings") || f.path.endsWith("Mobs")
                || f.path.endsWith("bossType") || f.path.endsWith("bossNbt")
                || f.path.endsWith("GenerateBlocks")
                || f.path.endsWith("consoleCommands")) {
            openListEditor(p, h.dungeonName, f.path, 0, h.contextKey);
            return;
        }

        // ---- 6. 字符串 / 兜底 ----
        beginInput(p,
                LanguageManager.get(p, "editor.msg.enter-value", "field", fieldName),
                in -> { c.set(f.path, in); openConfigSection(p, h.dungeonName, h.contextKey, 0); },
                () -> openConfigSection(p, h.dungeonName, h.contextKey, 0));
    }

    private void clickRoomList(Player p, EditorHolder h, int slot) {
        if (slot == 0) { openRoomList(p, h.dungeonName, Math.max(0, h.pageIndex - 1)); return; }
        if (slot == 8) { openRoomList(p, h.dungeonName, h.pageIndex + 1); return; }
        if (slot == 52) { openDungeonEditor(p, h.dungeonName); return; }
        if (slot == 53) { openMain(p); return; }
        if (slot >= 9 && slot < 45) {
            ItemStack it = p.getOpenInventory().getTopInventory().getItem(slot);
            if (it != null && it.getItemMeta() != null) {
                String key = ChatColor.stripColor(it.getItemMeta().getDisplayName());
                openRoomConfig(p, h.dungeonName, key);
            }
        }
    }

    private void clickRoomConfig(Player p, EditorHolder h, int slot) {
        String d = h.dungeonName, base = h.contextKey;
        switch (slot) {
            case 0 -> {
                EditorSession s = session(p);
                FileConfiguration c = workingConfig(s, d);
                beginInput(p,
                        LanguageManager.get(p, "editor.msg.enter-weight"),
                        in -> {
                            try {
                                c.set("dungeonInstance.structureChoose." + base, Integer.parseInt(in));
                                saveWorking(p, s, d);
                            } catch (Exception ex) { }
                            openRoomConfig(p, d, base);
                        },
                        () -> openRoomConfig(p, d, base));
            }
            case 1 -> {
                // 编辑 type：直接输入字符串
                beginInput(p,
                    LanguageManager.get(p, "editor.msg.enter-room-type"),
                    in -> { writeRoomType(d, base, in.trim()); openRoomConfig(p, d, base); },
                    () -> openRoomConfig(p, d, base));
                }
            case 2 -> toggleUse(p, d, base, "inStart");
            case 3 -> toggleUse(p, d, base, "inMainPath");
            case 4 -> toggleUse(p, d, base, "inBranch");
            case 5 -> toggleUse(p, d, base, "inBoss");
            case 6 -> toggleUse(p, d, base, "inFallback");
            case 9  -> openDoorList(p, d, base, 0);
            case 10 -> openChestList(p, d, base, 0);
            case 11 -> openSpawnOrCheckpoint(p, d, base, false);
            case 12 -> openSpawnOrCheckpoint(p, d, base, true);
            case 26 -> openRoomList(p, d, 0);
        }
    }
/** 单文件写 type；若文件不存在则创建（不破坏旧 _door 等文件） */
private void writeRoomType(String dungeon, String baseName, String type) {
    File f = roomFileSingle(dungeon, baseName);
    YamlConfiguration y = f.exists()
            ? YamlConfiguration.loadConfiguration(f)
            : new YamlConfiguration();
    y.set("type", type);
    try {
        f.getParentFile().mkdirs();
        y.save(f);
        RoomConfigReader.invalidate(dungeon);
    } catch (Exception ignored) {}
}

/** 切换 use 开关 */
private void toggleUse(Player p, String dungeon, String baseName, String field) {
    File f = roomFileSingle(dungeon, baseName);
    YamlConfiguration y = f.exists()
            ? YamlConfiguration.loadConfiguration(f)
            : new YamlConfiguration();
    boolean cur = y.getBoolean("use." + field, true);
    y.set("use." + field, !cur);
    try {
        f.getParentFile().mkdirs();
        y.save(f);
        RoomConfigReader.invalidate(dungeon);
    } catch (Exception ignored) {}
    openRoomConfig(p, dungeon, baseName);
}

private File roomFileSingle(String dungeon, String baseName) {
    File rooms = new File(plugin.getDataFolder(), dungeon + "/rooms");
    return new File(rooms, baseName.replace('/', File.separatorChar) + ".yml");
}
    private void clickDoorList(Player p, EditorHolder h, int slot) {
        String d = h.dungeonName, base = h.contextKey;
        if (slot == 0) { openDoorList(p, d, base, Math.max(0, h.pageIndex - 1)); return; }
        if (slot == 8) { openDoorList(p, d, base, h.pageIndex + 1); return; }
        if (slot == 45) {
            org.bukkit.block.Block target = p.getTargetBlockExact(5);
            if (target == null) { MessageUtil.sendError(p, "editor.msg.target-block"); return; }
            int[] origin = originOf(p);
            if (origin == null) { MessageUtil.sendError(p, "editor.msg.need-selection-framing"); return; }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("x", target.getX() - origin[0]);
            entry.put("y", target.getY() - origin[1]);
            entry.put("z", target.getZ() - origin[2]);
            entry.put("facing", playerFacing(p));
            entry.put("type", "default");
            entry.put("weight", 10);
List<Map<?, ?>> doorList = new ArrayList<>(readDoorMaps(d, base));
doorList.add(entry);
writeDoorMaps(d, base, doorList);
            RoomConfigReader.invalidate(d);
            openDoorList(p, d, base, h.pageIndex);
            return;
        }
        if (slot == 53) { openRoomConfig(p, d, base); return; }
        if (slot >= 9 && slot < 45) openSingleDoor(p, d, base, h.pageIndex * 36 + (slot - 9));
    }

    private void clickSingleDoor(Player p, EditorHolder h, int slot, InventoryClickEvent e) {
        String[] parts = h.contextKey.split("#");
        String d = h.dungeonName, base = parts[0];
        int idx = Integer.parseInt(parts[1]);
        List<Map<?, ?>> doors = readDoorMaps(d, base);
        if (idx < 0 || idx >= doors.size()) { openDoorList(p, d, base, 0); return; }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = new LinkedHashMap<>((Map<String, Object>) doors.get(idx));

        switch (slot) {
            case 10, 11, 12 -> {
                String key = slot == 10 ? "x" : slot == 11 ? "y" : "z";
                beginInput(p,
                        LanguageManager.get(p, "editor.msg.enter-value-int", "field", key),
                        in -> {
                            try { m.put(key, Integer.parseInt(in)); saveDoorAt(d, base, idx, m); }
                            catch (Exception ex) { }
                            openSingleDoor(p, d, base, idx);
                        },
                        () -> openSingleDoor(p, d, base, idx));
            }
            case 13 -> {
                String[] order = {"x+", "z+", "x-", "z-", "y+", "y-"};
                String cur = String.valueOf(m.getOrDefault("facing", "x+"));
                int i = 0;
                for (int j = 0; j < order.length; j++) if (order[j].equals(cur)) { i = (j + 1) % order.length; break; }
                m.put("facing", order[i]);
                saveDoorAt(d, base, idx, m);
                openSingleDoor(p, d, base, idx);
            }
            case 14 -> {
                String[] order = {"default", "stairs", "fallback", "door", "arch"};
                String cur = String.valueOf(m.getOrDefault("type", "default"));
                int i = 0;
                for (int j = 0; j < order.length; j++) if (order[j].equals(cur)) { i = (j + 1) % order.length; break; }
                m.put("type", order[i]);
                saveDoorAt(d, base, idx, m);
                openSingleDoor(p, d, base, idx);
            }
            case 15 -> beginInput(p,
                    LanguageManager.get(p, "editor.msg.enter-weight"),
                    in -> {
                        try { m.put("weight", Integer.parseInt(in)); saveDoorAt(d, base, idx, m); }
                        catch (Exception ex) { }
                        openSingleDoor(p, d, base, idx);
                    },
                    () -> openSingleDoor(p, d, base, idx));
            case 21 -> {
List<Map<?, ?>> list = new ArrayList<>(readDoorMaps(d, base));
list.remove(idx);
writeDoorMaps(d, base, list);
            }
            case 26 -> openDoorList(p, d, base, 0);
        }
    }
private void saveDoorAt(String d, String base, int idx, Map<String, Object> m) {
    List<Map<?, ?>> list = new ArrayList<>(readDoorMaps(d, base));
    if (idx < 0 || idx >= list.size()) return;
    list.set(idx, m);
    writeDoorMaps(d, base, list);
}

    private void clickChestList(Player p, EditorHolder h, int slot) {
        String d = h.dungeonName, base = h.contextKey;
        if (slot == 0) { openChestList(p, d, base, Math.max(0, h.pageIndex - 1)); return; }
        if (slot == 8) { openChestList(p, d, base, h.pageIndex + 1); return; }
        if (slot == 45) {
            org.bukkit.block.Block target = p.getTargetBlockExact(5);
            if (target == null) { MessageUtil.sendError(p, "editor.msg.target-block"); return; }
            int[] origin = originOf(p);
            if (origin == null) { MessageUtil.sendError(p, "editor.msg.need-selection-framing"); return; }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("pos", Arrays.asList(
                    target.getX() - origin[0],
                    target.getY() - origin[1],
                    target.getZ() - origin[2]));
            entry.put("loot", "50 DIAMOND 1");
List<Map<?, ?>> chestList = new ArrayList<>(readChestMaps(d, base));
chestList.add(entry);
writeChestMaps(d, base, chestList);
            openChestList(p, d, base, h.pageIndex);
            return;
        }
        if (slot == 53) { openRoomConfig(p, d, base); return; }
        if (slot >= 9 && slot < 45) openSingleChest(p, d, base, h.pageIndex * 36 + (slot - 9));
    }

    private void clickSingleChest(Player p, EditorHolder h, int slot) {
        String[] parts = h.contextKey.split("#");
        String d = h.dungeonName, base = parts[0];
        int idx = Integer.parseInt(parts[1]);
List<Map<?, ?>> chests = new ArrayList<>(readChestMaps(d, base));
        if (idx < 0 || idx >= chests.size()) { openChestList(p, d, base, 0); return; }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = new LinkedHashMap<>((Map<String, Object>) chests.get(idx));
        switch (slot) {
            case 13 -> {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) {
                    MessageUtil.sendError(p, "editor.msg.chest-need-hand");
                    return;
                }
                m.put("loot", "50 " + hand.getType().name() + " " + hand.getAmount());
                chests.set(idx, m);
chests.set(idx, m);
writeChestMaps(d, base, chests);
                openSingleChest(p, d, base, idx);
            }
            case 21 -> {
List<Map<?, ?>> list = new ArrayList<>(chests);
list.remove(idx);
writeChestMaps(d, base, list);
                openChestList(p, d, base, 0);
            }
            case 26 -> openChestList(p, d, base, 0);
        }
        RoomConfigReader.invalidate(d);   
    }

private void clickSpawnOrCheckpoint(Player p, EditorHolder h, int slot) {
    boolean cp = P_CHECKPOINT.equals(h.pageId);
    String d = h.dungeonName, base = h.contextKey;

    if (slot == 13) {
        int[] origin = originOf(p);
        if (origin == null) { MessageUtil.sendError(p, "editor.msg.need-selection-framing"); return; }
        Location loc = p.getLocation();
        writeSpawnVec(d, base,
                loc.getBlockX() - origin[0],
                loc.getBlockY() - origin[1],
                loc.getBlockZ() - origin[2],
                cp);
        MessageUtil.sendMessage(p, "editor.msg.coord-updated");
        openSpawnOrCheckpoint(p, d, base, cp);
        return;
    }
    if (slot == 26) { openRoomConfig(p, d, base); return; }
}

    private void clickCodeFiles(Player p, EditorHolder h, int slot) {
        String d = h.dungeonName;
        String rel = h.contextKey == null ? "" : h.contextKey;
        if (slot == 0) { openCodeFiles(p, d, rel, Math.max(0, h.pageIndex - 1)); return; }
        if (slot == 8) { openCodeFiles(p, d, rel, h.pageIndex + 1); return; }
        if (slot == 52) {
            if (rel.isEmpty()) openDungeonEditor(p, d);
            else openCodeFiles(p, d, rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/')) : "", 0);
            return;
        }
        if (slot == 53) { openDungeonEditor(p, d); return; }
        if (slot >= 9 && slot < 45) {
            ItemStack it = p.getOpenInventory().getTopInventory().getItem(slot);
            if (it == null || it.getItemMeta() == null) return;
            String name = ChatColor.stripColor(it.getItemMeta().getDisplayName());
            String newRel = rel.isEmpty() ? name : rel + "/" + name;
            File f = new File(plugin.getDataFolder(), d + "/code/" + newRel);
            if (f.isDirectory()) {
                openCodeFiles(p, d, newRel, 0);
            } else {
                p.closeInventory();
                try {
                    List<String> lines = java.nio.file.Files.readAllLines(f.toPath());
                    MessageUtil.sendMessage(p, "editor.code-files.file-preview",
                            "file", newRel, "lines", lines.size());
                    for (int i = 0; i < Math.min(20, lines.size()); i++) p.sendMessage("§7" + lines.get(i));
                    if (lines.size() > 20) p.sendMessage(LanguageManager.get(p, "editor.msg.code-truncated"));
                } catch (Exception ex) {
                    MessageUtil.sendError(p, "editor.msg.file-read-fail", "error", ex.getMessage());
                }
            }
        }
    }

    private void clickListEditor(Player p, EditorHolder h, int slot) {
        EditorSession s = session(p);
        FileConfiguration c = workingConfig(s, h.dungeonName);
        List<String> list = new ArrayList<>(c.getStringList(h.contextKey));
        String back = h.returnSection != null ? h.returnSection : "player";

        if (slot == 0) {
            openListEditor(p, h.dungeonName, h.contextKey,
                    Math.max(0, h.pageIndex - 1), h.returnSection);
            return;
        }
        if (slot == 8) {
            openListEditor(p, h.dungeonName, h.contextKey,
                    h.pageIndex + 1, h.returnSection);
            return;
        }
        if (slot == 45) {
            beginInput(p,
                    LanguageManager.get(p, "editor.msg.enter-value", "field", h.contextKey),
                    in -> {
                        list.add(in);
                        c.set(h.contextKey, list);
                        openListEditor(p, h.dungeonName, h.contextKey,
                                h.pageIndex, h.returnSection);
                    },
                    () -> openListEditor(p, h.dungeonName, h.contextKey,
                            h.pageIndex, h.returnSection));
            return;
        }
        if (slot == 53) { openConfigSection(p, h.dungeonName, back, 0); return; }
        if (slot >= 9 && slot < 45) {
            int idx = h.pageIndex * 36 + (slot - 9);
            if (idx < list.size()) {
                list.remove(idx);
                c.set(h.contextKey, list);
                openListEditor(p, h.dungeonName, h.contextKey,
                        h.pageIndex, h.returnSection);
            }
        }
    }

    private void clickMapEditor(Player p, EditorHolder h, int slot, InventoryClickEvent e) {
        EditorSession s = session(p);
        FileConfiguration c = workingConfig(s, h.dungeonName);
        ConfigurationSection sec = c.getConfigurationSection(h.contextKey);
        if (sec == null) return;
        String back = h.returnSection != null ? h.returnSection : "economy";

        if (slot == 0) {
            openMapEditor(p, h.dungeonName, h.contextKey,
                    Math.max(0, h.pageIndex - 1), h.returnSection);
            return;
        }
        if (slot == 8) {
            openMapEditor(p, h.dungeonName, h.contextKey,
                    h.pageIndex + 1, h.returnSection);
            return;
        }
        if (slot == 45) {
            beginInput(p,
                    LanguageManager.get(p, "editor.msg.enter-key-value"),
                    in -> {
                        String[] kv = in.split("=", 2);
                        if (kv.length == 2) {
                            try { sec.set(kv[0], Double.parseDouble(kv[1])); }
                            catch (Exception ex) { sec.set(kv[0], kv[1]); }
                        }
                        openMapEditor(p, h.dungeonName, h.contextKey,
                                h.pageIndex, h.returnSection);
                    },
                    () -> openMapEditor(p, h.dungeonName, h.contextKey,
                            h.pageIndex, h.returnSection));
            return;
        }
        if (slot == 53) { openConfigSection(p, h.dungeonName, back, 0); return; }
        if (slot >= 9 && slot < 45) {
            ItemStack it = p.getOpenInventory().getTopInventory().getItem(slot);
            if (it == null || it.getItemMeta() == null) return;
            String key = ChatColor.stripColor(it.getItemMeta().getDisplayName());
            if (e.isRightClick()) {
                sec.set(key, null);
                openMapEditor(p, h.dungeonName, h.contextKey, h.pageIndex, h.returnSection);
            } else {
                beginInput(p,
                        LanguageManager.get(p, "editor.msg.enter-new-value", "key", key),
                        in -> {
                            try { sec.set(key, Double.parseDouble(in)); }
                            catch (Exception ex) { sec.set(key, in); }
                            openMapEditor(p, h.dungeonName, h.contextKey,
                                    h.pageIndex, h.returnSection);
                        },
                        () -> openMapEditor(p, h.dungeonName, h.contextKey,
                                h.pageIndex, h.returnSection));
            }
        }
    }

    // ==================== YAML 工具 ====================
    private List<Map<?, ?>> readYamlList(File f, String root) {
        if (!f.exists()) return new ArrayList<>();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<?> list = y.getList(root);
        if (list == null) list = y.getList("");
        List<Map<?, ?>> out = new ArrayList<>();
        if (list != null) for (Object o : list) if (o instanceof Map) out.add((Map<?, ?>) o);
        return out;
    }

    private void writeYamlList(File f, String root, List<?> list) {
        YamlConfiguration y = f.exists()
                ? YamlConfiguration.loadConfiguration(f)
                : new YamlConfiguration();
        y.set(root, list);
        try {
            f.getParentFile().mkdirs();
            y.save(f);
        } catch (Exception ignored) { }
    }

    private void appendYamlList(File f, String root, Map<String, Object> entry) {
        List<Map<?, ?>> list = new ArrayList<>(readYamlList(f, root));
        list.add(entry);
        writeYamlList(f, root, list);
    }

    private int[] originOf(Player p) {
        SelectionManager.Selection sel = SelectionManager.getSelection(p.getUniqueId());
        if (sel == null) return null;
        return new int[]{sel.minX, sel.minY, sel.minZ};
    }

    private String playerFacing(Player p) {
        float yaw = p.getLocation().getYaw();
        yaw = ((yaw % 360) + 360) % 360;
        if (yaw >= 315 || yaw < 45) return "z+";
        if (yaw < 135) return "x-";
        if (yaw < 225) return "z-";
        return "x+";
    }

    // ==================== 添加 / 移除 ====================
    private void beginAddRoom(Player p, String dungeon) {
        SelectionManager.Selection sel = SelectionManager.getSelection(p.getUniqueId());
        if (sel == null) { MessageUtil.sendError(p, "editor.msg.need-selection"); return; }
        beginInput(p,
                LanguageManager.get(p, "editor.msg.enter-room-base"),
                in -> {
                    String baseName = in.trim();
                    if (baseName.isEmpty()) { openDungeonEditor(p, dungeon); return; }
                    File roomsDir = new File(plugin.getDataFolder(), dungeon + "/rooms");
                    String path = baseName.replace('/', File.separatorChar);
                    File schematicFile = new File(roomsDir, path + ".schem");
                    schematicFile.getParentFile().mkdirs();
                    int saveFormat = ConfigUtil.getSchematicSaveFormat();
                    com.him.dungeons.schematic.AsyncSchematicSaver.saveAsync(
                            sel, schematicFile, saveFormat, success -> {
                                if (success) {
                                    MessageUtil.sendMessage(p, "editor.msg.schematic-saved",
                                            "path", schematicFile.getPath());
                                    ConfigUtil.refreshStructureWeights(dungeon);
                                } else {
                                    MessageUtil.sendError(p, "editor.msg.schematic-save-fail");
                                }
                                openDungeonEditor(p, dungeon);
                            });
                },
                () -> openDungeonEditor(p, dungeon));
    }

private void beginAddDoor(Player p, String dungeon) {
    org.bukkit.block.Block target = p.getTargetBlockExact(5);
    if (target == null) { MessageUtil.sendError(p, "editor.msg.target-block"); return; }
    int[] origin = originOf(p);
    if (origin == null) { MessageUtil.sendError(p, "editor.msg.need-selection-framing"); return; }
    int relX = target.getX() - origin[0];
    int relY = target.getY() - origin[1];
    int relZ = target.getZ() - origin[2];
    beginInput(p,
            LanguageManager.get(p, "editor.msg.enter-door"),
            in -> {
                String[] parts = in.trim().split("\\s+");
                if (parts.length < 2) {
                    MessageUtil.sendError(p, "editor.msg.format-error");
                    openDungeonEditor(p, dungeon);
                    return;
                }
                String baseName = parts[0];
                String facing = parts[1];
                String type = parts.length >= 3 ? parts[2] : "default";
                int weight = 10;
                if (parts.length >= 4) {
                    try { weight = Integer.parseInt(parts[3]); } catch (Exception ignored) { }
                }
                if (!com.him.dungeons.util.TransformUtil.isValidFacing(facing)) {
                    MessageUtil.sendError(p, "editor.msg.invalid-facing");
                    openDungeonEditor(p, dungeon);
                    return;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("x", relX);
                entry.put("y", relY);
                entry.put("z", relZ);
                entry.put("facing", facing);
                entry.put("type", type);
                entry.put("weight", weight);
                List<Map<?, ?>> list = new ArrayList<>(readDoorMaps(dungeon, baseName));
int nextId = 0;
for (Map<?, ?> m : list) {
    Object raw = m.get("id");
    if (raw instanceof Number n) nextId = Math.max(nextId, n.intValue() + 1);
}
entry.put("id", nextId);
                list.add(entry);
                writeDoorMaps(dungeon, baseName, list);
                MessageUtil.sendMessage(p, "editor.msg.door-added",
                        "path", roomFileSingle(dungeon, baseName).getPath(),
                        "type", type,
                        "weight", weight);
                openDungeonEditor(p, dungeon);
            },
            () -> openDungeonEditor(p, dungeon));
}

    private void beginAddChest(Player p, String dungeon) {
        org.bukkit.block.Block target = p.getTargetBlockExact(5);
        if (target == null) { MessageUtil.sendError(p, "editor.msg.target-block"); return; }
        int[] origin = originOf(p);
        if (origin == null) { MessageUtil.sendError(p, "editor.msg.need-selection-framing"); return; }
        ChestAddSession cs = new ChestAddSession(dungeon,
                target.getX() - origin[0],
                target.getY() - origin[1],
                target.getZ() - origin[2]);
        chestAddSessions.put(p.getUniqueId(), cs);
        p.closeInventory();
        MessageUtil.sendMessage(p, "editor.msg.chest-path");
        p.sendMessage(LanguageManager.getPrefix()
                + LanguageManager.get(p, "editor.msg.chest-hint"));
    }

private void beginAddSpawn(Player p, String dungeon) {
    int[] origin = originOf(p);
    if (origin == null) { MessageUtil.sendError(p, "editor.msg.need-selection-framing"); return; }
    Location loc = p.getLocation();
    int relX = loc.getBlockX() - origin[0];
    int relY = loc.getBlockY() - origin[1];
    int relZ = loc.getBlockZ() - origin[2];
    beginInput(p,
            LanguageManager.get(p, "editor.msg.enter-start-path"),
            in -> {
                String baseName = in.trim();
                File f = roomFileSingle(dungeon, baseName);
                f.getParentFile().mkdirs();
                YamlConfiguration yaml = f.exists()
                        ? YamlConfiguration.loadConfiguration(f)
                        : new YamlConfiguration();
                yaml.set("spawn.x", relX);
                yaml.set("spawn.y", relY);
                yaml.set("spawn.z", relZ);
                try {
                    yaml.save(f);
                    RoomConfigReader.invalidate(dungeon);
                    MessageUtil.sendMessage(p, "editor.msg.spawn-saved",
                            "path", f.getPath());
                } catch (Exception ex) {
                    MessageUtil.sendError(p, "editor.msg.save-fail-generic",
                            "error", ex.getMessage());
                }
                openDungeonEditor(p, dungeon);
            },
            () -> openDungeonEditor(p, dungeon));
}

private void beginAddCheckpoint(Player p, String dungeon) {
    int[] origin = originOf(p);
    if (origin == null) { MessageUtil.sendError(p, "editor.msg.need-selection-framing"); return; }
    Location loc = p.getLocation();
    int relX = loc.getBlockX() - origin[0];
    int relY = loc.getBlockY() - origin[1];
    int relZ = loc.getBlockZ() - origin[2];
    beginInput(p,
            LanguageManager.get(p, "editor.msg.enter-room-path"),
            in -> {
                String baseName = in.trim();
                File f = roomFileSingle(dungeon, baseName);
                f.getParentFile().mkdirs();
                YamlConfiguration yaml = f.exists()
                        ? YamlConfiguration.loadConfiguration(f)
                        : new YamlConfiguration();
                yaml.set("checkpoint.x", relX);
                yaml.set("checkpoint.y", relY);
                yaml.set("checkpoint.z", relZ);
                try {
                    yaml.save(f);
                    RoomConfigReader.invalidate(dungeon);
                    MessageUtil.sendMessage(p, "editor.msg.checkpoint-saved",
                            "path", f.getPath());
                } catch (Exception ex) {
                    MessageUtil.sendError(p, "editor.msg.save-fail-generic",
                            "error", ex.getMessage());
                }
                openDungeonEditor(p, dungeon);
            },
            () -> openDungeonEditor(p, dungeon));
}

    private void beginRemoveRoom(Player p, String dungeon) {
        beginInput(p,
                LanguageManager.get(p, "editor.msg.enter-delete-room"),
                in -> {
                    String baseName = in.trim();
                    File roomsDir = new File(plugin.getDataFolder(), dungeon + "/rooms");
                    String path = baseName.replace('/', File.separatorChar);
                    File roomFile = new File(roomsDir, path + ".schem");
                    if (!roomFile.exists()) {
                        MessageUtil.sendError(p, "editor.msg.no-room");
                        openDungeonEditor(p, dungeon);
                        return;
                    }
                    roomFile.delete();
                    new File(roomsDir, path + ".yml").delete(); 
                    new File(roomsDir, path + "_door.yml").delete();
                    new File(roomsDir, path + "_chest.yml").delete();
                    new File(roomsDir, path + "_spawn.yml").delete();
                    new File(roomsDir, path + "_checkpoint.yml").delete();
                    MessageUtil.sendMessage(p, "editor.msg.room-deleted", "room", baseName);
                    ConfigUtil.refreshStructureWeights(dungeon);
                    openDungeonEditor(p, dungeon);
                },
                () -> openDungeonEditor(p, dungeon));
        RoomConfigReader.invalidate(dungeon);   
    }

private void beginRemoveDoor(Player p, String dungeon) {
    beginInput(p,
            LanguageManager.get(p, "editor.msg.enter-room-path"),
            in -> {
                String baseName = in.trim();
                File single = roomFileSingle(dungeon, baseName);
                File legacy = roomFile(dungeon, baseName, "_door.yml");
                boolean any = false;
                if (single.exists()) {
                    YamlConfiguration y = YamlConfiguration.loadConfiguration(single);
                    if (y.contains("doors")) {
                        y.set("doors", null);
                        try { y.save(single); any = true; } catch (Exception ignored) {}
                    }
                }
                if (legacy.exists()) { legacy.delete(); any = true; }
                if (!any) MessageUtil.sendError(p, "editor.msg.no-door-file");
                else {
                    MessageUtil.sendMessage(p, "editor.msg.door-deleted");
                    RoomConfigReader.invalidate(dungeon);
                }
                openDungeonEditor(p, dungeon);
            },
            () -> openDungeonEditor(p, dungeon));
}

private void saveQuiet(YamlConfiguration y, File f) {
    try { f.getParentFile().mkdirs(); y.save(f); } catch (Exception ignored) {}
}
private void beginRemoveChest(Player p, String dungeon) {
    beginInput(p,
            LanguageManager.get(p, "editor.msg.enter-room-path"),
            in -> {
                String baseName = in.trim();
                File single = roomFileSingle(dungeon, baseName);
                File legacy = roomFile(dungeon, baseName, "_chest.yml");
                boolean any = false;
                if (single.exists()) {
                    YamlConfiguration y = YamlConfiguration.loadConfiguration(single);
                    if (y.contains("chests")) {
                        y.set("chests", null);
                        try { y.save(single); any = true; } catch (Exception ignored) {}
                    }
                }
                if (legacy.exists()) { legacy.delete(); any = true; }
                if (!any) MessageUtil.sendError(p, "editor.msg.no-chest-file");
                else {
                    MessageUtil.sendMessage(p, "editor.msg.chest-deleted");
                    RoomConfigReader.invalidate(dungeon);
                }
                openDungeonEditor(p, dungeon);
            },
            () -> openDungeonEditor(p, dungeon));
}
private void beginRemoveSpawn(Player p, String dungeon) {
    beginInput(p,
            LanguageManager.get(p, "editor.msg.enter-room-path"),
            in -> {
                String baseName = in.trim();
                File single = roomFileSingle(dungeon, baseName);
                File legacy = roomFile(dungeon, baseName, "_spawn.yml");
                boolean any = false;
                if (single.exists()) {
                    YamlConfiguration y = YamlConfiguration.loadConfiguration(single);
                    if (y.contains("spawn")) {
                        y.set("spawn", null);
                        try { y.save(single); any = true; } catch (Exception ignored) {}
                    }
                }
                if (legacy.exists()) { legacy.delete(); any = true; }
                if (!any) MessageUtil.sendError(p, "editor.msg.no-spawn-file");
                else {
                    MessageUtil.sendMessage(p, "editor.msg.spawn-deleted");
                    RoomConfigReader.invalidate(dungeon);
                }
                openDungeonEditor(p, dungeon);
            },
            () -> openDungeonEditor(p, dungeon));
}
private void beginRemoveCheckpoint(Player p, String dungeon) {
    beginInput(p,
            LanguageManager.get(p, "editor.msg.enter-room-path"),
            in -> {
                String baseName = in.trim();
                File single = roomFileSingle(dungeon, baseName);
                File legacy = roomFile(dungeon, baseName, "_checkpoint.yml");
                boolean any = false;
                if (single.exists()) {
                    YamlConfiguration y = YamlConfiguration.loadConfiguration(single);
                    if (y.contains("checkpoint")) {
                        y.set("checkpoint", null);
                        try { y.save(single); any = true; } catch (Exception ignored) {}
                    }
                }
                if (legacy.exists()) { legacy.delete(); any = true; }
                if (!any) MessageUtil.sendError(p, "editor.msg.no-checkpoint-file");
                else {
                    MessageUtil.sendMessage(p, "editor.msg.checkpoint-deleted");
                    RoomConfigReader.invalidate(dungeon);
                }
                openDungeonEditor(p, dungeon);
            },
            () -> openDungeonEditor(p, dungeon));
}

/** 门：优先 <base>.yml 的 doors 段，缺失时回退 _door.yml */
private List<Map<?, ?>> readDoorMaps(String dungeon, String baseName) {
    File rooms = new File(plugin.getDataFolder(), dungeon + "/rooms");
    String rel = baseName.replace('/', File.separatorChar);
    File single = new File(rooms, rel + ".yml");

    if (single.exists()) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(single);
        if (y.contains("doors")) {
            List<?> list = y.getList("doors");
            return toMapList(list);
        }
    }
    File legacy = new File(rooms, rel + "_door.yml");
    if (legacy.exists()) {
        YamlConfiguration ly = YamlConfiguration.loadConfiguration(legacy);
        List<?> list = ly.getList("doors");
        if (list == null) list = ly.getList("");
        return toMapList(list);
    }
    return new ArrayList<>();
}

/** 门：永远写 <base>.yml */
private void writeDoorMaps(String dungeon, String baseName, List<?> list) {
    File f = roomFileSingle(dungeon, baseName);
    YamlConfiguration y = f.exists()
            ? YamlConfiguration.loadConfiguration(f)
            : new YamlConfiguration();
    y.set("doors", list);
    try { f.getParentFile().mkdirs(); y.save(f); } catch (Exception ignored) {}
    RoomConfigReader.invalidate(dungeon);
}

/** 箱子：优先 <base>.yml 的 chests 段，缺失时回退 _chest.yml */
private List<Map<?, ?>> readChestMaps(String dungeon, String baseName) {
    File rooms = new File(plugin.getDataFolder(), dungeon + "/rooms");
    String rel = baseName.replace('/', File.separatorChar);
    File single = new File(rooms, rel + ".yml");

    if (single.exists()) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(single);
        if (y.contains("chests")) {
            List<?> list = y.getList("chests");
            return toMapList(list);
        }
    }
    File legacy = new File(rooms, rel + "_chest.yml");
    if (legacy.exists()) {
        YamlConfiguration ly = YamlConfiguration.loadConfiguration(legacy);
        List<?> list = ly.getList("chests");
        if (list == null) list = ly.getList("");
        return toMapList(list);
    }
    return new ArrayList<>();
}

/** 箱子：永远写 <base>.yml */
private void writeChestMaps(String dungeon, String baseName, List<?> list) {
    File f = roomFileSingle(dungeon, baseName);
    YamlConfiguration y = f.exists()
            ? YamlConfiguration.loadConfiguration(f)
            : new YamlConfiguration();
    y.set("chests", list);
    try { f.getParentFile().mkdirs(); y.save(f); } catch (Exception ignored) {}
    RoomConfigReader.invalidate(dungeon);
}

/** 出生点/存档点：读 <base>.yml 对应段，缺失回退旧文件 */
private org.bukkit.util.Vector readSpawnVec(String dungeon, String baseName, boolean checkpoint) {
    File rooms = new File(plugin.getDataFolder(), dungeon + "/rooms");
    String rel = baseName.replace('/', File.separatorChar);
    File single = new File(rooms, rel + ".yml");
    String key = checkpoint ? "checkpoint" : "spawn";

    if (single.exists()) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(single);
        ConfigurationSection sec = y.getConfigurationSection(key);
        if (sec != null && sec.contains("x")) {
            return new org.bukkit.util.Vector(
                    sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"));
        }
    }
    String suffix = checkpoint ? "_checkpoint.yml" : "_spawn.yml";
    File legacy = new File(rooms, rel + suffix);
    if (legacy.exists()) {
        return YamlDoorUtil.parseSpawnPoint(legacy);
    }
    return null;
}

/** 出生点/存档点：写 <base>.yml 对应段（checkpoint 写 checkpoint 段，spawn 写 spawn 段） */
private void writeSpawnVec(String dungeon, String baseName,
                           int relX, int relY, int relZ, boolean checkpoint) {
    File f = roomFileSingle(dungeon, baseName);
    YamlConfiguration y = f.exists()
            ? YamlConfiguration.loadConfiguration(f)
            : new YamlConfiguration();
    String key = checkpoint ? "checkpoint" : "spawn";
    y.set(key + ".x", relX);
    y.set(key + ".y", relY);
    y.set(key + ".z", relZ);
    try { f.getParentFile().mkdirs(); y.save(f); } catch (Exception ignored) {}
    RoomConfigReader.invalidate(dungeon);
}

private List<Map<?, ?>> toMapList(List<?> list) {
    List<Map<?, ?>> out = new ArrayList<>();
    if (list != null) for (Object o : list) if (o instanceof Map) out.add((Map<?, ?>) o);
    return out;
}

/** 显示实际数据文件（迁移后是 <base>.yml，未迁移是 legacy） */
private String displayFile(String dungeon, String baseName, String legacySuffix) {
    File f = yamlFile(dungeon, baseName, legacySuffix);
    return f.exists() ? f.getName() : baseName + ".yml";
}
}