package com.him.dungeons;

import com.him.dungeons.api.hook.DungeonContext;
import com.him.dungeons.api.hook.DungeonHookRegistry;
import com.him.dungeons.generator.DungeonGenerator;
import com.him.dungeons.generator.ConfigApplier;
import com.him.dungeons.gui.editor.EditorGuiManager;
import com.him.dungeons.gui.DungeonStartGUI;
import com.him.dungeons.gui.LeaderboardGui;
import com.him.dungeons.util.*;
import com.him.dungeons.listener.*;
import com.him.dungeons.service.DungeonSessionService;
import com.him.dungeons.storage.BackupStorage;
import com.him.dungeons.storage.SqliteBackupStorage;
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
        sessionService.restorePendingOnEnable();

        Bukkit.getPluginManager().registerEvents(new DungeonPlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DungeonBossListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DungeonLifecycleListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SelectionToolListener(), this);

        startGUI = new DungeonStartGUI(this);
        editorGuiManager = new EditorGuiManager(this);
        bossBarManager = new BossBarManager(this);
        roomFogManager = new RoomFogManager(this);
        bossPhaseManager = new BossPhaseManager(this);
        leaderboardGui = new LeaderboardGui(this);
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
        Bukkit.getHelpMap().addTopic(new DungeonHelpTopic());

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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + LanguageManager.get("common.player-only"));
            return true;
        }
        if (args.length == 0) {
            MessageUtil.sendMessage(player, "command.help.header");
            MessageUtil.sendMessage(player, "common.unknown-subcommand", "label", label);
            return true;
        }
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "blockpos" -> { cmdBlockpos(player, args); return true; }
            case "create" -> { cmdCreate(player, args); return true; }
            case "start" -> { cmdStart(player, args); return true; }
            case "edit" -> { cmdEdit(player, args); return true; }
            case "help" -> { sendHelp(player); return true; }
            case "leave" -> { cmdLeave(player); return true; }
            case "reload" -> { cmdReload(player, args); return true; }
            case "list" -> { cmdList(player); return true; }
            case "kick" -> { cmdKick(player, args); return true; }
            case "forceend" -> { cmdForceEnd(player, args); return true; }
            case "validate" -> { cmdValidate(player, args); return true; }
            case "lang" -> { cmdLang(player, args); return true; }
            case "setlobby" -> { cmdSetLobby(player, args); return true; }
            case "leaderboard" -> { cmdLeaderboard(player, args); return true; }
            default -> MessageUtil.sendError(player, "common.unknown-subcommand", "label", label);
        }
        return true;
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

    private void cmdCreate(Player player, String[] args) {
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

    private void cmdReload(Player player, String[] args) {
        if (!player.isOp() && !player.hasPermission("himdungeons.admin")) {
            MessageUtil.sendError(player, "command.reload.no-permission"); return;
        }
        if (args.length >= 2) {
            String dungeon = args[1];
            if (!getDungeonConfigExists(dungeon)) {
                MessageUtil.sendError(player, "command.reload-one.not-found", "dungeon", dungeon);
                return;
            }
            // 热重载单实例前缀
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
        MessageUtil.sendMessage(player, "common.reload-success");
    }

    private void cmdList(Player player) {
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

    private void cmdKick(Player player, String[] args) {
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

    private void cmdForceEnd(Player player, String[] args) {
        if (!player.hasPermission("himdungeons.admin.world")) {
            MessageUtil.sendError(player, "command.forceend.no-permission"); return;
        }
        if (args.length < 2) { MessageUtil.sendError(player, "command.forceend.usage"); return; }
        DungeonInstance inst = dungeonInstances.get(args[1]);
        if (inst == null) { MessageUtil.sendError(player, "command.forceend.not-found", "world", args[1]); return; }
        forceFinishDungeon(inst);
        MessageUtil.sendMessage(player, "command.forceend.success", "world", args[1]);
    }

    private void cmdValidate(Player player, String[] args) {
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
                    MessageUtil.sendError(player, "validate.no-rooms-dir", "path", e.substring("no-rooms-dir:".length()));
                }
            }
            return;
        }
        int errors = 0, warnings = 0;
        for (String err : result.errors) {
            errors++;
            if (err.startsWith("missing-schem:")) MessageUtil.sendMessage(player, "command.validate.missing-schem", "room", err.substring(14));
            else if (err.startsWith("missing-door:")) MessageUtil.sendMessage(player, "command.validate.missing-door", "room", err.substring(13));
        }
        for (String w : result.warnings) {
            warnings++;
            if (w.startsWith("orphan-weight:")) MessageUtil.sendMessage(player, "command.validate.orphan-weight", "room", w.substring(14));
        }
        MessageUtil.sendMessage(player, "command.validate.summary",
                "rooms", result.totalRooms, "errors", errors, "warnings", warnings);
    }

    private void cmdLang(Player player, String[] args) {
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

    private void handleCreateDungeon(Player player, String dungeonName) {
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
                List<String> subs = new ArrayList<>(Arrays.asList("start", "edit", "leave", "reload", "help", "create", "list", "kick", "forceend", "validate", "lang", "setlobby", "leaderboard"));
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
                        || sub.equals("setlobby") || sub.equals("reload") || sub.equals("leaderboard")) {
                    File[] dirs = getDataFolder().listFiles(File::isDirectory);
                    if (dirs == null) return Collections.emptyList();
                    List<String> names = new ArrayList<>();
                    for (File d : dirs) if (new File(d, "config.yml").exists()) names.add(d.getName());
                    if (sub.equals("edit")) {
                        names.add(0, ".RootConfig");
                        names.add(1, ".RoomList");
                    }
                    return names.stream().filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
                }
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
        public long startTime = 0; // 新增：用于最快时间记录
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

        // 超时/失败惩罚
        FileConfiguration config = ConfigUtil.getDungeonConfig(inst.dungeonName);
        ConfigApplier applier = configAppliers.get(inst.worldName);
        if (applier != null && config != null) applier.applyFailurePenalty(config, inst);

        DungeonContext ctx = inst.hookContext;
        if (ctx != null) hookRegistry.fireBeforeDestroy(ctx);

        World world = Bukkit.getWorld(inst.worldName);
        for (UUID uuid : inst.players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (inst.deepCloneActive) {
                    backupStorage.restore(uuid, inst.dungeonName, p);
                    p.updateInventory();
                }
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                MessageUtil.sendError(p, "session.timeout-closed");
                setPlayerDungeonWorld(uuid, null);
            } else {
                if (inst.deepCloneActive && backupStorage.hasBackup(uuid, inst.dungeonName)) {
                    backupStorage.updateStatus(uuid, inst.dungeonName, BackupStorage.Status.PENDING_RESTORE);
                }
            }
            clearPlayerCheckpoint(uuid);
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
        c.set("dungeonInstance.leaderboard", false);
        c.set("dungeonInstance.bossPhase.enabled", false);
        c.set("dungeonInstance.bossPhase.phases", new ArrayList<>());
        c.set("dungeonInstance.lootTable.enabled", false);
        c.set("dungeonInstance.lootTable.tables", new HashMap<>());
        return c;
    }
    private void cmdLeaderboard(Player player, String[] args) {
        if (!player.hasPermission("himdungeons.start")) {
            MessageUtil.sendError(player, "command.leaderboard.no-permission");
            return;
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
        leaderboardGui.open(player, dungeon, LeaderboardGui.Mode.FASTEST);
    }
    public RoomFogManager getRoomFogManager() { return roomFogManager; }
    public BossPhaseManager getBossPhaseManager() { return bossPhaseManager; }
    public LeaderboardGui getLeaderboardGui() { return leaderboardGui; }
}