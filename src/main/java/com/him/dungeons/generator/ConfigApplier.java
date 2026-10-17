package com.him.dungeons.generator;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.*;
import com.him.dungeons.schematic.*;
import com.him.dungeons.util.WinStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import java.lang.reflect.Method;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 配置落地工具类 - 集中处理所有未生效的配置项
 */
public class ConfigApplier {

    private final HimDungeons plugin;
    private final String dungeonName;
    private final Player player;
    private final FileConfiguration config;
    private final Random random = new Random();

    // ---- 房间/门复用状态 ----
    private final Map<String, Integer> usedRoomTemplates = new HashMap<>();      // baseName -> 使用次数
    private final Set<String> usedDoorPairs = new HashSet<>();                  // "roomA:idx|roomB:idx"

    // ---- 方块阻止检测 ----
    private final Map<RoomInstance, List<Pos3>> forbiddenBlockPositions = new HashMap<>(); // 已放置房间中需检测的方块相对坐标列表

    // ---- Boss选择 ----
    private Map<String, String> selectedBoss = null; // type, nbt

    public ConfigApplier(HimDungeons plugin, String dungeonName, Player player, FileConfiguration config) {
        this.plugin = plugin;
        this.dungeonName = dungeonName;
        this.player = player;
        this.config = config;
    }

    /**
     * 在生成开始前重置状态
     */
    public void prepare() {
        usedRoomTemplates.clear();
        usedDoorPairs.clear();
        forbiddenBlockPositions.clear();
        selectedBoss = null;
        DebugUtil.debug(player, "ConfigApplier 已重置");
    }

    // ========== 1. 自定义权重（generatorChoose） ==========
    /**
     * 如果配置了 generatorChoose，则返回覆盖权重；否则返回 null（使用 structureChoose）
     */
    public Map<String, Integer> getCustomWeights() {
        List<List<Object>> custom = ConfigUtil.getInstanceGeneratorChoose(config);
        if (custom == null || custom.isEmpty()) return null;
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (List<Object> entry : custom) {
            if (entry.size() >= 2) {
                String type = entry.get(0).toString();
                int weight = ((Number) entry.get(1)).intValue();
                if (weight > 0) weights.put(type, weight);
            }
        }
        return weights.isEmpty() ? null : weights;
    }

    // ========== 2. 房间复用 ==========
    /**
     * 过滤候选基础名列表，根据 allowRoomReuse 决定是否允许重复使用同一模板
     */
    public List<String> filterRoomTemplates(String type, List<String> baseNames) {
        if (baseNames == null || baseNames.isEmpty()) return baseNames;
        boolean reuse = ConfigUtil.isInstanceAllowRoomReuse(config);
        if (!reuse) {
            baseNames = baseNames.stream()
                    .filter(name -> usedRoomTemplates.getOrDefault(name, 0) == 0)
                    .collect(Collectors.toList());
        }
        return baseNames;
    }

    /**
     * 记录一个房间模板被使用
     */
    public void recordRoomUsed(String baseName) {
        usedRoomTemplates.put(baseName, usedRoomTemplates.getOrDefault(baseName, 0) + 1);
        DebugUtil.debug(player, "记录房间模板使用: %s (已用 %d 次)", baseName, usedRoomTemplates.get(baseName));
    }

    // ========== 3. 门复用 ==========
    /**
     * 检查两个门是否可以配对（考虑 allowDoorReuse）
     * @param roomAId 房间A的唯一标识（如 "RoomType@anchor"）
     * @param idxA 门索引
     * @param roomBId 房间B的唯一标识
     * @param idxB 门索引
     * @return true 允许配对
     */
    public boolean canPairDoors(String roomAId, int idxA, String roomBId, int idxB) {
        boolean reuse = ConfigUtil.isInstanceAllowDoorReuse(config);
        String pairKey = roomAId + ":" + idxA + "|" + roomBId + ":" + idxB;
        String reverseKey = roomBId + ":" + idxB + "|" + roomAId + ":" + idxA;
        if (!reuse) {
            if (usedDoorPairs.contains(pairKey) || usedDoorPairs.contains(reverseKey)) {
                return false;
            }
        }
        // 记录配对（无论是否允许复用，均记录，以便后续检查）
        usedDoorPairs.add(pairKey);
        usedDoorPairs.add(reverseKey);
        return true;
    }

    // ========== 4. Boss 类型/NBT ==========
    /**
     * 选择 Boss 类型和 NBT（基于 bossType / bossNbt 列表）
     */
    public Map<String, String> selectBoss() {
        if (selectedBoss != null) return selectedBoss;
        List<String> types = ConfigUtil.getInstanceBossTypeList(config);
        List<String> nbts = ConfigUtil.getInstanceBossNbtList(config);
        int size = Math.min(types.size(), nbts.size());
        if (size == 0) {
            selectedBoss = Map.of("type", "zombie", "nbt", "{Health:100.0f}");
        } else {
            int idx = random.nextInt(size);
            selectedBoss = Map.of("type", types.get(idx), "nbt", nbts.get(idx));
        }
        DebugUtil.debug(player, "Boss 选定: %s", selectedBoss);
        return selectedBoss;
    }

    // ========== 5. 方块阻止检测（blockRoomGenerateBlocks） ==========
    /**
     * 在房间放置成功后，解析该原理图中配置的阻止方块，存储其相对坐标。
     * 应在新房间粘贴完成后调用（主线程）。
     */
public void storeForbiddenBlocks(RoomInstance room) {
    List<String> blockNames = ConfigUtil.getInstanceBlockRoomGenerateBlocks(config);
    if (blockNames.isEmpty()) return;
    File schematicFile = ResourceUtil.getRoomSchematicFlat(dungeonName, room.baseName);
    if (schematicFile == null) return;

    // ★ 用 SpongeSchematicReader 替代 ClipboardReader
    NbtSchematic schem = SpongeSchematicReader.read(
            schematicFile, ConfigUtil.getSchematicReadFormat());
    if (schem == null) return;

    List<Pos3> coords = new ArrayList<>();
    for (int y = 0; y < schem.height; y++) {
        for (int z = 0; z < schem.length; z++) {
            for (int x = 0; x < schem.width; x++) {
                org.bukkit.block.data.BlockData data = schem.getBlockData(x, y, z);
                if (data == null) continue;
                if (blockNames.contains(data.getMaterial().name())) {
                    // NBT 坐标系本身就是相对原点的，无需 subtract(origin)
                    coords.add(new Pos3(x, y, z));
                }
            }
        }
    }
    if (!coords.isEmpty()) {
        forbiddenBlockPositions.put(room, coords);
        DebugUtil.debug(player, "房间 %s 含 %d 个阻止方块", room.roomType, coords.size());
    }
}

    /**
     * 检测候选房间位置是否会与已有房间的阻止方块重叠（用于 AABB 关闭时）
     * @param candidateAnchor 候选锚点
     * @param rotation 候选旋转
     * @param candidateSize 候选尺寸（未旋转）
     * @return true 如果无冲突
     */
    public boolean checkForbiddenBlocks(Location candidateAnchor, int rotation, Pos3 candidateSize) {
        if (forbiddenBlockPositions.isEmpty()) return true;
        // 构建候选 AABB（用于快速筛选）
AABBUtil.AABB candidateAABB = AABBUtil.buildRotated(candidateAnchor, candidateSize, rotation);
        // 遍历已有房间的阻止方块
        for (Map.Entry<RoomInstance, List<Pos3>> entry : forbiddenBlockPositions.entrySet()) {
            RoomInstance placed = entry.getKey();
            List<Pos3> coords = entry.getValue();
            for (Pos3 rel : coords) {
                // 旋转后的相对坐标
                Pos3 rotatedRel = TransformUtil.rotatePos(rel, placed.rotationTimes);
                // 世界坐标
                Location worldLoc = placed.anchor.clone().add(rotatedRel.getX(), rotatedRel.getY(), rotatedRel.getZ());
                // 检查是否在候选AABB内
                double x = worldLoc.getX(), y = worldLoc.getY(), z = worldLoc.getZ();
                if (candidateAABB.minX <= x && x <= candidateAABB.maxX &&
                    candidateAABB.minY <= y && y <= candidateAABB.maxY &&
                    candidateAABB.minZ <= z && z <= candidateAABB.maxZ) {
                    DebugUtil.debug(player, "候选位置与阻止方块冲突: %s", worldLoc);
                    return false;
                }
            }
        }
        return true;
    }

    // ========== 6. 深拷贝背包 ==========
    /**
     * 如果配置了 deepCloneInventory，则深拷贝玩家背包，返回副本；否则返回原背包引用
     */
    public ItemStack[] deepCloneInventory(Player player) {
        if (!ConfigUtil.isInstanceDeepCloneInventory(config)) {
            return player.getInventory().getContents(); // 原引用（浅拷贝）
        }
        PlayerInventory inv = player.getInventory();
        ItemStack[] original = inv.getContents();
        ItemStack[] clone = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) {
                clone[i] = new ItemStack(original[i]); // 构造新ItemStack，元数据也会复制
                // 如果有元数据，需要深拷贝Meta（ItemStack构造器默认会复制）
            }
        }
        return clone;
    }


    // ========== 8. 大厅传送点 ==========
    public Location getLobbyLocation(World world) {
        String lobbyStr = ConfigUtil.getInstanceLobby(config);
        if (lobbyStr == null || lobbyStr.isEmpty()) return null;
        // 格式: "world,x,y,z,yaw,pitch" 或仅 "x,y,z"
        try {
            String[] parts = lobbyStr.split(",");
            if (parts.length >= 3) {
                double x = Double.parseDouble(parts[0]);
                double y = Double.parseDouble(parts[1]);
                double z = Double.parseDouble(parts[2]);
                float yaw = 0, pitch = 0;
                if (parts.length >= 5) {
                    yaw = Float.parseFloat(parts[3]);
                    pitch = Float.parseFloat(parts[4]);
                }
                World targetWorld = Bukkit.getWorld(parts.length >= 6 ? parts[5] : world.getName());
                if (targetWorld == null) targetWorld = world;
                return new Location(targetWorld, x, y, z, yaw, pitch);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // ========== 9. 命令/建筑/破坏白名单检查（由监听器调用） ==========
public boolean isCommandAllowed(String fullCommand) {
    String listType = ConfigUtil.getInstanceCommandsAllowListType(config);
    List<String> list = ConfigUtil.getInstanceCommandsAllow(config);
    // 去除前导 '/'
    String cmd = fullCommand.startsWith("/") ? fullCommand.substring(1) : fullCommand;
    // 若列表为空，白名单模式默认全部禁止，黑名单模式默认全部允许
    if (list.isEmpty()) {
        return listType.equalsIgnoreCase("black");
    }
    boolean contains = list.contains(cmd);
    return listType.equalsIgnoreCase("white") ? contains : !contains;
}

    public boolean isBuildAllowed(Material material) {
        String listType = ConfigUtil.getInstanceBuildSettingsListType(config);
        List<String> list = ConfigUtil.getInstanceBuildSettings(config);
        return listType.equalsIgnoreCase("white") ? list.contains(material.name()) : !list.contains(material.name());
    }

    public boolean isBreakAllowed(Material material) {
        String listType = ConfigUtil.getInstanceBreakSettingsListType(config);
        List<String> list = ConfigUtil.getInstanceBreakSettings(config);
        return listType.equalsIgnoreCase("white") ? list.contains(material.name()) : !list.contains(material.name());
    }
// 在 ConfigApplier.java 中添加以下方法（或替换现有方法）

public void grantRewards(Player player) {
    // Vault（保持不变）
    if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
        try {
            net.milkbowl.vault.economy.Economy economy = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class).getProvider();
            if (economy != null) {
                Map<String, Double> vaultRewards = ConfigUtil.getInstanceVaultRewards(config);
                for (Map.Entry<String, Double> entry : vaultRewards.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase("money")) {
                        economy.depositPlayer(player, entry.getValue());
                        MessageUtil.sendMessage(player, "§a获得 " + entry.getValue() + " 金币");
                    }
                }
            }
        } catch (Exception e) {
            DebugUtil.debug(player, "Vault奖励发放失败: " + e.getMessage());
        }
    }

    // PlayerPoints（反射）
    if (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
        try {
            Object ppPlugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (ppPlugin != null) {
                Method getAPIMethod = ppPlugin.getClass().getMethod("getAPI");
                Object api = getAPIMethod.invoke(ppPlugin);
                if (api != null) {
                    Method giveMethod = api.getClass().getMethod("give", UUID.class, int.class);
                    Map<String, Integer> ppRewards = ConfigUtil.getInstancePlayerPointsRewards(config);
                    for (Map.Entry<String, Integer> entry : ppRewards.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase("points") || entry.getKey().equalsIgnoreCase("default")) {
                            giveMethod.invoke(api, player.getUniqueId(), entry.getValue());
                            MessageUtil.sendMessage(player, "§a获得 " + entry.getValue() + " 点券");
                        }
                    }
                }
            }
        } catch (Exception e) {
            DebugUtil.debug(player, "PlayerPoints奖励发放失败: " + e.getMessage());
        }
    }

    // 控制台命令
    List<String> commands = ConfigUtil.getInstanceConsoleCommands(config);
    for (String cmd : commands) {
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", player.getName()));
        } catch (Exception e) {
            DebugUtil.debug(player, "控制台命令执行失败: " + e.getMessage());
        }
    }
    MessageUtil.sendMessage(player, "§a地牢通关！奖励已发放。");
    // 在 grantRewards 方法末尾（所有奖励发放之后）
    WinStorage.incrementWin(dungeonName, player.getUniqueId(), player.getName());
}
public boolean isRoomReuseAllowed() {
    return ConfigUtil.isInstanceAllowRoomReuse(config);
}
public boolean isSlimefunAllowed() {
    return ConfigUtil.isInstanceAllowSlimefun(config);
}
public boolean isMythicMobsAllowed() {
    return ConfigUtil.isInstanceUseMythicMobs(config);
}
}