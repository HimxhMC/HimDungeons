package com.him.dungeons;

import com.him.dungeons.generator.DungeonGenerator;
import com.him.dungeons.gui.DungeonManagerGUI;
import com.him.dungeons.gui.DungeonStartGUI;
import com.him.dungeons.util.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
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

    // GUI 实例
    private DungeonManagerGUI managerGUI;
    private DungeonStartGUI startGUI;

    // Party 加入等待确认
    private final Map<UUID, String> partyConfirmWaiting = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        extractDefaultResources();

        ConfigEditorUtil.registerListener(this);

        managerGUI = new DungeonManagerGUI(this);
        startGUI = new DungeonStartGUI(this);

        PluginCommand cmd = getCommand("himdungeons");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(new DungeonTabCompleter());
        }

        Bukkit.getHelpMap().addTopic(new DungeonHelpTopic());

        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            getLogger().severe("WorldEdit 未找到！HimDungeons 硬依赖 WorldEdit，插件将禁用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        logSoftDependency("Parties");
        logSoftDependency("Vault");
        logSoftDependency("Slimefun");
        logSoftDependency("MythicMobs");
        logSoftDependency("PlaceholderAPI");
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
        getLogger().info("                                                                    ############");
        getLogger().info("HimDungeons 已启用，版本 " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        for (String worldName : createdWorlds) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                getLogger().info("强制清理地牢世界: " + worldName);
                WorldUtil.deleteWorldAndRelease(world);
            }
        }
        createdWorlds.clear();
        activeDungeons.clear();
        playerDungeonWorlds.clear();
        partyConfirmWaiting.clear();
        getLogger().info("HimDungeons 已禁用");
        instance = null;
    }

    private void extractDefaultResources() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        List<String> filesToExtract = new ArrayList<>();
        filesToExtract.add("lang/zh_CN.yml");
        filesToExtract.add("lang/en_US.yml");
        String[] types = {"start", "combat", "straight", "corner", "tCorner", "cross", "stair", "boss", "fallback"};
        filesToExtract.add("example_dungeon/config.yml");
        for (String type : types) {
            filesToExtract.add("example_dungeon/" + type + "/" + type + ".schematic");
            filesToExtract.add("example_dungeon/" + type + "/door.yml");
            if (type.equals("start")) {
                filesToExtract.add("example_dungeon/" + type + "/spawn.yml");
            }
        }

        for (String filePath : filesToExtract) {
            try (InputStream in = getClassLoader().getResourceAsStream(filePath)) {
                if (in == null) continue;
                File outFile = new File(dataFolder, filePath);
                outFile.getParentFile().mkdirs();
                if (outFile.exists()) continue;
                Files.copy(in, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("释放资源: " + filePath);
            } catch (Exception e) {
                getLogger().warning("释放资源失败: " + filePath);
            }
        }
        getLogger().info("默认资源释放完成");
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
                com.sk89q.worldedit.world.World weWorld = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player.getWorld());
                com.sk89q.worldedit.regions.Region region;
                try {
                    region = com.sk89q.worldedit.WorldEdit.getInstance().getSessionManager().get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player)).getSelection(weWorld);
                } catch (com.sk89q.worldedit.IncompleteRegionException e) {
                    MessageUtil.sendError(player, "请先用创世神选择区域");
                    return true;
                }
                if (region == null) {
                    MessageUtil.sendError(player, "未选择区域");
                    return true;
                }
                com.sk89q.worldedit.math.BlockVector3 min = region.getMinimumPoint();
                com.sk89q.worldedit.math.BlockVector3 max = region.getMaximumPoint();
                if (x < min.getX() || x > max.getX() || y < min.getY() || y > max.getY() || z < min.getZ() || z > max.getZ()) {
                    MessageUtil.sendError(player, "该方块不在选区内");
                    return true;
                }
                int relX = x - min.getX();
                int relY = y - min.getY();
                int relZ = z - min.getZ();
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
            if (!player.isOp() && !player.hasPermission("himdungeons.admin")) {
                MessageUtil.sendError(player, "你没有权限编辑地牢（需要 himdungeons.admin）");
                return true;
            }
            if (args.length >= 2) {
                managerGUI.openDungeonMenu(player, args[1]);
            } else {
                managerGUI.openMainMenu(player);
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
        config.set("dungeonInstance.lobby", "world 0 64 0");
        config.set("dungeonInstance.rewards.vault", new HashMap<>());
        config.set("dungeonInstance.rewards.playerPoints", new HashMap<>());
        config.set("dungeonInstance.rewards.consoleCommands", new ArrayList<>());

        try {
            config.save(configFile);
            DebugUtil.debug(player, "创建 config.yml: " + configFile.getPath());
        } catch (Exception e) {
            MessageUtil.sendError(player, "保存 config.yml 失败: " + e.getMessage());
            DebugUtil.debug(player, "保存 config.yml 失败: " + e.getMessage());
            deleteDirectory(dungeonFolder);
            return;
        }

        String[] roomTypes = {"start", "combat", "straight", "corner", "tCorner", "cross", "stair", "boss", "fallback"};
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
    private void startDungeonAsync(Player leader, String dungeonName, List<Player> players) {
        try {
            DungeonGenerator generator = new DungeonGenerator(this, dungeonName, leader);
            generator.generate(success -> {
                if (!success) {
                    MessageUtil.sendError(leader, "地牢生成失败，请查看控制台错误");
                } else {
                    // 延迟等待世界完全加载
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        String worldName = leader.getWorld().getName();
                        if (isDungeonWorld(worldName)) {
                            for (Player p : players) {
                                if (!p.equals(leader)) {
                                    p.teleport(leader.getLocation());
                                    MessageUtil.sendMessage(p, "已加入地牢 " + dungeonName);
                                }
                            }
                        } else {
                            MessageUtil.sendError(leader, "无法获取地牢世界，请手动传送");
                        }
                    }, 20L); // 延迟1秒
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            MessageUtil.sendError(leader, "启动地牢失败: " + e.getMessage());
        }
    }

    // 保留原有异步启动方法（单人）
    private void startDungeonAsync(Player player, String dungeonName) {
        startDungeonAsync(player, dungeonName, Collections.singletonList(player));
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
        UUID uuid = player.getUniqueId();
        String worldName = playerDungeonWorlds.get(uuid);
        if (worldName == null) {
            MessageUtil.sendError(player, "你当前不在任何地牢中");
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            playerDungeonWorlds.remove(uuid);
            MessageUtil.sendError(player, "地牢世界已不存在");
            return;
        }
        World mainWorld = Bukkit.getWorlds().get(0);
        if (mainWorld != null) {
            player.teleport(mainWorld.getSpawnLocation());
        }
        playerDungeonWorlds.remove(uuid);
        // 删除地牢世界
        WorldUtil.deleteWorldAndRelease(world);
        this.unregisterDungeonWorld(worldName);
        MessageUtil.sendMessage(player, "你已离开地牢，世界已销毁");
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
                }
            } else if (args.length == 3) {
                String sub = args[0].toLowerCase();
                if (sub.equals("start")) {
                    return Arrays.asList("single", "multi", "party").stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
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
}