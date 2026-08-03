package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfigEditorUtil {

    private static final Map<UUID, InputSession> inputSessions = new ConcurrentHashMap<>();
    private static final Map<UUID, EditorContext> editorContexts = new ConcurrentHashMap<>();
    private static ConfigEditorListener listenerInstance;
    private static final int PAGE_SIZE = 45;
    private static final Map<String, String> SOFT_DEPEND_MAP = new HashMap<>();

    static {
        SOFT_DEPEND_MAP.put("dungeonInstance.allowParties", "Parties");
        SOFT_DEPEND_MAP.put("dungeonInstance.allowSlimefun", "Slimefun");
        SOFT_DEPEND_MAP.put("dungeonInstance.useMythicMobs", "MythicMobs");
        SOFT_DEPEND_MAP.put("dungeonInstance.rewards.vault", "Vault");
        SOFT_DEPEND_MAP.put("dungeonInstance.rewards.playerPoints", "PlayerPoints");
    }

    private ConfigEditorUtil() {}

    private static class EditorContext {
        String dungeonName; // null 表示全局， "__BATCH__" 表示批量
        FileConfiguration config;
        List<ConfigItem> allItems;
        Inventory inventory;
        int currentPage;
        Map<Integer, ConfigItem> slotMap;
        boolean batchMode;

        EditorContext(String dungeonName, FileConfiguration config, List<ConfigItem> allItems, Inventory inv, int page, boolean batchMode) {
            this.dungeonName = dungeonName;
            this.config = config;
            this.allItems = allItems;
            this.inventory = inv;
            this.currentPage = page;
            this.slotMap = new HashMap<>();
            this.batchMode = batchMode;
        }
    }

    // 打开单个地牢或全局
    public static void openEditor(Player player, String dungeonName) {
        openEditorInternal(player, dungeonName, false);
    }

    // 打开批量编辑（所有地牢）
    public static void openBatchEditor(Player player) {
        openEditorInternal(player, "__BATCH__", true);
    }

    private static void openEditorInternal(Player player, String dungeonName, boolean batchMode) {
        FileConfiguration config;
        boolean isGlobal = (dungeonName == null);
        if (isGlobal) {
            config = ConfigUtil.getGlobalConfig();
        } else if (batchMode) {
            // 批量模式：使用第一个地牢的配置作为模板，仅用于显示当前值
            File dataFolder = HimDungeons.getInstance().getDataFolder();
            File[] dirs = dataFolder.listFiles(File::isDirectory);
            FileConfiguration template = null;
            if (dirs != null) {
                for (File dir : dirs) {
                    if (new File(dir, "config.yml").exists()) {
                        template = ConfigUtil.getDungeonConfig(dir.getName());
                        if (template != null) break;
                    }
                }
            }
            if (template == null) {
                MessageUtil.sendError(player, "没有可用的地牢配置作为模板");
                return;
            }
            config = template;
        } else {
            config = ConfigUtil.getDungeonConfig(dungeonName);
            if (config == null) {
                MessageUtil.sendError(player, "地牢 '" + dungeonName + "' 不存在或缺少 config.yml");
                return;
            }
        }

        List<ConfigItem> allItems = buildAllConfigItems(config);
        int totalPages = (int) Math.ceil(allItems.size() / (double) PAGE_SIZE);
        if (totalPages == 0) totalPages = 1;

        String title = ChatColor.BLUE + "配置编辑器 - " + (batchMode ? "批量" : (isGlobal ? "全局" : dungeonName));
        Inventory inv = Bukkit.createInventory(null, 54, title);

        EditorContext ctx = new EditorContext(dungeonName, config, allItems, inv, 0, batchMode);
        editorContexts.put(player.getUniqueId(), ctx);
        fillPage(player, ctx);

        player.openInventory(inv);

        if (listenerInstance == null) {
            listenerInstance = new ConfigEditorListener();
            Bukkit.getPluginManager().registerEvents(listenerInstance, HimDungeons.getInstance());
        }
    }

    private static void fillPage(Player player, EditorContext ctx) {
        Inventory inv = ctx.inventory;
        inv.clear();
        ctx.slotMap.clear();

        int start = ctx.currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, ctx.allItems.size());
        List<ConfigItem> pageItems = ctx.allItems.subList(start, end);

        int slot = 9;
        for (ConfigItem item : pageItems) {
            if (slot >= 54) break;
            // 检查软依赖
            if (SOFT_DEPEND_MAP.containsKey(item.configKey)) {
                String pluginName = SOFT_DEPEND_MAP.get(item.configKey);
                if (Bukkit.getPluginManager().getPlugin(pluginName) == null) {
                    ItemStack disabled = item.buildItemStack();
                    ItemMeta meta = disabled.getItemMeta();
                    meta.setDisplayName(ChatColor.RED + item.displayName + ChatColor.GRAY + " [缺失" + pluginName + "]");
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "需要插件: " + pluginName);
                    lore.add(ChatColor.DARK_GRAY + "该配置不可用");
                    meta.setLore(lore);
                    disabled.setItemMeta(meta);
                    inv.setItem(slot, disabled);
                    slot++;
                    continue;
                }
            }
            ItemStack is = item.buildItemStack();
            inv.setItem(slot, is);
            ctx.slotMap.put(slot, item);
            slot++;
        }

        // 导航栏
        ItemStack prevPage = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prevPage.getItemMeta();
        prevMeta.setDisplayName(ChatColor.GOLD + "上一页");
        prevPage.setItemMeta(prevMeta);

        ItemStack nextPage = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = nextPage.getItemMeta();
        nextMeta.setDisplayName(ChatColor.GOLD + "下一页");
        nextPage.setItemMeta(nextMeta);

        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = pageInfo.getItemMeta();
        int totalPages = (int) Math.ceil(ctx.allItems.size() / (double) PAGE_SIZE);
        infoMeta.setDisplayName(ChatColor.YELLOW + "第 " + (ctx.currentPage + 1) + " / " + totalPages + " 页");
        pageInfo.setItemMeta(infoMeta);

        inv.setItem(0, prevPage);
        inv.setItem(4, pageInfo);
        inv.setItem(8, nextPage);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "关闭并保存");
        close.setItemMeta(closeMeta);
        inv.setItem(53, close);
    }

    // ==================== 构建所有配置项（全局+实例） ====================
    private static List<ConfigItem> buildAllConfigItems(FileConfiguration config) {
        List<ConfigItem> items = new ArrayList<>();
        // 全局配置
        items.add(new ConfigItem(Material.BOOK, "语言", "lang",
                ConfigUtil.getGlobalLang(), "string", "zh_CN", null));
        items.add(new ConfigItem(Material.NAME_TAG, "消息前缀", "msgPrefix",
                ConfigUtil.getGlobalMsgPrefix(), "string", "&a[HimDungeons] &r", null));
        items.add(new ConfigItem(Material.CLOCK, "生成冷却(秒)", "generateCooldown",
                ConfigUtil.getGlobalGenerateCooldown(), "int", 0, null));

        // 实例配置（dungeonInstance.*）
        items.add(new ConfigItem(Material.LEVER, "启用", "dungeonInstance.enable",
                ConfigUtil.isInstanceEnabled(config), "boolean", true, null));
        items.add(new ConfigItem(Material.NAME_TAG, "显示名称", "dungeonInstance.displayName",
                ConfigUtil.getInstanceDisplayName(config), "string", "地牢", null));
        items.add(new ConfigItem(Material.PAPER, "文件名称", "dungeonInstance.fileName",
                ConfigUtil.getInstanceFileName(config), "string", "dungeon_01", null));
        items.add(new ConfigItem(Material.BOOK, "消息前缀", "dungeonInstance.msgPrefix",
                ConfigUtil.getInstanceMsgPrefix(config), "string", null, null));

        items.add(new ConfigItem(Material.IRON_INGOT, "最小房间数", "dungeonInstance.minRoomAmount",
                ConfigUtil.getInstanceMinRoomAmount(config), "int", 5, null));
        items.add(new ConfigItem(Material.GOLD_INGOT, "最大房间数", "dungeonInstance.maxRoomAmount",
                ConfigUtil.getInstanceMaxRoomAmount(config), "int", 12, null));
        items.add(new ConfigItem(Material.DIAMOND, "DFS最大深度", "dungeonInstance.dfsMaxDeep",
                ConfigUtil.getInstanceDfsMaxDeep(config), "int", 8, null));
        items.add(new ConfigItem(Material.COMPARATOR, "启用BFS检查", "dungeonInstance.useBfsCheck",
                ConfigUtil.isInstanceBfsCheckEnabled(config), "boolean", false, null));
        items.add(new ConfigItem(Material.OAK_FENCE, "启用AABB", "dungeonInstance.useAabb",
                ConfigUtil.isInstanceAabbEnabled(config), "boolean", true, null));
        items.add(new ConfigItem(Material.GLASS, "房间间距阈值", "dungeonInstance.disconnectedRoomDistance",
                ConfigUtil.getInstanceDisconnectedRoomDistance(config), "int", 0, null));
        items.add(new ConfigItem(Material.REPEATER, "允许房间复用", "dungeonInstance.allowRoomReuse",
                ConfigUtil.isInstanceAllowRoomReuse(config), "boolean", false, null));
        items.add(new ConfigItem(Material.REPEATER, "允许门复用", "dungeonInstance.allowDoorReuse",
                ConfigUtil.isInstanceAllowDoorReuse(config), "boolean", false, null));
        items.add(new ConfigItem(Material.REPEATER, "允许Fallback门复用", "dungeonInstance.allowFallbackDoorReuse",
                ConfigUtil.isInstanceAllowFallbackDoorReuse(config), "boolean", false, null));
        items.add(new ConfigItem(Material.STRING, "主干Boss范围", "dungeonInstance.startToBossRoomAmmount",
                config.getString("dungeonInstance.startToBossRoomAmmount", "10 10"), "string", "10 10", null));
        items.add(new ConfigItem(Material.BARRIER, "阻止生成的方块", "dungeonInstance.blockRoomGenerateBlocks",
                ConfigUtil.getInstanceBlockRoomGenerateBlocks(config), "list", new ArrayList<>(), null));

        // structureChoose 展开
        ConfigurationSection structSec = config.getConfigurationSection("dungeonInstance.structureChoose");
        if (structSec == null) structSec = config.createSection("dungeonInstance.structureChoose");
        String[] weightKeys = {"combat", "straight", "corner", "tCorner", "cross", "stair", "boss", "fallback"};
        for (String key : weightKeys) {
            Material mat = Material.PAPER;
            if (key.equals("boss")) mat = Material.SKELETON_SKULL;
            else if (key.equals("combat")) mat = Material.IRON_SWORD;
            else if (key.equals("fallback")) mat = Material.STONE;
            else if (key.equals("straight")) mat = Material.OAK_FENCE;
            else if (key.equals("corner")) mat = Material.BRICK;
            else if (key.equals("tCorner")) mat = Material.STONE_BRICKS;
            else if (key.equals("cross")) mat = Material.STICK;
            else if (key.equals("stair")) mat = Material.OAK_STAIRS;
            items.add(new ConfigItem(mat, "权重: " + key, "dungeonInstance.structureChoose." + key,
                    structSec.getInt(key, 0), "int", 0, null));
        }

        items.add(new ConfigItem(Material.PISTON, "生成器选择", "dungeonInstance.generatorChoose",
                ConfigUtil.getInstanceGeneratorChoose(config), "list", new ArrayList<>(), "复杂结构，请直接编辑YAML"));

        // 游戏规则
        items.add(new ConfigItem(Material.RED_BED, "允许组队", "dungeonInstance.allowParties",
                ConfigUtil.isInstanceAllowParties(config), "boolean", false, "软依赖 Parties"));
        items.add(new ConfigItem(Material.SLIME_BALL, "允许Slimefun", "dungeonInstance.allowSlimefun",
                ConfigUtil.isInstanceAllowSlimefun(config), "boolean", false, "软依赖 Slimefun"));
        items.add(new ConfigItem(Material.SKELETON_SKULL, "Boss类型列表", "dungeonInstance.bossType",
                ConfigUtil.getInstanceBossTypeList(config), "list", Collections.singletonList("zombie"), null));
        items.add(new ConfigItem(Material.WITHER_SKELETON_SKULL, "Boss NBT列表", "dungeonInstance.bossNbt",
                ConfigUtil.getInstanceBossNbtList(config), "list", Collections.singletonList("{Health:100.0f}"), null));
        items.add(new ConfigItem(Material.CHEST, "保留背包", "dungeonInstance.keepInventory",
                ConfigUtil.isInstanceKeepInventory(config), "boolean", false, null));
        items.add(new ConfigItem(Material.ENDER_CHEST, "深度克隆背包", "dungeonInstance.deepCloneInventory",
                ConfigUtil.isInstanceDeepCloneInventory(config), "boolean", false, null));
        items.add(new ConfigItem(Material.CLOCK, "限时(秒)", "dungeonInstance.time",
                ConfigUtil.getInstanceTime(config), "int", 0, null));
        items.add(new ConfigItem(Material.CLOCK, "世界时间(tick)", "dungeonInstance.dungeonTime",
                String.valueOf(ConfigUtil.getInstanceDungeonTime(config)), "string", "1200", null));
        items.add(new ConfigItem(Material.SUNFLOWER, "时间推进", "dungeonInstance.advanceTime",
                ConfigUtil.isInstanceAdvanceTime(config), "boolean", false, null));
        items.add(new ConfigItem(Material.WATER_BUCKET, "天气", "dungeonInstance.weather",
                ConfigUtil.getInstanceWeather(config), "string", "clear", null));
        items.add(new ConfigItem(Material.IRON_SWORD, "难度", "dungeonInstance.difficulty",
                ConfigUtil.getInstanceDifficulty(config), "string", "normal", null));
        items.add(new ConfigItem(Material.ZOMBIE_HEAD, "允许的怪物", "dungeonInstance.allowMobs",
                ConfigUtil.getInstanceAllowMobs(config), "list", Collections.singletonList("all"), null));
        items.add(new ConfigItem(Material.BLAZE_ROD, "使用MythicMobs", "dungeonInstance.useMythicMobs",
                ConfigUtil.isInstanceUseMythicMobs(config), "boolean", false, "软依赖 MythicMobs"));

        // 权限
        items.add(new ConfigItem(Material.BOOKSHELF, "命令白名单模式", "dungeonInstance.commandsAllowListType",
                ConfigUtil.getInstanceCommandsAllowListType(config), "string", "white", null));
        items.add(new ConfigItem(Material.COMMAND_BLOCK, "命令白名单", "dungeonInstance.commandsAllow",
                ConfigUtil.getInstanceCommandsAllow(config), "list", new ArrayList<>(), null));
        items.add(new ConfigItem(Material.BRICK, "建筑白名单模式", "dungeonInstance.buildSettingsListType",
                ConfigUtil.getInstanceBuildSettingsListType(config), "string", "white", null));
        items.add(new ConfigItem(Material.STONE, "建筑白名单", "dungeonInstance.buildSettings",
                ConfigUtil.getInstanceBuildSettings(config), "list", new ArrayList<>(), null));
        items.add(new ConfigItem(Material.TNT, "破坏白名单模式", "dungeonInstance.breakSettingsListType",
                ConfigUtil.getInstanceBreakSettingsListType(config), "string", "white", null));
        items.add(new ConfigItem(Material.DIAMOND_PICKAXE, "破坏白名单", "dungeonInstance.breakSettings",
                ConfigUtil.getInstanceBreakSettings(config), "list", new ArrayList<>(), null));

        // 传送与奖励
        items.add(new ConfigItem(Material.END_PORTAL_FRAME, "大厅传送点", "dungeonInstance.lobby",
                ConfigUtil.getInstanceLobby(config), "string", null, null));
        Map<String, Double> vaultRewards = ConfigUtil.getInstanceVaultRewards(config);
        items.add(new ConfigItem(Material.GOLD_INGOT, "Vault奖励", "dungeonInstance.rewards.vault",
                vaultRewards.isEmpty() ? "无" : vaultRewards.toString(), "string", "无", "软依赖 Vault"));
        Map<String, Integer> ppRewards = ConfigUtil.getInstancePlayerPointsRewards(config);
        items.add(new ConfigItem(Material.EMERALD, "PlayerPoints奖励", "dungeonInstance.rewards.playerPoints",
                ppRewards.isEmpty() ? "无" : ppRewards.toString(), "string", "无", "软依赖 PlayerPoints"));
        items.add(new ConfigItem(Material.COMMAND_BLOCK_MINECART, "奖励控制台命令", "dungeonInstance.rewards.consoleCommands",
                ConfigUtil.getInstanceConsoleCommands(config), "list", new ArrayList<>(), null));

        return items;
    }

    // ====== 内部类 ConfigItem ======
    private static class ConfigItem {
        Material material;
        String displayName;
        String configKey;
        Object currentValue;
        String type;
        Object defaultValue;
        String description;

        ConfigItem(Material material, String displayName, String configKey,
                   Object currentValue, String type, Object defaultValue, String description) {
            this.material = material;
            this.displayName = displayName;
            this.configKey = configKey;
            this.currentValue = currentValue;
            this.type = type;
            this.defaultValue = defaultValue;
            this.description = description;
        }

        ItemStack buildItemStack() {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + displayName);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "类型: " + type);
            lore.add(ChatColor.WHITE + "当前值: " + formatValue(currentValue));
            if (defaultValue != null) lore.add(ChatColor.DARK_GRAY + "默认值: " + formatValue(defaultValue));
            if (description != null) lore.add(ChatColor.ITALIC + description);
            if ("list".equals(type)) {
                lore.add(ChatColor.RED + "⚠ 不建议在这里修改列表");
                lore.add(ChatColor.GRAY + "请使用 /dg 管理GUI 或直接编辑 YAML");
            }
            lore.add(ChatColor.DARK_GRAY + "点击操作详见交互说明");
            lore.add("Key: " + configKey);
            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private String formatValue(Object val) {
            if (val == null) return "null";
            if (val instanceof List) {
                List<?> list = (List<?>) val;
                return list.isEmpty() ? "[]" : list.toString();
            }
            if (val instanceof Map) return val.toString();
            return val.toString();
        }

        void updateCurrentValue(Object newVal) {
            this.currentValue = newVal;
        }
    }

    // ====== 输入会话 ======
    private static class InputSession {
        UUID playerId;
        String dungeonName;
        String configKey;
        String type;
        boolean isDeleteMode;
        InputSession(UUID playerId, String dungeonName, String configKey, String type, boolean isDeleteMode) {
            this.playerId = playerId;
            this.dungeonName = dungeonName;
            this.configKey = configKey;
            this.type = type;
            this.isDeleteMode = isDeleteMode;
        }
    }

    // ====== 监听器 ======
    private static class ConfigEditorListener implements Listener {
        @EventHandler
        public void onInventoryClick(InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player)) return;
            Player p = (Player) e.getWhoClicked();
            String title = e.getView().getTitle();
            if (!title.startsWith(ChatColor.BLUE + "配置编辑器 - ")) return;
            e.setCancelled(true);
            int slot = e.getRawSlot();
            if (slot < 0 || slot >= 54) return;
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            EditorContext ctx = editorContexts.get(p.getUniqueId());
            if (ctx == null) return;

            if (slot == 0) {
                if (ctx.currentPage > 0) {
                    ctx.currentPage--;
                    fillPage(p, ctx);
                    p.updateInventory();
                }
                return;
            }
            if (slot == 8) {
                int totalPages = (int) Math.ceil(ctx.allItems.size() / (double) PAGE_SIZE);
                if (ctx.currentPage < totalPages - 1) {
                    ctx.currentPage++;
                    fillPage(p, ctx);
                    p.updateInventory();
                }
                return;
            }
            if (slot == 53) {
                p.closeInventory();
                return;
            }

            ConfigItem item = ctx.slotMap.get(slot);
            if (item == null) return;

            String configKey = item.configKey;
            // 检查软依赖
            if (SOFT_DEPEND_MAP.containsKey(configKey)) {
                String pluginName = SOFT_DEPEND_MAP.get(configKey);
                if (Bukkit.getPluginManager().getPlugin(pluginName) == null) {
                    MessageUtil.sendError(p, "该配置需要插件 " + pluginName + "，未安装");
                    return;
                }
            }

            if ("dungeonInstance.generatorChoose".equals(configKey) ||
                "dungeonInstance.rewards.vault".equals(configKey) ||
                "dungeonInstance.rewards.playerPoints".equals(configKey)) {
                MessageUtil.sendMessage(p, "该配置为复杂结构，请直接编辑 YAML 文件");
                return;
            }

            FileConfiguration config = ctx.config;
            String dungeonName = ctx.dungeonName;
            boolean isBatch = ctx.batchMode;

            boolean isLeft = e.isLeftClick();
            boolean isRight = e.isRightClick();
            boolean isShift = e.isShiftClick();
            boolean isMiddle = e.getClick() == ClickType.MIDDLE;
            Object current = config.get(configKey);
            if (current == null) current = item.defaultValue;
            if (current == null) return;

            Object newValue = null;
            boolean needUpdate = false;

            if (current instanceof Integer) {
                int val = (int) current;
                if (isMiddle) {
                    val = item.defaultValue instanceof Integer ? (int) item.defaultValue : 0;
                } else if (isLeft && isShift) {
                    val += 10;
                } else if (isRight && isShift) {
                    val -= 10;
                } else if (isLeft) {
                    val += 1;
                } else if (isRight) {
                    val -= 1;
                } else {
                    return;
                }
                newValue = val;
                needUpdate = true;
            } else if (current instanceof Boolean) {
                if (isLeft) {
                    newValue = !(boolean) current;
                    needUpdate = true;
                }
            } else if (current instanceof String) {
                if (isLeft) {
                    p.closeInventory();
                    MessageUtil.sendMessage(p, "请输入新的 " + configKey + " 值（输入 'cancel' 取消）：");
                    inputSessions.put(p.getUniqueId(), new InputSession(p.getUniqueId(), dungeonName, configKey, "string", false));
                    return;
                } else if (isRight) {
                    newValue = "";
                    needUpdate = true;
                }
            } else if (current instanceof List) {
                if (isLeft) {
                    p.closeInventory();
                    MessageUtil.sendMessage(p, "请输入要添加到 " + configKey + " 的值（输入 'cancel' 取消）：");
                    inputSessions.put(p.getUniqueId(), new InputSession(p.getUniqueId(), dungeonName, configKey, "list", false));
                    return;
                } else if (isRight) {
                    p.closeInventory();
                    MessageUtil.sendMessage(p, "请输入要从 " + configKey + " 中删除的值（输入 '*' 清空所有）：");
                    inputSessions.put(p.getUniqueId(), new InputSession(p.getUniqueId(), dungeonName, configKey, "list", true));
                    return;
                }
            }

            if (needUpdate) {
                if ("dungeonInstance.dungeonTime".equals(configKey) && newValue instanceof String) {
                    try {
                        newValue = Long.parseLong((String) newValue);
                    } catch (NumberFormatException ex) {
                        MessageUtil.sendError(p, "无效数字，保持原值");
                        return;
                    }
                }
                // 保存配置
                if (isBatch) {
                    // 批量模式：应用到所有地牢
                    applyToAllDungeons(configKey, newValue, p);
                } else {
                    config.set(configKey, newValue);
                    boolean success;
                    if (dungeonName == null) success = ConfigUtil.saveGlobalConfig(config);
                    else success = ConfigUtil.saveDungeonConfig(dungeonName, config);
                    if (!success) MessageUtil.sendError(p, "保存配置失败");
                }
                item.updateCurrentValue(newValue);
                fillPage(p, ctx);
                p.updateInventory();
            }
        }

        @EventHandler
        public void onInventoryClose(InventoryCloseEvent e) {
            // 保留上下文
        }

        @EventHandler
        public void onAsyncPlayerChat(AsyncPlayerChatEvent e) {
            Player p = e.getPlayer();
            UUID uuid = p.getUniqueId();
            InputSession session = inputSessions.remove(uuid);
            if (session == null) return;
            e.setCancelled(true);

            String input = e.getMessage().trim();
            if (input.equalsIgnoreCase("cancel")) {
                MessageUtil.sendMessage(p, "已取消输入");
                Bukkit.getScheduler().runTask(HimDungeons.getInstance(), () -> openEditor(p, session.dungeonName));
                return;
            }

            String dungeonName = session.dungeonName;
            boolean isBatch = "__BATCH__".equals(dungeonName);
            FileConfiguration config = isBatch ? null : (dungeonName == null ? ConfigUtil.getGlobalConfig() : ConfigUtil.getDungeonConfig(dungeonName));
            if (!isBatch && config == null) {
                MessageUtil.sendError(p, "配置加载失败");
                return;
            }

            if (session.type.equals("string")) {
                Object value = input;
                if ("dungeonInstance.dungeonTime".equals(session.configKey)) {
                    try {
                        value = Long.parseLong(input);
                    } catch (NumberFormatException ex) {
                        MessageUtil.sendError(p, "无效数字，取消修改");
                        return;
                    }
                }
                if (isBatch) {
                    applyToAllDungeons(session.configKey, value, p);
                } else {
                    config.set(session.configKey, value);
                    boolean success;
                    if (dungeonName == null) success = ConfigUtil.saveGlobalConfig(config);
                    else success = ConfigUtil.saveDungeonConfig(dungeonName, config);
                    if (!success) MessageUtil.sendError(p, "保存配置失败");
                }
                MessageUtil.sendMessage(p, "已设置 " + session.configKey + " = " + input);
            } else if (session.type.equals("list")) {
                List<String> list = isBatch ? new ArrayList<>() : config.getStringList(session.configKey);
                if (!isBatch && list == null) list = new ArrayList<>();
                if (session.isDeleteMode) {
                    if (input.equals("*")) {
                        list.clear();
                        MessageUtil.sendMessage(p, "已清空 " + session.configKey);
                    } else {
                        if (list.remove(input)) {
                            MessageUtil.sendMessage(p, "已从 " + session.configKey + " 中删除 '" + input + "'");
                        } else {
                            MessageUtil.sendError(p, "未找到 '" + input + "'");
                        }
                    }
                } else {
                    list.add(input);
                    MessageUtil.sendMessage(p, "已添加 '" + input + "' 到 " + session.configKey);
                }
                if (isBatch) {
                    applyToAllDungeons(session.configKey, list, p);
                } else {
                    config.set(session.configKey, list);
                    boolean success;
                    if (dungeonName == null) success = ConfigUtil.saveGlobalConfig(config);
                    else success = ConfigUtil.saveDungeonConfig(dungeonName, config);
                    if (!success) MessageUtil.sendError(p, "保存配置失败");
                }
            }

            Bukkit.getScheduler().runTask(HimDungeons.getInstance(), () -> openEditor(p, isBatch ? null : dungeonName));
        }

        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent e) {
            UUID uuid = e.getPlayer().getUniqueId();
            inputSessions.remove(uuid);
            editorContexts.remove(uuid);
        }

        // 批量应用到所有地牢
        private void applyToAllDungeons(String key, Object value, Player player) {
            File dataFolder = HimDungeons.getInstance().getDataFolder();
            File[] dirs = dataFolder.listFiles(File::isDirectory);
            if (dirs == null) return;
            int count = 0;
            for (File dir : dirs) {
                if (new File(dir, "config.yml").exists()) {
                    FileConfiguration config = ConfigUtil.getDungeonConfig(dir.getName());
                    if (config != null) {
                        config.set(key, value);
                        ConfigUtil.saveDungeonConfig(dir.getName(), config);
                        count++;
                    }
                }
            }
            MessageUtil.sendMessage(player, "已更新 " + count + " 个地牢的配置项 " + key);
        }
    }

    public static void registerListener(JavaPlugin plugin) {
        if (listenerInstance == null) {
            listenerInstance = new ConfigEditorListener();
            Bukkit.getPluginManager().registerEvents(listenerInstance, plugin);
        }
    }
}