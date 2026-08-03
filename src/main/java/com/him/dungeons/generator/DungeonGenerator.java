package com.him.dungeons.generator;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.*;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class DungeonGenerator {

    private final HimDungeons plugin;
    private final String dungeonName;
    private final Player player;
    private final FileConfiguration instanceConfig;

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

        // ---- 主路径生成 严格DFS打通start到boss的通路，完全禁止生成fallback房间 ----
        DebugUtil.debug(player, "开始生成主路径 (start → boss) 通路...");
        boolean bossPlaced = generateMainPath(pathMin, pathMax, maxRooms);
        if (!bossPlaced) {
            DebugUtil.debug(player, "主路径生成失败");
            throw new RuntimeException("无法生成主路径");
        }
        DebugUtil.debug(player, "主路径完成，当前非fallback房间数 %d", countNonFallbackRooms());

        // ---- 分支BFS拓展 从主路径所有空门作为第一层根节点开始 ----
        DebugUtil.debug(player, "开始从主路径空门作为根节点进行BFS分支拓展...");
        generateBranches(minRooms, maxRooms, maxDepth);
        DebugUtil.debug(player, "BFS分支拓展完成，当前非fallback房间数 %d", countNonFallbackRooms());

        preMatchEmptyDoors();

        // ---- 全房间fallback补全流程 所有空节点全部接入fallback封堵 ----
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
            Location spawnLoc = startRoom.anchor.clone();
            File spawnFile = ResourceUtil.getRoomSpawnYaml(dungeonName, "start", startRoom.baseName);
            if (spawnFile != null) {
                Vector relativeSpawn = YamlDoorUtil.parseSpawnPoint(spawnFile);
                if (relativeSpawn != null) {
                    BlockVector3 relativeVec = BlockVector3.at(
                            relativeSpawn.getX(), relativeSpawn.getY(), relativeSpawn.getZ()
                    );
                    BlockVector3 rotated = TransformUtil.rotateBlockVector(relativeVec, startRoom.rotationTimes);
                    spawnLoc.add(rotated.getX(), rotated.getY(), rotated.getZ());
                }
            }
            spawnLoc.setX(spawnLoc.getBlockX() + 0.5);
            spawnLoc.setZ(spawnLoc.getBlockZ() + 0.5);
            player.teleport(spawnLoc);
            DebugUtil.debug(player, "玩家传送至出生点 %s", spawnLoc);

            plugin.registerDungeonWorld(world.getName(), dungeonName);
            plugin.setPlayerDungeonWorld(player.getUniqueId(), world.getName());

            MessageUtil.sendMessage(player, "地牢 '" + dungeonName + "' 生成完成！共 " + countNonFallbackRooms() + " 个普通房间，" + fallbackCount + " 个fallback房间。");
            DebugUtil.debug(player, "地牢生成完成");
        });

        return true;
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
    private boolean generateMainPath(int pathMin, int pathMax, int maxRooms) {
        // 路径存储：每个房间及其已尝试的门索引
        List<PathNode> path = new ArrayList<>();
        path.add(new PathNode(startRoom, new HashSet<>()));
        int pathLength = 1;

        while (pathLength < pathMin && countNonFallbackRooms() < maxRooms) {
            PathNode currentNode = path.get(path.size() - 1);
            RoomInstance currentRoom = currentNode.room;

            // 获取当前房间所有空闲门
            List<Integer> avail = currentRoom.getAvailableDoorIndices();
            // 过滤掉已尝试的门
            List<Integer> untried = new ArrayList<>();
            for (int idx : avail) {
                if (!currentNode.tried.contains(idx)) untried.add(idx);
            }

            if (untried.isEmpty()) {
                // 当前房间所有门都已尝试，需要回溯
                if (path.size() <= 1) {
                    DebugUtil.debug(player, "主路径无法回溯（起始房间也无可用门），中断");
                    return false;
                }
                // 移除当前房间
                RoomInstance removed = placedRooms.remove(placedRooms.size() - 1);
                path.remove(path.size() - 1);
                pathLength--;
                DebugUtil.debug(player, "主路径回溯：移除房间 %s，路径长度 %d", removed.roomType, pathLength);
                continue;
            }

            // 选择一个未尝试的门
            int doorIdx = untried.get(0);
            currentNode.tried.add(doorIdx);

            // 强制禁止fallback 主路径永远不允许生成fallback
            RoomInstance next = tryExtend(currentRoom, doorIdx, false, false,false);
            if (next != null && !next.isFallback) {
                placedRooms.add(next);
                path.add(new PathNode(next, new HashSet<>()));
                pathLength++;
                DebugUtil.debug(player, "主路径扩展成功，路径长度 %d", pathLength);
            } else {
                // 扩展失败（门已标记废弃），继续循环尝试下一个门
                DebugUtil.debug(player, "主路径扩展失败，尝试下一个门");
            }
        }

        // 路径长度达到 pathMin，尝试放置 boss
        if (pathLength >= pathMin) {
            // 从当前路径末端开始尝试放置 boss
            for (int i = path.size() - 1; i >= 0; i--) {
                RoomInstance current = path.get(i).room;
                List<Integer> avail = current.getAvailableDoorIndices();
                for (int idx : avail) {
                    RoomInstance boss = tryExtend(current, idx, true, false,false);
                    if (boss != null) {
                        placedRooms.add(boss);
                        DebugUtil.debug(player, "主路径成功放置 boss，总非fallback房间数 %d", countNonFallbackRooms());
                        return true;
                    }
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
    private void generateBranches(int minRooms, int maxRooms, int maxDepth) {
        Queue<BranchNode> queue = new LinkedList<>();
        // 第一层根节点：主路径所有房间的全部空门
        for (int i = 0; i < placedRooms.size(); i++) {
            RoomInstance room = placedRooms.get(i);
            if (room.isFallback) continue;
            for (int idx : room.getAvailableDoorIndices()) {
                queue.offer(new BranchNode(i, idx, 1));
            }
        }

        while (!queue.isEmpty() && countNonFallbackRooms() < maxRooms) {
            // 达到最小房间数要求后，如果队列已经没有可拓展节点就提前结束
            if (countNonFallbackRooms() >= minRooms && !hasAnyExtendableNode(queue)) {
                DebugUtil.debug(player, "已达到最小房间数且无剩余可拓展节点，提前结束BFS拓展");
                break;
            }

            BranchNode node = queue.poll();
            if (node.depth >= maxDepth) continue;
            RoomInstance currentRoom = placedRooms.get(node.roomIndex);
            if (!currentRoom.getAvailableDoorIndices().contains(node.doorIdx)) continue;

            // 普通拓展 禁止生成fallback房间
            RoomInstance newRoom = tryExtend(currentRoom, node.doorIdx, false, false,false);
            if (newRoom != null && !newRoom.isFallback) {
                placedRooms.add(newRoom);
                int newIdx = placedRooms.size() - 1;
                // 新生成的普通房间的所有空门继续加入BFS队列
                for (int idx : newRoom.getAvailableDoorIndices()) {
                    queue.offer(new BranchNode(newIdx, idx, node.depth + 1));
                }
                DebugUtil.debug(player, "BFS拓展成功，新普通房间 %s，当前非fallback房间数 %d",
                        newRoom.roomType, countNonFallbackRooms());
            } else {
                // 当开启门复用时才允许尝试在这里放置fallback作为BFS的最后节点
                if (ConfigUtil.isInstanceAllowFallbackDoorReuse(instanceConfig)) {
                    RoomInstance fallbackRoom = tryPlaceFallback(currentRoom, node.doorIdx);
                    if (fallbackRoom != null) {
                        placedRooms.add(fallbackRoom);
                        DebugUtil.debug(player, "开启门复用模式，放置fallback作为BFS末端节点");
                    } else {
                        currentRoom.markDoorUsed(node.doorIdx);
                        DebugUtil.debug(player, "拓展失败，标记门 %d 废弃", node.doorIdx);
                    }
                } else {
                    currentRoom.markDoorUsed(node.doorIdx);
                    DebugUtil.debug(player, "拓展失败，标记门 %d 废弃", node.doorIdx);
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
    Set<BlockVector3> usedAnchors = new HashSet<>();
    
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
            BlockVector3 anchor = BlockVector3.at(
                fb.anchor.getBlockX(),
                fb.anchor.getBlockY(),
                fb.anchor.getBlockZ()
            );
            if (usedAnchors.contains(anchor)) {
                // 锚点重复，不放置此 fallback，并标记该门为已使用（避免无限尝试）
                room.markDoorUsed(doorIdx);
                DebugUtil.debug(player, "空门 %d 的锚点 %s 已被占用，跳过", doorIdx, anchor);
                continue;
            }
            // 记录锚点，添加房间
            usedAnchors.add(anchor);
            placedRooms.add(fb);
            fallbackCount++;
            DebugUtil.debug(player, "空门 %d fallback封堵成功，锚点 %s，当前fallback总数 %d", 
                doorIdx, anchor, fallbackCount);
        } else {
            // 生成失败，标记门废弃
            room.markDoorUsed(doorIdx);
            DebugUtil.debug(player, "空门 %d fallback封堵失败，标记永久废弃", doorIdx);
        }
    }
    return fallbackCount;
}

    // ==================== 统一扩展方法 100%保留原有旋转对接和AABB检测逻辑 ====================
private RoomInstance tryExtend(RoomInstance currentRoom, int doorIdx,
                               boolean forceBoss, boolean allowFallback, boolean forceFallbackIgnoreCheck) {
    // 获取当前门信息
    YamlDoorUtil.DoorAnchor currentMeta = currentRoom.doors.get(doorIdx);
    BlockVector3 currentDoorWorld = DoorMatcherUtil.toWorldDoorPosition(
            currentMeta, currentRoom.anchor, currentRoom.rotationTimes);
    String currentFacing = TransformUtil.rotateFacing(currentMeta.facing, currentRoom.rotationTimes);

    // 构建可用类型（普通流程）
    Map<String, Integer> availableTypes = new HashMap<>();
    Map<String, Integer> weights = ConfigUtil.getInstanceStructureWeights(instanceConfig);
    for (Map.Entry<String, Integer> entry : weights.entrySet()) {
        String type = entry.getKey();
        if (type.equals("start")) continue;
        if (forceBoss && !type.equals("boss")) continue;
        if (!forceBoss && type.equals("boss")) continue;
        if (!allowFallback && type.equals("fallback")) continue;
        if (entry.getValue() > 0) {
            availableTypes.put(type, entry.getValue());
        }
    }

    // ========== 强制封堵模式：直接匹配 fallback 资源，不旋转 ==========
    if (forceFallbackIgnoreCheck && allowFallback) {
        // 获取当前门所需的反向朝向
        String targetFacing = TransformUtil.oppositeFacing(currentFacing);
        if (targetFacing == null) {
            DebugUtil.debug(player, "无法获取反向朝向，跳过");
            return null;
        }

        // 扫描所有 fallback 资源，匹配门朝向
        List<String> allFallbackNames = ResourceUtil.listAvailableBaseNames(dungeonName, "fallback");
        List<String> matchedNames = new ArrayList<>();
        for (String baseName : allFallbackNames) {
            File doorFile = ResourceUtil.getRoomDoorYaml(dungeonName, "fallback", baseName);
            if (doorFile == null) continue;
            List<YamlDoorUtil.DoorAnchor> doors;
            try {
                doors = YamlDoorUtil.parseDoorAnchors(doorFile);
            } catch (Exception e) {
                continue;
            }
            // 要求该资源有且仅有一个门，且朝向匹配
            if (doors.size() == 1) {
                String doorFacing = doors.get(0).facing;
                if (targetFacing.equals(doorFacing)) {
                    matchedNames.add(baseName);
                }
            }
        }

        if (matchedNames.isEmpty()) {
            DebugUtil.debug(player, "没有找到朝向 %s 的 fallback 资源", targetFacing);
            return null;
        }

        // 随机选择一个匹配的资源
        String baseName = matchedNames.get(rand.nextInt(matchedNames.size()));
        File schematic = ResourceUtil.getRoomSchematic(dungeonName, "fallback", baseName);
        File doorFile = ResourceUtil.getRoomDoorYaml(dungeonName, "fallback", baseName);
        if (schematic == null || doorFile == null) {
            DebugUtil.debug(player, "fallback 资源文件缺失: %s", baseName);
            return null;
        }

        BlockVector3 size = WorldEditUtil.getClipboardSizeAndRelease(schematic);
        if (size == null) return null;

        List<YamlDoorUtil.DoorAnchor> candidateDoors;
        try {
            candidateDoors = YamlDoorUtil.parseDoorAnchors(doorFile);
        } catch (Exception e) {
            return null;
        }
        if (candidateDoors.size() != 1) {
            DebugUtil.debug(player, "fallback 资源门数不为1，实际: %d", candidateDoors.size());
            return null;
        }

        // 固定旋转为 0（资源已朝向目标）
        int rot = 0;
        YamlDoorUtil.DoorAnchor candDoor = candidateDoors.get(0);
        BlockVector3 rotatedCandPos = TransformUtil.rotateBlockVector(
                BlockVector3.at(candDoor.x, candDoor.y, candDoor.z), rot);

        // 计算锚点
        Vector dirVec = TransformUtil.facingToVector(currentFacing);
        BlockVector3 dirBlock = BlockVector3.at(dirVec.getX(), dirVec.getY(), dirVec.getZ());
        BlockVector3 anchorVec = currentDoorWorld.add(dirBlock).subtract(rotatedCandPos);
        Location candidateAnchor = WorldUtil.toLocation(anchorVec, world);
        if (candidateAnchor == null) return null;

        BlockVector3 candWorldPos = BlockVector3.at(
                candidateAnchor.getX() + rotatedCandPos.getX(),
                candidateAnchor.getY() + rotatedCandPos.getY(),
                candidateAnchor.getZ() + rotatedCandPos.getZ()
        );
        String candFacing = TransformUtil.rotateFacing(candDoor.facing, rot);

        // 门匹配检查（仍应通过）
        if (!DoorMatcherUtil.isMatching(currentDoorWorld, currentFacing, candWorldPos, candFacing)) {
            DebugUtil.debug(player, "fallback 门匹配失败，资源可能不可用");
            return null;
        }

        // 碰撞检测跳过（已是 fallback 强制模式）
        BlockVector3 rotatedSize = TransformUtil.rotateBlockVector(size, rot);
        int w = Math.abs(rotatedSize.getX());
        int h = Math.abs(rotatedSize.getY());
        int d = Math.abs(rotatedSize.getZ());
        BlockVector3 absSize = BlockVector3.at(w, h, d);
        AABBUtil.AABB newAabb = AABBUtil.build(candidateAnchor, absSize);

        // 创建房间实例
        RoomInstance newRoom = new RoomInstance(
                "fallback", baseName, candidateAnchor, rot,
                absSize, newAabb, candidateDoors, true
        );
        currentRoom.markDoorUsed(doorIdx);
        newRoom.markDoorUsed(0);
        DebugUtil.debug(player, "Fallback 封堵成功，资源 %s，锚点 %s", baseName, candidateAnchor);
        return newRoom;
    }
    // ================================================================

    // ---------- 非强制封堵的正常流程（普通房间 / boss） ----------
    if (availableTypes.isEmpty()) return null;

    for (int attempt = 0; attempt < 100; attempt++) {
        String candidateType = selectByWeight(availableTypes);
        if (candidateType == null) continue;

        List<String> baseNames = ResourceUtil.listAvailableBaseNames(dungeonName, candidateType);
        if (baseNames.isEmpty()) continue;
        String baseName = baseNames.get(rand.nextInt(baseNames.size()));

        File schematic = ResourceUtil.getRoomSchematic(dungeonName, candidateType, baseName);
        File doorFile = ResourceUtil.getRoomDoorYaml(dungeonName, candidateType, baseName);
        if (schematic == null || doorFile == null) continue;

        BlockVector3 size = WorldEditUtil.getClipboardSizeAndRelease(schematic);
        if (size == null) continue;

        List<YamlDoorUtil.DoorAnchor> candidateDoors;
        try {
            candidateDoors = YamlDoorUtil.parseDoorAnchors(doorFile);
        } catch (Exception e) {
            continue;
        }
        if (candidateDoors.isEmpty()) continue;

        if (!forceBoss && !allowFallback && candidateDoors.size() < 2) continue;

        for (int candIdx = 0; candIdx < candidateDoors.size(); candIdx++) {
            YamlDoorUtil.DoorAnchor candDoor = candidateDoors.get(candIdx);
            boolean isFallback = false; // 此分支不可能为 fallback

            // 普通房间尝试所有旋转
            for (int rot = 0; rot < 4; rot++) {
                BlockVector3 rotatedCandPos = TransformUtil.rotateBlockVector(
                        BlockVector3.at(candDoor.x, candDoor.y, candDoor.z), rot);

                Vector dirVec = TransformUtil.facingToVector(currentFacing);
                BlockVector3 dirBlock = BlockVector3.at(dirVec.getX(), dirVec.getY(), dirVec.getZ());
                BlockVector3 anchorVec = currentDoorWorld.add(dirBlock).subtract(rotatedCandPos);
                Location candidateAnchor = WorldUtil.toLocation(anchorVec, world);
                if (candidateAnchor == null) continue;

                BlockVector3 candWorldPos = BlockVector3.at(
                        candidateAnchor.getX() + rotatedCandPos.getX(),
                        candidateAnchor.getY() + rotatedCandPos.getY(),
                        candidateAnchor.getZ() + rotatedCandPos.getZ()
                );
                String candFacing = TransformUtil.rotateFacing(candDoor.facing, rot);

                if (!DoorMatcherUtil.isMatching(currentDoorWorld, currentFacing, candWorldPos, candFacing)) {
                    continue;
                }

                // 碰撞与间距检测
                BlockVector3 rotatedSize = TransformUtil.rotateBlockVector(size, rot);
                int w = Math.abs(rotatedSize.getX());
                int h = Math.abs(rotatedSize.getY());
                int d = Math.abs(rotatedSize.getZ());
                BlockVector3 absSize = BlockVector3.at(w, h, d);
                AABBUtil.AABB newAabb = AABBUtil.build(candidateAnchor, absSize);

                boolean useAabb = ConfigUtil.isInstanceAabbEnabled(instanceConfig);
                if (useAabb) {
                    boolean overlap = false;
                    for (RoomInstance placed : placedRooms) {
                        if (AABBUtil.AABB.overlaps(newAabb, placed.aabb)) {
                            overlap = true;
                            break;
                        }
                    }
                    if (overlap) {
                        DebugUtil.debug(player, "房间AABB碰撞失败，锚点 %s", candidateAnchor);
                        continue;
                    }
                }
                int disconnectedDistance = ConfigUtil.getInstanceDisconnectedRoomDistance(instanceConfig);
                if (disconnectedDistance > 0) {
                    double minDist = Double.MAX_VALUE;
                    for (RoomInstance placed : placedRooms) {
                        if (placed == currentRoom) continue;
                        double dist = AABBUtil.AABB.minDistance(newAabb, placed.aabb);
                        if (dist < minDist) minDist = dist;
                    }
                    if (minDist < disconnectedDistance) {
                        DebugUtil.debug(player, "房间间距检测失败，最小距离 %.2f < %d", minDist, disconnectedDistance);
                        continue;
                    }
                }

                RoomInstance newRoom = new RoomInstance(
                        candidateType, baseName, candidateAnchor, rot,
                        absSize, newAabb, candidateDoors, false
                );
                currentRoom.markDoorUsed(doorIdx);
                newRoom.markDoorUsed(candIdx);
                DebugUtil.debug(player, "房间生成成功，类型 %s，锚点: %s", candidateType, candidateAnchor);
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
    // 后者会强制只使用 fallback 类型
    return tryExtend(currentRoom, doorIdx, false, true, true);
}


    // ==================== 辅助方法 ====================
    private String selectByWeight(Map<String, Integer> weightMap) {
        int total = 0;
        for (int w : weightMap.values()) total += w;
        if (total == 0) return null;
        int r = rand.nextInt(total);
        int cum = 0;
        for (Map.Entry<String, Integer> entry : weightMap.entrySet()) {
            cum += entry.getValue();
            if (r < cum) return entry.getKey();
        }
        return null;
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
                BlockVector3 posA = DoorMatcherUtil.toWorldDoorPosition(doorA, a.anchor, a.rotationTimes);
                BlockVector3 posB = DoorMatcherUtil.toWorldDoorPosition(doorB, b.anchor, b.rotationTimes);
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
        String baseName = "start";
        File schematic = ResourceUtil.getRoomSchematic(dungeonName, "start", baseName);
        if (schematic == null) {
            DebugUtil.debug(player, "start.schem 不存在");
            return null;
        }
        File doorFile = ResourceUtil.getRoomDoorYaml(dungeonName, "start", baseName);
        if (doorFile == null) {
            DebugUtil.debug(player, "start_door.yml 不存在");
            return null;
        }
        BlockVector3 size = WorldEditUtil.getClipboardSizeAndRelease(schematic);
        if (size == null) {
            DebugUtil.debug(player, "无法读取 start.schem 尺寸");
            return null;
        }
        List<YamlDoorUtil.DoorAnchor> doors;
        try {
            doors = YamlDoorUtil.parseDoorAnchors(doorFile);
        } catch (Exception e) {
            DebugUtil.debug(player, "解析 start_door.yml 失败: %s", e.getMessage());
            return null;
        }
        Location anchor = new Location(world, 0, 64, 0);
        int rotation = 0;
        BlockVector3 rotatedSize = TransformUtil.rotateBlockVector(size, rotation);
        AABBUtil.AABB aabb = AABBUtil.build(anchor, rotatedSize);
        return new RoomInstance("start", baseName, anchor, rotation, rotatedSize, aabb, doors);
    }

    private CompletableFuture<Void> pasteAllRoomsAsync() {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (RoomInstance room : placedRooms) {
            future = future.thenCompose(v -> {
                CompletableFuture<Void> f = new CompletableFuture<>();
                File schematic = ResourceUtil.getRoomSchematic(dungeonName, room.roomType, room.baseName);
                if (schematic == null) {
                    DebugUtil.debug(player, "房间 schematic 缺失: " + room.roomType + "/" + room.baseName);
                    f.completeExceptionally(new RuntimeException("房间 schematic 缺失"));
                    return f;
                }
                WorldEditUtil.pasteAndReleaseAsync(schematic, room.anchor, room.rotationTimes, success -> {
                    if (success) {
                        f.complete(null);
                    } else {
                        DebugUtil.debug(player, "粘贴失败: " + room.roomType + "/" + room.baseName);
                        f.completeExceptionally(new RuntimeException("粘贴失败"));
                    }
                });
                return f;
            });
        }
        return future;
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
            BlockVector3 posA = DoorMatcherUtil.toWorldDoorPosition(doorA, a.anchor, a.rotationTimes);
            String facingA = TransformUtil.rotateFacing(doorA.facing, a.rotationTimes);
            for (int j = i + 1; j < nonFallback.size(); j++) {
                RoomInstance b = nonFallback.get(j);
                for (int idxB : b.getAvailableDoorIndices()) {
                    YamlDoorUtil.DoorAnchor doorB = b.doors.get(idxB);
                    BlockVector3 posB = DoorMatcherUtil.toWorldDoorPosition(doorB, b.anchor, b.rotationTimes);
                    String facingB = TransformUtil.rotateFacing(doorB.facing, b.rotationTimes);
                    if (DoorMatcherUtil.isMatching(posA, facingA, posB, facingB)) {
                        // 配对成功，标记为已使用
                        a.markDoorUsed(idxA);
                        b.markDoorUsed(idxB);
                        DebugUtil.debug(player, "预配对成功：%s 门%d ↔ %s 门%d", 
                                a.roomType, idxA, b.roomType, idxB);
                        // 跳出内层循环，因为 b 的该门已使用
                        break;
                    }
                }
            }
        }
    }
}
}
