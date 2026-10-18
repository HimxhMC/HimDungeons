package com.him.dungeons.util;

import org.bukkit.Location;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.him.dungeons.schematic.NbtSchematic;
import org.bukkit.block.data.BlockData;

/**
 * 选区管理器。
 * 存储玩家用木镐选择的两个点，替代 WorldEdit 的 Region 选区。
 */
public final class SelectionManager {

    private static final Map<UUID, Location> pos1Map = new ConcurrentHashMap<>();
    private static final Map<UUID, Location> pos2Map = new ConcurrentHashMap<>();

    private SelectionManager() {}

    public static void setPos1(UUID uuid, Location loc) {
        pos1Map.put(uuid, loc);
    }

    public static void setPos2(UUID uuid, Location loc) {
        pos2Map.put(uuid, loc);
    }

    public static Location getPos1(UUID uuid) {
        return pos1Map.get(uuid);
    }

    public static Location getPos2(UUID uuid) {
        return pos2Map.get(uuid);
    }

    public static void clear(UUID uuid) {
        pos1Map.remove(uuid);
        pos2Map.remove(uuid);
    }

    /**
     * 获取玩家的选区。两个点都已设置且世界相同时返回 Selection，否则返回 null。
     */
    public static Selection getSelection(UUID uuid) {
        Location p1 = pos1Map.get(uuid);
        Location p2 = pos2Map.get(uuid);
        if (p1 == null || p2 == null) return null;
        if (!p1.getWorld().equals(p2.getWorld())) return null;
        return new Selection(p1, p2);
    }

    /**
     * 选区数据。
     */
    public static class Selection {
        public final Location pos1;
        public final Location pos2;
        public final int minX, minY, minZ;
        public final int maxX, maxY, maxZ;
        public final int width, height, length;

        public Selection(Location p1, Location p2) {
            this.pos1 = p1;
            this.pos2 = p2;
            this.minX = Math.min(p1.getBlockX(), p2.getBlockX());
            this.minY = Math.min(p1.getBlockY(), p2.getBlockY());
            this.minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
            this.maxX = Math.max(p1.getBlockX(), p2.getBlockX());
            this.maxY = Math.max(p1.getBlockY(), p2.getBlockY());
            this.maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());
            this.width  = maxX - minX + 1;
            this.height = maxY - minY + 1;
            this.length = maxZ - minZ + 1;
        }

        /** 从选区构建 NbtSchematic（暂不含 BlockEntities） */
        public NbtSchematic toSchematic() {
            NbtSchematic schem = new NbtSchematic();
            schem.version = 3;
            schem.width = width;
            schem.height = height;
            schem.length = length;

            // 收集调色板
            java.util.Map<String, Integer> paletteMap = new java.util.LinkedHashMap<>();
            java.util.List<BlockData> paletteList = new java.util.ArrayList<>();
            int[] indices = new int[width * height * length];

            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int wx = minX + x, wy = minY + y, wz = minZ + z;
                        BlockData data = pos1.getWorld().getBlockAt(wx, wy, wz).getBlockData();
                        String key = data.getAsString();
                        Integer idx = paletteMap.get(key);
                        if (idx == null) {
                            idx = paletteList.size();
                            paletteMap.put(key, idx);
                            paletteList.add(data);
                        }
                        int bi = (y * length + z) * width + x;
                        indices[bi] = idx;
                    }
                }
            }

            schem.palette = paletteList.toArray(new BlockData[0]);
            schem.blockData = indices;
            return schem;
        }
    }
}