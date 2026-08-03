package com.him.dungeons.gui;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.*;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
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

    private static class SaveRoomData {
        String dungeonName;
        SaveRoomData(String dungeon) {
            this.dungeonName = dungeon;
        }
    }

    private static class DoorTempData {
        String dungeonName;
        BlockVector3 relativePos;
        DoorTempData(String dungeon, BlockVector3 pos) {
            this.dungeonName = dungeon;
            this.relativePos = pos;
        }
    }

    private static class ChestInputSession {
        String dungeonName;
        String roomName;
        String baseName;
        BlockVector3 relativePos;
        List<Map<String, Object>> chests;
        boolean collecting;
    }

    private static class SpawnTempData {
        String dungeonName;
        BlockVector3 relativePos;
        SpawnTempData(String dungeon, BlockVector3 pos) {
            this.dungeonName = dungeon;
            this.relativePos = pos;
        }
    }

    private static class DeleteData {
        String dungeonName;
        String roomName;
        String baseName;
        DeleteData(String dungeon, String room, String base) {
            this.dungeonName = dungeon;
            this.roomName = room;
            this.baseName = base;
        }
    }

    public DungeonManagerGUI(HimDungeons plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ---------- 主菜单 ----------
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
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "点击编辑");
                    meta.setLore(lore);
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

    // ---------- 地牢子菜单 ----------
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

    // ---------- 点击事件 ----------
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
            }
        }
    }

    // ---------- 功能实现 ----------
    private void handleSaveRoom(Player player, String dungeonName) {
        Region region;
        try {
            region = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player)).getSelection(BukkitAdapter.adapt(player.getWorld()));
        } catch (IncompleteRegionException e) {
            MessageUtil.sendError(player, "请先用创世神选择区域");
            return;
        }
        if (region == null) {
            MessageUtil.sendError(player, "未选择区域");
            return;
        }
        waitingRoomData.put(player.getUniqueId(), new SaveRoomData(dungeonName));
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入房间类型和基础文件名（用空格分隔），例如 combat myCombat");
        MessageUtil.sendMessage(player, "将保存为 <房间类型>/<基础文件名>.schem");
    }

    private void handleAddDoor(Player player, String dungeonName) {
        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            MessageUtil.sendError(player, "请对准一个方块（距离 ≤ 5 格）");
            return;
        }
        Region region;
        try {
            region = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player)).getSelection(BukkitAdapter.adapt(player.getWorld()));
        } catch (IncompleteRegionException e) {
            MessageUtil.sendError(player, "请先用创世神选择区域（用于确定相对坐标原点）");
            return;
        }
        if (region == null) {
            MessageUtil.sendError(player, "未选择区域");
            return;
        }
        BlockVector3 origin = region.getMinimumPoint();
        BlockVector3 worldPos = BlockVector3.at(target.getX(), target.getY(), target.getZ());
        BlockVector3 relative = worldPos.subtract(origin);
        doorTempData.put(player.getUniqueId(), new DoorTempData(dungeonName, relative));
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入房间类型、基础文件名和朝向（用空格分隔），例如 combat myCombat x+");
        MessageUtil.sendMessage(player, "将保存为 <基础文件名>_door.yml");
    }

    private void handleAddChest(Player player, String dungeonName) {
        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            MessageUtil.sendError(player, "请对准一个方块（距离 ≤ 5 格）");
            return;
        }
        Region region;
        try {
            region = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player)).getSelection(BukkitAdapter.adapt(player.getWorld()));
        } catch (IncompleteRegionException e) {
            MessageUtil.sendError(player, "请先用创世神选择区域（用于确定相对坐标原点）");
            return;
        }
        if (region == null) {
            MessageUtil.sendError(player, "未选择区域");
            return;
        }
        BlockVector3 origin = region.getMinimumPoint();
        BlockVector3 worldPos = BlockVector3.at(target.getX(), target.getY(), target.getZ());
        BlockVector3 relative = worldPos.subtract(origin);
        ChestInputSession session = new ChestInputSession();
        session.dungeonName = dungeonName;
        session.relativePos = relative;
        session.chests = new ArrayList<>();
        session.collecting = false;
        waitingChest.put(player.getUniqueId(), session);
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入房间类型和基础文件名（用空格分隔），例如 combat myCombat");
        MessageUtil.sendMessage(player, "然后手持物品输入概率（不带%），可重复添加多个战利品");
        MessageUtil.sendMessage(player, "输入 'done' 完成，输入 'cancel' 取消");
    }

    private void handleAddSpawn(Player player, String dungeonName) {
        Region region;
        try {
            region = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player)).getSelection(BukkitAdapter.adapt(player.getWorld()));
        } catch (IncompleteRegionException e) {
            MessageUtil.sendError(player, "请先用创世神选择区域（用于确定相对坐标原点）");
            return;
        }
        if (region == null) {
            MessageUtil.sendError(player, "未选择区域");
            return;
        }
        BlockVector3 origin = region.getMinimumPoint();
        Location loc = player.getLocation();
        BlockVector3 worldPos = BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        BlockVector3 relative = worldPos.subtract(origin);
        spawnTempData.put(player.getUniqueId(), new SpawnTempData(dungeonName, relative));
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入房间类型和基础文件名（用空格分隔），例如 start myStart");
        MessageUtil.sendMessage(player, "将保存为 <基础文件名>_spawn.yml");
    }

    private void handleDeleteRoom(Player player, String dungeonName) {
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入要删除的房间类型名，输入 cancel 取消");
        deleteRoomTemp.put(player.getUniqueId(), new DeleteData(dungeonName, null, null));
    }

    private void handleDeleteDoor(Player player, String dungeonName) {
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入房间类型和基础文件名（用空格分隔），例如 combat myCombat");
        deleteDoorTemp.put(player.getUniqueId(), new DeleteData(dungeonName, null, null));
    }

    private void handleDeleteChest(Player player, String dungeonName) {
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入房间类型和基础文件名（用空格分隔），例如 combat myCombat");
        deleteChestTemp.put(player.getUniqueId(), new DeleteData(dungeonName, null, null));
    }

    private void handleDeleteSpawn(Player player, String dungeonName) {
        player.closeInventory();
        MessageUtil.sendMessage(player, "请输入房间类型和基础文件名（用空格分隔），例如 start myStart");
        deleteSpawnTemp.put(player.getUniqueId(), new DeleteData(dungeonName, null, null));
    }

    // ---------- 聊天监听 ----------
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String msg = e.getMessage().trim();

        // 保存房间
        if (waitingRoomData.containsKey(uuid)) {
            e.setCancelled(true);
            SaveRoomData data = waitingRoomData.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) { MessageUtil.sendMessage(p, "已取消"); return; }
            String[] parts = msg.split(" ");
            if (parts.length < 2) {
                MessageUtil.sendError(p, "格式: 房间类型 基础文件名");
                return;
            }
            String roomType = parts[0];
            String baseName = parts[1];
            Region region;
            try {
                region = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(p)).getSelection(BukkitAdapter.adapt(p.getWorld()));
            } catch (IncompleteRegionException ex) {
                MessageUtil.sendError(p, "请先用创世神选择区域");
                return;
            }
            if (region == null) {
                MessageUtil.sendError(p, "未选择区域");
                return;
            }
            File dungeonFolder = new File(plugin.getDataFolder(), data.dungeonName);
            if (!dungeonFolder.exists()) dungeonFolder.mkdirs();
            File roomFolder = new File(dungeonFolder, roomType);
            if (!roomFolder.exists()) roomFolder.mkdirs();
            // 强制保存为 .schem
            File schematicFile = new File(roomFolder, baseName + ".schem");
            boolean success = WorldEditUtil.saveSchematic(schematicFile, region, BukkitAdapter.adapt(p.getWorld()));
            if (success) {
                MessageUtil.sendMessage(p, "原理图已保存到 " + schematicFile.getPath());
            } else {
                MessageUtil.sendError(p, "保存失败");
            }
            openDungeonMenuSync(p, data.dungeonName);
            return;
        }

        // 添加门
        if (doorTempData.containsKey(uuid)) {
            e.setCancelled(true);
            DoorTempData data = doorTempData.remove(uuid);
            String[] parts = msg.split(" ");
            if (parts.length < 3) {
                MessageUtil.sendError(p, "格式: 房间类型 基础文件名 朝向");
                return;
            }
            String roomName = parts[0];
            String baseName = parts[1];
            String facing = parts[2];
            if (!TransformUtil.isValidFacing(facing)) {
                MessageUtil.sendError(p, "无效朝向，请输入 x+, x-, y+, y-, z+, z- 之一");
                return;
            }
            File roomFolder = new File(plugin.getDataFolder(), data.dungeonName + "/" + roomName);
            if (!roomFolder.exists()) {
                MessageUtil.sendError(p, "房间类型 " + roomName + " 不存在，请先保存房间");
                return;
            }
            File doorFile = new File(roomFolder, baseName + "_door.yml");
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
                                doorList.add((Map<String, Object>) obj);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("x", data.relativePos.getX());
            entry.put("y", data.relativePos.getY());
            entry.put("z", data.relativePos.getZ());
            entry.put("facing", facing);
            doorList.add(entry);
            yaml.set("doors", doorList);
            try {
                yaml.save(doorFile);
                MessageUtil.sendMessage(p, "门数据已追加到 " + doorFile.getPath());
            } catch (Exception ex) {
                MessageUtil.sendError(p, "保存失败: " + ex.getMessage());
            }
            openDungeonMenuSync(p, data.dungeonName);
            return;
        }

        // 添加箱子（支持多个战利品条目）
        if (waitingChest.containsKey(uuid)) {
            e.setCancelled(true);
            ChestInputSession session = waitingChest.get(uuid);
            if (msg.equalsIgnoreCase("cancel")) {
                waitingChest.remove(uuid);
                MessageUtil.sendMessage(p, "已取消箱子数据添加");
                return;
            }
            if (msg.equalsIgnoreCase("done")) {
                // 结束收集，保存箱子
                if (session.chests.isEmpty()) {
                    MessageUtil.sendMessage(p, "未添加任何战利品，箱子未保存");
                } else {
                    File roomFolder = new File(plugin.getDataFolder(), session.dungeonName + "/" + session.roomName);
                    if (!roomFolder.exists()) {
                        MessageUtil.sendError(p, "房间类型 " + session.roomName + " 不存在，请先保存房间");
                        waitingChest.remove(uuid);
                        return;
                    }
                    File chestFile = new File(roomFolder, session.baseName + "_chest.yml");
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
                                        chestList.add((Map<String, Object>) obj);
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

            // 如果还没有设置房间名和基础文件名，先解析它们
            if (session.roomName == null || session.baseName == null) {
                String[] parts = msg.split(" ");
                if (parts.length < 2) {
                    MessageUtil.sendError(p, "请先输入房间类型和基础文件名（用空格分隔），例如 combat myCombat");
                    return;
                }
                session.roomName = parts[0];
                session.baseName = parts[1];
                MessageUtil.sendMessage(p, "已设置房间: " + session.roomName + ", 基础文件名: " + session.baseName);
                MessageUtil.sendMessage(p, "请手持物品输入概率（不带%），输入 'done' 完成，输入 'cancel' 取消");
                return;
            }

            // 已有房间名和文件名，输入概率
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
            // 添加条目
            Map<String, Object> chestEntry = new LinkedHashMap<>();
            chestEntry.put("pos", Arrays.asList(
                    session.relativePos.getX(),
                    session.relativePos.getY(),
                    session.relativePos.getZ()
            ));
            String lootStr = prob + " " + hand.getType().name() + " " + hand.getAmount();
            chestEntry.put("loot", lootStr);
            session.chests.add(chestEntry);
            MessageUtil.sendMessage(p, "已添加: " + lootStr + " (当前共 " + session.chests.size() + " 个条目)");
            MessageUtil.sendMessage(p, "继续输入下一个概率，或输入 'done' 完成");
            return;
        }

        // 添加出生点
        if (spawnTempData.containsKey(uuid)) {
            e.setCancelled(true);
            SpawnTempData data = spawnTempData.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) { MessageUtil.sendMessage(p, "已取消"); return; }
            String[] parts = msg.split(" ");
            if (parts.length < 2) {
                MessageUtil.sendError(p, "格式: 房间类型 基础文件名");
                return;
            }
            String roomName = parts[0];
            String baseName = parts[1];
            File roomFolder = new File(plugin.getDataFolder(), data.dungeonName + "/" + roomName);
            if (!roomFolder.exists()) {
                MessageUtil.sendError(p, "房间类型 " + roomName + " 不存在，请先保存房间");
                return;
            }
            File spawnFile = new File(roomFolder, baseName + "_spawn.yml");
            YamlConfiguration yaml = new YamlConfiguration();
            Map<String, Object> spawnMap = new LinkedHashMap<>();
            spawnMap.put("x", data.relativePos.getX());
            spawnMap.put("y", data.relativePos.getY());
            spawnMap.put("z", data.relativePos.getZ());
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

        // 删除房间（整个目录）
        if (deleteRoomTemp.containsKey(uuid)) {
            e.setCancelled(true);
            DeleteData data = deleteRoomTemp.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) { MessageUtil.sendMessage(p, "已取消"); return; }
            String roomName = msg;
            File roomFolder = new File(plugin.getDataFolder(), data.dungeonName + "/" + roomName);
            if (!roomFolder.exists()) {
                MessageUtil.sendError(p, "房间不存在");
                return;
            }
            if (deleteDirectory(roomFolder)) {
                MessageUtil.sendMessage(p, "已删除房间 " + roomName);
            } else {
                MessageUtil.sendError(p, "删除失败");
            }
            openDungeonMenuSync(p, data.dungeonName);
            return;
        }

        // 删除门
        if (deleteDoorTemp.containsKey(uuid)) {
            e.setCancelled(true);
            DeleteData data = deleteDoorTemp.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) { MessageUtil.sendMessage(p, "已取消"); return; }
            String[] parts = msg.split(" ");
            if (parts.length < 2) {
                MessageUtil.sendError(p, "格式: 房间类型 基础文件名");
                return;
            }
            String roomName = parts[0];
            String baseName = parts[1];
            File doorFile = new File(plugin.getDataFolder(), data.dungeonName + "/" + roomName + "/" + baseName + "_door.yml");
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

        // 删除箱子
        if (deleteChestTemp.containsKey(uuid)) {
            e.setCancelled(true);
            DeleteData data = deleteChestTemp.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) { MessageUtil.sendMessage(p, "已取消"); return; }
            String[] parts = msg.split(" ");
            if (parts.length < 2) {
                MessageUtil.sendError(p, "格式: 房间类型 基础文件名");
                return;
            }
            String roomName = parts[0];
            String baseName = parts[1];
            File chestFile = new File(plugin.getDataFolder(), data.dungeonName + "/" + roomName + "/" + baseName + "_chest.yml");
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

        // 删除出生点
        if (deleteSpawnTemp.containsKey(uuid)) {
            e.setCancelled(true);
            DeleteData data = deleteSpawnTemp.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) { MessageUtil.sendMessage(p, "已取消"); return; }
            String[] parts = msg.split(" ");
            if (parts.length < 2) {
                MessageUtil.sendError(p, "格式: 房间类型 基础文件名");
                return;
            }
            String roomName = parts[0];
            String baseName = parts[1];
            File spawnFile = new File(plugin.getDataFolder(), data.dungeonName + "/" + roomName + "/" + baseName + "_spawn.yml");
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
    }

    private void openDungeonMenuSync(Player player, String dungeonName) {
        Bukkit.getScheduler().runTask(plugin, () -> openDungeonMenu(player, dungeonName));
    }

    private boolean deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return true;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) deleteDirectory(child);
            }
        }
        return dir.delete();
    }
}