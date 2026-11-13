package com.him.dungeons.generator;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.*;
import com.him.dungeons.generator.ConfigApplier;
import com.him.dungeons.listener.*;
import com.him.dungeons.generator.RoomConfig;
import com.him.dungeons.generator.RoomConfigReader;
import com.him.dungeons.generator.RoomTypeRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;

import java.util.stream.Collectors;
import com.him.dungeons.HimDungeons.DungeonInstance;
import com.him.dungeons.api.hook.BossHookContext;
import com.him.dungeons.api.hook.ChestFillContext;
import com.him.dungeons.api.hook.DungeonContext;
import com.him.dungeons.api.hook.RoomContext;
import com.him.dungeons.api.hook.RoomSelectContext;
import com.him.dungeons.schematic.*;
import com.him.dungeons.api.hook.*;


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
    private final boolean testMode;
    private volatile DungeonContext hookContext;
    private final Random chestRandom = new Random();

    private World world;
    private List<RoomInstance> placedRooms;
    private RoomInstance startRoom;
    private final Random rand = new Random();
    private static final int PROGRESS_BAR_LENGTH = 200;

    public DungeonGenerator(HimDungeons plugin, String dungeonName, Player player) {
        this(plugin, dungeonName, player, false);
    }

    public DungeonGenerator(HimDungeons plugin, String dungeonName, Player player, boolean testMode) {
        this.plugin = plugin;
        this.dungeonName = dungeonName;
        this.player = player;
        this.testMode = testMode;
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

        hookContext = new DungeonContext(dungeonName, player, testMode, instanceConfig);
        reportProgress(0);
        plugin.getHookRegistry().fireBeforeCreateWorld(hookContext);

        String targetName = "dungeon_" + dungeonName
                + (testMode ? "_test_" : "_") + System.currentTimeMillis();

        // ===== 尝试池子 + 缓存 =====
        String poolName = null;
        PrecomputedDungeon pre = null;
        World newWorld = null;

        if (!testMode) {
            poolName = plugin.borrowPoolWorldName(dungeonName);
            if (poolName != null) {
                pre = plugin.takePoolPrecomputed(poolName);
                newWorld = plugin.consumePoolWorldAs(poolName, targetName);
                if (newWorld == null) {
                    DebugUtil.debug(player, "池子 %s 消费失败，回退完整生成", poolName);
                    pre = null;
                } else {
                    DebugUtil.debug(player, "借用池子 %s -> %s（缓存: %s）",
                            poolName, targetName, pre != null ? "有" : "无");
                    plugin.replenishPoolAsync();
                }
            }
        }

        if (newWorld == null) {
            try {
                newWorld = WorldUtil.createVoidWorld(targetName);
            } catch (Exception e) {
                DebugUtil.debug(player, "世界创建失败: %s", e.getMessage());
                clearProgressBar();
                if (callback != null) callback.accept(false);
                return;
            }
        }

        world = newWorld;
        if (world == null) {
            clearProgressBar();
            if (callback != null) callback.accept(false);
            return;
        }

        hookContext.world = world;
        hookContext.worldName = targetName;
        plugin.addCreatedWorld(targetName);

        try {
            WorldUtil.applyWorldSettings(world, instanceConfig);
            reportProgress(20);
            plugin.getHookRegistry().fireAfterCreateWorld(hookContext);
        } catch (Exception e) {
            DebugUtil.debug(player, "世界设置异常: %s", e.getMessage());
            clearProgressBar();
            if (callback != null) callback.accept(false);
            return;
        }

        // ===== 有缓存 → 直接粘贴 =====
        if (pre != null) {
            pastePrecomputed(pre, callback);
            return;
        }

        // ===== 无缓存 → 完整生成 =====
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean success = generateAsync();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (callback != null) callback.accept(success);
                });
            } catch (Exception e) {
                e.printStackTrace();
                DebugUtil.debug(player, "生成异常: %s", e.getMessage());
                clearProgressBar();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (world != null) WorldUtil.deleteWorldAndRelease(world);
                    if (callback != null) callback.accept(false);
                });
            }
        });
    }

    private boolean generateAsync() throws Exception {
        DebugUtil.debug(player, "开始生成地牢: %s", dungeonName);
        plugin.getHookRegistry().fireBeforeGenerate(hookContext);

        placedRooms = new ArrayList<>();
        roomPool = new HashMap<>();
RoomTypeRegistry registry = RoomTypeRegistry.load(dungeonName);
if (!registry.healthy()) {
    DebugUtil.debug(player, "地牢配置不健康: %s", registry.healthErrors());
    plugin.getLogger().warning("[地牢] " + dungeonName + " 配置不健康: "
            + registry.healthErrors() + "（生成可能失败）");
}
roomPool.put("start",    registry.byPool("start"));
roomPool.put("boss",     registry.byPool("boss"));
roomPool.put("fallback", registry.byPool("fallback"));
roomPool.put("normal",   registry.byPool("normal"));
        configApplier.prepare();
        reportProgress(22);

        startRoom = placeStartRoom();
        if (startRoom == null) { DebugUtil.debug(player, "起始房间放置失败"); throw new RuntimeException("无法放置起始房间"); }
        placedRooms.add(startRoom);
        fireRoomPlaced(startRoom);
        DebugUtil.debug(player, "起始房间放置成功，锚点 %s，门数量 %d", startRoom.anchor, startRoom.doors.size());
        reportProgress(24);

        int maxRooms = ConfigUtil.getInstanceMaxRoomAmount(instanceConfig);
        int minRooms = ConfigUtil.getInstanceMinRoomAmount(instanceConfig);
        int maxDepth = ConfigUtil.getInstanceDfsMaxDeep(instanceConfig);
        int[] bossRange = ConfigUtil.getInstanceStartToBossRoomAmount(instanceConfig);
        int pathMin = bossRange[0];
        int pathMax = bossRange.length > 1 ? bossRange[1] : bossRange[0];
        DebugUtil.debug(player, "参数: minRooms=%d, maxRooms=%d, maxDepth=%d, pathMin=%d, pathMax=%d",
                minRooms, maxRooms, maxDepth, pathMin, pathMax);

        DebugUtil.debug(player, "开始生成主路径 (start → boss) 通路...");
        boolean bossPlaced = generateMainPath(pathMin, pathMax, maxRooms);
        if (!bossPlaced) { DebugUtil.debug(player, "主路径生成失败"); throw new RuntimeException("无法生成主路径"); }
        DebugUtil.debug(player, "主路径完成，当前非fallback房间数 %d", countNonFallbackRooms());
        reportProgress(25);

        DebugUtil.debug(player, "开始从主路径空门作为根节点进行BFS分支拓展...");
        generateBranches(minRooms, maxRooms, maxDepth);
        DebugUtil.debug(player, "BFS分支拓展完成，当前非fallback房间数 %d", countNonFallbackRooms());
        reportProgress(27);

        preMatchEmptyDoors();

        DebugUtil.debug(player, "开始为所有未连接的房间空门接入fallback封堵...");
        int fallbackCount = fillAllEmptyDoorsWithFallback();
        DebugUtil.debug(player, "Fallback封堵完成，总fallback房间数 %d", fallbackCount);
        reportProgress(28);

        if (ConfigUtil.isInstanceBfsCheckEnabled(instanceConfig)) {
            DebugUtil.debug(player, "开始连通性检查 ...");
            if (!checkConnectivity()) { DebugUtil.debug(player, "连通性检查失败"); throw new RuntimeException("逻辑连通性检查失败"); }
            DebugUtil.debug(player, "连通性检查通过");
        }
        reportProgress(30);

        hookContext.placedRooms = placedRooms;
        plugin.getHookRegistry().fireAfterGenerate(hookContext);

        DebugUtil.debug(player, "开始粘贴所有房间 ...");
        reportProgress(30);
        plugin.getHookRegistry().fireBeforePaste(hookContext);
        CompletableFuture<Void> pasteFuture = pasteAllRoomsAsync();
        pasteFuture.join();
        plugin.getHookRegistry().fireAfterPaste(hookContext);
        DebugUtil.debug(player, "粘贴完成");
        reportProgress(100);

        Bukkit.getScheduler().runTask(plugin, () -> {
Location spawnLoc = startRoom.anchor.clone();
RoomConfig startCfg = RoomConfigReader.read(dungeonName, startRoom.baseName);
DebugUtil.debug(player,
        "startRoom.baseName=[%s]  startCfg=%s  startCfg.spawn=%s  anchor=%s",
        startRoom.baseName,
        startCfg == null ? "NULL_CFG" : "OK",
        startCfg == null ? "-" : startCfg.spawn,
        startRoom.anchor);
if (startCfg != null && startCfg.spawn != null) {
    Vector relativeSpawn = startCfg.spawn;
    Pos3 relativeVec = Pos3.at(relativeSpawn.getX(), relativeSpawn.getY(), relativeSpawn.getZ());
    Pos3 rotated = TransformUtil.rotatePos(relativeVec, startRoom.rotationTimes);
    spawnLoc.add(rotated.getX(), rotated.getY(), rotated.getZ());
}
spawnLoc.setX(spawnLoc.getBlockX() + 0.5);
spawnLoc.setZ(spawnLoc.getBlockZ() + 0.5);

            HimDungeons.DungeonInstance instance = new HimDungeons.DungeonInstance(
                    dungeonName, world.getName(), spawnLoc);
            instance.placedRooms = new ArrayList<>(placedRooms);
            instance.deepCloneActive = ConfigUtil.isInstanceDeepCloneInventory(instanceConfig);
            instance.hookContext = hookContext;

            plugin.dungeonInstances.put(world.getName(), instance);
            if (!testMode) plugin.dungeonNameToWorld.put(dungeonName, world.getName());
            plugin.registerConfigApplier(world.getName(), configApplier);
            plugin.registerDungeonWorld(world.getName(), dungeonName);
            plugin.setWorldPrefix(world.getName(), ConfigUtil.getInstanceMsgPrefix(instanceConfig));
            
            hookContext.spawnLocation = spawnLoc;

            plugin.getHookRegistry().fireDungeonReady(hookContext);

            if (!plugin.getSessionService().enter(player, instance)) {
                MessageUtil.sendError(player, "dungeon.enter-denied");
                plugin.forceFinishDungeon(instance);
                return;
            }
            fillChestsBatch(placedRooms);
            DebugUtil.debug(player, "玩家已进入地牢，deepClone=%s", instance.deepCloneActive);

            RoomInstance bossRoom = findBossRoom();
            if (bossRoom != null) spawnBossEntity(bossRoom, world);

            int timeSeconds = ConfigUtil.getInstanceTime(instanceConfig);
if (timeSeconds > 0) {
    List<Integer> warns = ConfigUtil.getInstanceCountdownWarn(instanceConfig);
    String timeoutAction = ConfigUtil.getInstanceTimeoutAction(instanceConfig);
    scheduleCountdown(world.getName(), timeSeconds, warns, timeoutAction);
}

            MessageUtil.sendMessage(player, "dungeon.generated",
                    "dungeon", dungeonName,
                    "rooms", countNonFallbackRooms(),
                    "fallback", fallbackCount);
        });
        NmsFastPaster.startLightRefreshTask(world);
        return true;
    }

private RoomInstance findBossRoom() {
    for (RoomInstance room : placedRooms) {
        if ("boss".equalsIgnoreCase(room.roomType)) return room;
    }
    // 兜底：旧构造可能把 baseName 当 roomType
    for (RoomInstance room : placedRooms) {
        if (room.baseName.toLowerCase().startsWith("boss")) return room;
    }
    return null;
}
/**
 * 双向连接约束检查。
 *
 * <p>语义：A.allow 单向声明 + 双向 deny。任一方的 deny 否决即失败。</p>
 */
private boolean checkConnect(YamlDoorUtil.DoorAnchor currentDoor,
                              RoomInstance currentRoom,
                              YamlDoorUtil.DoorAnchor candDoor,
                              RoomConfig candConfig) {
    if (currentDoor.connect != null
            && !currentDoor.connect.permits(
                    candConfig.baseName, candConfig.type, candDoor.id)) {
        return false;
    }
    if (candDoor.connect != null
            && !candDoor.connect.permits(
                    currentRoom.baseName, currentRoom.type, currentDoor.id)) {
        return false;
    }
    return true;
}
    private void spawnBossEntity(RoomInstance bossRoom, World world) {
RoomConfig bossCfg = RoomConfigReader.read(dungeonName, bossRoom.baseName);
Location spawnLoc = bossRoom.anchor.clone();
if (bossCfg != null && bossCfg.spawn != null) {
    Vector rel = bossCfg.spawn;
    Pos3 relVec = Pos3.at(rel.getX(), rel.getY(), rel.getZ());
    Pos3 rotated = TransformUtil.rotatePos(relVec, bossRoom.rotationTimes);
    spawnLoc.add(rotated.getX(), rotated.getY(), rotated.getZ());
}
        spawnLoc.add(0.5, 0.5, 0.5);
        DebugUtil.debug(player, "Boss 出生位置: %s", spawnLoc);

        Map<String, String> bossInfo = configApplier.selectBoss();
        String typeName = bossInfo.get("type");
        String nbt = bossInfo.get("nbt");
        DebugUtil.debug(player, "选择的 Boss 类型: %s, NBT: %s", typeName, nbt);

        LivingEntity boss = null;

        if (ConfigUtil.isInstanceUseMythicMobForBoss(instanceConfig)
                && Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
            String mmName = ConfigUtil.getInstanceMythicMobBossName(instanceConfig);
            if (mmName != null && !mmName.isEmpty()) {
                try {
                    boss = SoftDependencyUtil.spawnMythicMob(mmName, spawnLoc, 1);
                    if (boss != null) {
                        boss.setRemoveWhenFarAway(false);
                        if (boss instanceof org.bukkit.entity.Mob) ((org.bukkit.entity.Mob) boss).setPersistent(true);
                        DebugUtil.debug(player, "MythicMobs Boss 生成成功: " + mmName);
                    } else {
                        DebugUtil.debug(player, "MythicMobs Boss 返回 null，降级到原版");
                    }
                } catch (Exception e) {
                    DebugUtil.debug(player, "MythicMobs Boss 生成异常: " + e.getMessage());
                }
            } else {
                DebugUtil.debug(player, "useMythicMobForBoss=true 但 mythicMobBossName 为空，降级到原版");
            }
        }

        if (boss == null) {
            EntityType type;
            try { type = EntityType.valueOf(typeName.toUpperCase()); }
            catch (IllegalArgumentException e) {
                DebugUtil.debug(player, "无效的 Boss 类型 '%s'，使用 ZOMBIE", typeName);
                type = EntityType.ZOMBIE;
            }
            try { boss = (LivingEntity) world.spawnEntity(spawnLoc, type); }
            catch (Exception e) { DebugUtil.debug(player, "生成 Boss 实体失败: " + e.getMessage()); return; }
            if (boss == null) { DebugUtil.debug(player, "world.spawnEntity 返回 null，Boss 生成失败"); return; }

            double maxHealth = 10000.0;
            if (nbt != null && !nbt.isEmpty()) {
                try {
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("Health:\\s*([0-9.]+)f?");
                    java.util.regex.Matcher m = p.matcher(nbt);
                    if (m.find()) maxHealth = Double.parseDouble(m.group(1));
                } catch (Exception ignored) {}
            }
            boss.setMaxHealth(maxHealth);
            boss.setHealth(maxHealth);
            boss.setRemoveWhenFarAway(false);
            if (boss instanceof org.bukkit.entity.Mob) ((org.bukkit.entity.Mob) boss).setPersistent(true);
            DebugUtil.debug(player, "Boss 血量设置为 %.1f", maxHealth);
        }

        BossHookContext bhc = new BossHookContext(hookContext, bossRoom, spawnLoc, boss);
        plugin.getHookRegistry().fireBossSpawn(bhc);
        if (bhc.boss == null) {
            DebugUtil.debug(player, "Boss 生成被钩子否决");
            if (boss.isValid()) boss.remove();
            return;
        }
        boss = bhc.boss;

        HimDungeons.BossData data = new HimDungeons.BossData(boss, configApplier, dungeonName,
                world.getName(), spawnLoc, bossRoom);
        plugin.registerBoss(world.getName(), data);
        if (plugin.getBossBarManager() != null) {
            plugin.getBossBarManager().create(world.getName());
            plugin.getBossBarManager().setBossName(world.getName(),
                    typeName == null || typeName.isEmpty() ? "Boss" : typeName);
        }
        if (plugin.getBossPhaseManager() != null) {
            plugin.getBossPhaseManager().initBoss(boss);
        }
DebugUtil.debug(player, "Boss 已注册，当前 dungeonBosses 包含: %s", plugin.dungeonBosses.keySet());
    }

    private int countNonFallbackRooms() {
        int count = 0;
        for (RoomInstance room : placedRooms) {
            if (!room.isFallback) count++;
        }
        return count;
    }

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
                fireRoomPlaced(next);
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
                    fireRoomPlaced(boss);
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

            RoomInstance newRoom = tryExtend(currentRoom, node.doorIdx, false, false, false);
            if (newRoom != null && !newRoom.isFallback) {
                placedRooms.add(newRoom);
                fireRoomPlaced(newRoom);
                int newIdx = placedRooms.size() - 1;
                for (int idx : newRoom.getAvailableDoorIndices()) {
                    queue.offer(new BranchNode(newIdx, idx, node.depth + 1));
                }
                DebugUtil.debug(player, "BFS拓展成功，新普通房间 %s，当前非fallback房间数 %d",
                        newRoom.roomType, countNonFallbackRooms());
            } else {
                RoomInstance fallbackRoom = tryPlaceFallback(currentRoom, node.doorIdx);
                if (fallbackRoom != null) {
                    placedRooms.add(fallbackRoom);
                    fireRoomPlaced(fallbackRoom);
                    DebugUtil.debug(player, "BFS拓展失败，成功放置fallback封堵门 %d", node.doorIdx);
                } else {
                    DebugUtil.debug(player, "拓展失败且无法放置fallback，门 %d", node.doorIdx);
                }
            }
        }
    }

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

    private int fillAllEmptyDoorsWithFallback() {
        int fallbackCount = 0;
        Set<Pos3> usedAnchors = new HashSet<>();

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
            if (!room.getAvailableDoorIndices().contains(doorIdx)) continue;

            RoomInstance fb = tryPlaceFallback(room, doorIdx);
            if (fb != null) {
                Pos3 anchor = Pos3.at(fb.anchor.getBlockX(), fb.anchor.getBlockY(), fb.anchor.getBlockZ());
                if (usedAnchors.contains(anchor)) {
                    DebugUtil.debug(player, "空门 %d 的锚点 %s 已被占用，跳过（门保留）", doorIdx, anchor);
                    continue;
                }
                usedAnchors.add(anchor);
                placedRooms.add(fb);
                fireRoomPlaced(fb);
                fallbackCount++;
                DebugUtil.debug(player, "空门 %d fallback封堵成功，锚点 %s，当前fallback总数 %d",
                        doorIdx, anchor, fallbackCount);
            } else {
                DebugUtil.debug(player, "空门 %d fallback封堵失败，但门保留，等待后续重试", doorIdx);
            }
        }
        return fallbackCount;
    }

private RoomInstance tryExtend(RoomInstance currentRoom, int doorIdx,
                               boolean forceBoss, boolean allowFallback, boolean forceFallbackIgnoreCheck) {

    if (doorIdx == -1) {
        List<Integer> avail = currentRoom.getAvailableDoorIndices();
        if (avail.isEmpty()) return null;
        Collections.shuffle(avail, rand);
        avail.sort((i1, i2) -> Integer.compare(
                currentRoom.doors.get(i2).weight,
                currentRoom.doors.get(i1).weight));
        for (int idx : avail) {
            RoomInstance result = tryExtend(currentRoom, idx, forceBoss, allowFallback, forceFallbackIgnoreCheck);
            if (result != null) return result;
        }
        return null;
    }

    YamlDoorUtil.DoorAnchor currentMeta = currentRoom.doors.get(doorIdx);
    Pos3 currentDoorWorld = DoorMatcherUtil.toWorldDoorPosition(
            currentMeta, currentRoom.anchor, currentRoom.rotationTimes);
    String currentFacing = TransformUtil.rotateFacing(currentMeta.facing, currentRoom.rotationTimes);
    String currentDoorType = currentMeta.type != null ? currentMeta.type : "default";

    // ============ fallback 分支 ============
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
            if (schem == null) continue;

            RoomConfig fbConfig = RoomConfigReader.read(dungeonName, fbBase);
            if (fbConfig == null || fbConfig.doors.size() != 1) continue;

            Pos3 size = WorldEditUtil.getClipboardSizeAndRelease(schem);
            if (size == null) continue;

            YamlDoorUtil.DoorAnchor candDoor = fbConfig.doors.get(0);

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
                        candidateAnchor.getZ() + rotatedCandPos.getZ());
                if (!DoorMatcherUtil.isMatching(currentDoorWorld, currentFacing, candWorldPos, candFacing)) {
                    continue;
                }

                if (!checkConnect(currentMeta, currentRoom, candDoor, fbConfig)) continue;

                AABBUtil.AABB newAabb = AABBUtil.buildRotated(candidateAnchor, size, rot);
                Pos3 rotatedSize = TransformUtil.rotatePos(size, rot);
                int w = Math.abs(rotatedSize.getX());
                int h = Math.abs(rotatedSize.getY());
                int d = Math.abs(rotatedSize.getZ());
                Pos3 absSize = Pos3.at(w, h, d);

                RoomInstance newRoom = new RoomInstance(
                        "fallback", fbConfig.type, fbBase, candidateAnchor, rot,
                        absSize, newAabb, fbConfig.doors, true);
                currentRoom.markDoorUsed(doorIdx);
                newRoom.markDoorUsed(0);
                configApplier.recordRoomUsed(fbBase);
                return newRoom;
            }
        }
        DebugUtil.debug(player, "无法找到合适的 fallback 封堵门 %d，但保留门未使用", doorIdx);
        return null;
    }

    // ============ 普通 / boss 分支 ============
    List<ResourceUtil.RoomEntry> pool = forceBoss ? roomPool.get("boss") : roomPool.get("normal");
    if (pool.isEmpty()) return null;

    List<String> allBaseNames = pool.stream().map(e -> e.baseName).collect(Collectors.toList());
    List<String> filteredBaseNames = configApplier.filterRoomTemplates(forceBoss ? "boss" : "normal", allBaseNames);
    if (filteredBaseNames != null) {
        Set<String> allowed = new HashSet<>(filteredBaseNames);
        pool = pool.stream().filter(e -> allowed.contains(e.baseName)).collect(Collectors.toList());
    }
    if (pool.isEmpty()) return null;

    for (int attempt = 0; attempt < 20; attempt++) {
        ResourceUtil.RoomEntry selected = selectByWeight(pool);
        if (selected == null) continue;
        String candidateBaseName = selected.baseName;

        RoomSelectContext rsc = new RoomSelectContext(hookContext, currentRoom, doorIdx,
                forceBoss ? "boss" : "normal", candidateBaseName);
        plugin.getHookRegistry().fireRoomSelect(rsc);
        if (rsc.rejected) continue;
        candidateBaseName = rsc.baseName;
        if (candidateBaseName == null || candidateBaseName.isEmpty()) continue;

        if (!configApplier.isRoomReuseAllowed() && usedRoomBaseNames.contains(candidateBaseName)) continue;

        File schematic = ResourceUtil.getRoomSchematicFlat(dungeonName, candidateBaseName);
        if (schematic == null) continue;

        RoomConfig candidateConfig = RoomConfigReader.read(dungeonName, candidateBaseName);
        if (candidateConfig == null) continue;
        List<YamlDoorUtil.DoorAnchor> candidateDoors = candidateConfig.doors;
        if (candidateDoors.isEmpty()) continue;
        if (!forceBoss && candidateDoors.size() < 2) continue;

        Pos3 size = WorldEditUtil.getClipboardSizeAndRelease(schematic);
        if (size == null) continue;

        for (int candIdx = 0; candIdx < candidateDoors.size(); candIdx++) {
            YamlDoorUtil.DoorAnchor candDoor = candidateDoors.get(candIdx);
            String candDoorType = candDoor.type != null ? candDoor.type : "default";

            boolean currentIsFallback = currentRoom.isFallback || currentDoorType.equals("fallback");
            boolean candIsFallback = "fallback".equals(candidateConfig.type) || candDoorType.equals("fallback");
            if (!currentIsFallback && !candIsFallback && !currentDoorType.equals(candDoorType)) continue;

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
                        candidateAnchor.getZ() + rotatedCandPos.getZ());
                String candFacing = TransformUtil.rotateFacing(candDoor.facing, rot);
                if (!DoorMatcherUtil.isMatching(currentDoorWorld, currentFacing, candWorldPos, candFacing)) continue;

                if (!checkConnect(currentMeta, currentRoom, candDoor, candidateConfig)) continue;

                String roomAId = currentRoom.roomType + "@" + currentRoom.anchor;
                String roomBId = candidateBaseName + "@" + candidateAnchor;
                if (!configApplier.canPairDoors(roomAId, doorIdx, roomBId, candIdx)) continue;

                AABBUtil.AABB newAabb = AABBUtil.buildRotated(candidateAnchor, size, rot);
                Pos3 rotatedSize = TransformUtil.rotatePos(size, rot);
                int w = Math.abs(rotatedSize.getX());
                int h = Math.abs(rotatedSize.getY());
                int d = Math.abs(rotatedSize.getZ());
                Pos3 absSize = Pos3.at(w, h, d);

                if (ConfigUtil.isInstanceAabbEnabled(instanceConfig)) {
                    boolean overlap = false;
                    for (RoomInstance placed : placedRooms) {
                        if (placed.isFallback) continue;
                        if (AABBUtil.AABB.overlaps(newAabb, placed.aabb)) { overlap = true; break; }
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

                boolean isFallback = "fallback".equals(candidateConfig.type);
                String roomType = isFallback ? "fallback" : (forceBoss ? "boss" : "normal");
                RoomInstance newRoom = new RoomInstance(
                        roomType, candidateConfig.type, candidateBaseName,
                        candidateAnchor, rot, absSize, newAabb, candidateDoors, isFallback);
                currentRoom.markDoorUsed(doorIdx);
                newRoom.markDoorUsed(candIdx);
                configApplier.recordRoomUsed(candidateBaseName);
                if (!configApplier.isRoomReuseAllowed()) usedRoomBaseNames.add(candidateBaseName);
                return newRoom;
            }
        }
    }
    return null;
}

    private int computeRequiredRotation(String currentFacing, String candOriginalFacing) {
        String opposite = TransformUtil.oppositeFacing(currentFacing);
        if (opposite == null) return -1;

        for (int rot = 0; rot < 4; rot++) {
            String rotated = TransformUtil.rotateFacing(candOriginalFacing, rot);
            if (rotated.equals(opposite)) {
                return rot;
            }
        }
        return -1;
    }

    private RoomInstance tryPlaceFallback(RoomInstance currentRoom, int doorIdx) {
        return tryExtend(currentRoom, doorIdx, false, true, true);
    }

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
        DebugUtil.debug(player, "没有 inStart=true 的房间");
        return null;
    }
    ResourceUtil.RoomEntry selected = selectByWeight(starts);
    String baseName = selected.baseName;

    File schematic = ResourceUtil.getRoomSchematicFlat(dungeonName, baseName);
    if (schematic == null) {
        DebugUtil.debug(player, "start 房间文件缺失: " + baseName);
        return null;
    }
    Pos3 size = WorldEditUtil.getClipboardSizeAndRelease(schematic);
    if (size == null) return null;

    RoomConfig config = RoomConfigReader.read(dungeonName, baseName);
    if (config.doors.isEmpty()) {
        DebugUtil.debug(player, "start 房间门配置为空: " + baseName);
        return null;
    }

    Location anchor = new Location(world, 0, 64, 0);
    int rotation = 0;
    Pos3 rotatedSize = TransformUtil.rotatePos(size, rotation);
    AABBUtil.AABB aabb = AABBUtil.buildRotated(anchor, size, rotation);
    return new RoomInstance("start", config.type, baseName, anchor, rotation,
            rotatedSize, aabb, config.doors ,false);
}

    private CompletableFuture<Void> pasteAllRoomsAsync() {
        if (placedRooms == null || placedRooms.isEmpty())
            return CompletableFuture.completedFuture(null);

        List<AsyncPaster.BatchRequest> requests = new ArrayList<>(placedRooms.size());
        int format = ConfigUtil.getSchematicReadFormat();
        for (RoomInstance room : placedRooms) {
            File schematic = ResourceUtil.getRoomSchematicFlat(dungeonName, room.baseName);
            if (schematic == null) {
                DebugUtil.debug(player, "房间 %s 的 schematic 缺失，跳过粘贴", room.baseName);
                continue;
            }
            requests.add(new AsyncPaster.BatchRequest(
                    room.anchor, schematic, room.rotationTimes, format, room));
        }
        if (requests.isEmpty()) return CompletableFuture.completedFuture(null);

        CompletableFuture<Void> future = new CompletableFuture<>();
        int totalReq = requests.size();
        java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger(0);

AsyncPaster.pasteBatchAsync(world, requests, (req, schem) -> {
    RoomInstance room = (RoomInstance) req.tag;
    if (schem != null) {
        configApplier.storeForbiddenBlocksFromSchem(room, schem);
    }
    // fillChests(room);   ← 删除（异步线程不安全）
    plugin.getHookRegistry().fireRoomPasted(new RoomContext(hookContext, room));
    int d = done.incrementAndGet();
    int p = 30 + (int) ((double) d / totalReq * 70);
    reportProgress(p);
}, success -> {
            if (success) future.complete(null);
            else future.completeExceptionally(new RuntimeException("批量粘贴失败"));
        });

        return future;
    }

    /** 批量填箱子：按 chunk 分组，减少 getBlockAt/getState 开销 */
    private void fillChestsBatch(List<RoomInstance> rooms) {
        // 1. 收集所有 (chunkKey → List<ChestFill>)
        Map<Long, List<ChestFillData>> byChunk = new HashMap<>();
        for (RoomInstance room : rooms) {
            // ★ 修改点：使用 RoomConfigReader 读取合并后的配置
            com.him.dungeons.generator.RoomConfig config = com.him.dungeons.generator.RoomConfigReader.read(dungeonName, room.baseName);
            if (config == null || config.chests.isEmpty()) continue;
            
            List<Map<String, Object>> chests = config.chests;
            for (Map<String, Object> entry : chests) {
                @SuppressWarnings("unchecked")
                List<Integer> pos = (List<Integer>) entry.get("pos");
                if (pos == null || pos.size() < 3) continue;
                String lootStr = (String) entry.get("loot");
                if (lootStr == null) continue;

                String[] parts = lootStr.split(" ");
                if (parts.length < 2) continue;
                int chance;
                try { chance = Integer.parseInt(parts[0]); } catch (Exception e) { continue; }
                Material mat = Material.getMaterial(parts[1].toUpperCase());
                if (mat == null) continue;
                int amount = (parts.length >= 3) ? Integer.parseInt(parts[2]) : 1;

                Pos3 relPos = Pos3.at(pos.get(0), pos.get(1), pos.get(2));
                Pos3 rotated = TransformUtil.rotatePos(relPos, room.rotationTimes);
                int wx = room.anchor.getBlockX() + rotated.getX();
                int wy = room.anchor.getBlockY() + rotated.getY();
                int wz = room.anchor.getBlockZ() + rotated.getZ();

                long key = ((long) (wx >> 4) << 32) | ((wz >> 4) & 0xFFFFFFFFL);
                byChunk.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new ChestFillData(room, wx, wy, wz, mat, amount, chance));
            }
        }

        // 2. 按 chunk 批量处理
        for (Map.Entry<Long, List<ChestFillData>> entry : byChunk.entrySet()) {
            long key = entry.getKey();
            int cx = (int) (key >> 32);
            int cz = (int) (long) key;
            org.bukkit.Chunk chunk = world.getChunkAt(cx, cz);   // 已预加载，O(1)

            // 一次性拿 chunk 内所有 tile entity
            Map<Location, Chest> chestsByLoc = new HashMap<>();
            for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
                if (state instanceof Chest c) {
                    chestsByLoc.put(c.getLocation(), c);
                }
            }

            for (ChestFillData data : entry.getValue()) {
                Location loc = new Location(world, data.wx, data.wy, data.wz);
                Chest chest = chestsByLoc.get(loc);
                if (chest == null) continue;

                ChestFillContext cfc = new ChestFillContext(
                        hookContext, data.room, loc, data.item, data.amount, data.chance);
                plugin.getHookRegistry().fireChestFill(cfc);
                if (cfc.cancelled) continue;
                if (cfc.chance < 100 && (cfc.chance <= 0 || chestRandom.nextInt(100) >= cfc.chance)) continue;

                Inventory inv = chest.getBlockInventory();
                if (inv.firstEmpty() != -1) inv.addItem(new ItemStack(cfc.item, cfc.amount));
            }
        }
    }

    private static class ChestFillData {
        final RoomInstance room;
        final int wx, wy, wz;
        final Material item;
        final int amount, chance;
        ChestFillData(RoomInstance room, int wx, int wy, int wz,
                      Material item, int amount, int chance) {
            this.room = room; this.wx = wx; this.wy = wy; this.wz = wz;
            this.item = item; this.amount = amount; this.chance = chance;
        }
    }
    private List<String> resolveLootEntries(String lootStr) {
        if (lootStr == null || lootStr.isEmpty()) return Collections.emptyList();
        if (!lootStr.startsWith("@")) return Collections.singletonList(lootStr);

        String[] refParts = lootStr.substring(1).split("\\s+");
        if (refParts.length == 0) return Collections.emptyList();
        String tableName = refParts[0];
        int count = 1;
        if (refParts.length >= 2) {
            try { count = Math.max(1, Integer.parseInt(refParts[1])); } catch (Exception ignored) {}
        }

        List<String> entries = ConfigUtil.getLootTableEntries(instanceConfig, tableName);
        if (entries.isEmpty()) {
            DebugUtil.debug(player, "lootTable '%s' 不存在或为空", tableName);
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(entries.get(rand.nextInt(entries.size())));
        }
        return result;
    }
    private static class PathNode {
        RoomInstance room;
        Set<Integer> tried;
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
    List<RoomInstance> nonFallback = new ArrayList<>();
    for (RoomInstance r : placedRooms) if (!r.isFallback) nonFallback.add(r);

    for (int i = 0; i < nonFallback.size(); i++) {
        RoomInstance a = nonFallback.get(i);
        for (int idxA : a.getAvailableDoorIndices()) {
            YamlDoorUtil.DoorAnchor doorA = a.doors.get(idxA);
            Pos3 posA = DoorMatcherUtil.toWorldDoorPosition(doorA, a.anchor, a.rotationTimes);
            String facingA = TransformUtil.rotateFacing(doorA.facing, a.rotationTimes);
            String typeA = doorA.type != null ? doorA.type : "default";
            boolean aIsFallback = typeA.equals("fallback");

            for (int j = i + 1; j < nonFallback.size(); j++) {
                RoomInstance b = nonFallback.get(j);
                for (int idxB : b.getAvailableDoorIndices()) {
                    YamlDoorUtil.DoorAnchor doorB = b.doors.get(idxB);
                    Pos3 posB = DoorMatcherUtil.toWorldDoorPosition(doorB, b.anchor, b.rotationTimes);
                    String facingB = TransformUtil.rotateFacing(doorB.facing, b.rotationTimes);
                    String typeB = doorB.type != null ? doorB.type : "default";
                    boolean bIsFallback = typeB.equals("fallback");

                    if (!aIsFallback && !bIsFallback && !typeA.equals(typeB)) continue;
                    if (!DoorMatcherUtil.isMatching(posA, facingA, posB, facingB)) continue;

                    RoomConfig bConfig = RoomConfigReader.read(dungeonName, b.baseName);
                    if (!checkConnect(doorA, a, doorB, bConfig)) continue;

                    a.markDoorUsed(idxA);
                    b.markDoorUsed(idxB);
                    DebugUtil.debug(player, "预配对成功：%s 门%d ↔ %s 门%d (类型: %s/%s)",
                            a.roomType, idxA, b.roomType, idxB, typeA, typeB);
                    break;
                }
            }
        }
    }
}
/**
 * 倒计时警告 + 超时处理。
 * timeoutAction = forceEnd  ：到点强制结束
 * timeoutAction = extend    ：到点加时 300 秒，再按原 warn 列表倒计时一次
 */
private void scheduleCountdown(String worldName, int totalSeconds,
                               List<Integer> warns, String timeoutAction) {
    Set<Integer> warnSet = new HashSet<>(warns);
    for (int remaining = totalSeconds; remaining >= 0; remaining--) {
        final int r = remaining;
        if (warnSet.contains(r) && r > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                DungeonInstance inst = plugin.dungeonInstances.get(worldName);
                if (inst == null || inst.isFinished) return;
                for (UUID uuid : inst.players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        int minutes = r / 60;
                        int seconds = r % 60;
                        String timeStr = minutes > 0
                                ? minutes + ":" + String.format("%02d", seconds)
                                : seconds + "s";
                        MessageUtil.sendMessage(p, "session.countdown", "time", timeStr);
                    }
                }
            }, (long) (totalSeconds - remaining) * 20L);
        }
    }
    // 到点
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        DungeonInstance inst = plugin.dungeonInstances.get(worldName);
        if (inst == null || inst.isFinished) return;
        if ("extend".equals(timeoutAction)) {
            // 加时 5 分钟，重置倒计时
            for (UUID uuid : inst.players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) MessageUtil.sendMessage(p, "session.timeout-extended");
            }
            scheduleCountdown(worldName, 300, warns, "forceEnd");
        } else {
            plugin.forceFinishDungeon(inst);
        }
    }, totalSeconds * 20L);
}
    private void fireRoomPlaced(RoomInstance room) {
        plugin.getHookRegistry().fireRoomPlaced(new RoomContext(hookContext, room));
    }
    private void reportProgress(int percent) {
        if (player == null) return;
        int p = Math.max(0, Math.min(100, percent));
        Runnable task = () -> {
            if (!player.isOnline()) return;
            // 把百分比映射到实际格数，100% = 全部格子绿色
            int filled = (int) Math.round(p * PROGRESS_BAR_LENGTH / 100.0);
            StringBuilder bar = new StringBuilder(PROGRESS_BAR_LENGTH * 4);
            for (int i = 0; i < PROGRESS_BAR_LENGTH; i++) {
                bar.append(i < filled ? "§a|" : "§7|");
            }
            String text = bar + " §e" + p + "%";
            player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(text));
        };
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    /** 生成完成后延迟清空 ActionBar */
    private void clearProgressBar() {
        if (player == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(""));
        }, 40L);
    }
    /**
     * 池子预计算：跑完选房 + 门配对 + schem 坐标旋转，但不粘贴到世界。
     *
     * <p>要求：调用前 world 已创建（因为 RoomInstance.anchor 依赖 world）。</p>
     * <p>线程：整个方法必须在主线程调用（内部会调度异步计算），完成后在主线程回调。</p>
     *
     * @param callback 成功返回 PrecomputedDungeon，失败返回 null
     */
    /**
     * 池子预计算：跑完选房 + 门配对 + 方块坐标旋转，但不粘贴。
     *
     * @param existingWorld 已加载的池子世界（由调用方负责创建和卸载）
     * @param callback      成功返回 PrecomputedDungeon，失败返回 null（主线程回调）
     */
    /**
     * 池子预计算：跑完选房 + 门配对 + 方块坐标旋转，但不加载任何世界。
     * 纯数学计算，Location 的 world 字段留 null，后续 relocateRooms 会绑定真实世界。
     */
    public void computeOnly(Consumer<PrecomputedDungeon> callback) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> computeOnly(callback));
            return;
        }

        // 用主世界作为 placeStartRoom 的 world 占位（避免 NPE）
        // 但 anchor 的 world 字段会在 relocateRooms 里被替换，不影响最终结果
        this.world = Bukkit.getWorlds().get(0);
        hookContext = new DungeonContext(dungeonName, player, false, instanceConfig);
        hookContext.world = world;
        hookContext.worldName = "_precompute_";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                placedRooms = new ArrayList<>();
                roomPool = new HashMap<>();
                List<ResourceUtil.RoomEntry> allRooms = ResourceUtil.scanRooms(dungeonName);
                roomPool.put("start", allRooms.stream().filter(e -> e.isStart).collect(Collectors.toList()));
                roomPool.put("boss", allRooms.stream().filter(e -> e.isBoss).collect(Collectors.toList()));
                roomPool.put("fallback", allRooms.stream().filter(e -> e.isFallback).collect(Collectors.toList()));
                roomPool.put("normal", allRooms.stream().filter(e -> !e.isStart && !e.isBoss && !e.isFallback).collect(Collectors.toList()));
                configApplier.prepare();

                startRoom = placeStartRoom();
                if (startRoom == null) throw new RuntimeException("无法放置起始房间");
                placedRooms.add(startRoom);

                int maxRooms = ConfigUtil.getInstanceMaxRoomAmount(instanceConfig);
                int minRooms = ConfigUtil.getInstanceMinRoomAmount(instanceConfig);
                int maxDepth = ConfigUtil.getInstanceDfsMaxDeep(instanceConfig);
                int[] bossRange = ConfigUtil.getInstanceStartToBossRoomAmount(instanceConfig);

                if (!generateMainPath(bossRange[0], bossRange[1], maxRooms))
                    throw new RuntimeException("主路径失败");
                generateBranches(minRooms, maxRooms, maxDepth);
                preMatchEmptyDoors();
                fillAllEmptyDoorsWithFallback();

                // 构建 requests（anchor 的 world 是主世界，但没关系，只是占位）
                List<AsyncPaster.BatchRequest> requests = new ArrayList<>();
                int format = ConfigUtil.getSchematicReadFormat();
                for (RoomInstance room : placedRooms) {
                    File schematic = ResourceUtil.getRoomSchematicFlat(dungeonName, room.baseName);
                    if (schematic == null) continue;
                    requests.add(new AsyncPaster.BatchRequest(
                            room.anchor, schematic, room.rotationTimes, format, room));
                }

Location spawnLoc = startRoom.anchor.clone();
RoomConfig startCfg = RoomConfigReader.read(dungeonName, startRoom.baseName);
if (startCfg != null && startCfg.spawn != null) {
    Vector rel = startCfg.spawn;
    Pos3 rotated = TransformUtil.rotatePos(
            Pos3.at(rel.getX(), rel.getY(), rel.getZ()), startRoom.rotationTimes);
    spawnLoc.add(rotated.getX(), rotated.getY(), rotated.getZ());
}
                spawnLoc.setX(spawnLoc.getBlockX() + 0.5);
                spawnLoc.setZ(spawnLoc.getBlockZ() + 0.5);
                final Location finalSpawn = spawnLoc;
                final List<RoomInstance> finalRooms = new ArrayList<>(placedRooms);

                List<AsyncPaster.BlockEntityPlacement> allBlockEntities = new ArrayList<>();
                AsyncPaster.computeBatchAsync(requests,
                    (req, schem) -> {
                        if (schem == null || schem.blockEntities == null) return;
                        for (NbtSchematic.BlockEntityData be : schem.blockEntities) {
                            int[] pos = AsyncPaster.rotate90Y(be.x, be.y, be.z, req.rotationTimes);
                            allBlockEntities.add(new AsyncPaster.BlockEntityPlacement(
                                    req.anchor.getBlockX() + pos[0],
                                    req.anchor.getBlockY() + pos[1],
                                    req.anchor.getBlockZ() + pos[2],
                                    be.id, be.nbtData));
                        }
                    },
                    blocks -> {
                        PrecomputedDungeon pre = new PrecomputedDungeon(
                                dungeonName, blocks, allBlockEntities, finalRooms, finalSpawn);
                        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(pre));
                    });
            } catch (Exception e) {
                plugin.getLogger().warning("[预计算] " + dungeonName + " 失败: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            }
        });
    }
    // ==================== 缓存粘贴 ====================

    private void pastePrecomputed(PrecomputedDungeon pre, Consumer<Boolean> callback) {
        long t0 = System.currentTimeMillis();
        try {
            if (!NmsFastPaster.isAvailable()) {
                DebugUtil.debug(player, "NMS 不可用，回退完整生成");
                fallbackFullGenerate(callback);
                return;
            }

            // 重定位 rooms 到新世界
            List<RoomInstance> relocated = relocateRooms(pre.placedRooms, world);
            Location newSpawn = new Location(world,
                    pre.spawnLocation.getX(), pre.spawnLocation.getY(), pre.spawnLocation.getZ(),
                    pre.spawnLocation.getYaw(), pre.spawnLocation.getPitch());

            placedRooms = relocated;
            startRoom = relocated.isEmpty() ? null : relocated.get(0);

            DebugUtil.debug(player, "缓存粘贴开始，方块数 %d，方块实体 %d，房间数 %d",
                    pre.blocks.size(),
                    pre.blockEntities == null ? 0 : pre.blockEntities.size(),
                    relocated.size());
            reportProgress(30);
            DebugUtil.debug(player, "[耗时] 准备阶段 %d ms", System.currentTimeMillis() - t0);

NmsFastPaster.pasteBatch(world, pre.blocks, ok -> {
    if (!ok) {
        DebugUtil.debug(player, "缓存粘贴失败");
        if (callback != null) callback.accept(false);
        return;
    }
    if (pre.blockEntities != null && !pre.blockEntities.isEmpty()) {
        AsyncPaster.applyBlockEntities(world, pre.blockEntities);
    }

    // ★ 检查是否需要重载世界来修复光照
    if (world.hasMetadata("dungeon_need_reload_for_light")) {
        world.removeMetadata("dungeon_need_reload_for_light", plugin);
        reloadWorldForLightFix(pre, callback);   // 新方法，见下
        return;
    }

    reportProgress(100);
    Bukkit.getScheduler().runTask(plugin, () ->
            finishFromPrecomputed(relocateRooms(pre.placedRooms, world),
                    newSpawnLocation(pre, world), callback, world));
});
        } catch (Exception e) {
            DebugUtil.debug(player, "缓存粘贴异常: %s，回退完整生成", e.getMessage());
            fallbackFullGenerate(callback);
        }
    }
/** 根据预计算出生点数值，重新绑定到指定世界 */
private static Location newSpawnLocation(PrecomputedDungeon pre, World targetWorld) {
    Location src = pre.spawnLocation;
    return new Location(targetWorld,
            src.getX(), src.getY(), src.getZ(),
            src.getYaw(), src.getPitch());
}
private void reloadWorldForLightFix(PrecomputedDungeon pre, Consumer<Boolean> callback) {
    String worldName = world.getName();
    DebugUtil.debug(player, "光照修复需要重载世界: %s", worldName);

    // 1. 保存并卸载
    world.save();
    if (!Bukkit.unloadWorld(world, true)) {
        DebugUtil.debug(player, "世界卸载失败，放弃重载");
        if (callback != null) callback.accept(false);
        return;
    }

    // 2. 用现有工具重新加载
    World reloaded = WorldUtil.loadFromFolder(worldName);
    if (reloaded == null) {
        DebugUtil.debug(player, "世界重载失败");
        if (callback != null) callback.accept(false);
        return;
    }

    // 3. 重新绑定房间到新世界
    List<RoomInstance> relocated = relocateRooms(pre.placedRooms, reloaded);
    Location newSpawn = new Location(reloaded,
            pre.spawnLocation.getX(), pre.spawnLocation.getY(), pre.spawnLocation.getZ(),
            pre.spawnLocation.getYaw(), pre.spawnLocation.getPitch());

    // 4. 重载后的世界光照数据由服务端在 chunk 加载时自然重建
    DebugUtil.debug(player, "世界重载完成，进入 finishFromPrecomputed");
    finishFromPrecomputed(relocated, newSpawn, callback, reloaded);
}
    private void fallbackFullGenerate(Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean success = generateAsync();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (callback != null) callback.accept(success);
                });
            } catch (Exception e) {
                e.printStackTrace();
                DebugUtil.debug(player, "回退生成异常: %s", e.getMessage());
                clearProgressBar();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (world != null) WorldUtil.deleteWorldAndRelease(world);
                    if (callback != null) callback.accept(false);
                });
            }
        });
    }

private List<RoomInstance> relocateRooms(List<RoomInstance> original, World newWorld) {
    List<RoomInstance> result = new ArrayList<>(original.size());
    for (RoomInstance r : original) {
        Location newAnchor = new Location(newWorld,
                r.anchor.getX(), r.anchor.getY(), r.anchor.getZ(),
                r.anchor.getYaw(), r.anchor.getPitch());
        RoomInstance copy = new RoomInstance(
                r.roomType, r.type, r.baseName, newAnchor, r.rotationTimes,
                r.size, r.aabb, r.doors, r.isFallback);
        for (int idx : r.usedDoorIndices) copy.markDoorUsed(idx);
        result.add(copy);
    }
    return result;
}

    private void finishFromPrecomputed(List<RoomInstance> rooms, Location spawn,
                                        Consumer<Boolean> callback, World targetWorld) {
        this.world = targetWorld;
        long a = System.currentTimeMillis();
        try {
            // 注册新的 applier（预计算阶段的 applier 状态已失效）
            plugin.registerConfigApplier(targetWorld.getName(), configApplier);

            HimDungeons.DungeonInstance instance = new HimDungeons.DungeonInstance(
                    dungeonName, targetWorld.getName(), spawn);
            instance.placedRooms = new ArrayList<>(rooms);
            instance.deepCloneActive = ConfigUtil.isInstanceDeepCloneInventory(instanceConfig);
            instance.hookContext = hookContext;

            plugin.dungeonInstances.put(world.getName(), instance);
            if (!testMode) plugin.dungeonNameToWorld.put(dungeonName, world.getName());
            plugin.registerDungeonWorld(world.getName(), dungeonName);
            plugin.setWorldPrefix(world.getName(), ConfigUtil.getInstanceMsgPrefix(instanceConfig));
            hookContext.spawnLocation = spawn;

            plugin.getHookRegistry().fireDungeonReady(hookContext);
        long b = System.currentTimeMillis();
        DebugUtil.debug(player, "[耗时] 注册实例 %d ms", b - a);
            if (player != null && !plugin.getSessionService().enter(player, instance)) {
                MessageUtil.sendError(player, "dungeon.enter-denied");
                plugin.forceFinishDungeon(instance);
                if (callback != null) callback.accept(false);
                return;
            }
        long c = System.currentTimeMillis();
        DebugUtil.debug(player, "[耗时] 玩家进入 %d ms", c - b);
            // 填箱子
            fillChestsBatch(rooms);
        long d = System.currentTimeMillis();
        DebugUtil.debug(player, "[耗时] 填箱子 %d ms（%d 房间）", d - c, rooms.size());
            // Boss
            RoomInstance bossRoom = null;
            for (RoomInstance r : rooms) {
                if (r.roomType.toLowerCase().startsWith("boss")) { bossRoom = r; break; }
            }
            if (bossRoom != null) spawnBossEntity(bossRoom, world);
        long e = System.currentTimeMillis();
        DebugUtil.debug(player, "[耗时] 生成 Boss %d ms", e - d);
            // 倒计时
            int timeSeconds = ConfigUtil.getInstanceTime(instanceConfig);
            if (timeSeconds > 0) {
                List<Integer> warns = ConfigUtil.getInstanceCountdownWarn(instanceConfig);
                String action = ConfigUtil.getInstanceTimeoutAction(instanceConfig);
                scheduleCountdown(world.getName(), timeSeconds, warns, action);
            }

if (player != null) {
    int nonFallback = 0, fallback = 0;
    for (RoomInstance r : rooms) {
        if (r.isFallback) fallback++;
        else nonFallback++;
    }
    MessageUtil.sendMessage(player, "dungeon.generated",
            "dungeon", dungeonName, "rooms", nonFallback, "fallback", fallback);
}
            if (callback != null) callback.accept(true);
        } catch (Exception e) {
            DebugUtil.debug(player, "finishFromPrecomputed 异常: %s", e.getMessage());
            e.printStackTrace();
            if (world != null) WorldUtil.deleteWorldAndRelease(world);
            if (callback != null) callback.accept(false);
        }
    }
}