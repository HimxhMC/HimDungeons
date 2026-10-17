package com.him.dungeons.generator;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.*;
import com.him.dungeons.generator.ConfigApplier;
import com.him.dungeons.listener.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.Inventory;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import java.util.Random;
import java.util.stream.Collectors;
import org.bukkit.inventory.ItemStack;
import com.him.dungeons.HimDungeons.DungeonInstance;
import com.him.dungeons.schematic.*;
import com.him.dungeons.util.Pos3;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class DungeonGenerator {

    private final HimDungeons plugin;
    private final String dungeonName;
    private final Player player;
    private final FileConfiguration instanceConfig;
    private final ConfigApplier configApplier;
    private Map<String, List<ResourceUtil.RoomEntry>> roomPool;
    private final Set<String> usedRoomBaseNames = new HashSet<>();

    private World world;
    private List<RoomInstance> placedRooms;
    private RoomInstance startRoom;
    private final Random rand = new Random();

public DungeonGenerator(HimDungeons plugin, String dungeonName, Player player) {
    this.plugin = plugin;
    this.dungeonName = dungeonName;
    this.player = player;
    this.instanceConfig = ConfigUtil.getDungeonConfig(dungeonName);
    if (this.instanceConfig == null) {
        throw new IllegalArgumentException("地牢配置不存在: " + dungeonName);
    }
    this.configApplier = new ConfigApplier(plugin, dungeonName, player, instanceConfig);
}

    public void generate(Consumer<Boolean> callback) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> generate(callback));
            return;
        }

        try {
            String worldName = "dungeon_" + dungeonName + "_" + System.currentTimeMillis();
            DebugUtil.debug(player, "创建虚空世界: %s", worldName);
            world = WorldUtil.createVoidWorld(worldName);
            if (world == null) {
                DebugUtil.debug(player, "虚空世界创建失败");
                if (callback != null) callback.accept(false);
                return;
            }
            plugin.addCreatedWorld(worldName);
            WorldUtil.applyWorldSettings(world, instanceConfig);
            DebugUtil.debug(player, "世界设置已应用");
        } catch (Exception e) {
            e.printStackTrace();
            DebugUtil.debug(player, "世界创建异常: %s", e.getMessage());
            if (callback != null) callback.accept(false);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean success = generateAsync();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (callback != null) callback.accept(success);
                });
            } catch (Exception e) {
                e.printStackTrace();
                DebugUtil.debug(player, "生成异常: %s", e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (world != null) WorldUtil.deleteWorldAndRelease(world);
                    if (callback != null) callback.accept(false);
                });
            }
        });
    }

private boolean generateAsync() throws Exception {
    DebugUtil.debug(player, "开始生成地牢: %s", dungeonName);
    placedRooms = new ArrayList<>();
    // 初始化房间池
    roomPool = new HashMap<>();
    List<ResourceUtil.RoomEntry> allRooms = ResourceUtil.scanRooms(dungeonName);
    roomPool.put("start", allRooms.stream().filter(e -> e.isStart).collect(Collectors.toList()));
    roomPool.put("boss", allRooms.stream().filter(e -> e.isBoss).collect(Collectors.toList()));
    roomPool.put("fallback", allRooms.stream().filter(e -> e.isFallback).collect(Collectors.toList()));
    roomPool.put("normal", allRooms.stream().filter(e -> !e.isStart && !e.isBoss && !e.isFallback).collect(Collectors.toList()));
    configApplier.prepare();

    startRoom = placeStartRoom();
    if (startRoom == null) {
        DebugUtil.debug(player, "起始房间放置失败");
        throw new RuntimeException("无法放置起始房间");
    }
    placedRooms.add(startRoom);
    DebugUtil.debug(player, "起始房间放置成功，锚点 %s，门数量 %d", startRoom.anchor, startRoom.doors.size());

    int maxRooms = ConfigUtil.getInstanceMaxRoomAmount(instanceConfig);
    int minRooms = ConfigUtil.getInstanceMinRoomAmount(instanceConfig);
    int maxDepth = ConfigUtil.getInstanceDfsMaxDeep(instanceConfig);
    int[] bossRange = ConfigUtil.getInstanceStartToBossRoomAmount(instanceConfig);
    int pathMin = bossRange[0];
    int pathMax = bossRange.length > 1 ? bossRange[1] : bossRange[0];
    DebugUtil.debug(player, "参数: minRooms=%d, maxRooms=%d, maxDepth=%d, pathMin=%d, pathMax=%d",
            minRooms, maxRooms, maxDepth, pathMin, pathMax);

    // ---- 主路径生成 ----
    DebugUtil.debug(player, "开始生成主路径 (start → boss) 通路...");
    boolean bossPlaced = generateMainPath(pathMin, pathMax, maxRooms);
    if (!bossPlaced) {
        DebugUtil.debug(player, "主路径生成失败");
        throw new RuntimeException("无法生成主路径");
    }
    DebugUtil.debug(player, "主路径完成，当前非fallback房间数 %d", countNonFallbackRooms());

    // ---- 分支BFS拓展 ----
    DebugUtil.debug(player, "开始从主路径空门作为根节点进行BFS分支拓展...");
    generateBranches(minRooms, maxRooms, maxDepth);
    DebugUtil.debug(player, "BFS分支拓展完成，当前非fallback房间数 %d", countNonFallbackRooms());

    preMatchEmptyDoors();

    // ---- 全房间fallback补全 ----
    DebugUtil.debug(player, "开始为所有未连接的房间空门接入fallback封堵...");
    int fallbackCount = fillAllEmptyDoorsWithFallback();
    DebugUtil.debug(player, "Fallback封堵完成，总fallback房间数 %d", fallbackCount);

    // ---- 连通性检查 ----
    if (ConfigUtil.isInstanceBfsCheckEnabled(instanceConfig)) {
        DebugUtil.debug(player, "开始连通性检查 ...");
        if (!checkConnectivity()) {
            DebugUtil.debug(player, "连通性检查失败");
            throw new RuntimeException("逻辑连通性检查失败");
        }
        DebugUtil.debug(player, "连通性检查通过");
    }

    // ---- 粘贴 ----
    DebugUtil.debug(player, "开始粘贴所有房间 ...");
    CompletableFuture<Void> pasteFuture = pasteAllRoomsAsync();
    pasteFuture.join();
    DebugUtil.debug(player, "粘贴完成");

    // ---- 传送玩家 ----
Bukkit.getScheduler().runTask(plugin, () -> {
    // ---- 1. 计算玩家出生点 ----
    Location spawnLoc = startRoom.anchor.clone();
    File spawnFile = ResourceUtil.getRoomSpawnYamlFlat(dungeonName, startRoom.baseName);
    if (spawnFile != null) {
        Vector relativeSpawn = YamlDoorUtil.parseSpawnPoint(spawnFile);
        if (relativeSpawn != null) {
            Pos3 relativeVec = Pos3.at(
                    relativeSpawn.getX(), relativeSpawn.getY(), relativeSpawn.getZ());
            Pos3 rotated = TransformUtil.rotatePos(relativeVec, startRoom.rotationTimes);
            spawnLoc.add(rotated.getX(), rotated.getY(), rotated.getZ());
        }
    }
    spawnLoc.setX(spawnLoc.getBlockX() + 0.5);
    spawnLoc.setZ(spawnLoc.getBlockZ() + 0.5);

    // ---- 2. 初始化 DungeonInstance ----
    HimDungeons.DungeonInstance instance = new HimDungeons.DungeonInstance(
            dungeonName, world.getName(), spawnLoc);
    instance.placedRooms = new ArrayList<>(placedRooms);
    instance.deepCloneActive = ConfigUtil.isInstanceDeepCloneInventory(instanceConfig);
    plugin.dungeonInstances.put(world.getName(), instance);
    plugin.dungeonNameToWorld.put(dungeonName, world.getName());
    plugin.registerConfigApplier(world.getName(), configApplier);
    plugin.registerDungeonWorld(world.getName(), dungeonName);

    // ---- 3. 玩家入地牢（sessionService 统一处理备份 + 清空 + 传送） ----
    plugin.getSessionService().enter(player, instance);
    DebugUtil.debug(player, "玩家已进入地牢，deepClone=%s", instance.deepCloneActive);

    // ---- 4. 生成 Boss ----
    RoomInstance bossRoom = findBossRoom();
    if (bossRoom != null) spawnBossEntity(bossRoom, world);

    // ---- 5. 限时计时器 ----
    int timeSeconds = ConfigUtil.getInstanceTime(instanceConfig);
    if (timeSeconds > 0) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            DungeonInstance inst = plugin.dungeonInstances.get(world.getName());
            if (inst != null && !inst.isFinished) plugin.forceFinishDungeon(inst);
        }, timeSeconds * 20L);
    }

    // ---- 6. 完成消息 ----
    MessageUtil.sendMessage(player, "地牢 '" + dungeonName + "' 生成完成！共 "
            + countNonFallbackRooms() + " 个普通房间，" + fallbackCount + " 个fallback房间。");
});

return true;
}
private RoomInstance findBossRoom() {
    for (RoomInstance room : placedRooms) {
        if (room.roomType.toLowerCase().startsWith("boss")) return room;
    }
    return null;
}
private void spawnBossEntity1(RoomInstance bossRoom, World world) {
    // 读取出生点
    File spawnFile = ResourceUtil.getRoomSpawnYamlFlat(dungeonName, bossRoom.baseName);
    Location spawnLoc = bossRoom.anchor.clone();
    if (spawnFile != null) {
        Vector rel = YamlDoorUtil.parseSpawnPoint(spawnFile);
        if (rel != null) {
            Pos3 relVec = Pos3.at(rel.getX(), rel.getY(), rel.getZ());
            Pos3 rotated = TransformUtil.rotatePos(relVec, bossRoom.rotationTimes);
            spawnLoc.add(rotated.getX(), rotated.getY(), rotated.getZ());
        }
    }
    spawnLoc.add(0.5, 0.5, 0.5);

    Map<String, String> bossInfo = configApplier.selectBoss();
    String typeName = bossInfo.get("type");
    String nbt = bossInfo.get("nbt");

    EntityType type;
    try {
        type = EntityType.valueOf(typeName.toUpperCase());
    } catch (IllegalArgumentException e) {
        type = EntityType.ZOMBIE;
    }
    LivingEntity boss = (LivingEntity) world.spawnEntity(spawnLoc, type);

    // ----- 处理血量和NBT -----
    double maxHealth = 100.0;
    // 简单解析 Health 字段
    if (nbt != null && !nbt.isEmpty()) {
        try {
            // 支持格式 "Health:100.0f" 或 "Health:100.0"
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("Health:\\s*([0-9.]+)f?");
            java.util.regex.Matcher m = p.matcher(nbt);
            if (m.find()) {
                maxHealth = Double.parseDouble(m.group(1));
            }
        } catch (Exception ignored) {}
    }
    // 设置最大血量和当前血量
    boss.setMaxHealth(maxHealth);
    boss.setHealth(maxHealth);

    // 如果希望应用更多NBT属性，可以使用反射或直接调用 setXxx 方法
    // 此处仅作为示例，可根据需要扩展

    // 注册 Boss 数据
    HimDungeons.BossData data = new HimDungeons.BossData(boss, configApplier, dungeonName, world.getName(), spawnLoc,bossRoom);
    plugin.registerBoss(world.getName(), data);
}
private void spawnBossEntity(RoomInstance bossRoom, World world) {
    // 获取出生点
    File spawnFile = ResourceUtil.getRoomSpawnYamlFlat(dungeonName, bossRoom.baseName);
    Location spawnLoc = bossRoom.anchor.clone();
    if (spawnFile != null) {
        Vector rel = YamlDoorUtil.parseSpawnPoint(spawnFile);
        if (rel != null) {
            Pos3 relVec = Pos3.at(rel.getX(), rel.getY(), rel.getZ());
            Pos3 rotated = TransformUtil.rotatePos(relVec, bossRoom.rotationTimes);
            spawnLoc.add(rotated.getX(), rotated.getY(), rotated.getZ());
        }
    }
    spawnLoc.add(0.5, 0.5, 0.5);
    DebugUtil.debug(player, "Boss 出生位置: %s", spawnLoc);

    Map<String, String> bossInfo = configApplier.selectBoss();
    String typeName = bossInfo.get("type");
    String nbt = bossInfo.get("nbt");
    DebugUtil.debug(player, "选择的 Boss 类型: %s, NBT: %s", typeName, nbt);

    LivingEntity boss = null;

    // ===== 1. MythicMobs 支持（若启用且插件存在） =====
    if (ConfigUtil.isInstanceUseMythicMobs(instanceConfig) && Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
        try {
            boss = SoftDependencyUtil.spawnMythicMob(typeName, spawnLoc, 1);
            if (boss != null) {
                // 防止Boss消失
                boss.setRemoveWhenFarAway(false);
                if (boss instanceof org.bukkit.entity.Mob) {
                    ((org.bukkit.entity.Mob) boss).setPersistent(true);
                }
                DebugUtil.debug(player, "MythicMobs Boss 生成成功");
                HimDungeons.BossData data = new HimDungeons.BossData(boss, configApplier, dungeonName,
                        world.getName(), spawnLoc, bossRoom);
                plugin.registerBoss(world.getName(), data);
                return;
            } else {
                DebugUtil.debug(player, "MythicMobs 生成失败，降级到原版");
            }
        } catch (Exception e) {
            DebugUtil.debug(player, "MythicMobs 生成异常: " + e.getMessage());
            // 降级到原版
        }
    }

    // ===== 2. 原版生成 =====
    EntityType type;
    try {
        type = EntityType.valueOf(typeName.toUpperCase());
    } catch (IllegalArgumentException e) {
        DebugUtil.debug(player, "无效的 Boss 类型 '%s'，使用 ZOMBIE", typeName);
        type = EntityType.ZOMBIE;
    }

    try {
        boss = (LivingEntity) world.spawnEntity(spawnLoc, type);
    } catch (Exception e) {
        DebugUtil.debug(player, "生成 Boss 实体失败: " + e.getMessage());
        return;
    }

    if (boss == null) {
        DebugUtil.debug(player, "world.spawnEntity 返回 null，Boss 生成失败");
        return;
    }

    // 设置血量
    double maxHealth = 10000.0;
    if (nbt != null && !nbt.isEmpty()) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("Health:\\s*([0-9.]+)f?");
            java.util.regex.Matcher m = p.matcher(nbt);
            if (m.find()) {
                maxHealth = Double.parseDouble(m.group(1));
            }
        } catch (Exception ignored) {}
    }
    boss.setMaxHealth(maxHealth);
    boss.setHealth(maxHealth);

    // ===== 防止Boss消失 =====
    boss.setRemoveWhenFarAway(false);
    if (boss instanceof org.bukkit.entity.Mob) {
        ((org.bukkit.entity.Mob) boss).setPersistent(true);
    }

    DebugUtil.debug(player, "Boss 血量设置为 %.1f", maxHealth);

    // 注册
    HimDungeons.BossData data = new HimDungeons.BossData(boss, configApplier, dungeonName,
            world.getName(), spawnLoc, bossRoom);
    plugin.registerBoss(world.getName(), data);
    DebugUtil.debug(player, "Boss 已注册，当前 dungeonBosses 包含: %s", plugin.dungeonBosses.keySet());
}

    // 统计非fallback的普通房间数量 fallback不计入总房间数
    private int countNonFallbackRooms() {
        int count = 0;
        for (RoomInstance room : placedRooms) {
            if (!room.isFallback) count++;
        }
        return count;
    }

    // ==================== 主路径生成（含回溯） 完全禁止fallback房间进入主路径 ====================
// ==================== 主路径生成（含回溯） 完全禁止fallback房间进入主路径 ====================
private boolean generateMainPath(int pathMin, int pathMax, int maxRooms) {
    List<PathNode> path = new ArrayList<>();
    path.add(new PathNode(startRoom, new HashSet<>()));
    int pathLength = 1;

    while (pathLength < pathMin && countNonFallbackRooms() < maxRooms) {
        PathNode currentNode = path.get(path.size() - 1);
        RoomInstance currentRoom = currentNode.room;

        RoomInstance next = tryExtend(currentRoom, -1, false, false, false);
        if (next != null && !next.isFallback) {
            placedRooms.add(next);
            path.add(new PathNode(next, new HashSet<>()));
            pathLength++;
            DebugUtil.debug(player, "主路径扩展成功，路径长度 %d", pathLength);
        } else {
            if (path.size() <= 1) {
                DebugUtil.debug(player, "主路径无法回溯（起始房间也无可用门），中断");
                return false;
            }
            currentNode.tried.addAll(currentRoom.getAvailableDoorIndices());
            RoomInstance removed = placedRooms.remove(placedRooms.size() - 1);
            path.remove(path.size() - 1);
            pathLength--;
            DebugUtil.debug(player, "主路径回溯：移除房间 %s，路径长度 %d", removed.roomType, pathLength);
        }
    }

    if (pathLength >= pathMin) {
        for (int i = path.size() - 1; i >= 0; i--) {
            RoomInstance current = path.get(i).room;
            RoomInstance boss = tryExtend(current, -1, true, false, false);
            if (boss != null) {
                placedRooms.add(boss);
                DebugUtil.debug(player, "主路径成功放置 boss，总非fallback房间数 %d", countNonFallbackRooms());
                return true;
            }
        }
        DebugUtil.debug(player, "主路径所有房间均无法放置 boss");
        return false;
    } else {
        DebugUtil.debug(player, "路径长度 %d 未达到 pathMin %d，且无法继续", pathLength, pathMin);
        return false;
    }
}

    // ==================== 分支BFS拓展 从主路径所有空门作为第一层根节点开始 ====================
// ==================== 分支BFS拓展 从主路径所有空门作为第一层根节点开始 ====================
private void generateBranches(int minRooms, int maxRooms, int maxDepth) {
    Queue<BranchNode> queue = new LinkedList<>();
    for (int i = 0; i < placedRooms.size(); i++) {
        RoomInstance room = placedRooms.get(i);
        if (room.isFallback) continue;
        for (int idx : room.getAvailableDoorIndices()) {
            queue.offer(new BranchNode(i, idx, 1));
        }
    }

    while (!queue.isEmpty() && countNonFallbackRooms() < maxRooms) {
        if (countNonFallbackRooms() >= minRooms && !hasAnyExtendableNode(queue)) {
            DebugUtil.debug(player, "已达到最小房间数且无剩余可拓展节点，提前结束BFS拓展");
            break;
        }

        BranchNode node = queue.poll();
        if (node.depth >= maxDepth) continue;
        RoomInstance currentRoom = placedRooms.get(node.roomIndex);
        if (!currentRoom.getAvailableDoorIndices().contains(node.doorIdx)) continue;

        // ★ 关键改动：使用 node.doorIdx，而不是 -1
        RoomInstance newRoom = tryExtend(currentRoom, node.doorIdx, false, false, false);
        if (newRoom != null && !newRoom.isFallback) {
            placedRooms.add(newRoom);
            int newIdx = placedRooms.size() - 1;
            for (int idx : newRoom.getAvailableDoorIndices()) {
                queue.offer(new BranchNode(newIdx, idx, node.depth + 1));
            }
            DebugUtil.debug(player, "BFS拓展成功，新普通房间 %s，当前非fallback房间数 %d",
                    newRoom.roomType, countNonFallbackRooms());
        } else {
            // ★ 同样使用 node.doorIdx
            RoomInstance fallbackRoom = tryPlaceFallback(currentRoom, node.doorIdx);
            if (fallbackRoom != null) {
                placedRooms.add(fallbackRoom);
                DebugUtil.debug(player, "BFS拓展失败，成功放置fallback封堵门 %d", node.doorIdx);
            } else {
                DebugUtil.debug(player, "拓展失败且无法放置fallback，门 %d", node.doorIdx);
            }
        }
    }
}

    // 检查队列中是否还存在可以正常拓展的节点
    private boolean hasAnyExtendableNode(Queue<BranchNode> queue) {
        for (BranchNode node : queue) {
            if (node.depth < ConfigUtil.getInstanceDfsMaxDeep(instanceConfig)) {
                RoomInstance currentRoom = placedRooms.get(node.roomIndex);
                if (currentRoom.getAvailableDoorIndices().contains(node.doorIdx)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== 全量空门fallback封堵方法 所有未连接节点全部接入fallback ====================
private int fillAllEmptyDoorsWithFallback() {
    int fallbackCount = 0;
    // 记录已使用的 fallback 锚点（防止重叠）
    Set<Pos3> usedAnchors = new HashSet<>();
    
    // 收集所有房间的所有空闲门（仍按原方式遍历，但生成时检查）
    List<int[]> emptyDoors = new ArrayList<>();
    for (int i = 0; i < placedRooms.size(); i++) {
        RoomInstance room = placedRooms.get(i);
        for (int idx : room.getAvailableDoorIndices()) {
            emptyDoors.add(new int[]{i, idx});
        }
    }
    
for (int[] pair : emptyDoors) {
    int roomIdx = pair[0];
    int doorIdx = pair[1];
    RoomInstance room = placedRooms.get(roomIdx);
    
    // 如果该门已被标记为废弃（可能被其他逻辑修改），跳过
    if (!room.getAvailableDoorIndices().contains(doorIdx)) continue;
    
    // 尝试生成 fallback
    RoomInstance fb = tryPlaceFallback(room, doorIdx);
    if (fb != null) {
        // 检查锚点是否已被占用
        Pos3 anchor = Pos3.at(
            fb.anchor.getBlockX(),
            fb.anchor.getBlockY(),
            fb.anchor.getBlockZ()
        );
        if (usedAnchors.contains(anchor)) {
            // 锚点重复，不放置此 fallback，但保留门（不标记废弃）
            DebugUtil.debug(player, "空门 %d 的锚点 %s 已被占用，跳过（门保留）", doorIdx, anchor);
            continue;
        }
        usedAnchors.add(anchor);
        placedRooms.add(fb);
        fallbackCount++;
        DebugUtil.debug(player, "空门 %d fallback封堵成功，锚点 %s，当前fallback总数 %d", 
            doorIdx, anchor, fallbackCount);
    } else {
        // 生成失败，但不再标记门废弃，仅记录
        DebugUtil.debug(player, "空门 %d fallback封堵失败，但门保留，等待后续重试", doorIdx);
        // 注意：不调用 room.markDoorUsed(doorIdx);
    }
}
    return fallbackCount;
}

    // ==================== 统一扩展方法 100%保留原有旋转对接和AABB检测逻辑 ====================
// ==================== 统一扩展方法 100%保留原有旋转对接和AABB检测逻辑 ====================
private RoomInstance tryExtend(RoomInstance currentRoom, int doorIdx,
                               boolean forceBoss, boolean allowFallback, boolean forceFallbackIgnoreCheck) {

    // ===== 1. 若 doorIdx == -1，按权重从空闲门中选择 =====
    if (doorIdx == -1) {
        List<Integer> avail = currentRoom.getAvailableDoorIndices();
        if (avail.isEmpty()) return null;

        // ★ 先打乱，再按权重降序排序，让同权重的门随机优先
        Collections.shuffle(avail, rand);
        avail.sort((i1, i2) -> Integer.compare(
                currentRoom.doors.get(i2).weight,
                currentRoom.doors.get(i1).weight
        ));

        // 依次尝试每个门，成功则返回
        for (int idx : avail) {
            RoomInstance result = tryExtend(currentRoom, idx, forceBoss, allowFallback, forceFallbackIgnoreCheck);
            if (result != null) return result;
        }
        return null;
    }

    // ===== 2. 当前门信息 =====
    YamlDoorUtil.DoorAnchor currentMeta = currentRoom.doors.get(doorIdx);
    Pos3 currentDoorWorld = DoorMatcherUtil.toWorldDoorPosition(
            currentMeta, currentRoom.anchor, currentRoom.rotationTimes);
    String currentFacing = TransformUtil.rotateFacing(currentMeta.facing, currentRoom.rotationTimes);
    String currentDoorType = currentMeta.type != null ? currentMeta.type : "default";

    // ===== 3. 强制 fallback 封堵逻辑（允许 4 种旋转，不参与碰撞） =====
    if (forceFallbackIgnoreCheck && allowFallback) {
        List<ResourceUtil.RoomEntry> fallbackPool = roomPool.get("fallback");
        if (fallbackPool.isEmpty()) {
            DebugUtil.debug(player, "错误：没有 fallback 房间可用！");
            return null;
        }

        String targetFacing = TransformUtil.oppositeFacing(currentFacing);
        if (targetFacing == null) return null;

        List<ResourceUtil.RoomEntry> shuffled = new ArrayList<>(fallbackPool);
        Collections.shuffle(shuffled, rand);

        for (ResourceUtil.RoomEntry entry : shuffled) {
            String fbBase = entry.baseName;
            File schem = ResourceUtil.getRoomSchematicFlat(dungeonName, fbBase);
            File door = ResourceUtil.getRoomDoorYamlFlat(dungeonName, fbBase);
            if (schem == null || door == null) continue;

            Pos3 size = WorldEditUtil.getClipboardSizeAndRelease(schem);
            if (size == null) continue;

            List<YamlDoorUtil.DoorAnchor> candidateDoors;
            try {
                candidateDoors = YamlDoorUtil.parseDoorAnchors(door);
            } catch (Exception e) {
                continue;
            }
            if (candidateDoors.size() != 1) continue;

            YamlDoorUtil.DoorAnchor candDoor = candidateDoors.get(0);

            // ★ 遍历 4 种旋转，找到使 candFacing == targetFacing 的那个
            for (int rot = 0; rot < 4; rot++) {
                String candFacing = TransformUtil.rotateFacing(candDoor.facing, rot);
                if (!candFacing.equals(targetFacing)) continue;

                Pos3 rotatedCandPos = TransformUtil.rotatePos(
                        Pos3.at(candDoor.x, candDoor.y, candDoor.z), rot);

                Vector dirVec = TransformUtil.facingToVector(currentFacing);
                Pos3 dirBlock = Pos3.at(dirVec.getX(), dirVec.getY(), dirVec.getZ());
                Pos3 anchorVec = currentDoorWorld.add(dirBlock).subtract(rotatedCandPos);
                Location candidateAnchor = WorldUtil.toLocation(anchorVec, world);
                if (candidateAnchor == null) continue;

                Pos3 candWorldPos = Pos3.at(
                        candidateAnchor.getX() + rotatedCandPos.getX(),
                        candidateAnchor.getY() + rotatedCandPos.getY(),
                        candidateAnchor.getZ() + rotatedCandPos.getZ()
                );
                if (!DoorMatcherUtil.isMatching(currentDoorWorld, currentFacing, candWorldPos, candFacing)) {
                    continue;
                }

AABBUtil.AABB newAabb = AABBUtil.buildRotated(candidateAnchor, size, rot);
Pos3 rotatedSize = TransformUtil.rotatePos(size, rot);
int w = Math.abs(rotatedSize.getX());
int h = Math.abs(rotatedSize.getY());
int d = Math.abs(rotatedSize.getZ());
Pos3 absSize = Pos3.at(w, h, d);

                RoomInstance newRoom = new RoomInstance(
                        "fallback", fbBase, candidateAnchor, rot,   // ★ 记住旋转次数
                        absSize, newAabb, candidateDoors, true
                );
                currentRoom.markDoorUsed(doorIdx);
                newRoom.markDoorUsed(0);
                configApplier.recordRoomUsed(fbBase);
                return newRoom;
            }
        }

        DebugUtil.debug(player, "无法找到合适的 fallback 封堵门 %d，但保留门未使用", doorIdx);
        return null;
    }

    // ===== 4. 正常拓展（普通房间或 Boss，不包含 fallback） =====
    List<ResourceUtil.RoomEntry> pool;
    if (forceBoss) {
        pool = roomPool.get("boss");
    } else {
        pool = roomPool.get("normal");
    }
    if (pool.isEmpty()) return null;

    // ===== 应用房间复用过滤（allowRoomReuse） =====
    List<String> allBaseNames = pool.stream().map(e -> e.baseName).collect(Collectors.toList());
    List<String> filteredBaseNames = configApplier.filterRoomTemplates(forceBoss ? "boss" : "normal", allBaseNames);
    if (filteredBaseNames != null) {
        Set<String> allowed = new HashSet<>(filteredBaseNames);
        pool = pool.stream().filter(e -> allowed.contains(e.baseName)).collect(Collectors.toList());
    }
    if (pool.isEmpty()) {
        return null;
    }

    for (int attempt = 0; attempt < 20; attempt++) {
        ResourceUtil.RoomEntry selected = selectByWeight(pool);
        if (selected == null) continue;
        String candidateBaseName = selected.baseName;
        if (!configApplier.isRoomReuseAllowed() && usedRoomBaseNames.contains(candidateBaseName)) {
            continue;
        }

        File schematic = ResourceUtil.getRoomSchematicFlat(dungeonName, candidateBaseName);
        File doorFile = ResourceUtil.getRoomDoorYamlFlat(dungeonName, candidateBaseName);
        if (schematic == null || doorFile == null) continue;

        Pos3 size = WorldEditUtil.getClipboardSizeAndRelease(schematic);
        if (size == null) continue;

        List<YamlDoorUtil.DoorAnchor> candidateDoors;
        try {
            candidateDoors = YamlDoorUtil.parseDoorAnchors(doorFile);
        } catch (Exception e) {
            continue;
        }
        if (candidateDoors.isEmpty()) continue;
        if (!forceBoss && candidateDoors.size() < 2) continue;

        for (int candIdx = 0; candIdx < candidateDoors.size(); candIdx++) {
            YamlDoorUtil.DoorAnchor candDoor = candidateDoors.get(candIdx);
            String candDoorType = candDoor.type != null ? candDoor.type : "default";

            boolean currentIsFallback = currentRoom.isFallback || currentDoorType.equals("fallback");
            boolean candIsFallback = candidateBaseName.startsWith("fallback") || candDoorType.equals("fallback");
            if (!currentIsFallback && !candIsFallback && !currentDoorType.equals(candDoorType)) {
                continue;
            }

            for (int rot = 0; rot < 4; rot++) {
                Pos3 rotatedCandPos = TransformUtil.rotatePos(
                        Pos3.at(candDoor.x, candDoor.y, candDoor.z), rot);

                Vector dirVec = TransformUtil.facingToVector(currentFacing);
                Pos3 dirBlock = Pos3.at(dirVec.getX(), dirVec.getY(), dirVec.getZ());
                Pos3 anchorVec = currentDoorWorld.add(dirBlock).subtract(rotatedCandPos);
                Location candidateAnchor = WorldUtil.toLocation(anchorVec, world);
                if (candidateAnchor == null) continue;

                Pos3 candWorldPos = Pos3.at(
                        candidateAnchor.getX() + rotatedCandPos.getX(),
                        candidateAnchor.getY() + rotatedCandPos.getY(),
                        candidateAnchor.getZ() + rotatedCandPos.getZ()
                );
                String candFacing = TransformUtil.rotateFacing(candDoor.facing, rot);
                if (!DoorMatcherUtil.isMatching(currentDoorWorld, currentFacing, candWorldPos, candFacing)) {
                    continue;
                }

                String roomAId = currentRoom.roomType + "@" + currentRoom.anchor.toString();
                String roomBId = candidateBaseName + "@" + candidateAnchor.toString();
                if (!configApplier.canPairDoors(roomAId, doorIdx, roomBId, candIdx)) {
                    continue;
                }

AABBUtil.AABB newAabb = AABBUtil.buildRotated(candidateAnchor, size, rot);
Pos3 rotatedSize = TransformUtil.rotatePos(size, rot);
int w = Math.abs(rotatedSize.getX());
int h = Math.abs(rotatedSize.getY());
int d = Math.abs(rotatedSize.getZ());
Pos3 absSize = Pos3.at(w, h, d);

                boolean useAabb = ConfigUtil.isInstanceAabbEnabled(instanceConfig);
                if (useAabb) {
                    boolean overlap = false;
                    for (RoomInstance placed : placedRooms) {
                        if (placed.isFallback) continue;
                        if (AABBUtil.AABB.overlaps(newAabb, placed.aabb)) {
                            overlap = true;
                            break;
                        }
                    }
                    if (overlap) continue;
                } else {
                    if (!configApplier.checkForbiddenBlocks(candidateAnchor, rot, size)) continue;
                }

                int disconnectedDistance = ConfigUtil.getInstanceDisconnectedRoomDistance(instanceConfig);
                if (disconnectedDistance > 0) {
                    double minDist = Double.MAX_VALUE;
                    for (RoomInstance placed : placedRooms) {
                        if (placed.isFallback) continue;
                        if (placed == currentRoom) continue;
                        double dist = AABBUtil.AABB.minDistance(newAabb, placed.aabb);
                        if (dist < minDist) minDist = dist;
                    }
                    if (minDist < disconnectedDistance) continue;
                }

                boolean isFallback = candidateBaseName.startsWith("fallback");
                RoomInstance newRoom = new RoomInstance(
                        candidateBaseName, candidateBaseName, candidateAnchor, rot,
                        absSize, newAabb, candidateDoors, isFallback
                );
                currentRoom.markDoorUsed(doorIdx);
                newRoom.markDoorUsed(candIdx);
                configApplier.recordRoomUsed(candidateBaseName);
                if (!configApplier.isRoomReuseAllowed()) {
                    usedRoomBaseNames.add(candidateBaseName);
                }
                return newRoom;
            }
        }
    }
    return null;
}
/**
 * 计算候选门需要旋转多少次，使其朝向与当前门相反（即能相互配对）
 * @param currentFacing 当前门的世界朝向（已旋转）
 * @param candOriginalFacing 候选门的原始朝向（未旋转）
 * @return 旋转次数 (0~3)，若无法匹配（如垂直与水平）返回 -1
 */
private int computeRequiredRotation(String currentFacing, String candOriginalFacing) {
    // 获取当前门的反向朝向
    String opposite = TransformUtil.oppositeFacing(currentFacing);
    if (opposite == null) return -1;

    // 尝试 0~3 次旋转，看哪个旋转后候选门朝向等于 opposite
    for (int rot = 0; rot < 4; rot++) {
        String rotated = TransformUtil.rotateFacing(candOriginalFacing, rot);
        if (rotated.equals(opposite)) {
            return rot;
        }
    }
    return -1;
}

    // ==================== Fallback 放置 ====================
private RoomInstance tryPlaceFallback(RoomInstance currentRoom, int doorIdx) {
    // 直接调用 tryExtend，传入 allowFallback=true, forceFallbackIgnoreCheck=true
    // 这会尝试所有 fallback 房间
    return tryExtend(currentRoom, doorIdx, false, true, true);
}


    // ==================== 辅助方法 ====================
/**
 * 从 RoomEntry 列表中按权重随机选择一个
 */
private ResourceUtil.RoomEntry selectByWeight(List<ResourceUtil.RoomEntry> pool) {
    int total = pool.stream().mapToInt(e -> e.weight).sum();
    if (total == 0) {
        return pool.get(rand.nextInt(pool.size()));
    }
    int r = rand.nextInt(total);
    int cum = 0;
    for (ResourceUtil.RoomEntry entry : pool) {
        cum += entry.weight;
        if (r < cum) return entry;
    }
    return pool.get(0);
}
    private boolean checkConnectivity() {
        if (placedRooms.size() <= 1) return true;
        Map<RoomInstance, List<RoomInstance>> graph = new HashMap<>();
        for (RoomInstance r : placedRooms) graph.put(r, new ArrayList<>());
        for (int i = 0; i < placedRooms.size(); i++) {
            for (int j = i + 1; j < placedRooms.size(); j++) {
                RoomInstance a = placedRooms.get(i);
                RoomInstance b = placedRooms.get(j);
                if (areRoomsConnected(a, b)) {
                    graph.get(a).add(b);
                    graph.get(b).add(a);
                }
            }
        }
        Set<RoomInstance> visited = new HashSet<>();
        Queue<RoomInstance> queue = new LinkedList<>();
        queue.add(placedRooms.get(0));
        visited.add(placedRooms.get(0));
        while (!queue.isEmpty()) {
            RoomInstance cur = queue.poll();
            for (RoomInstance nb : graph.getOrDefault(cur, Collections.emptyList())) {
                if (!visited.contains(nb)) {
                    visited.add(nb);
                    queue.add(nb);
                }
            }
        }
        return visited.size() == placedRooms.size();
    }

    private boolean areRoomsConnected(RoomInstance a, RoomInstance b) {
        for (int idxA : a.usedDoorIndices) {
            for (int idxB : b.usedDoorIndices) {
                YamlDoorUtil.DoorAnchor doorA = a.doors.get(idxA);
                YamlDoorUtil.DoorAnchor doorB = b.doors.get(idxB);
                Pos3 posA = DoorMatcherUtil.toWorldDoorPosition(doorA, a.anchor, a.rotationTimes);
                Pos3 posB = DoorMatcherUtil.toWorldDoorPosition(doorB, b.anchor, b.rotationTimes);
                String facingA = TransformUtil.rotateFacing(doorA.facing, a.rotationTimes);
                String facingB = TransformUtil.rotateFacing(doorB.facing, b.rotationTimes);
                if (DoorMatcherUtil.isMatching(posA, facingA, posB, facingB)) {
                    return true;
                }
            }
        }
        return false;
    }

private RoomInstance placeStartRoom() {
    List<ResourceUtil.RoomEntry> starts = roomPool.get("start");
    if (starts.isEmpty()) {
        DebugUtil.debug(player, "没有 start 前缀的房间");
        return null;
    }
    ResourceUtil.RoomEntry selected = selectByWeight(starts);
    String baseName = selected.baseName;
    File schematic = ResourceUtil.getRoomSchematicFlat(dungeonName, baseName);
    File doorFile = ResourceUtil.getRoomDoorYamlFlat(dungeonName, baseName);
    if (schematic == null || doorFile == null) {
        DebugUtil.debug(player, "start 房间文件缺失: " + baseName);
        return null;
    }
    Pos3 size = WorldEditUtil.getClipboardSizeAndRelease(schematic);
    if (size == null) return null;
    List<YamlDoorUtil.DoorAnchor> doors;
    try {
        doors = YamlDoorUtil.parseDoorAnchors(doorFile);
    } catch (Exception e) {
        DebugUtil.debug(player, "解析 start 门文件失败: " + e.getMessage());
        return null;
    }
    Location anchor = new Location(world, 0, 64, 0);
    int rotation = 0;
    Pos3 rotatedSize = TransformUtil.rotatePos(size, rotation);
    AABBUtil.AABB aabb = AABBUtil.buildRotated(anchor, size, rotation);
    return new RoomInstance("start", baseName, anchor, rotation, rotatedSize, aabb, doors);
}

private CompletableFuture<Void> pasteAllRoomsAsync() {
    CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
    for (RoomInstance room : placedRooms) {
        future = future.thenCompose(v -> {
            CompletableFuture<Void> f = new CompletableFuture<>();
            File schematic = ResourceUtil.getRoomSchematicFlat(dungeonName, room.baseName);
            if (schematic == null) {
                f.completeExceptionally(new RuntimeException("房间 schematic 缺失"));
                return f;
            }
            // ★ 用 AsyncPaster 替代 WorldEditUtil.pasteAndReleaseAsync
            AsyncPaster.pasteAsync(
                    world,
                    room.anchor,
                    schematic,
                    room.rotationTimes,
                    ConfigUtil.getSchematicReadFormat(),
                    success -> {
                        if (success) {
                            configApplier.storeForbiddenBlocks(room);
                            fillChests(room);
                            f.complete(null);
                        } else {
                            f.completeExceptionally(new RuntimeException("粘贴失败"));
                        }
                    });
            return f;
        });
    }
    return future;
}

/**
 * 填充房间内的所有箱子
 */
private void fillChests(RoomInstance room) {
    File chestFile = ResourceUtil.getRoomChestYamlFlat(dungeonName, room.baseName);
    if (chestFile == null || !chestFile.exists()) return;

    List<Map<String, Object>> chests = YamlDoorUtil.parseChestLoot(chestFile);
    for (Map<String, Object> entry : chests) {
        List<Integer> pos = (List<Integer>) entry.get("pos");
        if (pos == null || pos.size() < 3) continue;
        String lootStr = (String) entry.get("loot");
        if (lootStr == null) continue;

        // 解析概率、物品和数量
        String[] parts = lootStr.split(" ");
        if (parts.length < 2) continue;
        int chance;
        try {
            chance = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) { continue; }
        Material mat = Material.getMaterial(parts[1].toUpperCase());
        if (mat == null) continue;
        int amount = (parts.length >= 3) ? Integer.parseInt(parts[2]) : 1;

        // 计算旋转后的世界坐标
        Pos3 relPos = Pos3.at(pos.get(0), pos.get(1), pos.get(2));
        Pos3 rotated = TransformUtil.rotatePos(relPos, room.rotationTimes);
        Location worldLoc = room.anchor.clone().add(rotated.getX(), rotated.getY(), rotated.getZ());

        // 获取该位置的箱子
        Block block = world.getBlockAt(worldLoc);
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) continue;
        Chest chest = (Chest) block.getState();

        // 随机决定是否生成
        if (chance >= 100 || (chance > 0 && new Random().nextInt(100) < chance)) {
            ItemStack item = new ItemStack(mat, amount);
            // 如果箱子为空，直接设置；否则追加
            Inventory inv = chest.getBlockInventory();
            if (inv.firstEmpty() != -1) {
                inv.addItem(item);
            }
        }
    }
}

    // ==================== 内部类 ====================
    private static class PathNode {
        RoomInstance room;
        Set<Integer> tried; // 已尝试的门索引
        PathNode(RoomInstance room, Set<Integer> tried) {
            this.room = room;
            this.tried = tried;
        }
    }

    private static class BranchNode {
        int roomIndex;
        int doorIdx;
        int depth;
        BranchNode(int roomIndex, int doorIdx, int depth) {
            this.roomIndex = roomIndex;
            this.doorIdx = doorIdx;
            this.depth = depth;
        }
    }
private void preMatchEmptyDoors() {
    // 遍历所有非 fallback 房间
    List<RoomInstance> nonFallback = new ArrayList<>();
    for (RoomInstance r : placedRooms) {
        if (!r.isFallback) nonFallback.add(r);
    }
    for (int i = 0; i < nonFallback.size(); i++) {
        RoomInstance a = nonFallback.get(i);
        for (int idxA : a.getAvailableDoorIndices()) {
            YamlDoorUtil.DoorAnchor doorA = a.doors.get(idxA);
            Pos3 posA = DoorMatcherUtil.toWorldDoorPosition(doorA, a.anchor, a.rotationTimes);
            String facingA = TransformUtil.rotateFacing(doorA.facing, a.rotationTimes);
            // 获取门类型
            String typeA = doorA.type != null ? doorA.type : "default";
            boolean aIsFallback = typeA.equals("fallback");

            for (int j = i + 1; j < nonFallback.size(); j++) {
                RoomInstance b = nonFallback.get(j);
                for (int idxB : b.getAvailableDoorIndices()) {
                    YamlDoorUtil.DoorAnchor doorB = b.doors.get(idxB);
                    Pos3 posB = DoorMatcherUtil.toWorldDoorPosition(doorB, b.anchor, b.rotationTimes);
                    String facingB = TransformUtil.rotateFacing(doorB.facing, b.rotationTimes);
                    // 获取门类型
                    String typeB = doorB.type != null ? doorB.type : "default";
                    boolean bIsFallback = typeB.equals("fallback");

                    // 类型匹配：除非一方是 fallback，否则必须同类型
                    if (!aIsFallback && !bIsFallback && !typeA.equals(typeB)) {
                        continue;
                    }

                    if (DoorMatcherUtil.isMatching(posA, facingA, posB, facingB)) {
                        // 配对成功，标记为已使用
                        a.markDoorUsed(idxA);
                        b.markDoorUsed(idxB);
                        DebugUtil.debug(player, "预配对成功：%s 门%d ↔ %s 门%d (类型: %s/%s)",
                                a.roomType, idxA, b.roomType, idxB, typeA, typeB);
                        // 跳出内层循环，因为 b 的该门已使用
                        break;
                    }
                }
            }
        }
    }
}
}
