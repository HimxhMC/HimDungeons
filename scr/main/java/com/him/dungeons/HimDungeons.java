package com.him.dungeons;

import com.him.dungeons.generator.DungeonGenerator;
import com.him.dungeons.generator.ConfigApplier;
import com.him.dungeons.gui.editor.EditorGuiManager;
import com.him.dungeons.gui.DungeonStartGUI;
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
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import java.lang.reflect.Method;
import org.bukkit.configuration.file.FileConfiguration;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class HimDungeons extends JavaPlugin {
    
    private static HimDungeons instance;
    private final ConcurrentHashMap<String, String> activeDungeons = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> playerDungeonWorlds = new ConcurrentHashMap<>();
    private final Set<String> createdWorlds = ConcurrentHashMap.newKeySet();
    // 存储每个地牢世界的 ConfigApplier 实例（用于权限控制等）
    public final Map<String, ConfigApplier> configAppliers = new ConcurrentHashMap<>();
    // 存储玩家背包备份（深拷贝）
    private final Map<UUID, ItemStack[]> playerBackups = new ConcurrentHashMap<>();
    public final Map<String, Set<UUID>> dungeonPlayers = new ConcurrentHashMap<>();
    private BackupStorage backupStorage;
    private DungeonSessionService sessionService;
    
    public BackupStorage getBackupStorage() { return backupStorage; }
    public DungeonSessionService getSessionService() { return sessionService; }
    // GUI 实例
    private DungeonStartGUI startGUI;
    
    // Party 加入等待确认
    private final Map<UUID, String> partyConfirmWaiting = new ConcurrentHashMap<>();
    private EditorGuiManager editorGuiManager;
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        
        this.backupStorage = new SqliteBackupStorage(this);
        this.sessionService = new DungeonSessionService(this, backupStorage);
        sessionService.restorePendingOnEnable();
        
        extractDefaultResources();
        
        Bukkit.getPluginManager().registerEvents(new DungeonPlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DungeonBossListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DungeonLifecycleListener(this), this);
        
        // ★ 新增：注册木镐选区监听器
        Bukkit.getPluginManager().registerEvents(new SelectionToolListener(), this);
        
        startGUI = new DungeonStartGUI(this);
        editorGuiManager = new EditorGuiManager(this);
        
        PluginCommand cmd = getCommand("himdungeons");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(new DungeonTabCompleter());
        }
        
        Bukkit.getHelpMap().addTopic(new DungeonHelpTopic());
        
        // ★ 移除：WorldEdit 硬依赖检查
        // 原代码：if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) { ... }
        
        logSoftDependency("Parties");
        logSoftDependency("Vault");
        logSoftDependency("Slimefun");
        logSoftDependency("MythicMobs");
        logSoftDependency("PlaceholderAPI");
        
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            File dataFolder = getDataFolder();
            File[] dirs = dataFolder.listFiles(File::isDirectory);
            if (dirs == null) return;
            for (File dir : dirs) {
                if (new File(dir, "config.yml").exists()) {
                    ConfigUtil.refreshStructureWeights(dir.getName());
                }
            }
        }, 20L * 10, 20L * 10);
        getLogger().info("");
        getLogger().info("");
        getLogger().info("######     ######  #####                          ##########");
        getLogger().info("######     ######  #####                          ##############");
        getLogger().info("######     ######                                 ###### ########");
        getLogger().info("#################  #####   #### ###############   ######    #####   ############");
        getLogger().info("#################  #####   ####################   ######    ###### ######  #####");
        getLogger().info("#################  #####   #####   #####   ####   ######    ###### #####    ####");
        getLogger().info("######     ######  #####   #####   #####   ####   ######   ######  #####   #####");
        getLogger().info("######     ######  #####   #####   #####   ####   ##############    ############");
        getLogger().info("######     ######  #####   #####   #####   ####   ############        ####  ####");
        getLogger().info("                                                                    ####   #####");
        getLogger().info(" HimDungeons v3.0                                                   ############");
        getLogger().info("HimDungeons 已启用，版本 " + getDescription().getVersion());
    }
    
    @Override
    public void onDisable() {
        // 取消所有清理任务（交给 sessionService 统一管理）
        if (sessionService != null) {
            for (DungeonInstance inst : dungeonInstances.values()) {
                sessionService.cancelCleanup(inst.worldName);
            }
        }
        
        // 删除所有地牢世界
        for (String worldName : createdWorlds) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) WorldUtil.deleteWorldAndRelease(world);
        }
        
        // 关闭数据库
        if (backupStorage != null) {
            backupStorage.close();
            backupStorage = null;
        }
        
        createdWorlds.clear();
        activeDungeons.clear();
        playerDungeonWorlds.clear();
        dungeonInstances.clear();
        dungeonNameToWorld.clear();
        dungeonBosses.clear();
        dungeonPlayers.clear();
        configAppliers.clear();
        partyConfirmWaiting.clear();
        
        getLogger().info("HimDungeons 已禁用");
        instance = null;
    }
    
    private void extractDefaultResources() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();
        
        File exampleDir = new File(dataFolder, "example_dungeon");
        if (exampleDir.exists()) {
            getLogger().info("示例地牢已存在，跳过默认资源释放。");
            return;
        }
        
        getLogger().info("首次运行，释放默认资源...");
        
        // 1. 释放语言文件（主 config.yml 由 saveDefaultConfig() 自动释放）
        String[] langs = {"zh_CN.yml", "en_US.yml"};
        for (String lang : langs) {
            String path = "lang/" + lang;
            copyResourceIfNotExists(path, new File(dataFolder, path));
        }
        
        // 2. 递归复制 example_dungeon 整个目录（包括所有子目录和文件）
        try {
            copyDirectoryFromJar("example_dungeon", exampleDir);
        } catch (Exception e) {
            getLogger().warning("复制 example_dungeon 失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        getLogger().info("默认资源释放完成");
    }
    
    /**
    * 从 JAR 中递归复制资源目录到目标文件夹（仅复制不存在的文件）
    */
    private void copyDirectoryFromJar(String sourceDir, File targetDir) throws IOException {
        ClassLoader classLoader = getClassLoader();
        URL dirUrl = classLoader.getResource(sourceDir);
        if (dirUrl == null) {
            getLogger().warning("资源目录不存在: " + sourceDir);
            return;
        }
        
        if (dirUrl.getProtocol().equals("jar")) {
            // 生产环境：从 JAR 中枚举条目
            JarURLConnection conn = (JarURLConnection) dirUrl.openConnection();
            try (JarFile jarFile = conn.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();
                String prefix = sourceDir + "/";
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith(prefix) && !entry.isDirectory()) {
                        String relativePath = name.substring(prefix.length());
                        File targetFile = new File(targetDir, relativePath);
                        targetFile.getParentFile().mkdirs();
                        if (!targetFile.exists()) {
                            try (InputStream in = classLoader.getResourceAsStream(name)) {
                                if (in != null) {
                                    Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                    getLogger().info("释放资源: " + name);
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 开发环境：从文件系统直接复制
            File sourceFile = new File(dirUrl.getFile());
            if (sourceFile.exists() && sourceFile.isDirectory()) {
                copyFileSystemDirectory(sourceFile, targetDir);
            }
        }
    }
    
    /**
    * 递归复制文件系统目录（开发环境用）
    */
    private void copyFileSystemDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists()) target.mkdirs();
            for (File child : source.listFiles()) {
                copyFileSystemDirectory(child, new File(target, child.getName()));
            }
        } else {
            if (!target.exists()) {
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
    
    /**
    * 从 JAR 复制单个资源文件（如果目标不存在）
    */
    private void copyResourceIfNotExists(String resourcePath, File targetFile) {
        if (targetFile.exists()) return;
        try (InputStream in = getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                getLogger().warning("资源不存在: " + resourcePath);
                return;
            }
            targetFile.getParentFile().mkdirs();
            Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("释放资源: " + resourcePath);
        } catch (Exception e) {
            getLogger().warning("释放资源失败: " + resourcePath + " - " + e.getMessage());
        }
    }
    
    private void logSoftDependency(String pluginName) {
        if (Bukkit.getPluginManager().getPlugin(pluginName) != null) {
            getLogger().info("软依赖 " + pluginName + " 已找到");
        } else {
            getLogger().info("软依赖 " + pluginName + " 未找到");
        }
    }
    
    // ====== 命令处理 ======
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "该命令只能由玩家执行");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            MessageUtil.sendMessage(player, "使用 /" + label + " help 查看完整帮助");
            return true;
        }
        
        String subCmd = args[0].toLowerCase();
        
        // ----- 调试命令 -----
        if (subCmd.equals("blockpos")) {
            if (!player.isOp() && !player.hasPermission("himdungeons.admin")) {
                MessageUtil.sendError(player, "你没有权限使用此命令（需要 himdungeons.admin）");
                return true;
            }
            if (!DebugUtil.isDebugMode()) {
                MessageUtil.sendError(player, "该命令仅在调试模式下可用");
                return true;
            }
            if (args.length < 4) {
                MessageUtil.sendError(player, "用法: /dg blockpos <x> <y> <z>");
                return true;
            }
            try {
                int x = Integer.parseInt(args[1]);
                int y = Integer.parseInt(args[2]);
                int z = Integer.parseInt(args[3]);
                SelectionManager.Selection sel = SelectionManager.getSelection(player.getUniqueId());
                if (sel == null) {
                    MessageUtil.sendError(player, "请先用木镐（创造模式 + OP）选择区域");
                    return true;
                }
                if (x < sel.minX || x > sel.maxX || y < sel.minY || y > sel.maxY || z < sel.minZ || z > sel.maxZ) {
                    MessageUtil.sendError(player, "该方块不在选区内");
                    return true;
                }
                int relX = x - sel.minX;
                int relY = y - sel.minY;
                int relZ = z - sel.minZ;
                MessageUtil.sendMessage(player, String.format("相对坐标: (%d, %d, %d)", relX, relY, relZ));
                return true;
            } catch (NumberFormatException e) {
                MessageUtil.sendError(player, "坐标必须为整数");
                return true;
            }
        }
        
        // ----- create -----
        if (subCmd.equals("create")) {
            if (!player.isOp() && !player.hasPermission("himdungeons.admin")) {
                MessageUtil.sendError(player, "你没有权限创建地牢（需要 himdungeons.admin）");
                return true;
            }
            if (args.length < 2) {
                MessageUtil.sendError(player, "用法: /dg create <地牢名>");
                return true;
            }
            String dungeonName = args[1];
            handleCreateDungeon(player, dungeonName);
            return true;
        }
        
        // ----- start -----
        if (subCmd.equals("start")) {
            if (!player.hasPermission("himdungeons.start")) {
                MessageUtil.sendError(player, "你没有权限启动地牢（需要 himdungeons.start）");
                return true;
            }
            // 处理 /dg start party confirm
            if (args.length >= 3 && args[1].equalsIgnoreCase("party") && args[2].equalsIgnoreCase("confirm")) {
                handlePartyConfirm(player);
                return true;
            }
            if (args.length >= 2) {
                String dungeonName = args[1];
                String mode = "single";
                if (args.length >= 3) {
                    mode = args[2].toLowerCase();
                }
                startDungeon(player, dungeonName, mode);
            } else {
                startGUI.openStartMenu(player);
            }
            return true;
        }
        
        // ----- edit -----
        if (subCmd.equals("edit")) {
            if (!player.hasPermission("himdungeons.admin.gui")) {
                MessageUtil.sendError(player, "没有权限");
                return true;
            }
            // /dg edit  → 主页面
            if (args.length == 1) { editorGuiManager.openMain(player); return true; }
            String arg = args[1];
            if (arg.equals(".RootConfig")) {
                if (!player.hasPermission("himdungeons.admin.edit")) { MessageUtil.sendError(player, "无权限"); return true; }
                editorGuiManager.openRoot(player);
                return true;
            }
            if (arg.equals(".RoomList")) { editorGuiManager.openDungeonList(player, 0); return true; }
            
            String dungeon = arg;
            if (args.length == 2) { editorGuiManager.openDungeonEditor(player, dungeon); return true; }
            String sub = args[2].toLowerCase();
            switch (sub) {
                case "config" -> {
                    if (args.length == 3) { editorGuiManager.openConfigCat(player, dungeon); return true; }
                    String section = args[3].toLowerCase();
                    if (!section.matches("root|gen|world|player|boss|economy|soft")) {
                        MessageUtil.sendError(player, "无效分类: " + section); return true;
                    }
                    editorGuiManager.openConfigSection(player, dungeon, section, 0);
                    return true;
                }
                case "roomconfig" -> { editorGuiManager.openRoomList(player, dungeon, 0); return true; }
                case "codefiles" -> { editorGuiManager.openCodeFiles(player, dungeon, "", 0); return true; }
                default -> MessageUtil.sendError(player, "未知子命令: " + sub);
            }
            return true;
        }
        
        // ----- help -----
        if (subCmd.equals("help")) {
            sendHelp(player);
            return true;
        }
        
        // ----- leave -----
        if (subCmd.equals("leave")) {
            if (!player.hasPermission("himdungeons.leave")) {
                MessageUtil.sendError(player, "你没有权限离开地牢（需要 himdungeons.leave）");
                return true;
            }
            handleLeave(player);
            return true;
        }
        
        // ----- reload -----
        if (subCmd.equals("reload")) {
            if (!player.isOp() && !player.hasPermission("himdungeons.admin")) {
                MessageUtil.sendError(player, "你没有权限重载配置（需要 himdungeons.admin）");
                return true;
            }
            reloadConfig();
            MessageUtil.sendMessage(player, "配置已重载");
            return true;
        }
        
        MessageUtil.sendError(player, "未知子命令，使用 /" + label + " help 查看帮助");
        return true;
    }
    
    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== HimDungeons 命令帮助 ===");
        player.sendMessage(ChatColor.YELLOW + "/dg start [地牢名] [模式] " + ChatColor.WHITE + "启动地牢，模式: single(默认)/multi/party");
        player.sendMessage(ChatColor.YELLOW + "/dg start party confirm " + ChatColor.WHITE + "确认Party加入");
        player.sendMessage(ChatColor.YELLOW + "/dg edit [地牢名] " + ChatColor.WHITE + "管理地牢（不带参数打开管理GUI）");
        player.sendMessage(ChatColor.YELLOW + "/dg leave " + ChatColor.WHITE + "离开当前地牢");
        player.sendMessage(ChatColor.YELLOW + "/dg reload " + ChatColor.WHITE + "重载配置");
        player.sendMessage(ChatColor.YELLOW + "/dg create <地牢名> " + ChatColor.WHITE + "创建新地牢目录结构");
        player.sendMessage(ChatColor.YELLOW + "/dg help " + ChatColor.WHITE + "显示此帮助");
        if (DebugUtil.isDebugMode()) {
            player.sendMessage(ChatColor.YELLOW + "/dg blockpos <x> <y> <z> " + ChatColor.WHITE + "计算方块在选区内的相对坐标");
        }
        player.sendMessage(ChatColor.GRAY + "权限节点: himdungeons.start, himdungeons.admin, himdungeons.leave");
    }
    
    // ====== create 实现 ======
    private void handleCreateDungeon(Player player, String dungeonName) {
        DebugUtil.debug(player, "开始创建地牢目录结构: " + dungeonName);
        
        File dungeonFolder = new File(getDataFolder(), dungeonName);
        if (dungeonFolder.exists()) {
            MessageUtil.sendError(player, "地牢 '" + dungeonName + "' 已存在");
            DebugUtil.debug(player, "地牢已存在，创建失败");
            return;
        }
        
        if (!dungeonFolder.mkdirs()) {
            MessageUtil.sendError(player, "创建地牢目录失败");
            DebugUtil.debug(player, "创建地牢目录失败: " + dungeonFolder.getPath());
            return;
        }
        DebugUtil.debug(player, "创建地牢根目录: " + dungeonFolder.getPath());
        
        File configFile = new File(dungeonFolder, "config.yml");
        org.bukkit.configuration.file.FileConfiguration config = buildDefaultInstanceConfig(dungeonName);
        
        try {
            config.save(configFile);
            DebugUtil.debug(player, "创建 config.yml: " + configFile.getPath());
        } catch (Exception e) {
            MessageUtil.sendError(player, "保存 config.yml 失败: " + e.getMessage());
            DebugUtil.debug(player, "保存 config.yml 失败: " + e.getMessage());
            deleteDirectory(dungeonFolder);
            return;
        }
        
        String[] roomTypes = {"rooms"};
        for (String type : roomTypes) {
            File roomFolder = new File(dungeonFolder, type);
            if (roomFolder.mkdir()) {
                DebugUtil.debug(player, "创建房间文件夹: " + roomFolder.getPath());
            } else {
                DebugUtil.debug(player, "创建房间文件夹失败: " + roomFolder.getPath());
            }
        }
        MessageUtil.sendMessage(player, "地牢 '" + dungeonName + "' 创建成功！");
        DebugUtil.debug(player, "地牢创建完成: " + dungeonName);
    }
    
    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) deleteDirectory(child);
            }
        }
        dir.delete();
    }
    private boolean deductEnterCost(Player player, String dungeonName) {
        FileConfiguration config = ConfigUtil.getDungeonConfig(dungeonName);
        if (config == null) {
            MessageUtil.sendError(player, "无法加载地牢配置");
            return false;
        }
        
        // ---------- 1. Vault 货币扣除 ----------
        Map<String, Double> vaultCosts = ConfigUtil.getInstanceEnterVaultCost(config);
        if (!vaultCosts.isEmpty() && Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try {
                net.milkbowl.vault.economy.Economy economy = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class).getProvider();
                if (economy != null) {
                    // 简单累加所有条目（实际可按权限组选择，这里简化）
                    double total = 0.0;
                    for (double v : vaultCosts.values()) total += v;
                    if (total > 0) {
                        if (!economy.has(player, total)) {
                            MessageUtil.sendError(player, "余额不足，需要 " + total + " 金币");
                            return false;
                        }
                        economy.withdrawPlayer(player, total);
                        MessageUtil.sendMessage(player, "扣除 " + total + " 金币");
                    }
                }
            } catch (Exception e) {
                MessageUtil.sendError(player, "Vault 扣费失败: " + e.getMessage());
                return false;
            }
        }
        
        // ---------- 2. PlayerPoints 点券扣除 ----------
        Map<String, Integer> ppCosts = ConfigUtil.getInstanceEnterPlayerPointsCost(config);
        if (!ppCosts.isEmpty() && Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
            try {
                Object ppPlugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
                if (ppPlugin != null) {
                    Method getAPIMethod = ppPlugin.getClass().getMethod("getAPI");
                    Object api = getAPIMethod.invoke(ppPlugin);
                    if (api != null) {
                        // 检查点券余额（需要先实现look方法）
                        Method lookMethod = api.getClass().getMethod("look", UUID.class);
                        int balance = (int) lookMethod.invoke(api, player.getUniqueId());
                        int total = 0;
                        for (int v : ppCosts.values()) total += v;
                        if (balance < total) {
                            MessageUtil.sendError(player, "点券不足，需要 " + total + " 点券，当前有 " + balance);
                            return false;
                        }
                        // 扣除点券
                        Method giveMethod = api.getClass().getMethod("take", UUID.class, int.class);
                        giveMethod.invoke(api, player.getUniqueId(), total);
                        MessageUtil.sendMessage(player, "扣除 " + total + " 点券");
                    }
                }
            } catch (Exception e) {
                MessageUtil.sendError(player, "PlayerPoints 扣费失败: " + e.getMessage());
                return false;
            }
        }
        
        // ---------- 3. 执行控制台命令（扣费成功后执行） ----------
        List<String> commands = ConfigUtil.getInstanceEnterConsoleCommands(config);
        for (String cmd : commands) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", player.getName()));
            } catch (Exception e) {
                getLogger().warning("执行入场命令失败: " + cmd);
            }
        }
        
        return true;
    }
    // ====== 核心地牢启动方法 ======
    public void startDungeon(Player player, String dungeonName, String mode) {
        if (!getDungeonConfigExists(dungeonName)) {
            MessageUtil.sendError(player, "地牢 '" + dungeonName + "' 不存在或配置缺失");
            return;
        }
        org.bukkit.configuration.file.FileConfiguration config = ConfigUtil.getDungeonConfig(dungeonName);
        if (config == null || !ConfigUtil.isInstanceEnabled(config)) {
            MessageUtil.sendError(player, "地牢 '" + dungeonName + "' 未启用");
            return;
        }
        
        
        // 检查是否已有活跃地牢世界
        String existingWorld = dungeonNameToWorld.get(dungeonName);
        if (existingWorld != null) {
            DungeonInstance instance = dungeonInstances.get(existingWorld);
            if (instance != null && !instance.isFinished) {
                if (!instance.players.contains(player.getUniqueId())) {
                    // 重入：由 sessionService 处理备份+清空+传送
                    sessionService.enter(player, instance);
                    MessageUtil.sendMessage(player, "重新进入未完成的地牢 " + dungeonName);
                } else {
                    MessageUtil.sendMessage(player, "你已在该地牢中");
                }
                return;
            }
        }
        
        switch (mode.toLowerCase()) {
            case "single":
            MessageUtil.sendMessage(player, "正在生成地牢 '" + dungeonName + "'，请稍候...");
            startDungeonAsync(player, dungeonName);
            break;
            
            case "multi":
            handleMultiplayerStart(player, dungeonName);
            break;
            
            case "party":
            handlePartyStart(player, dungeonName);
            break;
            
            default:
            MessageUtil.sendError(player, "未知模式: " + mode + "，可用: single, multi, party");
        }
        // 在 startDungeon 方法中，检查并扣除入场费
        if (!deductEnterCost(player, dungeonName)) {
            return; // 扣费失败，不启动
        }
    }
    
    // ----- 组队模式（multi）：直接加入附近3格内潜行玩家 -----
    private void handleMultiplayerStart(Player leader, String dungeonName) {
        List<Player> players = new ArrayList<>();
        players.add(leader);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(leader)) continue;
            if (p.isSneaking() && p.getWorld().equals(leader.getWorld()) && p.getLocation().distance(leader.getLocation()) <= 3.0) {
                players.add(p);
            }
        }
        if (players.size() == 1) {
            MessageUtil.sendMessage(leader, "未找到附近3格内潜行的玩家，以单人模式启动");
            startDungeonAsync(leader, dungeonName);
            return;
        }
        
        String names = players.stream().map(Player::getName).collect(Collectors.joining(", "));
        MessageUtil.sendMessage(leader, "组队加入 " + dungeonName + "，队员: " + names);
        // 为每个玩家启动地牢（但实际上是同一个实例，需要单独处理）
        // 这里简化：只给队长生成，其他队员通过回调传送
        startDungeonAsync(leader, dungeonName, players);
    }
    
    // 带玩家列表的异步启动（支持多人传送）
private void startDungeonAsync(Player player, String dungeonName) {
    startDungeonAsync(player, dungeonName, Collections.singletonList(player), false);
}

private void startDungeonAsync(Player leader, String dungeonName, List<Player> players) {
    startDungeonAsync(leader, dungeonName, players, false);
}

private void startDungeonAsync(Player leader, String dungeonName, List<Player> players, boolean testMode) {
    try {
        DungeonGenerator generator = new DungeonGenerator(this, dungeonName, leader, testMode);
        generator.generate(success -> {
            if (!success) {
                MessageUtil.sendError(leader, "地牢生成失败，请查看控制台错误");
                return;
            }
            Bukkit.getScheduler().runTaskLater(this, () -> {
                String worldName = leader.getWorld().getName();
                DungeonInstance instance = dungeonInstances.get(worldName);
                if (instance != null && isDungeonWorld(worldName)) {
                    for (Player p : players) {
                        if (!p.equals(leader)) {
                            sessionService.enter(p, instance);
                            MessageUtil.sendMessage(p, "已加入地牢 " + dungeonName);
                        }
                    }
                } else {
                    MessageUtil.sendError(leader, "无法获取地牢世界，请手动传送");
                }
            }, 20L);
        });
    } catch (Exception e) {
        e.printStackTrace();
        MessageUtil.sendError(leader, "启动地牢失败: " + e.getMessage());
    }
}
public void generateTestWorld(String dungeon, Player player) {
    deleteTestWorld(dungeon);
    MessageUtil.sendMessage(player, "§e正在生成测试世界...");
    startDungeonAsync(player, dungeon, Collections.singletonList(player), true);
}

public void deleteTestWorld(String dungeon) {
    String prefix = "dungeon_" + dungeon + "_test_";
    // 已加载的世界：走 WorldUtil 正常卸载 + 删目录
    for (World w : new ArrayList<>(Bukkit.getWorlds())) {
        if (w.getName().startsWith(prefix)) {
            WorldUtil.deleteWorldAndRelease(w);
        }
    }
    // 未加载的孤儿目录：直接递归删除
    File[] dirs = Bukkit.getWorldContainer().listFiles(File::isDirectory);
    if (dirs != null) {
        for (File d : dirs) {
            if (d.getName().startsWith(prefix)) {
                deleteDirectoryRecursive(d);
            }
        }
    }
}

private void deleteDirectoryRecursive(File f) {
    if (f == null || !f.exists()) return;
    if (f.isDirectory()) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteDirectoryRecursive(c);
    }
    f.delete();
}
    // ----- Party 模式 -----
    private void handlePartyStart(Player player, String dungeonName) {
        if (Bukkit.getPluginManager().getPlugin("Parties") == null) {
            MessageUtil.sendError(player, "Party插件未安装，无法使用Party模式");
            return;
        }
        org.bukkit.configuration.file.FileConfiguration config = ConfigUtil.getDungeonConfig(dungeonName);
        if (config == null || !ConfigUtil.isInstanceAllowParties(config)) {
            MessageUtil.sendError(player, "该地牢不允许Party加入");
            return;
        }
        
        try {
            // 向队伍成员发送邀请（实际应使用Party API）
            partyConfirmWaiting.put(player.getUniqueId(), dungeonName);
            MessageUtil.sendMessage(player, "已向队伍成员发送加入邀请，请队员输入 /dg start party confirm 确认加入");
        } catch (Exception e) {
            MessageUtil.sendError(player, "Party功能未正确集成，请检查插件版本");
            e.printStackTrace();
        }
    }
    
    // ----- Party确认处理 -----
    private void handlePartyConfirm(Player player) {
        if (!partyConfirmWaiting.containsKey(player.getUniqueId())) {
            MessageUtil.sendError(player, "没有待确认的Party加入请求");
            return;
        }
        String dungeonName = partyConfirmWaiting.remove(player.getUniqueId());
        MessageUtil.sendMessage(player, "确认加入地牢 '" + dungeonName + "'，正在生成...");
        startDungeonAsync(player, dungeonName);
    }
    
    // ====== leave 处理 ======
    private void handleLeave(Player player) {
        sessionService.leave(player);
    }
    
    private boolean getDungeonConfigExists(String dungeonName) {
        return new File(getDataFolder(), dungeonName + "/config.yml").exists();
    }
    
    // ====== Tab 补全 ======
    private class DungeonTabCompleter implements TabCompleter {
        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) {
                List<String> subs = new ArrayList<>(Arrays.asList("start", "edit", "leave", "reload", "help", "create"));
                if (DebugUtil.isDebugMode()) subs.add("blockpos");
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    if (!p.hasPermission("himdungeons.start")) subs.remove("start");
                    if (!p.hasPermission("himdungeons.leave")) subs.remove("leave");
                    if (!p.hasPermission("himdungeons.admin")) {
                        subs.remove("edit");
                        subs.remove("reload");
                        subs.remove("create");
                        subs.remove("blockpos");
                    }
                }
                return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
            } else if (args.length == 2) {
                String sub = args[0].toLowerCase();
                if (sub.equals("start") || sub.equals("edit") || sub.equals("create")) {
                    if (sub.equals("create")) {
                        return Collections.emptyList();
                    }
                    File dataFolder = getDataFolder();
                    File[] dirs = dataFolder.listFiles(File::isDirectory);
                    if (dirs == null) return Collections.emptyList();
                    List<String> names = new ArrayList<>();
                    for (File dir : dirs) {
                        if (new File(dir, "config.yml").exists()) {
                            names.add(dir.getName());
                        }
                    }
                    return names.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                }if (sub.equals("edit")) {
                    List<String> opts = new ArrayList<>(Arrays.asList(".RootConfig", ".RoomList"));
                    File dataFolder = getDataFolder();
                    File[] dirs = dataFolder.listFiles(File::isDirectory);
                    if (dirs != null) for (File dir : dirs)
                        if (new File(dir, "config.yml").exists()) opts.add(dir.getName());
                    return opts.stream().filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
                }
            } else if (args.length == 3) {
                String sub = args[0].toLowerCase();
                if (sub.equals("start")) {
                    return Arrays.asList("single", "multi", "party").stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                }
                if ("edit".equals(args[0].toLowerCase()) && !args[1].startsWith(".")) {
                    return Arrays.asList("config", "roomConfig", "codeFiles").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                }
            } else if (args.length == 4) {
                if ("edit".equals(args[0].toLowerCase()) && args.length == 4
                && "config".equalsIgnoreCase(args[2])) {
                    return Arrays.asList("root", "gen", "world", "player", "boss", "economy", "soft")
                    .stream().filter(s -> s.startsWith(args[3].toLowerCase())).collect(Collectors.toList());
                }
            }
            return Collections.emptyList();
        }
    }
    
    // ====== Help 主题 ======
    private class DungeonHelpTopic extends org.bukkit.help.HelpTopic {
        @Override
        public String getFullText(CommandSender sender) {
            String base = ChatColor.GOLD + "=== HimDungeons 帮助 ===\n" +
            ChatColor.YELLOW + "/dungeon start [地牢名] [模式] " + ChatColor.WHITE + "启动地牢，模式: single(默认)/multi/party\n" +
            ChatColor.YELLOW + "/dungeon start party confirm " + ChatColor.WHITE + "确认Party加入\n" +
            ChatColor.YELLOW + "/dungeon edit [地牢名] " + ChatColor.WHITE + "管理地牢（不带参数打开管理GUI）\n" +
            ChatColor.YELLOW + "/dungeon leave " + ChatColor.WHITE + "离开当前地牢\n" +
            ChatColor.YELLOW + "/dungeon reload " + ChatColor.WHITE + "重载配置\n" +
            ChatColor.YELLOW + "/dungeon create <地牢名> " + ChatColor.WHITE + "创建新地牢目录结构\n" +
            ChatColor.YELLOW + "/dungeon help " + ChatColor.WHITE + "显示此帮助";
            if (DebugUtil.isDebugMode()) {
                base += "\n" + ChatColor.YELLOW + "/dungeon blockpos <x> <y> <z> " + ChatColor.WHITE + "计算方块在选区内的相对坐标";
            }
            return base;
        }
        
        @Override
        public String getName() {
            return "HimDungeons";
        }
        
        @Override
        public boolean canSee(CommandSender sender) {
            return sender.hasPermission("himdungeons.start") ||
            sender.hasPermission("himdungeons.leave") ||
            sender.hasPermission("himdungeons.admin");
        }
    }
    
    // ====== 公共 API ======
    public static HimDungeons getInstance() {
        return instance;
    }
    
    public void addCreatedWorld(String worldName) {
        createdWorlds.add(worldName);
    }
    
    public void registerDungeonWorld(String worldName, String dungeonName) {
        activeDungeons.put(worldName, dungeonName);
        createdWorlds.add(worldName);
    }
    
    public void unregisterDungeonWorld(String worldName) {
        activeDungeons.remove(worldName);
    }
    
    public boolean isDungeonWorld(String worldName) {
        return activeDungeons.containsKey(worldName);
    }
    
    public String getPlayerDungeonWorld(UUID playerId) {
        return playerDungeonWorlds.get(playerId);
    }
    
    public void setPlayerDungeonWorld(UUID playerId, String worldName) {
        if (worldName == null) playerDungeonWorlds.remove(playerId);
        else playerDungeonWorlds.put(playerId, worldName);
    }
    public void registerConfigApplier(String worldName, ConfigApplier applier) {
        configAppliers.put(worldName, applier);
    }
    
    public ConfigApplier getConfigApplier(String worldName) {
        return configAppliers.get(worldName);
    }
    
    public void setPlayerBackup(UUID uuid, ItemStack[] backup) {
        playerBackups.put(uuid, backup);
    }
    
    public ItemStack[] getPlayerBackup(UUID uuid) {
        return playerBackups.remove(uuid);
    }
    
    /**
    * 恢复玩家背包（从 player_data.json 加载并删除）
    * @param player 玩家
    * @param dungeonName 地牢名称
    */
    public void restorePlayerInventory(Player player, String dungeonName) {
        if (player == null || !player.isOnline()) return;
        if (backupStorage != null) {
            backupStorage.restore(player.getUniqueId(), dungeonName, player);
            player.updateInventory();
        }
    }
    // 新增字段
    public final Map<String, BossData> dungeonBosses = new ConcurrentHashMap<>();
    
    public static class BossData {
        public final LivingEntity entity;
        public final ConfigApplier applier;
        public final String dungeonName;
        public final String worldName;
        public final Location spawnLocation;
        public final RoomInstance bossRoom; // 新增
        
        public BossData(LivingEntity entity, ConfigApplier applier, String dungeonName,
            String worldName, Location spawnLocation, RoomInstance bossRoom) {
                this.entity = entity;
                this.applier = applier;
                this.dungeonName = dungeonName;
                this.worldName = worldName;
                this.spawnLocation = spawnLocation;
                this.bossRoom = bossRoom;
            }
        }
        
        public void registerBoss(String worldName, BossData data) { dungeonBosses.put(worldName, data); }
        public BossData getBossData(String worldName) { return dungeonBosses.get(worldName); }
        public void removeBossData(String worldName) { dungeonBosses.remove(worldName); }
        
        // 维护地牢世界状态
        public final Map<String, DungeonInstance> dungeonInstances = new ConcurrentHashMap<>();
        
        public static class DungeonInstance {
            public final String dungeonName;
            public final String worldName;
            public final Set<UUID> players = ConcurrentHashMap.newKeySet();
            public long lastActiveTime = 0;
            // public int cleanupTaskId = -1;   ← 可删除
            public boolean isFinished = false;
            public final Location spawnLocation;
            public List<RoomInstance> placedRooms;
            public boolean deepCloneActive = false;
            
            public DungeonInstance(String dungeonName, String worldName, Location spawnLocation) {
                this.dungeonName = dungeonName;
                this.worldName = worldName;
                this.spawnLocation = spawnLocation;
                this.placedRooms = null;
            }
        }
        public final Map<String, String> dungeonNameToWorld = new ConcurrentHashMap<>();
        
        //地牢清理计时器
        // ====== 地牢清理计时器（委托给 sessionService） ======
        public void startCleanupTimer(DungeonInstance instance) {
            if (sessionService != null && instance != null) {
                sessionService.startCleanup(instance.worldName);
            }
        }
        
        public void cancelCleanupTask(String worldName) {
            if (sessionService != null) {
                sessionService.cancelCleanup(worldName);
            }
        }
        
        public void forceFinishDungeon(DungeonInstance instance) {
            if (instance.isFinished) return;
            instance.isFinished = true;
            
            World world = Bukkit.getWorld(instance.worldName);
            for (UUID uuid : instance.players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    if (instance.deepCloneActive) {
                        backupStorage.restore(uuid, instance.dungeonName, p);
                        p.updateInventory();
                    }
                    p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                    p.sendMessage("§c地牢已超时关闭，你被传送回主世界");
                    setPlayerDungeonWorld(uuid, null);
                } else {
                    // 玩家已离线：正常场景下 DB 已无行；若仍有残留（例如进程崩溃前），
                    // 标为 PENDING_RESTORE，下次登录时恢复
                    if (instance.deepCloneActive && backupStorage.hasBackup(uuid, instance.dungeonName)) {
                        backupStorage.updateStatus(uuid, instance.dungeonName,
                            BackupStorage.Status.PENDING_RESTORE);
                        }
                    }
                    clearPlayerCheckpoint(uuid);
                }
                
                if (world != null) WorldUtil.deleteWorldAndRelease(world);
                unregisterDungeonWorld(instance.worldName);
                dungeonInstances.remove(instance.worldName);
                dungeonNameToWorld.remove(instance.dungeonName);
                dungeonBosses.remove(instance.worldName);
                dungeonPlayers.remove(instance.worldName);
                configAppliers.remove(instance.worldName);
                if (sessionService != null) {
                    sessionService.clearOfflineRecordsForWorld(instance.worldName);
                }
            }
            public void addPlayerToDungeon(String worldName, UUID playerId) {
                dungeonPlayers.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet()).add(playerId);
            }
            
            public DungeonInstance getDungeonInstanceByWorld(String worldName) {
                return dungeonInstances.get(worldName);
            }
            
            public Set<UUID> getDungeonPlayers(String worldName) {
                return dungeonPlayers.get(worldName);
            }
            
            public void removeDungeonPlayers(String worldName) {
                dungeonPlayers.remove(worldName);
            }
            
            
            
            private final Map<UUID, Location> playerCheckpoints = new ConcurrentHashMap<>();
            
            public void setPlayerCheckpoint(UUID uuid, Location loc) {
                if (loc == null) playerCheckpoints.remove(uuid);
                else playerCheckpoints.put(uuid, loc);
            }
            
            public Location getPlayerCheckpoint(UUID uuid) {
                return playerCheckpoints.get(uuid);
            }
            
            public void clearPlayerCheckpoint(UUID uuid) {
                playerCheckpoints.remove(uuid);
            }
            public void scanAndRegisterRooms(String dungeon) {
                ConfigUtil.refreshStructureWeights(dungeon);
                getLogger().info("已扫描并注册新房间: " + dungeon);
            }
            
        
            public DungeonStartGUI getStartGUI() { return startGUI; }
public org.bukkit.configuration.file.FileConfiguration buildDefaultInstanceConfig(String dungeonName) {
    org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
    config.set("dungeonInstance.enable", true);
    config.set("dungeonInstance.displayName", dungeonName);
    config.set("dungeonInstance.fileName", dungeonName);
    config.set("dungeonInstance.msgPrefix", "&a[" + dungeonName + "] &r");
    config.set("dungeonInstance.minRoomAmount", 5);
    config.set("dungeonInstance.maxRoomAmount", 12);
    config.set("dungeonInstance.dfsMaxDeep", 8);
    config.set("dungeonInstance.useBfsCheck", false);
    config.set("dungeonInstance.useAabb", true);
    config.set("dungeonInstance.structureChoose.combat", 100);
    config.set("dungeonInstance.structureChoose.straight", 80);
    config.set("dungeonInstance.structureChoose.corner", 60);
    config.set("dungeonInstance.structureChoose.tCorner", 40);
    config.set("dungeonInstance.structureChoose.cross", 20);
    config.set("dungeonInstance.structureChoose.stair", 20);
    config.set("dungeonInstance.structureChoose.boss", 30);
    config.set("dungeonInstance.structureChoose.fallback", 10);
    config.set("dungeonInstance.disconnectedRoomDistance", 0);
    config.set("dungeonInstance.allowRoomReuse", false);
    config.set("dungeonInstance.allowDoorReuse", false);
    config.set("dungeonInstance.allowFallbackDoorReuse", false);
    config.set("dungeonInstance.startToBossRoomAmmount", "10 10");
    config.set("dungeonInstance.blockRoomGenerateBlocks", new ArrayList<>());
    config.set("dungeonInstance.generatorChoose", new ArrayList<>());
    config.set("dungeonInstance.allowParties", false);
    config.set("dungeonInstance.allowSlimefun", false);
    config.set("dungeonInstance.bossType", Collections.singletonList("zombie"));
    config.set("dungeonInstance.bossNbt", Collections.singletonList("{Health:100.0f}"));
    config.set("dungeonInstance.useMythicMobForBoss", false);
    config.set("dungeonInstance.mythicMobBossName", "");
    config.set("dungeonInstance.keepInventory", false);
    config.set("dungeonInstance.deepCloneInventory", false);
    config.set("dungeonInstance.time", 0);
    config.set("dungeonInstance.dungeonTime", 1200);
    config.set("dungeonInstance.advanceTime", false);
    config.set("dungeonInstance.weather", "clear");
    config.set("dungeonInstance.difficulty", "normal");
    config.set("dungeonInstance.allowMobs", Collections.singletonList("all"));
    config.set("dungeonInstance.useMythicMobs", false);
    config.set("dungeonInstance.commandsAllowListType", "white");
    config.set("dungeonInstance.commandsAllow", new ArrayList<>());
    config.set("dungeonInstance.buildSettingsListType", "white");
    config.set("dungeonInstance.buildSettings", new ArrayList<>());
    config.set("dungeonInstance.breakSettingsListType", "white");
    config.set("dungeonInstance.breakSettings", new ArrayList<>());
    config.set("dungeonInstance.interactionSettingsListType", "white");
    config.set("dungeonInstance.interactionSettings", new ArrayList<>());
    config.set("dungeonInstance.lobby", "world 0 64 0");
    config.set("dungeonInstance.rewards.vault", new HashMap<>());
    config.set("dungeonInstance.rewards.playerPoints", new HashMap<>());
    config.set("dungeonInstance.rewards.consoleCommands", new ArrayList<>());
    config.set("dungeonInstance.enterCost.vault", new HashMap<>());
    config.set("dungeonInstance.enterCost.playerPoints", new HashMap<>());
    config.set("dungeonInstance.enterCost.consoleCommands", new ArrayList<>());
    return config;
}
        }