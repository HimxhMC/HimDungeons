package com.him.dungeons.gui.editor;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.ConfigUtil;
import com.him.dungeons.util.MessageUtil;
import com.him.dungeons.util.YamlDoorUtil;
import com.him.dungeons.util.SelectionManager;
import com.him.dungeons.api.hook.DungeonHookRegistry;
import com.him.dungeons.api.hook.DungeonHookRegistry.HookType;
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
    private static final String T_HOOK_OVERVIEW = "§f§lHimDungeons钩子管理";
    private static final String T_HOOK_DETAIL   = "§f§lHimDungeons钩子详情 - ";
    
    // ==================== 标题 ====================
    private static final String T_MAIN = "§e§lHimDungeons总编辑器";
    private static final String T_ROOT = "§f§lHimDungeons根配置编辑页面";
    private static final String T_DUNGEON_LIST = "§f§lHimDungeons地牢实例列表";
    private static final String T_DUNGEON_EDITOR = "§f§lHimDungeons实例修改选择页面";
    private static final String T_CONFIG_CAT = "§f§lHimDungeons实例配置修改选择页面";
    private static final String T_CONFIG_SECTION = "§f§lHimDungeons配置编辑 - ";
    private static final String T_ROOM_LIST = "§f§lHimDungeons房间配置列表";
    private static final String T_ROOM_CONFIG = "§f§lHimDungeons房间配置 - ";
    private static final String T_DOOR_LIST = "§f§lHimDungeons门文件编辑 - ";
    private static final String T_SINGLE_DOOR = "§f§lHimDungeons门编辑 - ";
    private static final String T_CHEST_LIST = "§f§lHimDungeons箱子文件编辑 - ";
    private static final String T_SINGLE_CHEST = "§f§lHimDungeons箱子编辑 - ";
    private static final String T_SPAWN = "§f§lHimDungeons出生点编辑 - ";
    private static final String T_CHECKPOINT = "§f§lHimDungeons存档点编辑 - ";
    private static final String T_CODE_FILES = "§f§lHimDungeons脚本文件列表";
    private static final String T_LIST = "§f§lHimDungeons列表编辑 - ";
    private static final String T_MAP = "§f§lHimDungeons键值编辑 - ";
    
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
            "§c此操作会把 " + dungeon + " 的 config.yml 恢复为默认值。输入 confirm 确认，输入 cancel 取消：",
            in -> {
                if (!"confirm".equalsIgnoreCase(in)) {
                    MessageUtil.sendMessage(p, "§7已取消");
                    openDungeonEditor(p, dungeon);
                    return;
                }
                org.bukkit.configuration.file.FileConfiguration def = plugin.buildDefaultInstanceConfig(dungeon);
                boolean ok = ConfigUtil.saveDungeonConfig(dungeon, def);
                session(p).working.remove(dungeon);
                MessageUtil.sendMessage(p, ok ? "§a已恢复默认配置" : "§c恢复失败，请查看控制台");
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
            MessageUtil.sendMessage(p, prompt);
            MessageUtil.sendMessage(p, "§7输入 cancel 取消");
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
                    MessageUtil.sendMessage(p, "§7已取消");
                    openDungeonEditor(p, cs.dungeonName);
                });
                return;
            }
            if (cs.baseName == null) {
                cs.baseName = msg;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    MessageUtil.sendMessage(p, "§a路径已设置: " + cs.baseName);
                    MessageUtil.sendMessage(p, "§e请手持物品输入概率（0-100），输入 done 完成，cancel 取消");
                });
                return;
            }
            if (msg.equalsIgnoreCase("done")) {
                if (cs.chests.isEmpty()) {
                    chestAddSessions.remove(uuid);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        MessageUtil.sendMessage(p, "§7未添加任何战利品，未保存");
                        openDungeonEditor(p, cs.dungeonName);
                    });
                    return;
                }
                File roomsDir = new File(plugin.getDataFolder(), cs.dungeonName + "/rooms");
                String path = cs.baseName.replace('/', File.separatorChar);
                File chestFile = new File(roomsDir, path + "_chest.yml");
                chestFile.getParentFile().mkdirs();
                YamlConfiguration yaml = chestFile.exists()
                        ? YamlConfiguration.loadConfiguration(chestFile) : new YamlConfiguration();
                List<Map<String, Object>> chestList = new ArrayList<>();
                List<?> existing = yaml.getList("chests");
                if (existing == null) existing = yaml.getList("");
                if (existing != null) {
                    for (Object o : existing) {
                        if (o instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = (Map<String, Object>) o;
                            chestList.add(m);
                        }
                    }
                }
                chestList.addAll(cs.chests);
                yaml.set("chests", chestList);
                try {
                    yaml.save(chestFile);
                    chestAddSessions.remove(uuid);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        MessageUtil.sendMessage(p, "§a箱子已保存：" + chestFile.getPath()
                                + "（共 " + cs.chests.size() + " 个条目）");
                        openDungeonEditor(p, cs.dungeonName);
                    });
                } catch (Exception ex) {
                    chestAddSessions.remove(uuid);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        MessageUtil.sendError(p, "保存失败: " + ex.getMessage());
                        openDungeonEditor(p, cs.dungeonName);
                    });
                }
                return;
            }
            int prob;
            try { prob = Integer.parseInt(msg); } catch (Exception ex) {
                Bukkit.getScheduler().runTask(plugin, () -> MessageUtil.sendError(p, "§c请输入 0-100 的整数或 done/cancel"));
                return;
            }
            if (prob < 0 || prob > 100) {
                Bukkit.getScheduler().runTask(plugin, () -> MessageUtil.sendError(p, "§c概率必须在 0-100"));
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                Bukkit.getScheduler().runTask(plugin, () -> MessageUtil.sendError(p, "§c请手持有效物品"));
                return;
            }
            if (prob == 0) {
                Bukkit.getScheduler().runTask(plugin, () -> MessageUtil.sendMessage(p, "§7概率为0，跳过"));
                return;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("pos", Arrays.asList(cs.x, cs.y, cs.z));
            entry.put("loot", prob + " " + hand.getType().name() + " " + hand.getAmount());
            cs.chests.add(entry);
            Bukkit.getScheduler().runTask(plugin, () -> {
                MessageUtil.sendMessage(p, "§a已添加战利品，共 " + cs.chests.size() + " 条");
                MessageUtil.sendMessage(p, "§e继续输入概率，或 done 完成");
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
            MessageUtil.sendMessage(p, ok ? "§a已保存" : "§c保存失败");
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
        
        private ItemStack cfgItem(Material mat, String name, String path, FileConfiguration cfg) {
            Object v = cfg.get(path);
            List<String> lore = new ArrayList<>();
            lore.add("§7当前值: §f" + (v == null ? "null" : v));
            lore.add("§7路径: §f" + path);
            lore.add("§e点击修改");
            return item(mat, "§e" + name, lore);
        }
        
        // ==================== P1 主页面 ====================
        public void openMain(Player p) {
            Inventory inv = Bukkit.createInventory(new EditorHolder(P_MAIN, null, null, 0), 27, T_MAIN);
            inv.setItem(10, item(Material.REPEATER, "§a钩子管理", List.of("§7查看并逐条卸载当前接入的钩子","§7当前共 §f" + plugin.getHookRegistry().countAll() + " §7个")));
            inv.setItem(9, item(Material.BOOK, "§a修改根配置", List.of("§7编辑 config.yml")));
            inv.setItem(11, item(Material.CHEST, "§a修改地牢实例配置", List.of("§7选择地牢")));
            inv.setItem(13, item(Material.REDSTONE, "§c重载整个插件", List.of("§7重新加载插件")));
            inv.setItem(15, item(Material.COMPASS, "§e跳转到加入游戏", null));
            p.openInventory(inv);
        }
        
        // ==================== P2 根配置 ====================
        public void openRoot(Player p) {
            EditorSession s = session(p);
            FileConfiguration c = workingConfig(s, null);
            Inventory inv = Bukkit.createInventory(new EditorHolder(P_ROOT, null, null, 0), 27, T_ROOT);
            inv.setItem(0, cfgItem(Material.BOOK, "全局语言", "lang", c));
            inv.setItem(1, cfgItem(Material.NAME_TAG, "全局消息前缀", "msgPrefix", c));
            inv.setItem(2, cfgItem(Material.REPEATER, "定时扫描并注册新房间", "scanAndRegisterNewRooms", c));
            inv.setItem(9, cfgItem(Material.CLOCK, "生成冷却", "generateCooldown", c));
            Object fmt = c.get("schematic.saveFormat", "sponge_v3");
            inv.setItem(10, item(Material.PAPER, "§e原理图保存版本", List.of(
                "§7当前: §f" + fmt, "§e左键切换 V2 / V3")));
                inv.setItem(18, item(Material.LIME_DYE, "§a保存", null));
                inv.setItem(25, item(Material.COMPASS, "§e跳转到加入游戏", null));
                inv.setItem(26, item(Material.ARROW, "§7回到主页面", null));
                p.openInventory(inv);
            }
            
            // ==================== P3 实例选择 ====================
            public void openDungeonList(Player p, int page) {
                List<String> names = listDungeonNames();
                Inventory inv = Bukkit.createInventory(new EditorHolder(P_DUNGEON_LIST, null, null, page), 54, T_DUNGEON_LIST);
                int start = page * 36;
                for (int i = 0; i < 36 && start + i < names.size(); i++) {
                    String name = names.get(start + i);
                    inv.setItem(9 + i, item(Material.CHEST, "§a" + name, List.of("§7点击编辑")));
                }
                inv.setItem(0, item(Material.ARROW, "§e向左翻页", null));
                inv.setItem(4, item(Material.PAPER, "§e第 " + (page + 1) + " 页", null));
                inv.setItem(8, item(Material.ARROW, "§e向右翻页", null));
                inv.setItem(52, item(Material.COMPASS, "§e跳转到加入游戏", null));
                inv.setItem(53, item(Material.ARROW, "§7回到主页面", null));
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
                Inventory inv = Bukkit.createInventory(new EditorHolder(P_DUNGEON_EDITOR, dungeon, null, 0), 54, T_DUNGEON_EDITOR);
                inv.setItem(9, item(Material.LIME_DYE, "§a添加房间", null));
                inv.setItem(10, item(Material.LIME_DYE, "§a添加门锚点", null));
                inv.setItem(11, item(Material.LIME_DYE, "§a添加箱子", null));
                inv.setItem(12, item(Material.LIME_DYE, "§a添加出生点（仅 start）", null));
                inv.setItem(13, item(Material.LIME_DYE, "§a添加存档点", null));
                inv.setItem(18, item(Material.RED_DYE, "§c移除房间", null));
                inv.setItem(19, item(Material.RED_DYE, "§c移除门锚点", null));
                inv.setItem(20, item(Material.RED_DYE, "§c移除箱子", null));
                inv.setItem(21, item(Material.RED_DYE, "§c移除出生点（仅 start）", null));
                inv.setItem(22, item(Material.RED_DYE, "§c移除存档点", null));
                inv.setItem(27, item(Material.BOOK, "§e更改配置文件", null));
                inv.setItem(28, item(Material.REPEATER, "§e更新配置文件", List.of("§7重读磁盘，丢弃内存改动")));
                inv.setItem(29, item(Material.PAPER, "§e更改房间配置", null));
                inv.setItem(30, item(Material.BARRIER, "§c恢复配置文件到默认值", null));
                inv.setItem(31, item(Material.COMPARATOR, "§e扫描一次目录并注册新房间", null));
                inv.setItem(32, item(Material.WRITABLE_BOOK, "§e脚本文件管理", null));
                inv.setItem(49, item(Material.REDSTONE_TORCH, "§e生成测试，不进行游戏", null));
                boolean hasTest = hasTestWorld(dungeon);
                inv.setItem(50, item(hasTest ? Material.TNT : Material.GRAY_DYE,
                    hasTest ? "§c删除测试世界" : "§7删除测试世界（无）", null));
                    inv.setItem(51, item(Material.EMERALD, "§a加入此实例进行游戏", null));
                    inv.setItem(52, item(Material.ARROW, "§7回到实例选择页面", null));
                    inv.setItem(53, item(Material.ARROW, "§7回到主页面", null));
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
                    Inventory inv = Bukkit.createInventory(new EditorHolder(P_CONFIG_CAT, dungeon, null, 0), 27, T_CONFIG_CAT);
                    inv.setItem(10, item(Material.BOOK, "§e实例根配置", null));
                    inv.setItem(11, item(Material.COMPARATOR, "§e生成算法", null));
                    inv.setItem(12, item(Material.GRASS_BLOCK, "§e世界规则和状态", null));
                    inv.setItem(13, item(Material.PLAYER_HEAD, "§e玩家配置", null));
                    inv.setItem(14, item(Material.SKELETON_SKULL, "§eBoss 配置", null));
                    inv.setItem(15, item(Material.GOLD_INGOT, "§e入场费结算和奖励", null));
                    inv.setItem(16, item(Material.SLIME_BALL, "§e软依赖配置", null));
                    inv.setItem(26, item(Material.ARROW, "§7返回实例修改选择", null));
                    p.openInventory(inv);
                }
                
                // ==================== P5a–P5g 配置子页面 ====================
                record Field(Material mat, String name, String path) {}
                
                private Map<String, List<Field>> sectionFields() {
                    Map<String, List<Field>> m = new LinkedHashMap<>();
                    m.put("root", List.of(
                        new Field(Material.LEVER, "启用", "dungeonInstance.enable"),
                        new Field(Material.NAME_TAG, "显示名称", "dungeonInstance.displayName"),
                        new Field(Material.PAPER, "文件名称", "dungeonInstance.fileName"),
                        new Field(Material.BOOK, "消息前缀", "dungeonInstance.msgPrefix"),
                        new Field(Material.END_PORTAL_FRAME, "大厅传送点", "dungeonInstance.lobby")
                    ));
                    m.put("gen", List.of(
                        new Field(Material.IRON_INGOT, "最小房间数", "dungeonInstance.minRoomAmount"),
                        new Field(Material.GOLD_INGOT, "最大房间数", "dungeonInstance.maxRoomAmount"),
                        new Field(Material.DIAMOND, "DFS 最大深度", "dungeonInstance.dfsMaxDeep"),
                        new Field(Material.COMPARATOR, "启用 BFS 检查", "dungeonInstance.useBfsCheck"),
                        new Field(Material.OAK_FENCE, "启用 AABB", "dungeonInstance.useAabb"),
                        new Field(Material.GLASS, "房间间距阈值", "dungeonInstance.disconnectedRoomDistance"),
                        new Field(Material.REPEATER, "允许房间复用", "dungeonInstance.allowRoomReuse"),
                        new Field(Material.REPEATER, "允许门复用", "dungeonInstance.allowDoorReuse"),
                        new Field(Material.REPEATER, "允许 Fallback 门复用", "dungeonInstance.allowFallbackDoorReuse"),
                        new Field(Material.STRING, "主干 Boss 范围", "dungeonInstance.startToBossRoomAmmount"),
                        new Field(Material.BARRIER, "阻止生成的方块", "dungeonInstance.blockRoomGenerateBlocks")
                    ));
                    m.put("world", List.of(
                        new Field(Material.CHEST, "保留背包", "dungeonInstance.keepInventory"),
                        new Field(Material.ENDER_CHEST, "深拷贝背包", "dungeonInstance.deepCloneInventory"),
                        new Field(Material.CLOCK, "限时（秒）", "dungeonInstance.time"),
                        new Field(Material.CLOCK, "世界时间（tick）", "dungeonInstance.dungeonTime"),
                        new Field(Material.SUNFLOWER, "时间推进", "dungeonInstance.advanceTime"),
                        new Field(Material.WATER_BUCKET, "天气", "dungeonInstance.weather"),
                        new Field(Material.IRON_SWORD, "难度", "dungeonInstance.difficulty"),
                        new Field(Material.ZOMBIE_HEAD, "允许的怪物", "dungeonInstance.allowMobs"),
                        new Field(Material.TNT, "TNT 爆炸", "dungeonInstance.tntExplosion"),
                        new Field(Material.CREEPER_HEAD, "实体爆炸", "dungeonInstance.entityExplosion"),
                        new Field(Material.RESPAWN_ANCHOR, "方块爆炸", "dungeonInstance.blockExplosion")
                    ));
                    m.put("player", List.of(
                        new Field(Material.BOOKSHELF, "命令白名单模式", "dungeonInstance.commandsAllowListType"),
                        new Field(Material.COMMAND_BLOCK, "命令白名单", "dungeonInstance.commandsAllow"),
                        new Field(Material.BRICK, "建筑白名单模式", "dungeonInstance.buildSettingsListType"),
                        new Field(Material.STONE, "建筑白名单", "dungeonInstance.buildSettings"),
                        new Field(Material.TNT, "破坏白名单模式", "dungeonInstance.breakSettingsListType"),
                        new Field(Material.DIAMOND_PICKAXE, "破坏白名单", "dungeonInstance.breakSettings"),
                        new Field(Material.OAK_BUTTON, "交互白名单模式", "dungeonInstance.interactionSettingsListType"),
                        new Field(Material.LEVER, "交互白名单", "dungeonInstance.interactionSettings")
                    ));
                    m.put("boss", List.of(
                        new Field(Material.SLIME_BALL, "使用 MythicMob 作 Boss", "dungeonInstance.useMythicMobForBoss"),
                        new Field(Material.NAME_TAG, "MythicMob Boss 名称", "dungeonInstance.mythicMobBossName"),
                        new Field(Material.SKELETON_SKULL, "Boss 类型列表", "dungeonInstance.bossType"),
                        new Field(Material.WITHER_SKELETON_SKULL, "Boss NBT 列表", "dungeonInstance.bossNbt")
                    ));
                    m.put("economy", List.of(
                        new Field(Material.GOLD_INGOT, "通关奖励 Vault", "dungeonInstance.rewards.vault"),
                        new Field(Material.EMERALD, "通关奖励 PlayerPoints", "dungeonInstance.rewards.playerPoints"),
                        new Field(Material.COMMAND_BLOCK_MINECART, "通关奖励命令", "dungeonInstance.rewards.consoleCommands"),
                        new Field(Material.GOLD_NUGGET, "入场费 Vault", "dungeonInstance.enterCost.vault"),
                        new Field(Material.DIAMOND, "入场费 PlayerPoints", "dungeonInstance.enterCost.playerPoints"),
                        new Field(Material.COMMAND_BLOCK, "入场费命令", "dungeonInstance.enterCost.consoleCommands")
                    ));
                    m.put("soft", List.of(
                        new Field(Material.RED_BED, "允许组队（Parties）", "dungeonInstance.allowParties"),
                        new Field(Material.SLIME_BALL, "允许 Slimefun", "dungeonInstance.allowSlimefun"),
                        new Field(Material.BLAZE_ROD, "允许 MythicMobs 小怪", "dungeonInstance.useMythicMobs")
                    ));
                    return m;
                }
                
                public void openConfigSection(Player p, String dungeon, String section, int page) {
                    EditorSession s = session(p);
                    FileConfiguration c = workingConfig(s, dungeon);
                    List<Field> fields = sectionFields().getOrDefault(section, List.of());
                    Inventory inv = Bukkit.createInventory(new EditorHolder(P_CONFIG_SECTION, dungeon, section, page),
                    27, T_CONFIG_SECTION + section);
                    int slot = 9;
                    for (Field f : fields) {
                        if (slot >= 26) break;
                        Object v = c.get(f.path);
                        List<String> lore = new ArrayList<>();
                        lore.add("§7当前: " + formatFieldValue(f.path, v));
                        lore.add("§7路径: §f" + f.path);
                        lore.add(isExplosionField(f.path) ? "§e点击循环切换" : "§e点击修改");
                        inv.setItem(slot++, item(f.mat, "§e" + f.name, lore));
                    }
                    inv.setItem(0, item(Material.ARROW, "§7返回分类选择", null));
                    inv.setItem(4, item(Material.PAPER, "§e" + section, null));
                    inv.setItem(8, item(Material.LIME_DYE, "§a保存", null));
                    p.openInventory(inv);
                }
                
    private static boolean isExplosionField(String path) {
        return path.endsWith("tntExplosion")
                || path.endsWith("entityExplosion")
                || path.endsWith("blockExplosion");
    }

    /** 原始值 → 用户可读展示 */
    private static String formatFieldValue(String path, Object v) {
        if (isExplosionField(path)) {
            String s = v == null ? "deny" : v.toString().toLowerCase(java.util.Locale.ROOT);
            return switch (s) {
                case "allow" -> "§a完全允许";
                case "damage_only", "damage", "damage-only" -> "§e仅造成伤害";
                default -> "§c完全禁止";
            };
        }
        return "§f" + (v == null ? "null" : v);
    }

    /** 循环切换：allow → damage_only → deny → allow */
    private static String cycleExplosion(String cur) {
        String s = cur == null ? "deny" : cur.toLowerCase(java.util.Locale.ROOT);
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
                    Inventory inv = Bukkit.createInventory(new EditorHolder(P_ROOM_LIST, dungeon, null, page), 54, T_ROOM_LIST);
                    int start = page * 36;
                    for (int i = 0; i < 36 && start + i < keys.size(); i++) {
                        String key = keys.get(start + i);
                        int weight = c.getInt("dungeonInstance.structureChoose." + key, 50);
                        inv.setItem(9 + i, item(Material.PAPER, "§e" + key, List.of("§7权重: §f" + weight)));
                    }
                    inv.setItem(0, item(Material.ARROW, "§e向左翻页", null));
                    inv.setItem(4, item(Material.PAPER, "§e第 " + (page + 1) + " 页", null));
                    inv.setItem(8, item(Material.ARROW, "§e向右翻页", null));
                    inv.setItem(52, item(Material.ARROW, "§7返回实例修改选择", null));
                    inv.setItem(53, item(Material.ARROW, "§7回到主页面", null));
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
                    Inventory inv = Bukkit.createInventory(new EditorHolder(P_ROOM_CONFIG, dungeon, baseName, 0), 9, T_ROOM_CONFIG + baseName);
                    int weight = c.getInt("dungeonInstance.structureChoose." + baseName, 50);
                    inv.setItem(0, item(Material.PAPER, "§e当前权重", List.of("§7" + weight, "§e点击修改")));
                    inv.setItem(1, item(Material.OAK_DOOR, "§e门文件", List.of("§7" + doorFileName(baseName))));
                    inv.setItem(2, item(Material.CHEST, "§e箱子文件", List.of("§7" + chestFileName(baseName))));
                    inv.setItem(3, item(Material.COMPASS, "§e出生点文件", null));
                    inv.setItem(4, item(Material.BEACON, "§e存档点文件", null));
                    inv.setItem(8, item(Material.ARROW, "§7返回房间列表", null));
                    p.openInventory(inv);
                }
                
                private String doorFileName(String base) { return base + "_door.yml"; }
                private String chestFileName(String base) { return base + "_chest.yml"; }
                private File roomFile(String dungeon, String baseName, String suffix) {
                    File rooms = new File(plugin.getDataFolder(), dungeon + "/rooms");
                    return new File(rooms, baseName.replace('/', File.separatorChar) + suffix);
                }
                
                // ==================== P6a-i 门列表 ====================
                public void openDoorList(Player p, String dungeon, String baseName, int page) {
                    List<Map<?, ?>> doors = readYamlList(roomFile(dungeon, baseName, "_door.yml"), "doors");
                    Inventory inv = Bukkit.createInventory(new EditorHolder(P_DOOR_LIST, dungeon, baseName, page),
                    54, T_DOOR_LIST + baseName);
                    int start = page * 36;
                    for (int i = 0; i < 36 && start + i < doors.size(); i++) {
                        Map<?, ?> m = doors.get(start + i);
                        inv.setItem(9 + i, item(Material.OAK_DOOR, "§e#" + (start + i),
                        List.of("§7" + m.toString(), "§e点击编辑")));
                    }
                    inv.setItem(0, item(Material.ARROW, "§e向左翻页", null));
                    inv.setItem(4, item(Material.PAPER, "§e第 " + (page + 1) + " 页 / 共 " + doors.size() + " 门", null));
                    inv.setItem(8, item(Material.ARROW, "§e向右翻页", null));
                    inv.setItem(45, item(Material.LIME_DYE, "§a添加新门", List.of("§7使用当前注视方块")));
                    inv.setItem(53, item(Material.ARROW, "§7返回房间配置", null));
                    p.openInventory(inv);
                }
                
                // ==================== P6a-i-1 单门 ====================
                public void openSingleDoor(Player p, String dungeon, String baseName, int index) {
                    List<Map<?, ?>> doors = readYamlList(roomFile(dungeon, baseName, "_door.yml"), "doors");
                    if (index < 0 || index >= doors.size()) { openDoorList(p, dungeon, baseName, 0); return; }
                    Map<?, ?> m = doors.get(index);
                    Inventory inv = Bukkit.createInventory(new EditorHolder(P_SINGLE_DOOR, dungeon, baseName + "#" + index, 0),
                    27, T_SINGLE_DOOR + baseName + "#" + index);
                    inv.setItem(10, item(Material.RED_STAINED_GLASS_PANE, "§eX: " + m.get("x"), null));
                    inv.setItem(11, item(Material.GREEN_STAINED_GLASS_PANE, "§eY: " + m.get("y"), null));
                    inv.setItem(12, item(Material.BLUE_STAINED_GLASS_PANE, "§eZ: " + m.get("z"), null));
                    inv.setItem(13, item(Material.COMPASS, "§e朝向: " + m.get("facing"), List.of("§e左键循环")));
                    Object typeObj = m.get("type");
String typeStr = typeObj == null ? "default" : String.valueOf(typeObj);
Object weightObj = m.get("weight");
int weightInt = weightObj instanceof Number ? ((Number) weightObj).intValue() : 10;

inv.setItem(14, item(Material.NAME_TAG, "§e类型: " + typeStr, List.of("§e左键循环")));
inv.setItem(15, item(Material.PAPER, "§e权重: " + weightInt, null));
                    inv.setItem(21, item(Material.BARRIER, "§c删除此门", null));
                    inv.setItem(22, item(Material.LIME_DYE, "§a保存", null));
                    inv.setItem(26, item(Material.ARROW, "§7返回门列表", null));
                    p.openInventory(inv);
                }
                
                // ==================== P6a-ii 箱子列表 ====================
                public void openChestList(Player p, String dungeon, String baseName, int page) {
                    List<Map<?, ?>> chests = readYamlList(roomFile(dungeon, baseName, "_chest.yml"), "chests");
                    Inventory inv = Bukkit.createInventory(new EditorHolder(P_CHEST_LIST, dungeon, baseName, page),
                    54, T_CHEST_LIST + baseName);
                    int start = page * 36;
                    for (int i = 0; i < 36 && start + i < chests.size(); i++) {
                        Map<?, ?> m = chests.get(start + i);
                        inv.setItem(9 + i, item(Material.CHEST, "§e#" + (start + i),
                        List.of("§7" + m.toString(), "§e点击编辑")));
                    }
                    inv.setItem(0, item(Material.ARROW, "§e向左翻页", null));
                    inv.setItem(4, item(Material.PAPER, "§e第 " + (page + 1) + " 页 / 共 " + chests.size() + " 箱子", null));
                    inv.setItem(8, item(Material.ARROW, "§e向右翻页", null));
                    inv.setItem(45, item(Material.LIME_DYE, "§a添加新箱子", List.of("§7使用当前注视方块")));
                    inv.setItem(53, item(Material.ARROW, "§7返回房间配置", null));
                    p.openInventory(inv);
                }
                
                // ==================== P6a-ii-1 单箱子 ====================
                public void openSingleChest(Player p, String dungeon, String baseName, int index) {
                    List<Map<?, ?>> chests = readYamlList(roomFile(dungeon, baseName, "_chest.yml"), "chests");
                    if (index < 0 || index >= chests.size()) { openChestList(p, dungeon, baseName, 0); return; }
                    Map<?, ?> m = chests.get(index);
                    Object pos = m.get("pos");
                    Object loot = m.get("loot");
                    Inventory inv = Bukkit.createInventory(new EditorHolder(P_SINGLE_CHEST, dungeon, baseName + "#" + index, 0),
                    27, T_SINGLE_CHEST + baseName + "#" + index);
                    inv.setItem(10, item(Material.PAPER, "§e坐标", List.of("§7" + pos)));
                    inv.setItem(11, item(Material.NAME_TAG, "§e战利品", List.of("§7" + loot)));
                    inv.setItem(13, item(Material.CHEST, "§e替换为手持物品", List.of("§7将 loot 改为手持物品")));
                    inv.setItem(21, item(Material.BARRIER, "§c删除此箱子", null));
                    inv.setItem(22, item(Material.LIME_DYE, "§a保存", null));
                    inv.setItem(26, item(Material.ARROW, "§7返回箱子列表", null));
                    p.openInventory(inv);
                }
                
                // ==================== P6a-iii / iv 出生点 / 存档点 ====================
                public void openSpawnOrCheckpoint(Player p, String dungeon, String baseName, boolean checkpoint) {
                    String suffix = checkpoint ? "_checkpoint.yml" : "_spawn.yml";
                    File f = roomFile(dungeon, baseName, suffix);
                    org.bukkit.util.Vector v = YamlDoorUtil.parseSpawnPoint(f);
                    String pid = checkpoint ? P_CHECKPOINT : P_SPAWN;
                    String title = (checkpoint ? T_CHECKPOINT : T_SPAWN) + baseName;
                    Inventory inv = Bukkit.createInventory(new EditorHolder(pid, dungeon, baseName, 0), 27, title);
                    inv.setItem(10, item(Material.RED_STAINED_GLASS_PANE, "§eX: " + (v == null ? "?" : v.getX()), null));
                    inv.setItem(11, item(Material.GREEN_STAINED_GLASS_PANE, "§eY: " + (v == null ? "?" : v.getY()), null));
                    inv.setItem(12, item(Material.BLUE_STAINED_GLASS_PANE, "§eZ: " + (v == null ? "?" : v.getZ()), null));
                    inv.setItem(13, item(Material.EMERALD, "§a用玩家当前位置覆盖", null));
                    inv.setItem(22, item(Material.LIME_DYE, "§a保存", null));
                    inv.setItem(26, item(Material.ARROW, "§7返回房间配置", null));
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
                        Inventory inv = Bukkit.createInventory(new EditorHolder(P_CODE_FILES, dungeon, rel, page),
                        54, T_CODE_FILES + (rel.isEmpty() ? "" : " - " + rel));
                        int start = page * 36;
                        for (int i = 0; i < 36 && start + i < files.length; i++) {
                            File f = files[start + i];
                            inv.setItem(9 + i, item(f.isDirectory() ? Material.CHEST : Material.PAPER,
                            (f.isDirectory() ? "§a" : "§e") + f.getName(),
                            List.of(f.isDirectory() ? "§7目录" : "§7" + f.length() + " bytes")));
                        }
                        inv.setItem(0, item(Material.ARROW, "§e向左翻页", null));
                        inv.setItem(4, item(Material.PAPER, "§e第 " + (page + 1) + " 页", null));
                        inv.setItem(8, item(Material.ARROW, "§e向右翻页", null));
                        inv.setItem(52, item(Material.ARROW, "§7返回上一级", null));
                        inv.setItem(53, item(Material.ARROW, "§7返回实例修改选择", null));
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
        Inventory inv = Bukkit.createInventory(
                new EditorHolder(P_HOOK_OVERVIEW, null, null, 0),
                54, T_HOOK_OVERVIEW);

        HookType[] types = HookType.values();
        for (int i = 0; i < types.length && i < 45; i++) {
            HookType t = types[i];
            int n = reg.count(t);
            Material mat = groupMaterial(t.group);
            List<String> lore = new ArrayList<>();
            lore.add("§7分组: §f" + t.group);
            lore.add("§7注册数: " + (n > 0 ? "§a" + n : "§70"));
            lore.add("§7ID: §f" + t.id);
            lore.add("");
            lore.add("§e左键查看详情并逐条卸载");
            if (n > 0) lore.add("§cShift + 右键清空此类");
            inv.setItem(i, item(mat, "§e" + t.displayName, lore));
        }

        inv.setItem(45, item(Material.TNT, "§c清空全部钩子", List.of(
                "§7当前总计: §f" + total,
                "§cShift + 左键确认清空",
                "§7清空后需插件重新注册才能恢复")));
        inv.setItem(53, item(Material.ARROW, "§7返回主页面", null));
        p.openInventory(inv);
    }

    public void openHookDetail(Player p, HookType type, int page) {
        DungeonHookRegistry reg = plugin.getHookRegistry();
        List<?> hooks = reg.getHooks(type);

        int perPage = 36;
        int maxPage = Math.max(0, (hooks.size() - 1) / perPage);
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        Inventory inv = Bukkit.createInventory(
                new EditorHolder(P_HOOK_DETAIL, null, type.name(), page),
                54, T_HOOK_DETAIL + type.displayName);

        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < hooks.size(); i++) {
            Object hook = hooks.get(start + i);
            List<String> lore = new ArrayList<>();
            lore.add("§7序号: §f#" + (start + i));
            lore.add("§7完整类型: §f" + hook.getClass().getName());
            lore.add("");
            lore.add("§c左键删除");
            inv.setItem(9 + i, item(Material.PAPER, "§e" + hookSource(hook), lore));
        }

        inv.setItem(0, item(Material.ARROW, "§e上一页", null));
        inv.setItem(4, item(Material.PAPER,
                "§e第 " + (page + 1) + " / " + (maxPage + 1) + " 页，共 " + hooks.size() + " 个", null));
        inv.setItem(8, item(Material.ARROW, "§e下一页", null));
        inv.setItem(45, item(Material.TNT, "§c清空此类", List.of(
                "§7当前: §f" + hooks.size() + " 个",
                "§cShift + 左键确认")));
        inv.setItem(53, item(Material.ARROW, "§7返回钩子总览", null));
        p.openInventory(inv);
    }

    private static Material groupMaterial(String group) {
        return switch (group) {
            case "L1" -> Material.LIGHT_BLUE_DYE;
            case "L2" -> Material.LIME_DYE;
            case "L3" -> Material.YELLOW_DYE;
            case "L4" -> Material.RED_DYE;
            default   -> Material.GRAY_DYE;
        };
    }

    /** 从 lambda 对象推断注册来源类名：com.foo.Bar$$Lambda$xxx → Bar */
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
                MessageUtil.sendMessage(p, "§c已清空全部钩子（共 " + n + " 个）");
                openHookOverview(p);
            } else {
                MessageUtil.sendError(p, "请按住 Shift 再左键以确认清空全部");
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
                MessageUtil.sendMessage(p, "§c已清空 " + type.displayName + " 类钩子（共 " + n + " 个）");
                openHookDetail(p, type, 0);
            } else {
                MessageUtil.sendError(p, "请按住 Shift 再左键以确认清空此类");
            }
            return;
        }
        if (slot >= 9 && slot < 45) {
            int idx = h.pageIndex * 36 + (slot - 9);
            if (plugin.getHookRegistry().removeHook(type, idx)) {
                MessageUtil.sendMessage(p, "§a已删除 1 个 " + type.displayName + " 钩子");
            }
            openHookDetail(p, type, h.pageIndex);
        }
    }
                    // ==================== 各页面点击处理 ====================
                    private void clickMain(Player p, int slot) {
                        if (slot == 9) openRoot(p);
                        else if (slot == 10) openHookOverview(p);
                        else if (slot == 11) openDungeonList(p, 0);
                        else if (slot == 13) { p.closeInventory(); plugin.reloadConfig(); MessageUtil.sendMessage(p, "§a插件已重载"); openMain(p); }
                        else if (slot == 15) { p.closeInventory(); plugin.getStartGUI().openStartMenu(p); }
                    }
                    
                    private void clickRoot(Player p, int slot, InventoryClickEvent e) {
                        EditorSession s = session(p);
                        FileConfiguration c = workingConfig(s, null);
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
                            beginInput(p, "§e请输入新的 " + path + " 值（整数）：",
                            input -> { try { c.set(path, Integer.parseInt(input)); } catch (Exception ex) { MessageUtil.sendError(p, "无效整数"); } openRoot(p); },
                            () -> openRoot(p));
                            return;
                        }
                        beginInput(p, "§e请输入新的 " + path + " 值：",
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
                MessageUtil.sendMessage(p, "§a已从磁盘重读配置");
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
                MessageUtil.sendMessage(p, "§a测试世界已删除");
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
    Inventory inv = Bukkit.createInventory(
            new EditorHolder(P_LIST_EDITOR, dungeon, configPath, page, sourceSection),
            54, T_LIST + configPath);
    int start = page * 36;
    for (int i = 0; i < 36 && start + i < list.size(); i++) {
        inv.setItem(9 + i, item(Material.PAPER, "§e#" + (start + i),
                List.of("§7" + list.get(start + i), "§c左键删除")));
    }
    inv.setItem(0, item(Material.ARROW, "§e向左翻页", null));
    inv.setItem(4, item(Material.PAPER, "§e第 " + (page + 1) + " 页", null));
    inv.setItem(8, item(Material.ARROW, "§e向右翻页", null));
    inv.setItem(45, item(Material.LIME_DYE, "§a添加条目", null));
    inv.setItem(53, item(Material.ARROW, "§7返回上一级", null));
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
    Inventory inv = Bukkit.createInventory(
            new EditorHolder(P_MAP_EDITOR, dungeon, configPath, page, sourceSection),
            54, T_MAP + configPath);
    int start = page * 36;
    for (int i = 0; i < 36 && start + i < keys.size(); i++) {
        String k = keys.get(start + i);
        Object v = sec.get(k);
        inv.setItem(9 + i, item(Material.PAPER, "§e" + k,
                List.of("§7值: §f" + v, "§e左键改值 §c右键删除")));
    }
    inv.setItem(0, item(Material.ARROW, "§e向左翻页", null));
    inv.setItem(4, item(Material.PAPER, "§e第 " + (page + 1) + " 页", null));
    inv.setItem(8, item(Material.ARROW, "§e向右翻页", null));
    inv.setItem(45, item(Material.LIME_DYE, "§a添加条目", null));
    inv.setItem(53, item(Material.ARROW, "§7返回上一级", null));
    p.openInventory(inv);
}
    private void clickConfigSection(Player p, EditorHolder h, int slot, InventoryClickEvent e) {
        EditorSession s = session(p);
        FileConfiguration c = workingConfig(s, h.dungeonName);
        if (slot == 0) { openConfigCat(p, h.dungeonName); return; }
        if (slot == 8) { saveWorking(p, s, h.dungeonName); openConfigSection(p, h.dungeonName, h.contextKey, 0); return; }
        List<Field> fields = sectionFields().getOrDefault(h.contextKey, List.of());
        int idx = slot - 9;
        if (idx < 0 || idx >= fields.size()) return;
        Field f = fields.get(idx);
        Object v = c.get(f.path);

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
            beginInput(p, "§e请输入 " + f.name + " 的新值（整数）：",
                    in -> { try { c.set(f.path, Long.parseLong(in)); } catch (Exception ex) { MessageUtil.sendError(p, "无效整数"); } openConfigSection(p, h.dungeonName, h.contextKey, 0); },
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
        beginInput(p, "§e请输入 " + f.name + " 的新值：",
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
                                beginInput(p, "§e请输入权重（整数）：",
                                in -> { try { c.set("dungeonInstance.structureChoose." + base, Integer.parseInt(in)); saveWorking(p, s, d); } catch (Exception ex) {} openRoomConfig(p, d, base); },
                                () -> openRoomConfig(p, d, base));
                            }
                            case 1 -> openDoorList(p, d, base, 0);
                            case 2 -> openChestList(p, d, base, 0);
                            case 3 -> openSpawnOrCheckpoint(p, d, base, false);
                            case 4 -> openSpawnOrCheckpoint(p, d, base, true);
                            case 8 -> openRoomList(p, d, 0);
                        }
                    }
                    
                    private void clickDoorList(Player p, EditorHolder h, int slot) {
                        String d = h.dungeonName, base = h.contextKey;
                        if (slot == 0) { openDoorList(p, d, base, Math.max(0, h.pageIndex - 1)); return; }
                        if (slot == 8) { openDoorList(p, d, base, h.pageIndex + 1); return; }
                        if (slot == 45) {
                            org.bukkit.block.Block target = p.getTargetBlockExact(5);
                            if (target == null) { MessageUtil.sendError(p, "请对准一个方块（距离 ≤ 5 格）"); return; }
                            int[] origin = originOf(p);
                            if (origin == null) { MessageUtil.sendError(p, "请先用木镐（创造 + OP）框选房间区域，用于确定相对坐标原点"); return; }
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("x", target.getX() - origin[0]);
                            entry.put("y", target.getY() - origin[1]);
                            entry.put("z", target.getZ() - origin[2]);
                            entry.put("facing", playerFacing(p));
                            entry.put("type", "default");
                            entry.put("weight", 10);
                            appendYamlList(roomFile(d, base, "_door.yml"), "doors", entry);
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
                        List<Map<?, ?>> doors = readYamlList(roomFile(d, base, "_door.yml"), "doors");
                        if (idx < 0 || idx >= doors.size()) { openDoorList(p, d, base, 0); return; }
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = new LinkedHashMap<>((Map<String, Object>) doors.get(idx));
                        
                        switch (slot) {
                            case 10, 11, 12 -> {
                                String key = slot == 10 ? "x" : slot == 11 ? "y" : "z";
                                beginInput(p, "§e请输入 " + key + "（整数）：",
                                in -> { try { m.put(key, Integer.parseInt(in)); saveDoorAt(d, base, idx, m); } catch (Exception ex) {} openSingleDoor(p, d, base, idx); },
                                () -> openSingleDoor(p, d, base, idx));
                            }
                            case 13 -> {
                                String[] order = {"x+", "z+", "x-", "z-", "y+", "y-"};
                                String cur = String.valueOf(m.getOrDefault("facing", "x+"));
                                int i = 0; for (int j = 0; j < order.length; j++) if (order[j].equals(cur)) { i = (j + 1) % order.length; break; }
                                m.put("facing", order[i]);
                                saveDoorAt(d, base, idx, m);
                                openSingleDoor(p, d, base, idx);
                            }
                            case 14 -> {
                                String[] order = {"default", "stairs", "fallback", "door", "arch"};
                                String cur = String.valueOf(m.getOrDefault("type", "default"));
                                int i = 0; for (int j = 0; j < order.length; j++) if (order[j].equals(cur)) { i = (j + 1) % order.length; break; }
                                m.put("type", order[i]);
                                saveDoorAt(d, base, idx, m);
                                openSingleDoor(p, d, base, idx);
                            }
                            case 15 -> beginInput(p, "§e请输入权重（整数）：",
                            in -> { try { m.put("weight", Integer.parseInt(in)); saveDoorAt(d, base, idx, m); } catch (Exception ex) {} openSingleDoor(p, d, base, idx); },
                            () -> openSingleDoor(p, d, base, idx));
                            case 21 -> {
                                List<Map<?, ?>> list = new ArrayList<>(readYamlList(roomFile(d, base, "_door.yml"), "doors"));
                                list.remove(idx);
                                writeYamlList(roomFile(d, base, "_door.yml"), "doors", list);
                                openDoorList(p, d, base, 0);
                            }
                            case 26 -> openDoorList(p, d, base, 0);
                        }
                    }
                    
                    private void saveDoorAt(String d, String base, int idx, Map<String, Object> m) {
                        File f = roomFile(d, base, "_door.yml");
                        List<Map<?, ?>> list = new ArrayList<>(readYamlList(f, "doors"));
                        if (idx < 0 || idx >= list.size()) return;
                        list.set(idx, m);
                        writeYamlList(f, "doors", list);
                    }
                    
                    private void clickChestList(Player p, EditorHolder h, int slot) {
                        String d = h.dungeonName, base = h.contextKey;
                        if (slot == 0) { openChestList(p, d, base, Math.max(0, h.pageIndex - 1)); return; }
                        if (slot == 8) { openChestList(p, d, base, h.pageIndex + 1); return; }
                        if (slot == 45) {
                            org.bukkit.block.Block target = p.getTargetBlockExact(5);
                            if (target == null) { MessageUtil.sendError(p, "请对准一个方块（距离 ≤ 5 格）"); return; }
                            int[] origin = originOf(p);
                            if (origin == null) { MessageUtil.sendError(p, "请先用木镐（创造 + OP）框选房间区域"); return; }
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("pos", Arrays.asList(
                                target.getX() - origin[0],
                                target.getY() - origin[1],
                                target.getZ() - origin[2]));
                                entry.put("loot", "50 DIAMOND 1");
                                appendYamlList(roomFile(d, base, "_chest.yml"), "chests", entry);
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
                            File f = roomFile(d, base, "_chest.yml");
                            List<Map<?, ?>> chests = readYamlList(f, "chests");
                            if (idx < 0 || idx >= chests.size()) { openChestList(p, d, base, 0); return; }
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = new LinkedHashMap<>((Map<String, Object>) chests.get(idx));
                            switch (slot) {
                                case 13 -> {
                                    ItemStack hand = p.getInventory().getItemInMainHand();
                                    if (hand.getType() == Material.AIR) { MessageUtil.sendError(p, "请手持有效物品"); return; }
                                    m.put("loot", "50 " + hand.getType().name() + " " + hand.getAmount());
                                    chests.set(idx, m);
                                    writeYamlList(f, "chests", chests);
                                    openSingleChest(p, d, base, idx);
                                }
                                case 21 -> {
                                    List<Map<?, ?>> list = new ArrayList<>(chests);
                                    list.remove(idx);
                                    writeYamlList(f, "chests", list);
                                    openChestList(p, d, base, 0);
                                }
                                case 26 -> openChestList(p, d, base, 0);
                            }
                        }
                        
                        private void clickSpawnOrCheckpoint(Player p, EditorHolder h, int slot) {
                            boolean cp = P_CHECKPOINT.equals(h.pageId);
                            String d = h.dungeonName, base = h.contextKey;
                            String suffix = cp ? "_checkpoint.yml" : "_spawn.yml";
                            File f = roomFile(d, base, suffix);
                            
                            if (slot == 13) {
                                int[] origin = originOf(p);
                                if (origin == null) { MessageUtil.sendError(p, "请先用木镐（创造 + OP）框选房间区域"); return; }
                                Location loc = p.getLocation();
                                YamlConfiguration yml = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
                                yml.set("spawn.x", loc.getBlockX() - origin[0]);
                                yml.set("spawn.y", loc.getBlockY() - origin[1]);
                                yml.set("spawn.z", loc.getBlockZ() - origin[2]);
                                try { f.getParentFile().mkdirs(); yml.save(f); } catch (Exception ex) { MessageUtil.sendError(p, "保存失败: " + ex.getMessage()); return; }
                                MessageUtil.sendMessage(p, "§a坐标已更新");
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
                                if (f.isDirectory()) openCodeFiles(p, d, newRel, 0);
                                else {
                                    p.closeInventory();
                                    try {
                                        List<String> lines = java.nio.file.Files.readAllLines(f.toPath());
                                        MessageUtil.sendMessage(p, "§e" + newRel + " (§7" + lines.size() + " 行§e)");
                                        for (int i = 0; i < Math.min(20, lines.size()); i++) p.sendMessage("§7" + lines.get(i));
                                        if (lines.size() > 20) p.sendMessage("§7...");
                                    } catch (Exception ex) {
                                        MessageUtil.sendError(p, "读取失败: " + ex.getMessage());
                                    }
                                }
                            }
                        }
                        
private void clickListEditor(Player p, EditorHolder h, int slot) {
    EditorSession s = session(p);
    FileConfiguration c = workingConfig(s, h.dungeonName);
    List<String> list = new ArrayList<>(c.getStringList(h.contextKey));
    String back = h.returnSection != null ? h.returnSection : "player";

    if (slot == 0) { openListEditor(p, h.dungeonName, h.contextKey,
            Math.max(0, h.pageIndex - 1), h.returnSection); return; }
    if (slot == 8) { openListEditor(p, h.dungeonName, h.contextKey,
            h.pageIndex + 1, h.returnSection); return; }
    if (slot == 45) {
        beginInput(p, "§e请输入要添加的值：",
                in -> { list.add(in); c.set(h.contextKey, list);
                        openListEditor(p, h.dungeonName, h.contextKey, h.pageIndex, h.returnSection); },
                () -> openListEditor(p, h.dungeonName, h.contextKey, h.pageIndex, h.returnSection));
        return;
    }
    if (slot == 53) { openConfigSection(p, h.dungeonName, back, 0); return; }
    if (slot >= 9 && slot < 45) {
        int idx = h.pageIndex * 36 + (slot - 9);
        if (idx < list.size()) {
            list.remove(idx);
            c.set(h.contextKey, list);
            openListEditor(p, h.dungeonName, h.contextKey, h.pageIndex, h.returnSection);
        }
    }
}
                        
private void clickMapEditor(Player p, EditorHolder h, int slot, InventoryClickEvent e) {
    EditorSession s = session(p);
    FileConfiguration c = workingConfig(s, h.dungeonName);
    ConfigurationSection sec = c.getConfigurationSection(h.contextKey);
    if (sec == null) return;
    String back = h.returnSection != null ? h.returnSection : "economy";

    if (slot == 0) { openMapEditor(p, h.dungeonName, h.contextKey,
            Math.max(0, h.pageIndex - 1), h.returnSection); return; }
    if (slot == 8) { openMapEditor(p, h.dungeonName, h.contextKey,
            h.pageIndex + 1, h.returnSection); return; }
    if (slot == 45) {
        beginInput(p, "§e请输入 key=value（例如 money=100）：",
                in -> {
                    String[] kv = in.split("=", 2);
                    if (kv.length == 2) {
                        try { sec.set(kv[0], Double.parseDouble(kv[1])); }
                        catch (Exception ex) { sec.set(kv[0], kv[1]); }
                    }
                    openMapEditor(p, h.dungeonName, h.contextKey, h.pageIndex, h.returnSection);
                },
                () -> openMapEditor(p, h.dungeonName, h.contextKey, h.pageIndex, h.returnSection));
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
            beginInput(p, "§e请输入 " + key + " 的新值：",
                    in -> { try { sec.set(key, Double.parseDouble(in)); }
                            catch (Exception ex) { sec.set(key, in); }
                            openMapEditor(p, h.dungeonName, h.contextKey, h.pageIndex, h.returnSection); },
                    () -> openMapEditor(p, h.dungeonName, h.contextKey, h.pageIndex, h.returnSection));
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
                            YamlConfiguration y = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
                            y.set(root, list);
                            try { f.getParentFile().mkdirs(); y.save(f); } catch (Exception ignored) {}
                        }
                        
                        private void appendYamlList(File f, String root, Map<String, Object> entry) {
                            List<Map<?, ?>> list = new ArrayList<>(readYamlList(f, root));
                            list.add(entry);
                            writeYamlList(f, root, list);
                        }
                        
                        /**
                        * 从 SelectionManager 获取选区原点（min 角）。
                        * 返回 null 表示玩家尚未设置有效选区。
                        */
                        private int[] originOf(Player p) {
                            com.him.dungeons.util.SelectionManager.Selection sel =
                            com.him.dungeons.util.SelectionManager.getSelection(p.getUniqueId());
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
        if (sel == null) {
            MessageUtil.sendError(p, "请先用木镐（创造 + OP）选择区域");
            return;
        }
        beginInput(p, "§e请输入房间基础文件名（例如 combat/myCombat）：",
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
                                    MessageUtil.sendMessage(p, "§a原理图已保存到 " + schematicFile.getPath());
                                    ConfigUtil.refreshStructureWeights(dungeon);
                                } else {
                                    MessageUtil.sendError(p, "保存失败");
                                }
                                openDungeonEditor(p, dungeon);
                            });
                },
                () -> openDungeonEditor(p, dungeon));
    }

    private void beginAddDoor(Player p, String dungeon) {
        org.bukkit.block.Block target = p.getTargetBlockExact(5);
        if (target == null) { MessageUtil.sendError(p, "请对准一个方块（距离 ≤ 5 格）"); return; }
        int[] origin = originOf(p);
        if (origin == null) { MessageUtil.sendError(p, "请先框选房间区域"); return; }
        int relX = target.getX() - origin[0];
        int relY = target.getY() - origin[1];
        int relZ = target.getZ() - origin[2];
        beginInput(p, "§e请输入：<路径> <朝向> [类型] [权重]（例如 combat/myCombat z+ default 10）",
                in -> {
                    String[] parts = in.trim().split("\\s+");
                    if (parts.length < 2) { MessageUtil.sendError(p, "格式错误"); openDungeonEditor(p, dungeon); return; }
                    String baseName = parts[0];
                    String facing = parts[1];
                    String type = parts.length >= 3 ? parts[2] : "default";
                    int weight = 10;
                    if (parts.length >= 4) try { weight = Integer.parseInt(parts[3]); } catch (Exception ignored) {}
                    if (!com.him.dungeons.util.TransformUtil.isValidFacing(facing)) {
                        MessageUtil.sendError(p, "无效朝向（x+/x-/y+/y-/z+/z-）");
                        openDungeonEditor(p, dungeon);
                        return;
                    }
                    File roomsDir = new File(plugin.getDataFolder(), dungeon + "/rooms");
                    String path = baseName.replace('/', File.separatorChar);
                    File doorFile = new File(roomsDir, path + "_door.yml");
                    doorFile.getParentFile().mkdirs();
                    YamlConfiguration yaml = doorFile.exists()
                            ? YamlConfiguration.loadConfiguration(doorFile) : new YamlConfiguration();
                    List<Map<String, Object>> doorList = new ArrayList<>();
                    List<?> existing = yaml.getList("doors");
                    if (existing == null) existing = yaml.getList("");
                    if (existing != null) for (Object o : existing) if (o instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) o;
                        doorList.add(m);
                    }
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("x", relX);
                    entry.put("y", relY);
                    entry.put("z", relZ);
                    entry.put("facing", facing);
                    entry.put("type", type);
                    entry.put("weight", weight);
                    doorList.add(entry);
                    yaml.set("doors", doorList);
                    try {
                        yaml.save(doorFile);
                        MessageUtil.sendMessage(p, "§a门已添加：" + doorFile.getPath()
                                + "（类型: " + type + "，权重: " + weight + "）");
                    } catch (Exception ex) {
                        MessageUtil.sendError(p, "保存失败: " + ex.getMessage());
                    }
                    openDungeonEditor(p, dungeon);
                },
                () -> openDungeonEditor(p, dungeon));
    }

    private void beginAddChest(Player p, String dungeon) {
        org.bukkit.block.Block target = p.getTargetBlockExact(5);
        if (target == null) { MessageUtil.sendError(p, "请对准一个方块（距离 ≤ 5 格）"); return; }
        int[] origin = originOf(p);
        if (origin == null) { MessageUtil.sendError(p, "请先框选房间区域"); return; }
        ChestAddSession cs = new ChestAddSession(dungeon,
                target.getX() - origin[0],
                target.getY() - origin[1],
                target.getZ() - origin[2]);
        chestAddSessions.put(p.getUniqueId(), cs);
        p.closeInventory();
        MessageUtil.sendMessage(p, "§e请输入房间路径（例如 combat/myCombat）：");
        MessageUtil.sendMessage(p, "§7然后手持物品输入概率（0-100），输入 done 完成，cancel 取消");
    }

    private void beginAddSpawn(Player p, String dungeon) {
        int[] origin = originOf(p);
        if (origin == null) { MessageUtil.sendError(p, "请先框选房间区域"); return; }
        Location loc = p.getLocation();
        int relX = loc.getBlockX() - origin[0];
        int relY = loc.getBlockY() - origin[1];
        int relZ = loc.getBlockZ() - origin[2];
        beginInput(p, "§e请输入房间路径（例如 start/myStart）：",
                in -> {
                    String baseName = in.trim();
                    File roomsDir = new File(plugin.getDataFolder(), dungeon + "/rooms");
                    String path = baseName.replace('/', File.separatorChar);
                    File spawnFile = new File(roomsDir, path + "_spawn.yml");
                    spawnFile.getParentFile().mkdirs();
                    YamlConfiguration yaml = new YamlConfiguration();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("x", relX); m.put("y", relY); m.put("z", relZ);
                    yaml.set("spawn", m);
                    try {
                        yaml.save(spawnFile);
                        MessageUtil.sendMessage(p, "§a出生点已保存：" + spawnFile.getPath());
                    } catch (Exception ex) {
                        MessageUtil.sendError(p, "保存失败: " + ex.getMessage());
                    }
                    openDungeonEditor(p, dungeon);
                },
                () -> openDungeonEditor(p, dungeon));
    }

    private void beginAddCheckpoint(Player p, String dungeon) {
        int[] origin = originOf(p);
        if (origin == null) { MessageUtil.sendError(p, "请先框选房间区域"); return; }
        Location loc = p.getLocation();
        int relX = loc.getBlockX() - origin[0];
        int relY = loc.getBlockY() - origin[1];
        int relZ = loc.getBlockZ() - origin[2];
        beginInput(p, "§e请输入房间路径（例如 combat/myCombat）：",
                in -> {
                    String baseName = in.trim();
                    File roomsDir = new File(plugin.getDataFolder(), dungeon + "/rooms");
                    String path = baseName.replace('/', File.separatorChar);
                    File f = new File(roomsDir, path + "_checkpoint.yml");
                    f.getParentFile().mkdirs();
                    YamlConfiguration yaml = new YamlConfiguration();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("x", relX); m.put("y", relY); m.put("z", relZ);
                    yaml.set("spawn", m);
                    try {
                        yaml.save(f);
                        MessageUtil.sendMessage(p, "§a存档点已保存：" + f.getPath());
                    } catch (Exception ex) {
                        MessageUtil.sendError(p, "保存失败: " + ex.getMessage());
                    }
                    openDungeonEditor(p, dungeon);
                },
                () -> openDungeonEditor(p, dungeon));
    }

    private void beginRemoveRoom(Player p, String dungeon) {
        beginInput(p, "§e请输入要删除的房间路径（例如 combat/myCombat）：",
                in -> {
                    String baseName = in.trim();
                    File roomsDir = new File(plugin.getDataFolder(), dungeon + "/rooms");
                    String path = baseName.replace('/', File.separatorChar);
                    File roomFile = new File(roomsDir, path + ".schem");
                    if (!roomFile.exists()) { MessageUtil.sendError(p, "房间不存在"); openDungeonEditor(p, dungeon); return; }
                    roomFile.delete();
                    new File(roomsDir, path + "_door.yml").delete();
                    new File(roomsDir, path + "_chest.yml").delete();
                    new File(roomsDir, path + "_spawn.yml").delete();
                    new File(roomsDir, path + "_checkpoint.yml").delete();
                    MessageUtil.sendMessage(p, "§a已删除房间 " + baseName);
                    ConfigUtil.refreshStructureWeights(dungeon);
                    openDungeonEditor(p, dungeon);
                },
                () -> openDungeonEditor(p, dungeon));
    }

    private void beginRemoveDoor(Player p, String dungeon) {
        beginInput(p, "§e请输入房间路径（例如 combat/myCombat）：",
                in -> {
                    String baseName = in.trim();
                    File roomsDir = new File(plugin.getDataFolder(), dungeon + "/rooms");
                    String path = baseName.replace('/', File.separatorChar);
                    File f = new File(roomsDir, path + "_door.yml");
                    if (!f.exists()) { MessageUtil.sendError(p, "门文件不存在"); openDungeonEditor(p, dungeon); return; }
                    f.delete();
                    MessageUtil.sendMessage(p, "§a已删除门文件");
                    openDungeonEditor(p, dungeon);
                },
                () -> openDungeonEditor(p, dungeon));
    }

    private void beginRemoveChest(Player p, String dungeon) {
        beginInput(p, "§e请输入房间路径（例如 combat/myCombat）：",
                in -> {
                    String baseName = in.trim();
                    File roomsDir = new File(plugin.getDataFolder(), dungeon + "/rooms");
                    String path = baseName.replace('/', File.separatorChar);
                    File f = new File(roomsDir, path + "_chest.yml");
                    if (!f.exists()) { MessageUtil.sendError(p, "箱子文件不存在"); openDungeonEditor(p, dungeon); return; }
                    f.delete();
                    MessageUtil.sendMessage(p, "§a已删除箱子文件");
                    openDungeonEditor(p, dungeon);
                },
                () -> openDungeonEditor(p, dungeon));
    }

    private void beginRemoveSpawn(Player p, String dungeon) {
        beginInput(p, "§e请输入房间路径（例如 start/myStart）：",
                in -> {
                    String baseName = in.trim();
                    File roomsDir = new File(plugin.getDataFolder(), dungeon + "/rooms");
                    String path = baseName.replace('/', File.separatorChar);
                    File f = new File(roomsDir, path + "_spawn.yml");
                    if (!f.exists()) { MessageUtil.sendError(p, "出生点文件不存在"); openDungeonEditor(p, dungeon); return; }
                    f.delete();
                    MessageUtil.sendMessage(p, "§a已删除出生点文件");
                    openDungeonEditor(p, dungeon);
                },
                () -> openDungeonEditor(p, dungeon));
    }

    private void beginRemoveCheckpoint(Player p, String dungeon) {
        beginInput(p, "§e请输入房间路径（例如 combat/myCombat）：",
                in -> {
                    String baseName = in.trim();
                    File roomsDir = new File(plugin.getDataFolder(), dungeon + "/rooms");
                    String path = baseName.replace('/', File.separatorChar);
                    File f = new File(roomsDir, path + "_checkpoint.yml");
                    if (!f.exists()) { MessageUtil.sendError(p, "存档点文件不存在"); openDungeonEditor(p, dungeon); return; }
                    f.delete();
                    MessageUtil.sendMessage(p, "§a已删除存档点文件");
                    openDungeonEditor(p, dungeon);
                },
                () -> openDungeonEditor(p, dungeon));
    }
                    }