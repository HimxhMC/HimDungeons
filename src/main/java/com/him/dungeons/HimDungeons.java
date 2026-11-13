package com.him.dungeons;

import com.him.dungeons.api.PlaceholderAPIHook;
import com.him.dungeons.api.hook.DungeonContext;
import com.him.dungeons.api.hook.DungeonHookRegistry;
import com.him.dungeons.debug.DebugVisualizer;
import com.him.dungeons.generator.DungeonGenerator;
import com.him.dungeons.generator.PrecomputedDungeon;
import com.him.dungeons.generator.RoomConfigReader;
import com.him.dungeons.generator.AutoRoomConfigMigrator;
import com.him.dungeons.generator.ConfigApplier;
import com.him.dungeons.gui.editor.EditorGuiManager;
import com.him.dungeons.gui.DungeonStartGUI;
import com.him.dungeons.gui.InstanceManagerGui;
import com.him.dungeons.gui.LeaderboardGui;
import com.him.dungeons.util.*;
import com.him.dungeons.listener.*;
import com.him.dungeons.schematic.NmsFastPaster;
import com.him.dungeons.service.DungeonSessionService;
import com.him.dungeons.service.InstanceSnapshotService;
import com.him.dungeons.service.RunRecordService;
import com.him.dungeons.generator.PrecomputedDungeon;
import com.him.dungeons.storage.*;
import com.him.dungeons.service.RunRecordService;
import com.him.dungeons.generator.AutoRoomConfigMigrator;
import com.him.dungeons.generator.RoomConfigReader;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public final class HimDungeons extends JavaPlugin {

    private static HimDungeons instance;
    private final ConcurrentHashMap<String, String> activeDungeons = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> playerDungeonWorlds = new ConcurrentHashMap<>();
    private final Set<String> createdWorlds = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, String> worldPrefixCache = new ConcurrentHashMap<>();

    public final Map<String, ConfigApplier> configAppliers = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> playerBackups = new ConcurrentHashMap<>();
    public final Map<String, Set<UUID>> dungeonPlayers = new ConcurrentHashMap<>();
    private BackupStorage backupStorage;
    private DungeonSessionService sessionService;

    private final DungeonHookRegistry hookRegistry = new DungeonHookRegistry();
    public DungeonHookRegistry getHookRegistry() { return hookRegistry; }
    private DungeonBossListener bossListener;
    public DungeonBossListener getBossListener() { return bossListener; }
    private DungeonStartGUI startGUI;
    private EditorGuiManager editorGuiManager;
    private final Map<UUID, String> partyConfirmWaiting = new ConcurrentHashMap<>();

    public BackupStorage getBackupStorage() { return backupStorage; }
    public DungeonSessionService getSessionService() { return sessionService; }
    public BossBarManager getBossBarManager() { return bossBarManager; }

    private BossBarManager bossBarManager;
    private RoomFogManager roomFogManager;
    private BossPhaseManager bossPhaseManager;
    private LeaderboardGui leaderboardGui;
    public static final String POOL_WORLD_PREFIX = "himdungeons_pool_";
    private final Set<String> poolWorldNames = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String, PrecomputedDungeon> poolPrecomputed = new ConcurrentHashMap<>();
    private final Map<String, String> poolDungeonName = new ConcurrentHashMap<>();  // 池子名 → 地牢名
    // 字段
private RunStorage runStorage;
private RunRecordService runRecordService;
private LeaderboardCache leaderboardCache;

public RunRecordService getRunRecordService() { return runRecordService; }
public RunStorage getRunStorage()             { return runStorage; }
public LeaderboardCache getLeaderboardCache() { return leaderboardCache; }
private InstanceSnapshotService instanceSnapshotService;
private InstanceManagerGui instanceManagerGui;

public InstanceSnapshotService getInstanceSnapshotService() { return instanceSnapshotService; }
public InstanceManagerGui getInstanceManagerGui()           { return instanceManagerGui; }
private DebugVisualizer debugVisualizer;
public DebugVisualizer getDebugVisualizer() { return debugVisualizer; }
    // ===== 世界前缀 =====
    public void setWorldPrefix(String world, String rawPrefix) {
        if (world == null) return;
        if (rawPrefix == null || rawPrefix.isEmpty()) worldPrefixCache.remove(world);
        else worldPrefixCache.put(world, ChatColor.translateAlternateColorCodes('&', rawPrefix));
    }
    public String getWorldPrefix(String world) {
        return world == null ? null : worldPrefixCache.get(world);
    }
    public void removeWorldPrefix(String world) {
        if (world != null) worldPrefixCache.remove(world);
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        extractDefaultResources();
        LanguageManager.init(this);
        ConfigMigrator.migrate(this);

        this.backupStorage = new SqliteBackupStorage(this);
        this.sessionService = new DungeonSessionService(this, backupStorage);
        sessionService.restorePendingOnEnable();this.debugVisualizer = new DebugVisualizer(this);

        Bukkit.getPluginManager().registerEvents(new DungeonPlayerListener(this), this);
        bossListener = new DungeonBossListener(this);
        Bukkit.getPluginManager().registerEvents(bossListener, this);
        Bukkit.getPluginManager().registerEvents(new DungeonLifecycleListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SelectionToolListener(this), this);

        startGUI = new DungeonStartGUI(this);
        editorGuiManager = new EditorGuiManager(this);
        bossBarManager = new BossBarManager(this);
        roomFogManager = new RoomFogManager(this);
        bossPhaseManager = new BossPhaseManager(this);
        leaderboardGui = new LeaderboardGui(this);
        this.runStorage = new SqliteRunStorage(this);
    this.leaderboardCache = new LeaderboardCache(runStorage);
    this.runRecordService = new RunRecordService(this, runStorage, leaderboardCache);
    // 实例快照服务（GUI 只读快照，不直接遍历实例）
this.instanceSnapshotService = new InstanceSnapshotService(this);
this.instanceSnapshotService.start();
this.instanceManagerGui = new InstanceManagerGui(this);
        DungeonEventBridge.install(this);
        int cleanupSec = ConfigUtil.getEntityCleanupInterval();
        if (cleanupSec > 0) {
            Bukkit.getScheduler().runTaskTimer(this, new EntityCleanupTask(this),
                    20L * cleanupSec, 20L * cleanupSec);
        }

        PluginCommand cmd = getCommand("himdungeons");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(new DungeonTabCompleter());
        }
        Bukkit.getHelpMap().addTopic(new DungeonHelpTopic());        // 世界模板 + 世界池（延后 3 秒，避免卡启动）
        // 启动时清理崩溃遗留的 dungeon_* 世界文件夹（此时内存里无任何实例，可安全删）
        cleanupStaleDungeonWorlds();

        // 世界模板 + 世界池（延后 3 秒，避免卡启动）
        Bukkit.getScheduler().runTaskLater(this, () ->
                WorldUtil.ensureWorldTemplate(this, ok -> {
                    if (ok) initWorldPool();
                    else getLogger().warning("[世界池] 模板初始化失败，世界池不可用");
                }), 60L);
// 启动时自动迁移旧房间到单文件元数据（异步，不卡启动）
if (ConfigUtil.isAutoMigrateRooms()) {
    Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> {
        int n = AutoRoomConfigMigrator.migrateAll(this, true);
        if (n > 0) {
            RoomConfigReader.invalidateAll();   // 清缓存让新文件立刻生效
        }
    }, 40L);   // 2 秒后跑
}
// 快速粘贴能力探测：无论配置如何都打印状态，便于排错
Bukkit.getScheduler().runTaskLater(this, () -> {
    boolean enabled = ConfigUtil.isFastPasteEnabled();
    boolean available = NmsFastPaster.isAvailable();   // 内部 tryInit 一定会跑
    if (available) {
        NmsFastPaster.saveDetectedPackageToConfig(this);
    }
    if (enabled && available) {
        getLogger().info("[快速粘贴] 已启用，包路径: " + NmsFastPaster.getDetectedPackage());
    } else if (enabled && !available) {
        getLogger().warning("[快速粘贴] 配置已启用但反射不可用，将回退普通粘贴");
    } else if (!enabled && available) {
        getLogger().info("[快速粘贴] 反射可用（"
                + NmsFastPaster.getDetectedPackage() + "），但配置未启用。"
                + "如需开启请在 config.yml 添加：fastPaste.enabled: true");
    } else {
        getLogger().info("[快速粘贴] 未启用且反射不可用");
    }
}, 20L);
        // 注册 PlaceholderAPI 扩展（如果已安装）
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Class<?> clazz = Class.forName("com.him.dungeons.api.PlaceholderAPIHook");
                java.lang.reflect.Method registerMethod = clazz.getMethod("register", HimDungeons.class);
                registerMethod.invoke(null, this);
                getLogger().info("[PlaceholderAPI] 扩展已注册。");
            } catch (Throwable t) {
                getLogger().warning("[PlaceholderAPI] 扩展注册失败: " + t.getMessage());
            }
        }
        logSoftDependency("Parties");
        logSoftDependency("Vault");
        logSoftDependency("Slimefun");
        logSoftDependency("MythicMobs");
        logSoftDependency("PlaceholderAPI");

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            File[] dirs = getDataFolder().listFiles(File::isDirectory);
            if (dirs == null) return;
            for (File dir : dirs) {
                if (new File(dir, "config.yml").exists()) ConfigUtil.refreshStructureWeights(dir.getName());
            }
        }, 20L * 10, 20L * 10);

        getLogger().info(LanguageManager.get("log.enable.banner-ready",
                "version", getDescription().getVersion()));
    }

    @Override
    public void onDisable() {
        if (sessionService != null) {
            for (DungeonInstance inst : dungeonInstances.values()) {
                sessionService.cancelCleanup(inst.worldName);
            }
        }
        for (String worldName : createdWorlds) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) WorldUtil.deleteWorldAndRelease(world);
        }
        if (backupStorage != null) { backupStorage.close(); backupStorage = null; }
if (runRecordService != null) {
    runRecordService.shutdown();  // 最后一次 drain
    runRecordService = null;
}
if (runStorage != null) { runStorage.close(); runStorage = null; }
if (instanceSnapshotService != null) {
    instanceSnapshotService.stop();
    instanceSnapshotService = null;
}
if (debugVisualizer != null) {
    debugVisualizer.clearAll();
    debugVisualizer = null;
}
instanceManagerGui = null;
leaderboardCache = null;
        hookRegistry.clearAll();
        worldPrefixCache.clear();
        createdWorlds.clear();
        activeDungeons.clear();
        playerDungeonWorlds.clear();
        dungeonInstances.clear();
        dungeonNameToWorld.clear();
        dungeonBosses.clear();
        dungeonPlayers.clear();
        configAppliers.clear();
        partyConfirmWaiting.clear();
        if (roomFogManager != null) {
            for (Player p : Bukkit.getOnlinePlayers()) roomFogManager.restore(p);
            roomFogManager = null;
        }
        getLogger().info(LanguageManager.get("log.enable.banner-disable"));
        instance = null;
    }

    // ==================== 资源释放（保持上版） ====================

    private void extractDefaultResources() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();
        String[] langs = {"zh_CN.yml", "en_US.yml"};
        for (String lang : langs) {
            copyResourceIfNotExists("lang/" + lang, new File(dataFolder, "lang/" + lang));
        }
        File exampleDir = new File(dataFolder, "example_dungeon");
        if (exampleDir.exists()) {
            getLogger().info(LanguageManager.get("log.resource.example-skip"));
            return;
        }
        getLogger().info(LanguageManager.get("log.resource.example-copy-start"));
        try { copyDirectoryFromJar("example_dungeon", exampleDir); }
        catch (Exception e) {
            getLogger().warning(LanguageManager.get("log.resource.example-copy-fail",
                    "error", e.getMessage()));
        }
        getLogger().info(LanguageManager.get("log.resource.release-complete"));
    }

    private void copyDirectoryFromJar(String sourceDir, File targetDir) throws IOException {
        ClassLoader cl = getClassLoader();
        URL dirUrl = cl.getResource(sourceDir);
        if (dirUrl == null) {
            getLogger().warning(LanguageManager.get("log.resource.dir-not-found", "path", sourceDir));
            return;
        }
        if (dirUrl.getProtocol().equals("jar")) {
            JarURLConnection conn = (JarURLConnection) dirUrl.openConnection();
            try (JarFile jarFile = conn.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();
                String prefix = sourceDir + "/";
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith(prefix) && !entry.isDirectory()) {
                        String rel = name.substring(prefix.length());
                        File target = new File(targetDir, rel);
                        target.getParentFile().mkdirs();
                        if (!target.exists()) {
                            try (InputStream in = cl.getResourceAsStream(name)) {
                                if (in != null) {
                                    Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                    getLogger().info(LanguageManager.get("log.resource.released", "path", name));
                                }
                            }
                        }
                    }
                }
            }
        } else {
            File src = new File(dirUrl.getFile());
            if (src.exists() && src.isDirectory()) copyFileSystemDirectory(src, targetDir);
        }
    }

    private void copyFileSystemDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists()) target.mkdirs();
            for (File child : source.listFiles()) copyFileSystemDirectory(child, new File(target, child.getName()));
        } else if (!target.exists()) {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void copyResourceIfNotExists(String resourcePath, File targetFile) {
        if (targetFile.exists()) return;
        try (InputStream in = getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                getLogger().warning(LanguageManager.get("log.resource.resource-not-found", "path", resourcePath));
                return;
            }
            targetFile.getParentFile().mkdirs();
            Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            getLogger().info(LanguageManager.get("log.resource.released", "path", resourcePath));
        } catch (Exception e) {
            getLogger().warning(LanguageManager.get("log.resource.release-fail",
                    "path", resourcePath, "error", e.getMessage()));
        }
    }

    private void logSoftDependency(String name) {
        if (Bukkit.getPluginManager().getPlugin(name) != null)
            getLogger().info(LanguageManager.get("log.enable.soft-dep-found", "name", name));
        else
            getLogger().info(LanguageManager.get("log.enable.soft-dep-missing", "name", name));
    }

    // ==================== 命令 ====================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 && sender instanceof Player) {
            sendHelp((Player) sender);
            return true;
        }
        String sub = args[0].toLowerCase();

        // 判定哪些命令只能玩家执行
boolean playerOnly = switch (sub) {
    case "start", "leave", "edit", "blockpos", "help", "setlobby", "instances" -> true;
    default -> false;
};
        if (playerOnly && !(sender instanceof Player)) {
            MessageUtil.sendError(sender, "common.player-only");
            return true;
        }

        Player player = sender instanceof Player p ? p : null;

        switch (sub) {
            case "blockpos" -> { if (player != null) cmdBlockpos(player, args); return true; }
            case "create"   -> { cmdCreate(sender, args); return true; }
            case "start"    -> { if (player != null) cmdStart(player, args); return true; }
            case "edit"     -> { if (player != null) cmdEdit(player, args); return true; }
            case "help"     -> { if (player != null) sendHelp(player); return true; }
            case "leave"    -> { if (player != null) cmdLeave(player); return true; }
            case "reload"   -> { cmdReload(sender, args); return true; }
            case "list"     -> { cmdList(sender); return true; }
            case "kick"     -> { cmdKick(sender, args); return true; }
            case "forceend" -> { cmdForceEnd(sender, args); return true; }
            case "validate" -> { cmdValidate(sender, args); return true; }
            case "lang"     -> { cmdLang(sender, args); return true; }
            case "setlobby" -> { if (player != null) cmdSetLobby(player, args); return true; }
            case "leaderboard" -> { if (player != null) cmdLeaderboard(player, args); return true; }
            case "migrate" -> { cmdMigrate(sender, args); return true; }
            case "stats" -> { cmdStats(sender, args); return true; }
            case "instances" -> { if (player != null) cmdInstances(player); return true; }
            case "debug" -> { cmdDebug(sender, args); return true; }
            default -> MessageUtil.sendError(sender, "common.unknown-subcommand", "label", label);
        }
        return true;
    }
private void cmdDebug(CommandSender sender, String[] args) {
    if (!sender.hasPermission("himdungeons.admin")) {
        MessageUtil.sendError(sender, "command.debug.no-permission");
        return;
    }
    if (!(sender instanceof Player p)) {
        MessageUtil.sendError(sender, "common.player-only");
        return;
    }
    String sub = args.length >= 2 ? args[1].toLowerCase() : "clear";
    if (sub.equals("clear")) {
        debugVisualizer.clear(p);
        MessageUtil.sendMessage(p, "debug.cleared");
        return;
    }
    MessageUtil.sendError(sender, "command.debug.usage");
}
private void cmdInstances(Player player) {
    if (!player.hasPermission("himdungeons.admin")) {
        MessageUtil.sendError(player, "command.instances.no-permission");
        return;
    }
    instanceManagerGui.openOverview(player, 0);
}
private void cmdStats(CommandSender sender, String[] args) {
    if (args.length < 2) {
        MessageUtil.sendError(sender, "command.stats.usage"); return;
    }
    String sub = args[1].toLowerCase();

    if (sub.equals("export")) {
        if (!sender.hasPermission("himdungeons.admin")) {
            MessageUtil.sendError(sender, "command.stats.no-permission"); return;
        }
        if (args.length < 3) { MessageUtil.sendError(sender, "command.stats.usage"); return; }
        String dungeon = args[2];
        if (!getDungeonConfigExists(dungeon)) {
            MessageUtil.sendError(sender, "command.leaderboard.not-found", "dungeon", dungeon); return;
        }
        exportStats(sender, dungeon);
        return;
    }

    if (sub.equals("me")) {
        if (!(sender instanceof Player p)) {
            MessageUtil.sendError(sender, "common.player-only"); return;
        }
        String dungeon = args.length >= 3 ? args[2] : currentDungeonOf(p);
        if (dungeon == null) {
            MessageUtil.sendError(p, "command.stats.not-in-dungeon"); return;
        }
        sendPlayerStats(p, dungeon, Period.ALL);
        return;
    }

    if (sub.equals("admin")) {
        if (!sender.hasPermission("himdungeons.admin")) {
            MessageUtil.sendError(sender, "command.stats.no-permission"); return;
        }
        String dungeon = args.length >= 3 ? args[2] : null;
        if (dungeon == null) {
            MessageUtil.sendError(sender, "command.stats.dungeon-required"); return;
        }
        if (!getDungeonConfigExists(dungeon)) {
            MessageUtil.sendError(sender, "command.leaderboard.not-found", "dungeon", dungeon); return;
        }
        sendDungeonSummary(sender, dungeon, Period.ALL);
        return;
    }
    MessageUtil.sendError(sender, "command.stats.usage");
}

private String currentDungeonOf(Player p) {
    String w = getPlayerDungeonWorld(p.getUniqueId());
    if (w == null) return null;
    DungeonInstance inst = getDungeonInstanceByWorld(w);
    return inst == null ? null : inst.dungeonName;
}

private void sendPlayerStats(Player p, String dungeon, Period period) {
    PlayerStats s = leaderboardCache.playerStats(p.getUniqueId(), dungeon, period);
    MessageUtil.sendMessage(p, "command.stats.player-header", "dungeon", dungeon);
    MessageUtil.sendMessage(p, "command.stats.player-success", "n", s.successCount);
    MessageUtil.sendMessage(p, "command.stats.player-fail", "n", s.failCount);
    MessageUtil.sendMessage(p, "command.stats.player-rate",
            "rate", String.format("%.1f", s.winRate() * 100));
    if (s.fastestMs != Long.MAX_VALUE) {
        MessageUtil.sendMessage(p, "command.stats.player-fastest",
                "time", formatDuration(s.fastestMs));
        MessageUtil.sendMessage(p, "command.stats.player-average",
                "time", formatDuration(s.averageMs()));
    }
    MessageUtil.sendMessage(p, "command.stats.player-deaths", "n", s.totalDeaths);
}

private void sendDungeonSummary(CommandSender sender, String dungeon, Period period) {
    DungeonSummary s = runStorage.dungeonSummary(dungeon, period);
    MessageUtil.sendMessage(sender, "command.stats.admin-header", "dungeon", dungeon);
    MessageUtil.sendMessage(sender, "command.stats.admin-runs",
            "total", s.totalRuns, "success", s.successCount, "fail", s.failCount);
    if (s.fastestMs > 0) {
        MessageUtil.sendMessage(sender, "command.stats.admin-fastest",
                "time", formatDuration(s.fastestMs));
        MessageUtil.sendMessage(sender, "command.stats.admin-average",
                "time", formatDuration(s.averageMs));
    }
    MessageUtil.sendMessage(sender, "command.stats.admin-deaths", "n", s.totalDeaths);
    if (!s.failReasons.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        s.failReasons.forEach((k, v) -> sb.append(k).append("=").append(v).append(' '));
        MessageUtil.sendMessage(sender, "command.stats.admin-fail-reasons", "list", sb.toString().trim());
    }
}

private void exportStats(CommandSender sender, String dungeon) {
    List<RunRecord> all = runStorage.exportAll(dungeon);
    File exportDir = new File(getDataFolder(), dungeon + "/exports");
    exportDir.mkdirs();
    File out = new File(exportDir, "run-" + System.currentTimeMillis() + ".csv");
    try (var w = new java.io.PrintWriter(out, "UTF-8")) {
        w.println("run_id,started_at,finished_at,duration_ms,team_size,success,fail_reason,"
                + "damage_dealt,damage_taken,mobs_killed,deaths,score,players");
        for (RunRecord r : all) {
            StringBuilder names = new StringBuilder();
            for (RunMember m : r.members) {
                if (names.length() > 0) names.append('|');
                names.append(m.name);
            }
            w.println(r.id + "," + r.startedAt + "," + r.finishedAt + ","
                    + r.durationMs() + "," + r.teamSize() + "," + (r.success ? 1 : 0) + ","
                    + (r.failReason == null ? "" : r.failReason) + ","
                    + r.totalDamageDealt() + "," + r.totalDamageTaken() + ","
                    + r.totalMobsKilled() + "," + r.totalDeaths() + ","
                    + r.computeScore() + "," + names);
        }
    } catch (Exception e) {
        MessageUtil.sendError(sender, "command.stats.export-fail", "error", e.getMessage());
        return;
    }
    MessageUtil.sendMessage(sender, "command.stats.export-success",
            "count", all.size(), "path", out.getPath());
}

private static String formatDuration(long ms) {
    long sec = Math.max(0, ms) / 1000;
    long m = sec / 60;
    long s = sec % 60;
    return m > 0 ? m + "m " + s + "s" : s + "s";
}
private void cmdMigrate(CommandSender sender, String[] args) {
    if (!sender.hasPermission("himdungeons.admin")) {
        MessageUtil.sendError(sender, "command.migrate.no-permission"); return;
    }
    if (args.length < 2) { MessageUtil.sendError(sender, "command.migrate.usage"); return; }
    String dungeon = args[1];
    if (!getDungeonConfigExists(dungeon)) {
        MessageUtil.sendError(sender, "command.migrate.not-found", "dungeon", dungeon); return;
    }
    File roomsDir = new File(getDataFolder(), dungeon + "/rooms");
    int migrated = migrateRooms(roomsDir, dungeon);
    RoomConfigReader.invalidate(dungeon);
    MessageUtil.sendMessage(sender, "command.migrate.success",
            "dungeon", dungeon, "count", migrated);
}

/** 递归迁移：把 _door/_chest/_spawn/_checkpoint 合并为 <baseName>.yml，旧文件保留 */
private int migrateRooms(File roomsDir, String dungeon) {
    int count = 0;
    File[] files = roomsDir.listFiles();
    if (files == null) return 0;
    for (File f : files) {
        if (f.isDirectory()) {
            count += migrateRooms(f, dungeon);
            continue;
        }
        if (!f.getName().endsWith("_door.yml")) continue;

        String stem = f.getName().substring(0, f.getName().length() - "_door.yml".length());
        File parent = f.getParentFile();
        File single = new File(parent, stem + ".yml");
        if (single.exists()) continue;  // 已有新文件，跳过

        YamlConfiguration y = new YamlConfiguration();
        try {
            // 合并 door
            YamlConfiguration doorYaml = YamlConfiguration.loadConfiguration(f);
            y.set("doors", doorYaml.get("doors"));
            // 合并 chest
            File chest = new File(parent, stem + "_chest.yml");
            if (chest.exists()) {
                YamlConfiguration cy = YamlConfiguration.loadConfiguration(chest);
                y.set("chests", cy.get("chests"));
            }
            // 合并 spawn
            File spawn = new File(parent, stem + "_spawn.yml");
            if (spawn.exists()) {
                org.bukkit.util.Vector v = YamlDoorUtil.parseSpawnPoint(spawn);
                if (v != null) {
                    y.set("spawn.x", (int) v.getX());
                    y.set("spawn.y", (int) v.getY());
                    y.set("spawn.z", (int) v.getZ());
                }
            }
            // 合并 checkpoint
            File cp = new File(parent, stem + "_checkpoint.yml");
            if (cp.exists()) {
                org.bukkit.util.Vector v = YamlDoorUtil.parseSpawnPoint(cp);
                if (v != null) {
                    y.set("checkpoint.x", (int) v.getX());
                    y.set("checkpoint.y", (int) v.getY());
                    y.set("checkpoint.z", (int) v.getZ());
                }
            }
            y.save(single);
            count++;
        } catch (Exception ignored) {}
    }
    return count;
}
    private void cmdBlockpos(Player player, String[] args) {
        if (!player.isOp() && !player.hasPermission("himdungeons.admin")) {
            MessageUtil.sendError(player, "command.blockpos.no-permission"); return;
        }
        if (!DebugUtil.isDebugMode()) {
            MessageUtil.sendError(player, "command.blockpos.debug-only"); return;
        }
        if (args.length < 4) { MessageUtil.sendError(player, "command.blockpos.usage"); return; }
        try {
            int x = Integer.parseInt(args[1]), y = Integer.parseInt(args[2]), z = Integer.parseInt(args[3]);
            SelectionManager.Selection sel = SelectionManager.getSelection(player.getUniqueId());
            if (sel == null) { MessageUtil.sendError(player, "command.blockpos.no-selection"); return; }
            if (x < sel.minX || x > sel.maxX || y < sel.minY || y > sel.maxY || z < sel.minZ || z > sel.maxZ) {
                MessageUtil.sendError(player, "command.blockpos.out-of-region"); return;
            }
            MessageUtil.sendMessage(player, "command.blockpos.relative",
                    "x", x - sel.minX, "y", y - sel.minY, "z", z - sel.minZ);
        } catch (NumberFormatException e) {
            MessageUtil.sendError(player, "command.blockpos.invalid-number");
        }
    }

    private void cmdCreate(CommandSender player, String[] args) {
        if (!player.isOp() && !player.hasPermission("himdungeons.admin")) {
            MessageUtil.sendError(player, "command.create.no-permission"); return;
        }
        if (args.length < 2) { MessageUtil.sendError(player, "command.create.usage"); return; }
        handleCreateDungeon(player, args[1]);
    }

    private void cmdStart(Player player, String[] args) {
        if (!player.hasPermission("himdungeons.start")) {
            MessageUtil.sendError(player, "command.start.no-permission"); return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("party") && args[2].equalsIgnoreCase("confirm")) {
            handlePartyConfirm(player); return;
        }
        if (args.length >= 2) {
            String d = args[1];
            String mode = args.length >= 3 ? args[2].toLowerCase() : "single";
            startDungeon(player, d, mode);
        } else {
            startGUI.openStartMenu(player);
        }
    }

    private void cmdEdit(Player player, String[] args) {
        if (!player.hasPermission("himdungeons.admin.gui")) {
            MessageUtil.sendError(player, "command.edit.no-permission"); return;
        }
        if (args.length == 1) { editorGuiManager.openMain(player); return; }
        String arg = args[1];
        if (arg.equals(".RootConfig")) {
            if (!player.hasPermission("himdungeons.admin.edit")) {
                MessageUtil.sendError(player, "command.edit.no-permission"); return;
            }
            editorGuiManager.openRoot(player); return;
        }
        if (arg.equals(".RoomList")) { editorGuiManager.openDungeonList(player, 0); return; }
        String dungeon = arg;
        if (args.length == 2) { editorGuiManager.openDungeonEditor(player, dungeon); return; }
        String s = args[2].toLowerCase();
        switch (s) {
            case "config" -> {
                if (args.length == 3) { editorGuiManager.openConfigCat(player, dungeon); return; }
                String section = args[3].toLowerCase();
                if (!section.matches("root|gen|world|player|boss|economy|soft")) {
                    MessageUtil.sendError(player, "command.edit.invalid-category", "section", section);
                    return;
                }
                editorGuiManager.openConfigSection(player, dungeon, section, 0);
            }
            case "roomconfig" -> editorGuiManager.openRoomList(player, dungeon, 0);
            case "codefiles" -> editorGuiManager.openCodeFiles(player, dungeon, "", 0);
            default -> MessageUtil.sendError(player, "common.unknown-subcommand", "label", "edit");
        }
    }

    private void cmdLeave(Player player) {
        if (!player.hasPermission("himdungeons.leave")) {
            MessageUtil.sendError(player, "command.leave.no-permission"); return;
        }
        sessionService.leave(player);
    }

private void cmdReload(CommandSender player, String[] args) {
    if (!player.isOp() && !player.hasPermission("himdungeons.admin")) {
        MessageUtil.sendError(player, "command.reload.no-permission"); return;
    }
    if (args.length >= 2) {
        String dungeon = args[1];
        if (!getDungeonConfigExists(dungeon)) {
            MessageUtil.sendError(player, "command.reload-one.not-found", "dungeon", dungeon);
            return;
        }
        RoomConfigReader.invalidate(dungeon);
        // ★ 只迁移这一个地牢
        if (ConfigUtil.isAutoMigrateRooms()) {
            File roomsDir = new File(getDataFolder(), dungeon + "/rooms");
            if (roomsDir.exists()) {
                AutoRoomConfigMigrator.migrateAll(this, false);   // 简化：全量跑，代价小
                RoomConfigReader.invalidate(dungeon);
            }
        }
        String world = dungeonNameToWorld.get(dungeon);
        if (world != null) {
            FileConfiguration c = ConfigUtil.getDungeonConfig(dungeon);
            if (c != null) setWorldPrefix(world, ConfigUtil.getInstanceMsgPrefix(c));
        }
        MessageUtil.sendMessage(player, "command.reload-one.success", "dungeon", dungeon);
        return;
    }
    reloadConfig();
    LanguageManager.reload();
    RoomConfigReader.invalidateAll();
    if (ConfigUtil.isAutoMigrateRooms()) {
        AutoRoomConfigMigrator.migrateAll(this, true);
        RoomConfigReader.invalidateAll();
    }
    MessageUtil.sendMessage(player, "common.reload-success");
}

    private void cmdList(CommandSender player) {
        if (!player.hasPermission("himdungeons.admin")) {
            MessageUtil.sendError(player, "command.list.no-permission"); return;
        }
        MessageUtil.sendMessage(player, "command.list.header");
        boolean any = false;
        for (Map.Entry<String, DungeonInstance> e : dungeonInstances.entrySet()) {
            DungeonInstance inst = e.getValue();
            FileConfiguration cfg = ConfigUtil.getDungeonConfig(inst.dungeonName);
            long remain = ConfigUtil.getInstanceTime(cfg);
            MessageUtil.sendMessage(player, "command.list.entry",
                    "world", e.getKey(),
                    "dungeon", inst.dungeonName,
                    "players", inst.players.size(),
                    "time", remain > 0 ? remain + "s" : "∞");
            any = true;
        }
        if (!any) MessageUtil.sendMessage(player, "command.list.empty");
    }

    private void cmdKick(CommandSender player, String[] args) {
        if (!player.hasPermission("himdungeons.admin")) {
            MessageUtil.sendError(player, "command.kick.no-permission"); return;
        }
        if (args.length < 2) { MessageUtil.sendError(player, "command.kick.usage"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { MessageUtil.sendError(player, "command.kick.not-found", "player", args[1]); return; }
        if (getPlayerDungeonWorld(target.getUniqueId()) == null) {
            MessageUtil.sendError(player, "command.kick.not-found", "player", args[1]); return;
        }
        sessionService.leave(target);
        MessageUtil.sendMessage(player, "command.kick.success", "player", target.getName());
    }
    private void cmdForceEnd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("himdungeons.admin.world")) {
            MessageUtil.sendError(sender, "command.forceend.no-permission");
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendError(sender, "command.forceend.usage");
            return;
        }
        String key = args[1];
        DungeonInstance inst = dungeonInstances.get(key);
        if (inst == null) {
            // 尝试按地牢名匹配
            String world = dungeonNameToWorld.get(key);
            if (world != null) inst = dungeonInstances.get(world);
        }
        if (inst == null) {
            // 部分匹配（世界名前缀）
            for (Map.Entry<String, DungeonInstance> e : dungeonInstances.entrySet()) {
                if (e.getKey().startsWith(key) || e.getValue().dungeonName.equals(key)) {
                    inst = e.getValue();
                    break;
                }
            }
        }
        if (inst == null) {
            MessageUtil.sendError(sender, "command.forceend.not-found", "world", key);
            return;
        }
        String actualWorld = inst.worldName;
        forceFinishDungeon(inst);
        MessageUtil.sendMessage(sender, "command.forceend.success", "world", actualWorld);
    }

private void cmdValidate(CommandSender player, String[] args) {
    if (!player.hasPermission("himdungeons.admin")) {
        MessageUtil.sendError(player, "command.validate.no-permission"); return;
    }
    if (args.length < 2) { MessageUtil.sendError(player, "command.validate.usage"); return; }
    String d = args[1];
    if (!getDungeonConfigExists(d)) {
        MessageUtil.sendError(player, "command.validate.not-found", "dungeon", d); return;
    }
    RoomValidator.Result result = RoomValidator.validate(d);
    MessageUtil.sendMessage(player, "command.validate.header", "dungeon", d);
    if (result.totalRooms == 0 && !result.errors.isEmpty()) {
        for (String e : result.errors) {
            if (e.startsWith("no-rooms-dir:")) {
                MessageUtil.sendError(player, "validate.no-rooms-dir",
                        "path", e.substring("no-rooms-dir:".length()));
            }
        }
        return;
    }
    int errors = 0, warnings = 0;
    for (String err : result.errors) {
        errors++;
        if (err.startsWith("missing-schem:")) {
            MessageUtil.sendMessage(player, "command.validate.missing-schem",
                    "room", err.substring(14));
        } else if (err.equals("no-start-room")) {
            MessageUtil.sendError(player, "validate.no-start-room");
        } else if (err.equals("no-boss-room")) {
            MessageUtil.sendError(player, "validate.no-boss-room");
        } else if (err.equals("no-fallback-room")) {
            MessageUtil.sendError(player, "validate.no-fallback-room");
        } else {
            MessageUtil.sendMessage(player, "command.validate.missing-schem",
                    "room", err);
        }
    }
    for (String w : result.warnings) {
        warnings++;
        if (w.startsWith("orphan-weight:")) {
            MessageUtil.sendMessage(player, "command.validate.orphan-weight",
                    "room", w.substring(14));
        } else if (w.startsWith("duplicate-door-id:")) {
            MessageUtil.sendMessage(player, "validate.duplicate-door-id",
                    "room", w.substring("duplicate-door-id:".length()));
        } else if (w.startsWith("connect-ref-missing:")) {
            MessageUtil.sendMessage(player, "validate.connect-ref-missing",
                    "room", w.substring("connect-ref-missing:".length()));
        }
    }
    MessageUtil.sendMessage(player, "command.validate.summary",
            "rooms", result.totalRooms, "errors", errors, "warnings", warnings);
}

    private void cmdLang(CommandSender player, String[] args) {
        if (!player.hasPermission("himdungeons.admin")) {
            MessageUtil.sendError(player, "command.lang.no-permission"); return;
        }
        if (args.length < 2) { MessageUtil.sendError(player, "command.lang.usage"); return; }
        String code = args[1];
        File langFile = new File(getDataFolder(), "lang/" + code + ".yml");
        if (!langFile.exists()) {
            StringBuilder available = new StringBuilder();
            File[] files = new File(getDataFolder(), "lang").listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) for (File f : files) {
                String n = f.getName();
                if (available.length() > 0) available.append(", ");
                available.append(n.substring(0, n.length() - 4));
            }
            MessageUtil.sendError(player, "command.lang.not-found",
                    "code", code, "available", available.toString());
            return;
        }
        FileConfiguration global = ConfigUtil.getGlobalConfig();
        global.set("lang", code);
        ConfigUtil.saveGlobalConfig(global);
        LanguageManager.reload();
        MessageUtil.sendMessage(player, "command.lang.success", "code", code);
    }

    private void cmdSetLobby(Player player, String[] args) {
        if (!player.hasPermission("himdungeons.admin")) {
            MessageUtil.sendError(player, "command.setlobby.no-permission"); return;
        }
        if (args.length < 2) { MessageUtil.sendError(player, "command.setlobby.usage"); return; }
        String d = args[1];
        FileConfiguration cfg = ConfigUtil.getDungeonConfig(d);
        if (cfg == null) { MessageUtil.sendError(player, "command.setlobby.not-found", "dungeon", d); return; }
        Location loc = player.getLocation();
        String value = loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ()
                + "," + loc.getYaw() + "," + loc.getPitch();
        cfg.set("dungeonInstance.lobby", value);
        try {
            cfg.save(new File(getDataFolder(), d + "/config.yml"));
        } catch (Exception e) {
            MessageUtil.sendError(player, "command.setlobby.save-fail", "error", e.getMessage());
            return;
        }
        MessageUtil.sendMessage(player, "command.setlobby.success",
                "dungeon", d, "world", loc.getWorld().getName(),
                "x", String.format("%.1f", loc.getX()),
                "y", String.format("%.1f", loc.getY()),
                "z", String.format("%.1f", loc.getZ()));
    }

    private void sendHelp(Player player) {
        MessageUtil.sendMessage(player, "command.help.header");
        player.sendMessage(LanguageManager.get(player, "command.help.start"));
        player.sendMessage(LanguageManager.get(player, "command.help.start-party-confirm"));
        player.sendMessage(LanguageManager.get(player, "command.help.edit"));
        player.sendMessage(LanguageManager.get(player, "command.help.leave"));
        player.sendMessage(LanguageManager.get(player, "command.help.reload"));
        player.sendMessage(LanguageManager.get(player, "command.help.create"));
        player.sendMessage(LanguageManager.get(player, "command.help.help"));
        if (DebugUtil.isDebugMode()) player.sendMessage(LanguageManager.get(player, "command.help.blockpos"));
        player.sendMessage(LanguageManager.get(player, "command.help.permissions"));
    }

    private void handleCreateDungeon(CommandSender player, String dungeonName) {
        File folder = new File(getDataFolder(), dungeonName);
        if (folder.exists()) {
            MessageUtil.sendError(player, "command.create.exists", "dungeon", dungeonName); return;
        }
        if (!folder.mkdirs()) { MessageUtil.sendError(player, "command.create.mkdir-fail"); return; }
        File configFile = new File(folder, "config.yml");
        FileConfiguration config = buildDefaultInstanceConfig(dungeonName);
        try { config.save(configFile); }
        catch (Exception e) {
            MessageUtil.sendError(player, "command.create.save-fail", "error", e.getMessage());
            deleteDirectory(folder); return;
        }
        new File(folder, "rooms").mkdir();
        MessageUtil.sendMessage(player, "command.create.success", "dungeon", dungeonName);
    }

    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        if (dir.isDirectory()) {
            File[] ch = dir.listFiles();
            if (ch != null) for (File c : ch) deleteDirectory(c);
        }
        dir.delete();
    }

    // ==================== 启动地牢 ====================

    public void startDungeon(Player player, String dungeonName, String mode) {
        if (!getDungeonConfigExists(dungeonName)) {
            MessageUtil.sendError(player, "command.start.not-found", "dungeon", dungeonName); return;
        }
        FileConfiguration cfg = ConfigUtil.getDungeonConfig(dungeonName);
        if (cfg == null || !ConfigUtil.isInstanceEnabled(cfg)) {
            MessageUtil.sendError(player, "command.start.not-enabled", "dungeon", dungeonName); return;
        }

        // 玩家冷却
        int remain = PlayerCooldownManager.getRemaining(player.getUniqueId(), dungeonName);
        if (remain > 0) {
            MessageUtil.sendError(player, "session.cooldown-remaining", "seconds", remain); return;
        }

        String existingWorld = dungeonNameToWorld.get(dungeonName);
        if (existingWorld != null) {
            DungeonInstance inst = dungeonInstances.get(existingWorld);
            if (inst != null && !inst.isFinished) {
                if (!inst.players.contains(player.getUniqueId())) {
                    boolean ok = sessionService.enter(player, inst);
                    if (ok) MessageUtil.sendMessage(player, "command.start.reentering", "dungeon", dungeonName);
                    else MessageUtil.sendError(player, "command.start.enter-denied");
                } else MessageUtil.sendMessage(player, "command.start.already-in");
                return;
            }
        }

        if (!deductEnterCost(player, dungeonName)) return;

        switch (mode.toLowerCase()) {
            case "single" -> {
                MessageUtil.sendMessage(player, "command.start.generating", "dungeon", dungeonName);
                startDungeonAsync(player, dungeonName);
            }
            case "multi" -> handleMultiplayerStart(player, dungeonName);
            case "party" -> handlePartyStart(player, dungeonName);
            default -> MessageUtil.sendError(player, "command.start.unknown-mode", "mode", mode);
        }
    }

    private void handleMultiplayerStart(Player leader, String dungeonName) {
        List<Player> players = new ArrayList<>();
        players.add(leader);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(leader)) continue;
            if (p.isSneaking() && p.getWorld().equals(leader.getWorld())
                    && p.getLocation().distance(leader.getLocation()) <= 3.0) {
                players.add(p);
            }
        }
        if (players.size() == 1) {
            MessageUtil.sendMessage(leader, "command.multi.no-nearby");
            startDungeonAsync(leader, dungeonName); return;
        }
        String names = players.stream().map(Player::getName).collect(Collectors.joining(", "));
        MessageUtil.sendMessage(leader, "command.multi.party-joined", "dungeon", dungeonName, "names", names);
        startDungeonAsync(leader, dungeonName, players);
    }

    private void startDungeonAsync(Player player, String dungeonName) {
        startDungeonAsync(player, dungeonName, Collections.singletonList(player), false);
    }

    private void startDungeonAsync(Player leader, String dungeonName, List<Player> players) {
        startDungeonAsync(leader, dungeonName, players, false);
    }

    private void startDungeonAsync(Player leader, String dungeonName, List<Player> players, boolean testMode) {
        try {
            DungeonGenerator gen = new DungeonGenerator(this, dungeonName, leader, testMode);
            gen.generate(success -> {
                if (!success) { MessageUtil.sendError(leader, "command.async.generate-failed"); return; }
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    String worldName = leader.getWorld().getName();
                    DungeonInstance inst = dungeonInstances.get(worldName);
                    if (inst != null && isDungeonWorld(worldName)) {
                        for (Player p : players) {
                            if (!p.equals(leader)) {
                                if (sessionService.enter(p, inst))
                                    MessageUtil.sendMessage(p, "command.async.join-success", "dungeon", dungeonName);
                                else MessageUtil.sendError(p, "command.async.enter-denied");
                            }
                        }
                    } else MessageUtil.sendError(leader, "command.async.world-not-found");
                }, 20L);
            });
        } catch (Exception e) {
            e.printStackTrace();
            MessageUtil.sendError(leader, "command.async.start-failed", "error", e.getMessage());
        }
    }

    public void generateTestWorld(String dungeon, Player player) {
        deleteTestWorld(dungeon);
        MessageUtil.sendMessage(player, "command.test.generating");
        startDungeonAsync(player, dungeon, Collections.singletonList(player), true);
    }

    public void deleteTestWorld(String dungeon) {
        String prefix = "dungeon_" + dungeon + "_test_";
        for (World w : new ArrayList<>(Bukkit.getWorlds())) {
            if (w.getName().startsWith(prefix)) WorldUtil.deleteWorldAndRelease(w);
        }
        File[] dirs = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (dirs != null) for (File d : dirs) if (d.getName().startsWith(prefix)) deleteDirectory(d);
    }

    private void handlePartyStart(Player player, String dungeonName) {
        if (Bukkit.getPluginManager().getPlugin("Parties") == null) {
            MessageUtil.sendError(player, "command.party.no-plugin"); return;
        }
        FileConfiguration cfg = ConfigUtil.getDungeonConfig(dungeonName);
        if (cfg == null || !ConfigUtil.isInstanceAllowParties(cfg)) {
            MessageUtil.sendError(player, "command.party.not-allowed"); return;
        }
        List<Player> members = getPartyOnlineMembers(player);
        if (members.size() <= 1) {
            MessageUtil.sendError(player, "command.party.no-members"); return;
        }
        for (Player m : members) {
            partyConfirmWaiting.put(m.getUniqueId(), dungeonName);
            MessageUtil.sendMessage(m, "command.party.invite-sent");
        }
        MessageUtil.sendMessage(player, "command.party.invite-sent");
    }

    private void handlePartyConfirm(Player player) {
        if (!partyConfirmWaiting.containsKey(player.getUniqueId())) {
            MessageUtil.sendError(player, "command.party.no-pending"); return;
        }
        String d = partyConfirmWaiting.remove(player.getUniqueId());
        MessageUtil.sendMessage(player, "command.party.confirmed", "dungeon", d);
        startDungeonAsync(player, d);
    }

    private List<Player> getPartyOnlineMembers(Player leader) {
        try {
            Object api = Class.forName("com.alessiodp.parties.api.Parties").getMethod("getApi").invoke(null);
            Object pp = api.getClass().getMethod("getPartyPlayer", UUID.class).invoke(api, leader.getUniqueId());
            if (pp == null) return List.of(leader);
            boolean in = (boolean) pp.getClass().getMethod("isInParty").invoke(pp);
            if (!in) return List.of(leader);
            Object partyId = pp.getClass().getMethod("getPartyId").invoke(pp);
            Object party = api.getClass().getMethod("getParty", partyId.getClass()).invoke(api, partyId);
            if (party == null) return List.of(leader);
            Object membersObj = party.getClass().getMethod("getOnlineMembers").invoke(party);
            if (membersObj instanceof Collection<?> col) {
                List<Player> result = new ArrayList<>();
                for (Object o : col) {
                    if (o instanceof Player p) result.add(p);
                    else if (o instanceof UUID u) { Player p = Bukkit.getPlayer(u); if (p != null) result.add(p); }
                }
                if (!result.contains(leader)) result.add(leader);
                return result;
            }
        } catch (Exception ignored) {}
        return List.of(leader);
    }

    private boolean getDungeonConfigExists(String d) {
        return new File(getDataFolder(), d + "/config.yml").exists();
    }

    private boolean deductEnterCost(Player player, String dungeonName) {
        FileConfiguration config = ConfigUtil.getDungeonConfig(dungeonName);
        if (config == null) { MessageUtil.sendError(player, "enter-cost.no-config"); return false; }

        Map<String, Double> vault = ConfigUtil.getInstanceEnterVaultCost(config);
        if (!vault.isEmpty() && Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try {
                var reg = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
                if (reg == null) { MessageUtil.sendError(player, "enter-cost.vault-error", "error", "No Economy provider"); return false; }
                net.milkbowl.vault.economy.Economy eco = reg.getProvider();
                double total = vault.values().stream().mapToDouble(Double::doubleValue).sum();
                if (total > 0) {
                    if (!eco.has(player, total)) { MessageUtil.sendError(player, "enter-cost.vault-insufficient", "amount", total); return false; }
                    eco.withdrawPlayer(player, total);
                    MessageUtil.sendMessage(player, "enter-cost.vault-deducted", "amount", total);
                }
            } catch (Exception e) { MessageUtil.sendError(player, "enter-cost.vault-error", "error", e.getMessage()); return false; }
        }

        Map<String, Integer> pp = ConfigUtil.getInstanceEnterPlayerPointsCost(config);
        if (!pp.isEmpty() && Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
            try {
                Object api = Bukkit.getPluginManager().getPlugin("PlayerPoints").getClass().getMethod("getAPI").invoke(Bukkit.getPluginManager().getPlugin("PlayerPoints"));
                int total = pp.values().stream().mapToInt(Integer::intValue).sum();
                int balance = getPoints(api, player);
                if (balance < total) { MessageUtil.sendError(player, "enter-cost.points-insufficient", "amount", total, "balance", balance); return false; }
                if (!takePoints(api, player, total)) { MessageUtil.sendError(player, "enter-cost.points-error", "error", "take failed"); return false; }
                MessageUtil.sendMessage(player, "enter-cost.points-deducted", "amount", total);
            } catch (Exception e) { MessageUtil.sendError(player, "enter-cost.points-error", "error", e.getMessage()); return false; }
        }

        for (String cmd : ConfigUtil.getInstanceEnterConsoleCommands(config)) {
            try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", player.getName())); }
            catch (Exception e) { getLogger().warning(LanguageManager.get("log.dungeon.enter-console-cmd-fail", "cmd", cmd)); }
        }
        return true;
    }

    static int getPoints(Object api, Player p) {
        try { return (int) api.getClass().getMethod("look", String.class).invoke(api, p.getName()); }
        catch (NoSuchMethodException e) {
            try { return (int) api.getClass().getMethod("look", UUID.class).invoke(api, p.getUniqueId()); }
            catch (Exception ex) { return 0; }
        } catch (Exception e) { return 0; }
    }

    static boolean takePoints(Object api, Player p, int amount) {
        if (amount <= 0) return true;
        try {
            Object r = api.getClass().getMethod("take", String.class, int.class).invoke(api, p.getName(), amount);
            return !(r instanceof Boolean) || (Boolean) r;
        } catch (NoSuchMethodException e) {
            try {
                Object r = api.getClass().getMethod("take", UUID.class, int.class).invoke(api, p.getUniqueId(), amount);
                return !(r instanceof Boolean) || (Boolean) r;
            } catch (Exception ex) { return false; }
        } catch (Exception e) { return false; }
    }

    // ==================== Tab 补全 ====================

    private class DungeonTabCompleter implements TabCompleter {
        @Override
        public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
            if (args.length == 1) {
                List<String> subs = new ArrayList<>(Arrays.asList(
        "start", "edit", "leave", "reload", "help", "create",
        "validate", "lang", "setlobby", "leaderboard", "stats",
        "instances", "migrate"));
                if (DebugUtil.isDebugMode()) subs.add("blockpos");
                if (sender instanceof Player p) {
                    if (!p.hasPermission("himdungeons.start")) subs.remove("start");
                    if (!p.hasPermission("himdungeons.leave")) subs.remove("leave");
                    if (!p.hasPermission("himdungeons.admin")) {
                        subs.removeAll(Arrays.asList("edit", "reload", "create", "blockpos", "list", "kick", "forceend", "validate", "lang", "setlobby"));
                    }
                }
                return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
            }
            if (args.length == 2) {
                String sub = args[0].toLowerCase();
                if (sub.equals("start") || sub.equals("edit") || sub.equals("validate")
                        || sub.equals("setlobby") || sub.equals("reload") || sub.equals("leaderboard") || sub.equals("migrate")) {
                    File[] dirs = getDataFolder().listFiles(File::isDirectory);
                    if (dirs == null) return Collections.emptyList();
                    List<String> names = new ArrayList<>();
                    for (File d : dirs) if (new File(d, "config.yml").exists()) names.add(d.getName());
                    if (sub.equals("edit")) {
                        names.add(0, ".RootConfig");
                        names.add(1, ".RoomList");
                    }
                    if (sub.equals("stats")){
                        names.add(0, "me");
                        names.add(1, "admin");
                        names.add(2, "export");
                    }
                    return names.stream().filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
                }
                if (sub.equals("debug")) return Collections.singletonList("clear");
                if (sub.equals("lang")) {
                    File[] files = new File(getDataFolder(), "lang").listFiles((d, n) -> n.endsWith(".yml"));
                    if (files == null) return Collections.emptyList();
                    List<String> codes = new ArrayList<>();
                    for (File f : files) codes.add(f.getName().substring(0, f.getName().length() - 4));
                    return codes.stream().filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
                }
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
                return Arrays.asList("single", "multi", "party").stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
            return Collections.emptyList();
        }
    }

    private class DungeonHelpTopic extends org.bukkit.help.HelpTopic {
        @Override public String getFullText(CommandSender sender) { return LanguageManager.get("command.help.header"); }
        @Override public String getName() { return "HimDungeons"; }
        @Override public boolean canSee(CommandSender s) {
            return s.hasPermission("himdungeons.start") || s.hasPermission("himdungeons.leave") || s.hasPermission("himdungeons.admin");
        }
    }

    // ==================== 公共 API ====================

    public static HimDungeons getInstance() { return instance; }

    public void addCreatedWorld(String w) { createdWorlds.add(w); }
    public void registerDungeonWorld(String w, String d) { activeDungeons.put(w, d); createdWorlds.add(w); }
    public void unregisterDungeonWorld(String w) { activeDungeons.remove(w); }
    public boolean isDungeonWorld(String w) { return activeDungeons.containsKey(w); }

    public String getPlayerDungeonWorld(UUID id) { return playerDungeonWorlds.get(id); }
    public void setPlayerDungeonWorld(UUID id, String w) {
        if (w == null) playerDungeonWorlds.remove(id); else playerDungeonWorlds.put(id, w);
    }

    public void registerConfigApplier(String w, ConfigApplier a) { configAppliers.put(w, a); }
    public ConfigApplier getConfigApplier(String w) { return configAppliers.get(w); }

    public void setPlayerBackup(UUID u, ItemStack[] b) { playerBackups.put(u, b); }
    public ItemStack[] getPlayerBackup(UUID u) { return playerBackups.remove(u); }

    public void restorePlayerInventory(Player p, String d) {
        if (p == null || !p.isOnline()) return;
        if (backupStorage != null) {
            backupStorage.restore(p.getUniqueId(), d, p);
            p.updateInventory();
        }
    }

    public final Map<String, BossData> dungeonBosses = new ConcurrentHashMap<>();

    public static class BossData {
        public final LivingEntity entity;
        public final ConfigApplier applier;
        public final String dungeonName;
        public final String worldName;
        public final Location spawnLocation;
        public final RoomInstance bossRoom;
        public BossData(LivingEntity e, ConfigApplier a, String d, String w, Location s, RoomInstance r) {
            this.entity = e; this.applier = a; this.dungeonName = d; this.worldName = w;
            this.spawnLocation = s; this.bossRoom = r;
        }
    }

    public void registerBoss(String w, BossData d) { dungeonBosses.put(w, d); }
    public BossData getBossData(String w) { return dungeonBosses.get(w); }
    public void removeBossData(String w) { dungeonBosses.remove(w); }

    public final Map<String, DungeonInstance> dungeonInstances = new ConcurrentHashMap<>();

public static class DungeonInstance {
    public final String dungeonName;
    public final String worldName;
    public final Set<UUID> players = ConcurrentHashMap.newKeySet();
    public boolean isFinished = false;
    public final Location spawnLocation;
    public List<RoomInstance> placedRooms;
    public boolean deepCloneActive = false;
    public DungeonContext hookContext;
    public long startTime = 0;
    public final RunStatsAccumulator runStats = new RunStatsAccumulator(); // ★ 新增
    public volatile boolean runRecorded = false;                          // ★ 新增

    public DungeonInstance(String d, String w, Location s) {
        this.dungeonName = d; this.worldName = w; this.spawnLocation = s;
    }
}

    public final Map<String, String> dungeonNameToWorld = new ConcurrentHashMap<>();

    public void startCleanupTimer(DungeonInstance i) {
        if (sessionService != null && i != null) sessionService.startCleanup(i.worldName);
    }
    public void cancelCleanupTask(String w) {
        if (sessionService != null) sessionService.cancelCleanup(w);
    }

    public void forceFinishDungeon(DungeonInstance inst) {
        if (inst.isFinished) return;
        inst.isFinished = true;
        if (!inst.runRecorded) {
            getRunRecordService().record(inst, false, "force_finish");
        }

        // 超时/失败惩罚
        FileConfiguration config = ConfigUtil.getDungeonConfig(inst.dungeonName);
        ConfigApplier applier = configAppliers.get(inst.worldName);
        if (applier != null && config != null) applier.applyFailurePenalty(config, inst);

        DungeonContext ctx = inst.hookContext;
        if (ctx != null && !ctx.objectiveFailed) {
            ctx.failObjective("force_finish");
            hookRegistry.fireObjectiveFail(
                    new com.him.dungeons.api.hook.ObjectiveContext(
                            ctx, null,
                            com.him.dungeons.api.hook.ObjectiveContext.Type.FAIL,
                            "force_finish"));
        }
        if (ctx != null) hookRegistry.fireBeforeDestroy(ctx);

        World world = Bukkit.getWorld(inst.worldName);
        
        // ★ 修改点：使用 HashSet 拷贝，防止在 leave(p) 中被并发移除报错
        for (UUID uuid : new HashSet<>(inst.players)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                MessageUtil.sendError(p, "session.timeout-closed");
                p.closeInventory(); 
                // ★ 核心修复：统一走 sessionService.leave()，彻底清理状态并传送
                if (sessionService != null) {
                    sessionService.leave(p);
                } else {
                    // 后备强退
                    if (inst.deepCloneActive) backupStorage.restore(uuid, inst.dungeonName, p);
                    p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                    setPlayerDungeonWorld(uuid, null);
                }
            } else {
                if (inst.deepCloneActive && backupStorage.hasBackup(uuid, inst.dungeonName)) {
                    backupStorage.updateStatus(uuid, inst.dungeonName, BackupStorage.Status.PENDING_RESTORE);
                }
                setPlayerDungeonWorld(uuid, null);
                clearPlayerCheckpoint(uuid);
            }
        }

        if (world != null) WorldUtil.deleteWorldAndRelease(world);
        removeWorldPrefix(inst.worldName);
        if (bossBarManager != null) bossBarManager.remove(inst.worldName);
        unregisterDungeonWorld(inst.worldName);
        dungeonInstances.remove(inst.worldName);
        dungeonNameToWorld.remove(inst.dungeonName);
        dungeonBosses.remove(inst.worldName);
        dungeonPlayers.remove(inst.worldName);
        configAppliers.remove(inst.worldName);
        if (sessionService != null) sessionService.clearOfflineRecordsForWorld(inst.worldName);

        if (ctx != null) hookRegistry.fireAfterDestroy(ctx);
    }

    public void addPlayerToDungeon(String w, UUID id) {
        dungeonPlayers.computeIfAbsent(w, k -> ConcurrentHashMap.newKeySet()).add(id);
    }
    public DungeonInstance getDungeonInstanceByWorld(String w) { return dungeonInstances.get(w); }
    public Set<UUID> getDungeonPlayers(String w) { return dungeonPlayers.get(w); }
    public void removeDungeonPlayers(String w) { dungeonPlayers.remove(w); }

    private final Map<UUID, Location> playerCheckpoints = new ConcurrentHashMap<>();
    public void setPlayerCheckpoint(UUID u, Location l) {
        if (l == null) playerCheckpoints.remove(u); else playerCheckpoints.put(u, l);
    }
    public Location getPlayerCheckpoint(UUID u) { return playerCheckpoints.get(u); }
    public void clearPlayerCheckpoint(UUID u) { playerCheckpoints.remove(u); }

    public void scanAndRegisterRooms(String d) {
        RoomConfigReader.invalidate(d);   
        ConfigUtil.refreshStructureWeights(d);
        getLogger().info(LanguageManager.get("log.dungeon.scan-registered", "dungeon", d));
    }

    public DungeonStartGUI getStartGUI() { return startGUI; }

    public FileConfiguration buildDefaultInstanceConfig(String dungeonName) {
        YamlConfiguration c = new YamlConfiguration();
        c.set("dungeonInstance.enable", true);
        c.set("dungeonInstance.displayName", dungeonName);
        c.set("dungeonInstance.fileName", dungeonName);
        c.set("dungeonInstance.msgPrefix", "&a[" + dungeonName + "] &r");
        c.set("dungeonInstance.minRoomAmount", 5);
        c.set("dungeonInstance.maxRoomAmount", 12);
        c.set("dungeonInstance.dfsMaxDeep", 8);
        c.set("dungeonInstance.useBfsCheck", false);
        c.set("dungeonInstance.useAabb", true);
        c.set("dungeonInstance.structureChoose.combat", 100);
        c.set("dungeonInstance.structureChoose.straight", 80);
        c.set("dungeonInstance.structureChoose.corner", 60);
        c.set("dungeonInstance.structureChoose.tCorner", 40);
        c.set("dungeonInstance.structureChoose.cross", 20);
        c.set("dungeonInstance.structureChoose.stair", 20);
        c.set("dungeonInstance.structureChoose.boss", 30);
        c.set("dungeonInstance.structureChoose.fallback", 10);
        c.set("dungeonInstance.disconnectedRoomDistance", 0);
        c.set("dungeonInstance.allowRoomReuse", false);
        c.set("dungeonInstance.allowDoorReuse", false);
        c.set("dungeonInstance.allowFallbackDoorReuse", false);
        c.set("dungeonInstance.startToBossRoomAmmount", "10 10");
        c.set("dungeonInstance.blockRoomGenerateBlocks", new ArrayList<>());
        c.set("dungeonInstance.generatorChoose", new ArrayList<>());
        c.set("dungeonInstance.allowParties", false);
        c.set("dungeonInstance.allowSlimefun", false);
        c.set("dungeonInstance.bossType", Collections.singletonList("zombie"));
        c.set("dungeonInstance.bossNbt", Collections.singletonList("{Health:100.0f}"));
        c.set("dungeonInstance.useMythicMobForBoss", false);
        c.set("dungeonInstance.mythicMobBossName", "");
        c.set("dungeonInstance.keepInventory", false);
        c.set("dungeonInstance.deepCloneInventory", false);
        c.set("dungeonInstance.time", 0);
        c.set("dungeonInstance.dungeonTime", 1200);
        c.set("dungeonInstance.advanceTime", false);
        c.set("dungeonInstance.weather", "clear");
        c.set("dungeonInstance.difficulty", "normal");
        c.set("dungeonInstance.allowMobs", Collections.singletonList("all"));
        c.set("dungeonInstance.useMythicMobs", false);
        c.set("dungeonInstance.tntExplosion", "deny");
        c.set("dungeonInstance.entityExplosion", "deny");
        c.set("dungeonInstance.blockExplosion", "deny");
        c.set("dungeonInstance.commandsAllowListType", "white");
        c.set("dungeonInstance.commandsAllow", new ArrayList<>());
        c.set("dungeonInstance.buildSettingsListType", "white");
        c.set("dungeonInstance.buildSettings", new ArrayList<>());
        c.set("dungeonInstance.breakSettingsListType", "white");
        c.set("dungeonInstance.breakSettings", new ArrayList<>());
        c.set("dungeonInstance.interactionSettingsListType", "white");
        c.set("dungeonInstance.interactionSettings", new ArrayList<>());
        c.set("dungeonInstance.lobby", "world 0 64 0");
        c.set("dungeonInstance.rewards.vault", new HashMap<>());
        c.set("dungeonInstance.rewards.playerPoints", new HashMap<>());
        c.set("dungeonInstance.rewards.consoleCommands", new ArrayList<>());
        c.set("dungeonInstance.enterCost.vault", new HashMap<>());
        c.set("dungeonInstance.enterCost.playerPoints", new HashMap<>());
        c.set("dungeonInstance.enterCost.consoleCommands", new ArrayList<>());
        // === 新增 P0~P3 默认值 ===
        c.set("dungeonInstance.respawnMode", "checkpoint");
        c.set("dungeonInstance.deathCooldown", 15);
        c.set("dungeonInstance.playerCooldown", 300);
        c.set("dungeonInstance.friendlyFire", false);
        c.set("dungeonInstance.autoPickup", true);
        c.set("dungeonInstance.teleportInvulnerable", 3);
        c.set("dungeonInstance.countdownWarn", List.of(300, 180, 60, 30, 10));
        c.set("dungeonInstance.bossBar", true);
        c.set("dungeonInstance.partyGlow", true);
        c.set("dungeonInstance.enterTitle", "");
        c.set("dungeonInstance.leaveTitle", "");
        c.set("dungeonInstance.roomProgress", true);
        c.set("dungeonInstance.bossDirection", false);
        c.set("dungeonInstance.soundVolume", 100);
        c.set("dungeonInstance.allowSpectatorAfterDeath", true);
        c.set("dungeonInstance.scaleByPartySize", true);
        c.set("dungeonInstance.bossHealthMultiplierPerPlayer", 1.5);
        c.set("dungeonInstance.mobSpawnRate", 1.0);
        c.set("dungeonInstance.maxMobsPerRoom", 30);
        c.set("dungeonInstance.timeoutAction", "forceEnd");
        c.set("dungeonInstance.rewards.firstClearBonus.vault", new HashMap<>());
        c.set("dungeonInstance.rewards.firstClearBonus.playerPoints", new HashMap<>());
        c.set("dungeonInstance.rewards.firstClearBonus.consoleCommands", new ArrayList<>());
        c.set("dungeonInstance.rewards.shareMode", "each");
        c.set("dungeonInstance.rewards.permissionMultiplier", new HashMap<>());
        c.set("dungeonInstance.failurePenalty.vault", new HashMap<>());
        c.set("dungeonInstance.failurePenalty.playerPoints", new HashMap<>());
        c.set("dungeonInstance.failurePenalty.consoleCommands", new ArrayList<>());
        c.set("dungeonInstance.stats.trackFastestTime", true);
        c.set("dungeonInstance.roomFog", false);
        c.set("dungeonInstance.minimap", false);
        c.set("dungeonInstance.objective.requireAfterBoss", false);
        c.set("dungeonInstance.leaderboard", false);
        c.set("dungeonInstance.bossPhase.enabled", false);
        c.set("dungeonInstance.bossPhase.phases", new ArrayList<>());
        c.set("dungeonInstance.lootTable.enabled", false);
        c.set("dungeonInstance.lootTable.tables", new HashMap<>());
        return c;
    }
private void cmdLeaderboard(Player player, String[] args) {
    if (!player.hasPermission("himdungeons.start")) {
        MessageUtil.sendError(player, "command.leaderboard.no-permission"); return;
    }
    if (args.length < 2) {
        MessageUtil.sendMessage(player, "command.leaderboard.list-header");
        boolean any = false;
        File[] dirs = getDataFolder().listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                if (!new File(dir, "config.yml").exists()) continue;
                int count = WinStorage.getAllWins(dir.getName()).size();
                MessageUtil.sendMessage(player, "command.leaderboard.list-entry",
                        "dungeon", dir.getName(), "players", count);
                any = true;
            }
        }
        if (!any) MessageUtil.sendMessage(player, "command.leaderboard.list-empty");
        return;
    }
    String dungeon = args[1];
    if (!getDungeonConfigExists(dungeon)) {
        MessageUtil.sendError(player, "command.leaderboard.not-found", "dungeon", dungeon);
        return;
    }
    leaderboardGui.open(player, dungeon, LeaderboardMode.FASTEST);
}
    public RoomFogManager getRoomFogManager() { return roomFogManager; }
    public BossPhaseManager getBossPhaseManager() { return bossPhaseManager; }
    public LeaderboardGui getLeaderboardGui() { return leaderboardGui; }
    // ==================== 世界池 ====================

    /**
     * 借出一个池子文件夹名（世界未加载）。
     * 调用方随后必须通过 {@link #consumePoolWorldAs(String, String)} 完成重命名 + 加载。
     * 池子为空返回 null。
     */
/** 返回 [池子名, 缓存的 PrecomputedDungeon]，池子为空返回 null */
    /** 借出指定地牢的池子名；该地牢无可用池子返回 null */
    public String borrowPoolWorldName(String dungeonName) {
        if (dungeonName == null) return null;
        synchronized (poolWorldNames) {
            Iterator<String> it = poolWorldNames.iterator();
            while (it.hasNext()) {
                String name = it.next();
                if (dungeonName.equals(poolDungeonName.get(name))) {
                    it.remove();
                    return name;
                }
            }
        }
        return null;
    }

    /** 取出池子对应的预计算缓存（同时清理地牢绑定） */
    public PrecomputedDungeon takePoolPrecomputed(String poolName) {
        poolDungeonName.remove(poolName);
        return poolPrecomputed.remove(poolName);
    }

public World consumePoolWorldAs(String poolName, String targetName) {
    return WorldUtil.consumePoolWorldAs(this, poolName, targetName);
}


    /** 借出后调用：延迟 60 秒异步补足池子（避开玩家刚进地牢的高峰） */
    public void replenishPoolAsync() {
        if (ConfigUtil.getWorldPoolSize() <= 0) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (String dn : listConfiguredDungeons()) {
                int need = ConfigUtil.getWorldPoolSize() - getPoolCountForDungeon(dn);
                if (need <= 0) continue;
                getLogger().info("[世界池] 地牢 " + dn + " 需补 " + need + " 个池子");
                for (int i = 0; i < need; i++) {
                    final String dungeonName = dn;
                    Bukkit.getScheduler().runTaskLater(this,
                            () -> tryCreateAndAddPoolWorld(dungeonName), 20L + i * 100L);
                }
            }
        }, 20L * 60);
    }

    private int getPoolCountForDungeon(String dungeonName) {
        synchronized (poolWorldNames) {
            int count = 0;
            for (String p : poolWorldNames) {
                if (dungeonName.equals(poolDungeonName.get(p))) count++;
            }
            return count;
        }
    }

    private List<String> listConfiguredDungeons() {
        List<String> result = new ArrayList<>();
        File[] dirs = getDataFolder().listFiles(File::isDirectory);
        if (dirs == null) return result;
        for (File d : dirs) {
            if (new File(d, "config.yml").exists()) result.add(d.getName());
        }
        Collections.sort(result);
        return result;
    }

    private void tryCreateAndAddPoolWorld(String dungeonName) {
        int target = ConfigUtil.getWorldPoolSize();
        if (target <= 0) return;
        if (getPoolCountForDungeon(dungeonName) >= target) return;

        String poolName = POOL_WORLD_PREFIX + dungeonName + "_"
                + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);

        WorldUtil.createPoolWorldFolderAsync(this, poolName, result -> {
            if (result == null) {
                getLogger().warning("[世界池] 创建文件夹失败: " + poolName);
                return;
            }
            try {
                DungeonGenerator gen = new DungeonGenerator(this, dungeonName, null, false);
                gen.computeOnly(pre -> Bukkit.getScheduler().runTask(this, () -> {
                    if (pre == null) {
                        getLogger().warning("[世界池] 预计算失败: " + poolName);
                        File poolDir = new File(WorldUtil.getPoolFolder(this), poolName);
                        WorldUtil.deleteDirectoryStrictPublic(poolDir);
                        return;
                    }
                    addToPool(poolName, dungeonName, pre);
                }));
            } catch (Exception e) {
                getLogger().warning("[世界池] 预计算异常: " + e.getMessage());
            }
        });
    }

    private void addToPool(String poolName, String dungeonName, PrecomputedDungeon pre) {
        synchronized (poolWorldNames) { poolWorldNames.add(poolName); }
        poolDungeonName.put(poolName, dungeonName);
        if (pre != null) poolPrecomputed.put(poolName, pre);
        getLogger().info("[世界池] " + poolName + " 就绪（地牢 " + dungeonName
                + "，方块 " + (pre == null ? 0 : pre.blocks.size())
                + "，当前 " + getPoolCountForDungeon(dungeonName) + "/"
                + ConfigUtil.getWorldPoolSize() + "）");
    }

private String pickAnyDungeonForPool() {
    File[] dirs = getDataFolder().listFiles(File::isDirectory);
    if (dirs == null) return null;
    for (File d : dirs) {
        if (new File(d, "config.yml").exists()) return d.getName();
    }
    return null;
}



    /**
     * 初始化池子：
     * 1. 扫描磁盘上已存在的 himdungeons_pool_* 文件夹（kill 后复用）
     * 2. 补足到 worldPool.size
     */
    private void initWorldPool() {
        File templateDir = WorldUtil.getWorldTemplateFolder(this);
        if (!WorldUtil.isTemplateHealthyPublic(templateDir)) {
            getLogger().warning("[世界池] 模板不存在或不健康，跳过初始化");
            return;
        }

        // 1. 扫描磁盘上的池子文件夹（plugins/HimDungeons/pool/）
        List<String> existing = WorldUtil.listPoolWorldFolders(this);
        synchronized (poolWorldNames) { poolWorldNames.clear(); }
        for (String p : existing) {
            String dn = parseDungeonFromPoolName(p);
            if (dn == null || !listConfiguredDungeons().contains(dn)) {
                // 孤儿池子（无对应地牢），清理
                File poolDir = new File(WorldUtil.getPoolFolder(this), p);
                WorldUtil.deleteDirectoryStrictPublic(poolDir);
                continue;
            }
            synchronized (poolWorldNames) { poolWorldNames.add(p); }
            poolDungeonName.put(p, dn);
            // ★ 磁盘恢复的池子：预计算缓存为空 → 异步重建
            final String poolName = p;
            final String dungeonName = dn;
            Bukkit.getScheduler().runTaskLater(this, () -> rebuildCacheForPool(poolName, dungeonName), 20L);
        }
        if (!existing.isEmpty()) {
            getLogger().info("[世界池] 从磁盘恢复 " + getPoolWorldCount() + " 个池子，开始异步重建缓存");
        }

        // 2. 补足
        int target = ConfigUtil.getWorldPoolSize();
        if (target <= 0) return;
        List<String> dungeons = listConfiguredDungeons();
        if (dungeons.isEmpty()) return;

        int idx = 0;
        for (String dn : dungeons) {
            int need = Math.max(0, target - getPoolCountForDungeon(dn));
            for (int i = 0; i < need; i++) {
                final String dungeonName = dn;
                long delay = 20L + (idx++) * 40L;
                Bukkit.getScheduler().runTaskLater(this,
                        () -> tryCreateAndAddPoolWorld(dungeonName), delay);
            }
        }
        getLogger().info("[世界池] 初始化完成，目标 " + (dungeons.size() * target) + " 个池子");
    }

    /** 磁盘恢复的池子：异步重建预计算缓存 */
    private void rebuildCacheForPool(String poolName, String dungeonName) {
        if (poolPrecomputed.containsKey(poolName)) return;   // 已有缓存，跳过
        try {
            DungeonGenerator gen = new DungeonGenerator(this, dungeonName, null, false);
            gen.computeOnly(pre -> {
                if (pre == null) {
                    getLogger().warning("[世界池] 缓存重建失败: " + poolName);
                    return;
                }
                poolPrecomputed.put(poolName, pre);
                getLogger().info("[世界池] " + poolName + " 缓存已重建（方块 "
                        + pre.blocks.size() + "）");
            });
        } catch (Exception e) {
            getLogger().warning("[世界池] 缓存重建异常: " + e.getMessage());
        }
    }

    /** 从池子名解析地牢名（格式: himdungeons_pool_<dungeonName>_<ts>_<rand>） */
    private String parseDungeonFromPoolName(String poolName) {
        if (poolName == null || !poolName.startsWith(POOL_WORLD_PREFIX)) return null;
        String rest = poolName.substring(POOL_WORLD_PREFIX.length());
        String[] parts = rest.split("_");
        if (parts.length < 3) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 2; i++) {
            if (sb.length() > 0) sb.append('_');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /**
     * 只在手动重置/故障时调用。正常关闭不会走这里（池子世界未加载，无需 unload）。
     */
    public void cleanupWorldPool() {
        synchronized (poolWorldNames) { poolWorldNames.clear(); }
        poolDungeonName.clear();
        poolPrecomputed.clear();
        File poolDir = WorldUtil.getPoolFolder(this);
        if (poolDir.exists()) {
            File[] children = poolDir.listFiles();
            if (children != null) for (File c : children) {
                WorldUtil.deleteDirectoryStrictPublic(c);
            }
        }
    }

    public int getPoolWorldCount() {
        synchronized (poolWorldNames) { return poolWorldNames.size(); }
    }

    // ==================== 崩溃遗留清理 ====================

    /**
     * 启动时清理 worldContainer 下未加载的 dungeon_* 文件夹（崩溃/强杀遗留）。
     * 已加载的不动（可能是 bukkit.yml 显式配置）。
     * 会一并清除测试世界 dungeon_*_test_*。
     */
    private void cleanupStaleDungeonWorlds() {
        File base = WorldUtil.getDimensionBaseFolder();
        File[] dirs = base.listFiles(File::isDirectory);
        if (dirs == null) {
            getLogger().info("[启动清理] 维度目录为空: " + base.getPath());
            return;
        }
        getLogger().info("[启动清理] 扫描维度目录: " + base.getPath());

        String mainWorldName = Bukkit.getWorlds().isEmpty()
                ? "world" : Bukkit.getWorlds().get(0).getName();
        int removed = 0;
        int found = 0;

        for (File d : dirs) {
            String name = d.getName();

            // 主世界的三个维度
            if (name.equals("overworld") || name.equals("the_nether") || name.equals("the_end")) continue;
            if (name.equals(mainWorldName)) continue;

            // 只处理这三类：dungeon_* / himdungeons_pool_* / himdungeons_tpl_init_*
            boolean isDungeon = name.startsWith("dungeon_");
            boolean isPool = name.startsWith("himdungeons_pool_");
            boolean isTplInit = name.startsWith("himdungeons_tpl_init_");
            if (!isDungeon && !isPool && !isTplInit) continue;

            found++;

            // 已加载的先卸载
            World loaded = Bukkit.getWorld(name);
            if (loaded != null) {
                try { Bukkit.unloadWorld(loaded, false); }
                catch (Exception e) {
                    getLogger().warning("[启动清理] 卸载失败: " + name + " - " + e.getMessage());
                    continue;
                }
            }

            boolean ok = WorldUtil.deleteDirectoryStrictPublic(d);
            if (ok && !d.exists()) {
                removed++;
                getLogger().info("[启动清理] 已删除: " + name);
            } else {
                getLogger().warning("[启动清理] 删除失败: " + name);
            }
        }
        getLogger().info("[启动清理] 结果: 找到=" + found + " 删除=" + removed);
    }
}
