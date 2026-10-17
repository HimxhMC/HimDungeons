package com.him.dungeons.gui;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.schematic.AsyncSchematicSaver;
import com.him.dungeons.util.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地牢管理 GUI（已完全移除 WorldEdit 依赖）
 * <p>选区通过 SelectionManager（木镐工具）获取，原理图保存通过 AsyncSchematicSaver 完成。</p>
 */
public class DungeonManagerGUI implements Listener {

    private final HimDungeons plugin;
    private final Map<UUID, String> editingDungeon = new ConcurrentHashMap<>();
    private final Map<UUID, SaveRoomData> waitingRoomData = new ConcurrentHashMap<>();
    private final Map<UUID, DoorTempData> doorTempData = new ConcurrentHashMap<>();
    private final Map<UUID, ChestInputSession> waitingChest = new ConcurrentHashMap<>();
    private final Map<UUID, SpawnTempData> spawnTempData = new ConcurrentHashMap<>();
    private final Map<UUID, DeleteData> deleteRoomTemp = new ConcurrentHashMap<>();
    private final Map<UUID, DeleteData> deleteDoorTemp = new ConcurrentHashMap<>();
    private final Map<UUID, DeleteData> deleteChestTemp = new ConcurrentHashMap<>();
    private final Map<UUID, DeleteData> deleteSpawnTemp = new ConcurrentHashMap<>();
    private final Map<UUID, CheckpointTempData> waitingCheckpoint = new ConcurrentHashMap<>();
    private final Map<UUID, DeleteCheckpointData> waitingDeleteCheckpoint = new ConcurrentHashMap<>();

    // ==================== 内部数据类 ====================

    private static class CheckpointTempData {
        final String dungeonName;
        final int x, y, z;
        CheckpointTempData(String dungeon, int x, int y, int z) {
            this.dungeonName = dungeon;
            this.x = x; this.y = y; this.z = z;
        }
    }

    private static class DeleteCheckpointData {
        final String dungeonName;
        DeleteCheckpointData(String dungeon) {
            this.dungeonName = dungeon;
        }
    }

    private static class SaveRoomData {
        final String dungeonName;
        SaveRoomData(String dungeon) {
            this.dungeonName = dungeon;
        }
    }

    private static class DoorTempData {
        final String dungeonName;
        final int x, y, z;
        DoorTempData(String dungeon, int x, int y, int z) {
            this.dungeonName = dungeon;
            this.x = x; this.y = y; this.z = z;
        }
    }

    private static class ChestInputSession {
        String dungeonName;
        String baseName;
        int x, y, z;
        final List<Map<String, Object>> chests = new ArrayList<>();
    }

    private static class SpawnTempData {
        final String dungeonName;
        final int x, y, z;
        SpawnTempData(String dungeon, int x, int y, int z) {
            this.dungeonName = dungeon;
            this.x = x; this.y = y; this.z = z;
        }
    }

    private static class DeleteData {
        final String dungeonName;
        DeleteData(String dungeon) {
            this.dungeonName = dungeon;
        }
    }

    // ==================== 构造 ====================

    public DungeonManagerGUI(HimDungeons plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ==================== 主菜单 ====================

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.BLUE + "地牢管理");
        File dataFolder = plugin.getDataFolder();
        File[] dirs = dataFolder.listFiles(File::isDirectory);
        int slot = 9;
        if (dirs != null) {
            for (File dir : dirs) {
                if (new File(dir, "config.yml").exists()) {
                    ItemStack item = new ItemStack(Material.CHEST);
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName(ChatColor.GREEN + dir.getName());
                    meta.setLore(Collections.singletonList(ChatColor.GRAY + "点击编辑"));
                    item.setItemMeta(meta);
                    inv.setItem(slot++, item);
                    if (slot >= 45) break;
                }
            }
        }
        setItem(inv, 45, Material.BOOK, "修改全局配置", null);
        setItem(inv, 46, Material.WRITABLE_BOOK, "统一修改所有地牢配置", null);
        setItem(inv, 47, Material.ARROW, "刷新列表", null);
        setItem(inv, 53, Material.BARRIER, "关闭", null);
        player.openInventory(inv);
    }

    // ==================== 地牢子菜单 ====================

    public void openDungeonMenu(Player player, String dungeonName) {
        editingDungeon.put(player.getUniqueId(), dungeonName);
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.GOLD + "管理: " + dungeonName);
        setItem(inv, 9, Material.GRASS_BLOCK, "保存新房间", "将选区保存为 <房间类型>/<基础文件名>.schem");
        setItem(inv, 10, Material.OAK_DOOR, "添加门锚点", "注视方块并输入朝向");
        setItem(inv, 11, Material.CHEST, "添加箱子", "注视方块并输入战利品");
        setItem(inv, 12, Material.COMPASS, "添加出生点", "注视方块保存玩家位置");
        setItem(inv, 13, Material.TNT, "删除房间", "删除整个房间类型目录");
        setItem(inv, 14, Material.OAK_DOOR, "删除门", "删除指定基础文件名的门文件");
        setItem(inv, 15, Material.CHEST, "删除箱子", "删除指定基础文件名的箱子文件");
        setItem(inv, 16, Material.COMPASS, "删除出生点", "删除指定基础文件名的出生点文件");
        setItem(inv, 17, Material.BOOK, "修改配置", "打开配置编辑器");
        setItem(inv, 0, Material.ARROW, "返回", null);
        setItem(inv, 53, Material.BARRIER, "关闭", null);
        setItem(inv, 20, Material.LIME_DYE, "添加存档点", "为指定房间设置存档点");
        setItem(inv, 21, Material.GRAY_DYE, "删除存档点", "移除指定房间的存档点");
        player.openInventory(inv);
    }

    private void setItem(Inventory inv, int slot, Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        if (lore != null) meta.setLore(Collections.singletonList(ChatColor.GRAY + lore));
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    // ==================== 点击事件 ====================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();
        if (!title.startsWith(ChatColor.BLUE + "地牢管理") && !title.startsWith(ChatColor.GOLD + "管理: ")) return;
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (title.startsWith(ChatColor.BLUE + "地牢管理")) {
            if (slot == 53) { p.closeInventory(); return; }
            if (slot == 45) { ConfigEditorUtil.openEditor(p, null); return; }
            if (slot == 46) { ConfigEditorUtil.openBatchEditor(p); return; }
            if (slot == 47) { openMainMenu(p); return; }
            if (slot >= 9 && slot < 45) {
                String dungeonName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                openDungeonMenu(p, dungeonName);
            }
        } else if (title.startsWith(ChatColor.GOLD + "管理: ")) {
            String dungeonName = editingDungeon.get(p.getUniqueId());
            if (dungeonName == null) { p.closeInventory(); return; }
            if (slot == 0) { openMainMenu(p); return; }
            if (slot == 53) { p.closeInventory(); return; }
            switch (slot) {
                case 9 -> handleSaveRoom(p, dungeonName);
                case 10 -> handleAddDoor(p, dungeonName);
                case 11 -> handleAddChest(p, dungeonName);
                case 12 -> handleAddSpawn(p, dungeonName);
                case 13 -> handleDeleteRoom(p, dungeonName);
                case 14 -> handleDeleteDoor(p, dungeonName);
                case 15 -> handleDeleteChest(p, dungeonName);
                case 16 -> handleDeleteSpawn(p, dungeonName);
                case 17 -> { p.closeInventory(); ConfigEditorUtil.openEditor(p, dungeonName); }
                case 20 -> handleAddCheckpoint(p, dungeonName);
                case 21 -> handleDeleteCheckpoint(p, dungeonName);
            }
        }
    }

    // ==================== 获取选区原点（用于相对坐标） ====================

    /**
     * 获取玩家当前选区原点（min 角），用于计算相对坐标。
     * 返回 null 表示未设置有效选区。
     */
    private int[] getSelectionOrigin(Player player) {
        SelectionManager.Selection sel = SelectionManager.getSelection(player.getUniqueId());
        if (sel == null) return null;
        return new int[]{sel.minX, sel.minY, sel.minZ};
    }

    // ==================== 功能实现 ====================

    private void handleSaveRoom(Player player, String dungeonName) {
        SelectionManager.Selection sel = SelectionManager.getSelection(player.getUniqueId());
        if (sel == null) {
            MessageUtil.sendError(player, "请先用木镐（创造模式 + OP）选择区域");
            return;
        }
        waitingRoomData.put(player.getUniqueId(), new SaveRoomData(dungeonName));
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入房间基础文件名（用空格分隔），例如 combat/myCombat");
        MessageUtil.sendMessage(player, "将保存为 <房间类型>/<基础文件名>.schem");
    }

    private void handleAddDoor(Player player, String dungeonName) {
        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            MessageUtil.sendError(player, "请对准一个方块（距离 ≤ 5 格）");
            return;
        }
        int[] origin = getSelectionOrigin(player);
        if (origin == null) {
            MessageUtil.sendError(player, "请先用木镐（创造模式 + OP）选择区域（用于确定相对坐标原点）");
            return;
        }
        int relX = target.getX() - origin[0];
        int relY = target.getY() - origin[1];
        int relZ = target.getZ() - origin[2];
        doorTempData.put(player.getUniqueId(), new DoorTempData(dungeonName, relX, relY, relZ));
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入相对于 rooms/ 的路径、朝向、类型(可选)、权重(可选)");
        MessageUtil.sendMessage(player, "例如: a/b x+ doorType 20  或  a/b x+");
    }

    private void handleAddChest(Player player, String dungeonName) {
        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            MessageUtil.sendError(player, "请对准一个方块（距离 ≤ 5 格）");
            return;
        }
        int[] origin = getSelectionOrigin(player);
        if (origin == null) {
            MessageUtil.sendError(player, "请先用木镐（创造模式 + OP）选择区域（用于确定相对坐标原点）");
            return;
        }
        ChestInputSession session = new ChestInputSession();
        session.dungeonName = dungeonName;
        session.x = target.getX() - origin[0];
        session.y = target.getY() - origin[1];
        session.z = target.getZ() - origin[2];
        waitingChest.put(player.getUniqueId(), session);
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入房间基础文件名（用空格分隔），例如 combat/myCombat");
        MessageUtil.sendMessage(player, "然后手持物品输入概率（不带%），可重复添加多个战利品");
        MessageUtil.sendMessage(player, "输入 'done' 完成，输入 'cancel' 取消");
    }

    private void handleAddSpawn(Player player, String dungeonName) {
        int[] origin = getSelectionOrigin(player);
        if (origin == null) {
            MessageUtil.sendError(player, "请先用木镐（创造模式 + OP）选择区域（用于确定相对坐标原点）");
            return;
        }
        Location loc = player.getLocation();
        int relX = loc.getBlockX() - origin[0];
        int relY = loc.getBlockY() - origin[1];
        int relZ = loc.getBlockZ() - origin[2];
        spawnTempData.put(player.getUniqueId(), new SpawnTempData(dungeonName, relX, relY, relZ));
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入房间基础文件名（用空格分隔），例如 combat/myCombat");
        MessageUtil.sendMessage(player, "将保存为 <基础文件名>_spawn.yml");
    }

    private void handleAddCheckpoint(Player player, String dungeonName) {
        int[] origin = getSelectionOrigin(player);
        if (origin == null) {
            MessageUtil.sendError(player, "请先用木镐（创造模式 + OP）选择区域（用于确定相对坐标原点）");
            return;
        }
        Location loc = player.getLocation();
        int relX = loc.getBlockX() - origin[0];
        int relY = loc.getBlockY() - origin[1];
        int relZ = loc.getBlockZ() - origin[2];
        waitingCheckpoint.put(player.getUniqueId(), new CheckpointTempData(dungeonName, relX, relY, relZ));
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入房间基础文件名（相对于 rooms/ 的路径），例如 combat/myCombat");
        MessageUtil.sendMessage(player, "将创建 <基础文件名>_checkpoint.yml 文件");
    }

    private void handleDeleteRoom(Player player, String dungeonName) {
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入要删除的房间类型名，输入 cancel 取消");
        deleteRoomTemp.put(player.getUniqueId(), new DeleteData(dungeonName));
    }

    private void handleDeleteDoor(Player player, String dungeonName) {
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入房间基础文件名（相对于 rooms/ 的路径），例如 combat/myCombat");
        deleteDoorTemp.put(player.getUniqueId(), new DeleteData(dungeonName));
    }

    private void handleDeleteChest(Player player, String dungeonName) {
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入房间基础文件名（相对于 rooms/ 的路径），例如 combat/myCombat");
        deleteChestTemp.put(player.getUniqueId(), new DeleteData(dungeonName));
    }

    private void handleDeleteSpawn(Player player, String dungeonName) {
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入房间基础文件名（相对于 rooms/ 的路径），例如 combat/myCombat");
        deleteSpawnTemp.put(player.getUniqueId(), new DeleteData(dungeonName));
    }

    private void handleDeleteCheckpoint(Player player, String dungeonName) {
        player.closeInventory();
        waitingDeleteCheckpoint.put(player.getUniqueId(), new DeleteCheckpointData(dungeonName));
        MessageUtil.sendMessage(player, "请输入要删除存档点的房间基础文件名（相对于 rooms/ 的路径）");
        MessageUtil.sendMessage(player, "输入 cancel 取消");
    }

    // ==================== 聊天监听 ====================

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String msg = e.getMessage().trim();

        // ========== 1. 保存房间 ==========
        if (waitingRoomData.containsKey(uuid)) {
            e.setCancelled(true);
            SaveRoomData data = waitingRoomData.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) {
                MessageUtil.sendMessage(p, "已取消");
                return;
            }
            String[] parts = msg.split(" ");
            if (parts.length < 1) {
                MessageUtil.sendError(p, "格式: 相对于 rooms/ 的路径");
                return;
            }
            String baseName = parts[0];

            SelectionManager.Selection sel = SelectionManager.getSelection(uuid);
            if (sel == null) {
                MessageUtil.sendError(p, "请先用木镐（创造模式 + OP）选择区域");
                return;
            }

            File roomsDir = new File(plugin.getDataFolder(), data.dungeonName + "/rooms");
            String path = baseName.replace('/', File.separatorChar);
            File schematicFile = new File(roomsDir, path + ".schem");
            schematicFile.getParentFile().mkdirs();

            // 保存格式从全局配置读取，默认 3
            int saveFormat = ConfigUtil.getSchematicSaveFormat();

            AsyncSchematicSaver.saveAsync(sel, schematicFile, saveFormat, success -> {
                if (success) {
                    MessageUtil.sendMessage(p, "原理图已保存到 " + schematicFile.getPath());
                } else {
                    MessageUtil.sendError(p, "保存失败");
                }
                openDungeonMenuSync(p, data.dungeonName);
            });
            return;
        }

        // ========== 2. 添加门 ==========
        if (doorTempData.containsKey(uuid)) {
            e.setCancelled(true);
            DoorTempData data = doorTempData.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) {
                MessageUtil.sendMessage(p, "已取消");
                return;
            }
            String[] parts = msg.split(" ");
            if (parts.length < 2) {
                MessageUtil.sendError(p, "格式: 路径 朝向 [类型] [权重]");
                return;
            }
            String baseName = parts[0];
            String facing = parts[1];
            String type = (parts.length >= 3) ? parts[2] : "default";
            int weight = 10;
            if (parts.length >= 4) {
                try {
                    weight = Integer.parseInt(parts[3]);
                } catch (NumberFormatException ex) {
                    MessageUtil.sendError(p, "权重必须为整数");
                    return;
                }
            }
            if (!TransformUtil.isValidFacing(facing)) {
                MessageUtil.sendError(p, "无效朝向，请输入 x+, x-, y+, y-, z+, z- 之一");
                return;
            }
            File roomsDir = new File(plugin.getDataFolder(), data.dungeonName + "/rooms");
            String path = baseName.replace('/', File.separatorChar);
            File doorFile = new File(roomsDir, path + "_door.yml");
            doorFile.getParentFile().mkdirs();
            YamlConfiguration yaml = new YamlConfiguration();
            List<Map<String, Object>> doorList = new ArrayList<>();
            if (doorFile.exists()) {
                try {
                    YamlConfiguration existing = YamlConfiguration.loadConfiguration(doorFile);
                    List<?> existingList = existing.getList("doors");
                    if (existingList == null) existingList = existing.getList("");
                    if (existingList != null) {
                        for (Object obj : existingList) {
                            if (obj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> m = (Map<String, Object>) obj;
                                doorList.add(m);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("x", data.x);
            entry.put("y", data.y);
            entry.put("z", data.z);
            entry.put("facing", facing);
            entry.put("type", type);
            entry.put("weight", weight);
            doorList.add(entry);
            yaml.set("doors", doorList);
            try {
                yaml.save(doorFile);
                MessageUtil.sendMessage(p, "门数据已追加到 " + doorFile.getPath() + " (类型: " + type + ", 权重: " + weight + ")");
            } catch (Exception ex) {
                MessageUtil.sendError(p, "保存失败: " + ex.getMessage());
            }
            openDungeonMenuSync(p, data.dungeonName);
            return;
        }

        // ========== 3. 添加箱子 ==========
        if (waitingChest.containsKey(uuid)) {
            e.setCancelled(true);
            ChestInputSession session = waitingChest.get(uuid);
            if (msg.equalsIgnoreCase("cancel")) {
                waitingChest.remove(uuid);
                MessageUtil.sendMessage(p, "已取消箱子数据添加");
                return;
            }
            if (msg.equalsIgnoreCase("done")) {
                if (session.chests.isEmpty()) {
                    MessageUtil.sendMessage(p, "未添加任何战利品，箱子未保存");
                } else {
                    File roomsDir = new File(plugin.getDataFolder(), session.dungeonName + "/rooms");
                    String path = session.baseName.replace('/', File.separatorChar);
                    File chestFile = new File(roomsDir, path + "_chest.yml");
                    chestFile.getParentFile().mkdirs();
                    YamlConfiguration yaml = new YamlConfiguration();
                    List<Map<String, Object>> chestList = new ArrayList<>();
                    if (chestFile.exists()) {
                        try {
                            YamlConfiguration existing = YamlConfiguration.loadConfiguration(chestFile);
                            List<?> existingList = existing.getList("chests");
                            if (existingList == null) existingList = existing.getList("");
                            if (existingList != null) {
                                for (Object obj : existingList) {
                                    if (obj instanceof Map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> m = (Map<String, Object>) obj;
                                        chestList.add(m);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    chestList.addAll(session.chests);
                    yaml.set("chests", chestList);
                    try {
                        yaml.save(chestFile);
                        MessageUtil.sendMessage(p, "箱子数据已保存到 " + chestFile.getPath() + " (共 " + session.chests.size() + " 个战利品条目)");
                    } catch (Exception ex) {
                        MessageUtil.sendError(p, "保存失败: " + ex.getMessage());
                    }
                }
                waitingChest.remove(uuid);
                openDungeonMenuSync(p, session.dungeonName);
                return;
            }

            // 尚未设置路径
            if (session.baseName == null) {
                String[] parts = msg.split(" ");
                if (parts.length < 1) {
                    MessageUtil.sendError(p, "请输入相对于 rooms/ 的路径");
                    return;
                }
                session.baseName = parts[0];
                MessageUtil.sendMessage(p, "已设置路径: " + session.baseName);
                MessageUtil.sendMessage(p, "请手持物品输入概率（不带%），输入 'done' 完成，输入 'cancel' 取消");
                return;
            }

            // 输入概率
            int prob;
            try {
                prob = Integer.parseInt(msg);
            } catch (NumberFormatException ex) {
                MessageUtil.sendError(p, "请输入整数概率（0-100），或输入 'done' 完成，'cancel' 取消");
                return;
            }
            if (prob < 0 || prob > 100) {
                MessageUtil.sendError(p, "概率必须在 0-100 之间");
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                MessageUtil.sendError(p, "请手持有效物品");
                return;
            }
            if (prob == 0) {
                MessageUtil.sendMessage(p, "概率为0，跳过此战利品");
                return;
            }
            Map<String, Object> chestEntry = new LinkedHashMap<>();
            chestEntry.put("pos", Arrays.asList(session.x, session.y, session.z));
            String lootStr = prob + " " + hand.getType().name() + " " + hand.getAmount();
            chestEntry.put("loot", lootStr);
            session.chests.add(chestEntry);
            MessageUtil.sendMessage(p, "已添加: " + lootStr + " (当前共 " + session.chests.size() + " 个条目)");
            MessageUtil.sendMessage(p, "继续输入下一个概率，或输入 'done' 完成");
            return;
        }

        // ========== 4. 添加出生点 ==========
        if (spawnTempData.containsKey(uuid)) {
            e.setCancelled(true);
            SpawnTempData data = spawnTempData.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) {
                MessageUtil.sendMessage(p, "已取消");
                return;
            }
            String[] parts = msg.split(" ");
            if (parts.length < 1) {
                MessageUtil.sendError(p, "格式: 相对于 rooms/ 的路径");
                return;
            }
            String baseName = parts[0];
            File roomsDir = new File(plugin.getDataFolder(), data.dungeonName + "/rooms");
            String path = baseName.replace('/', File.separatorChar);
            File spawnFile = new File(roomsDir, path + "_spawn.yml");
            spawnFile.getParentFile().mkdirs();
            YamlConfiguration yaml = new YamlConfiguration();
            Map<String, Object> spawnMap = new LinkedHashMap<>();
            spawnMap.put("x", data.x);
            spawnMap.put("y", data.y);
            spawnMap.put("z", data.z);
            yaml.set("spawn", spawnMap);
            try {
                yaml.save(spawnFile);
                MessageUtil.sendMessage(p, "出生点已保存到 " + spawnFile.getPath());
            } catch (Exception ex) {
                MessageUtil.sendError(p, "保存失败: " + ex.getMessage());
            }
            openDungeonMenuSync(p, data.dungeonName);
            return;
        }

        // ========== 5. 删除房间 ==========
        if (deleteRoomTemp.containsKey(uuid)) {
            e.setCancelled(true);
            DeleteData data = deleteRoomTemp.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) {
                MessageUtil.sendMessage(p, "已取消");
                return;
            }
            String baseName = msg;
            File roomsDir = new File(plugin.getDataFolder(), data.dungeonName + "/rooms");
            String path = baseName.replace('/', File.separatorChar);
            File roomFile = new File(roomsDir, path + ".schem");
            if (!roomFile.exists()) {
                MessageUtil.sendError(p, "房间不存在");
                return;
            }
            boolean deleted = roomFile.delete();
            new File(roomsDir, path + "_door.yml").delete();
            new File(roomsDir, path + "_chest.yml").delete();
            new File(roomsDir, path + "_spawn.yml").delete();
            new File(roomsDir, path + "_checkpoint.yml").delete();
            if (deleted) {
                MessageUtil.sendMessage(p, "已删除房间 " + baseName);
            } else {
                MessageUtil.sendError(p, "删除失败");
            }
            openDungeonMenuSync(p, data.dungeonName);
            return;
        }

        // ========== 6. 删除门 ==========
        if (deleteDoorTemp.containsKey(uuid)) {
            e.setCancelled(true);
            DeleteData data = deleteDoorTemp.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) {
                MessageUtil.sendMessage(p, "已取消");
                return;
            }
            File roomsDir = new File(plugin.getDataFolder(), data.dungeonName + "/rooms");
            String path = msg.replace('/', File.separatorChar);
            File doorFile = new File(roomsDir, path + "_door.yml");
            if (!doorFile.exists()) {
                MessageUtil.sendError(p, "门文件不存在");
                return;
            }
            if (doorFile.delete()) {
                MessageUtil.sendMessage(p, "已删除门文件");
            } else {
                MessageUtil.sendError(p, "删除失败");
            }
            openDungeonMenuSync(p, data.dungeonName);
            return;
        }

        // ========== 7. 删除箱子 ==========
        if (deleteChestTemp.containsKey(uuid)) {
            e.setCancelled(true);
            DeleteData data = deleteChestTemp.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) {
                MessageUtil.sendMessage(p, "已取消");
                return;
            }
            File roomsDir = new File(plugin.getDataFolder(), data.dungeonName + "/rooms");
            String path = msg.replace('/', File.separatorChar);
            File chestFile = new File(roomsDir, path + "_chest.yml");
            if (!chestFile.exists()) {
                MessageUtil.sendError(p, "箱子文件不存在");
                return;
            }
            if (chestFile.delete()) {
                MessageUtil.sendMessage(p, "已删除箱子文件");
            } else {
                MessageUtil.sendError(p, "删除失败");
            }
            openDungeonMenuSync(p, data.dungeonName);
            return;
        }

        // ========== 8. 删除出生点 ==========
        if (deleteSpawnTemp.containsKey(uuid)) {
            e.setCancelled(true);
            DeleteData data = deleteSpawnTemp.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) {
                MessageUtil.sendMessage(p, "已取消");
                return;
            }
            File roomsDir = new File(plugin.getDataFolder(), data.dungeonName + "/rooms");
            String path = msg.replace('/', File.separatorChar);
            File spawnFile = new File(roomsDir, path + "_spawn.yml");
            if (!spawnFile.exists()) {
                MessageUtil.sendError(p, "出生点文件不存在");
                return;
            }
            if (spawnFile.delete()) {
                MessageUtil.sendMessage(p, "已删除出生点文件");
            } else {
                MessageUtil.sendError(p, "删除失败");
            }
            openDungeonMenuSync(p, data.dungeonName);
            return;
        }

        // ========== 9. 添加存档点 ==========
        if (waitingCheckpoint.containsKey(uuid)) {
            e.setCancelled(true);
            CheckpointTempData data = waitingCheckpoint.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) {
                MessageUtil.sendMessage(p, "已取消");
                return;
            }
            String baseName = msg;
            File roomsDir = new File(plugin.getDataFolder(), data.dungeonName + "/rooms");
            String path = baseName.replace('/', File.separatorChar);
            File checkFile = new File(roomsDir, path + "_checkpoint.yml");
            checkFile.getParentFile().mkdirs();
            YamlConfiguration yaml = new YamlConfiguration();
            Map<String, Object> spawnMap = new LinkedHashMap<>();
            spawnMap.put("x", data.x);
            spawnMap.put("y", data.y);
            spawnMap.put("z", data.z);
            yaml.set("spawn", spawnMap);
            try {
                yaml.save(checkFile);
                MessageUtil.sendMessage(p, "存档点已保存到 " + checkFile.getPath());
            } catch (Exception ex) {
                MessageUtil.sendError(p, "保存失败: " + ex.getMessage());
            }
            openDungeonMenuSync(p, data.dungeonName);
            return;
        }

        // ========== 10. 删除存档点 ==========
        if (waitingDeleteCheckpoint.containsKey(uuid)) {
            e.setCancelled(true);
            DeleteCheckpointData data = waitingDeleteCheckpoint.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) {
                MessageUtil.sendMessage(p, "已取消");
                return;
            }
            String baseName = msg;
            File roomsDir = new File(plugin.getDataFolder(), data.dungeonName + "/rooms");
            String path = baseName.replace('/', File.separatorChar);
            File checkFile = new File(roomsDir, path + "_checkpoint.yml");
            if (!checkFile.exists()) {
                MessageUtil.sendError(p, "存档点文件不存在");
                return;
            }
            if (checkFile.delete()) {
                MessageUtil.sendMessage(p, "已删除存档点文件");
            } else {
                MessageUtil.sendError(p, "删除失败");
            }
            openDungeonMenuSync(p, data.dungeonName);
            return;
        }
    }

    private void openDungeonMenuSync(Player player, String dungeonName) {
        Bukkit.getScheduler().runTask(plugin, () -> openDungeonMenu(player, dungeonName));
    }
}