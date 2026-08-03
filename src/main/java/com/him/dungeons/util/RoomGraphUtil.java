package com.him.dungeons.util;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;

public final class RoomGraphUtil {

    private RoomGraphUtil() {}

    /**
     * 尝试扩展一个分支（只做门配对和AABB，不涉及BFS/DFS）
     */
    public static RoomInstance tryExtend(RoomInstance currentRoom,
                                         int doorIdx,
                                         List<RoomInstance> placedRooms,
                                         String dungeonName,
                                         World world,
                                         FileConfiguration config,
                                         Player debugPlayer) {
        DebugUtil.debug(debugPlayer, "尝试从房间 %s 的门索引 %d 扩展", currentRoom.roomType, doorIdx);

        // 当前门信息
        YamlDoorUtil.DoorAnchor currentMeta = currentRoom.doors.get(doorIdx);
        BlockVector3 currentDoorWorld = DoorMatcherUtil.toWorldDoorPosition(
                currentMeta, currentRoom.anchor, currentRoom.rotationTimes);
        String currentFacing = TransformUtil.rotateFacing(currentMeta.facing, currentRoom.rotationTimes);
        DebugUtil.debug(debugPlayer, "当前门世界坐标: %s, 朝向: %s", currentDoorWorld, currentFacing);

        // 候选房间类型权重
        Map<String, Integer> weights = ConfigUtil.getInstanceStructureWeights(config);
        // 排除 start, boss (除非强制，但这里忽略)
        Set<String> exclude = new HashSet<>();
        exclude.add("start");
        exclude.add("boss");
        // 如果禁止复用，排除已用类型
        if (!ConfigUtil.isInstanceAllowRoomReuse(config)) {
            for (RoomInstance r : placedRooms) exclude.add(r.roomType);
        }

        // 构建可用类型
        Map<String, Integer> available = new HashMap<>();
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            if (!exclude.contains(entry.getKey()) && entry.getValue() > 0) {
                available.put(entry.getKey(), entry.getValue());
            }
        }
        if (available.isEmpty()) {
            DebugUtil.debug(debugPlayer, "无可用房间类型");
            return null;
        }

        Random rand = new Random();
        for (int attempt = 0; attempt < 50; attempt++) {
            String candidateType = selectByWeight(available, rand);
            if (candidateType == null) continue;

            // 获取基础文件名
            List<String> baseNames = ResourceUtil.listAvailableBaseNames(dungeonName, candidateType);
            if (baseNames.isEmpty()) continue;
            String baseName = baseNames.get(rand.nextInt(baseNames.size()));

            File schematic = ResourceUtil.getRoomSchematic(dungeonName, candidateType, baseName);
            File doorFile = ResourceUtil.getRoomDoorYaml(dungeonName, candidateType, baseName);
            if (schematic == null || doorFile == null) continue;

            // 读取尺寸
            BlockVector3 size = WorldEditUtil.getClipboardSizeAndRelease(schematic);
            if (size == null) continue;

            // 解析门
            List<YamlDoorUtil.DoorAnchor> candidateDoors;
            try {
                candidateDoors = YamlDoorUtil.parseDoorAnchors(doorFile);
            } catch (Exception e) {
                continue;
            }
            if (candidateDoors.isEmpty()) continue;

            // 尝试每个候选门
            for (int candIdx = 0; candIdx < candidateDoors.size(); candIdx++) {
                YamlDoorUtil.DoorAnchor candDoor = candidateDoors.get(candIdx);
                for (int rot = 0; rot < 4; rot++) {
                    // ★ 1. 候选门相对坐标（旋转后）
                    BlockVector3 rotatedCandPos = TransformUtil.rotateBlockVector(
                            BlockVector3.at(candDoor.x, candDoor.y, candDoor.z), rot);

                    // ★ 2. 计算候选锚点: anchor = currentDoorWorld + dir - rotatedCandPos
                    Vector dirVec = TransformUtil.facingToVector(currentFacing);
                    BlockVector3 dirBlock = BlockVector3.at(dirVec.getX(), dirVec.getY(), dirVec.getZ());
                    BlockVector3 anchorVec = currentDoorWorld.add(dirBlock).subtract(rotatedCandPos);
                    Location candidateAnchor = WorldUtil.toLocation(anchorVec, world);
                    if (candidateAnchor == null) continue;

                    // ★ 3. 候选门世界坐标 = anchor + rotatedCandPos
                    BlockVector3 candWorldPos = anchorVec.add(rotatedCandPos);
                    String candFacing = TransformUtil.rotateFacing(candDoor.facing, rot);

                    DebugUtil.debug(debugPlayer, "尝试候选门 %d，旋转 %d，候选锚点 %s，候选门世界 %s，朝向 %s",
                            candIdx, rot, candidateAnchor, candWorldPos, candFacing);

                    // ★ 4. 验证配对
                    if (!DoorMatcherUtil.isMatching(currentDoorWorld, currentFacing, candWorldPos, candFacing)) {
                        DebugUtil.debug(debugPlayer, "配对失败");
                        continue;
                    }
                    DebugUtil.debug(debugPlayer, "配对成功");

                    // ★ 5. AABB 碰撞检测
                    boolean useAabb = ConfigUtil.isInstanceAabbEnabled(config);
                    BlockVector3 rotatedSize = TransformUtil.rotateBlockVector(size, rot);
                    AABBUtil.AABB newAabb = AABBUtil.build(candidateAnchor, rotatedSize);
                    if (useAabb) {
                        boolean overlap = false;
                        for (RoomInstance placed : placedRooms) {
                            if (AABBUtil.AABB.overlaps(newAabb, placed.aabb)) {
                                overlap = true;
                                break;
                            }
                        }
                        if (overlap) {
                            DebugUtil.debug(debugPlayer, "AABB 碰撞失败");
                            continue;
                        }
                        DebugUtil.debug(debugPlayer, "AABB 通过");
                    }

                    // ★ 6. 构建新房间
                    boolean isFallback = "fallback".equals(candidateType);
                    RoomInstance newRoom = new RoomInstance(
                            candidateType, baseName, candidateAnchor, rot,
                            rotatedSize, newAabb, candidateDoors, isFallback
                    );
                    // 标记门占用
                    currentRoom.markDoorUsed(doorIdx);
                    if (!isFallback || !ConfigUtil.isInstanceAllowFallbackDoorReuse(config)) {
                        newRoom.markDoorUsed(candIdx);
                    }
                    DebugUtil.debug(debugPlayer, "扩展成功，新房间 %s，锚点 %s", candidateType, candidateAnchor);
                    return newRoom;
                }
            }
        }
        DebugUtil.debug(debugPlayer, "所有尝试失败");
        return null;
    }

    private static String selectByWeight(Map<String, Integer> weightMap, Random rand) {
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

    // 其他辅助方法（getNextAvailableDoor等）可保留，但这里简化

    /**
     * 获取房间的下一个可用门索引
     */
    public static int getNextAvailableDoor(RoomInstance room) {
        List<Integer> avail = room.getAvailableDoorIndices();
        return avail.isEmpty() ? -1 : avail.get(0);
    }

    /**
     * 检查所有房间的所有门是否都已连接（即没有空闲门）
     */
    public static boolean isFullyConnected(List<RoomInstance> rooms) {
        for (RoomInstance r : rooms) {
            if (!r.getAvailableDoorIndices().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 使用 BFS 检查所有已放置房间是否在逻辑上连通（基于门连接关系）
     */
    public static boolean checkLogicalConnectivity(List<RoomInstance> placedRooms) {
        if (placedRooms == null || placedRooms.isEmpty()) return true;
        if (placedRooms.size() == 1) return true;

        Map<RoomInstance, List<RoomInstance>> graph = new HashMap<>();
        for (RoomInstance room : placedRooms) {
            graph.put(room, new ArrayList<>());
        }

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
            RoomInstance current = queue.poll();
            for (RoomInstance neighbor : graph.getOrDefault(current, Collections.emptyList())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return visited.size() == placedRooms.size();
    }

    /**
     * 检测两个房间是否通过一对已占用的门相互连接
     */
    private static boolean areRoomsConnected(RoomInstance a, RoomInstance b) {
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

    /**
     * 获取 DFS 下一个待扩展的节点（房间索引 + 门索引）
     * 从最后一个房间开始向前查找第一个有空闲门的房间
     */
    public static int[] getNextBranchNode(List<RoomInstance> placedRooms) {
    if (placedRooms == null || placedRooms.isEmpty()) return null;
    // 从最后一个房间开始，但也要检查当前深度是否超过 maxDepth（由调用方控制）
    for (int i = placedRooms.size() - 1; i >= 0; i--) {
        RoomInstance room = placedRooms.get(i);
        List<Integer> avail = room.getAvailableDoorIndices();
        if (!avail.isEmpty()) {
            return new int[]{i, avail.get(0)};
        }
    }
    return null;
}
}