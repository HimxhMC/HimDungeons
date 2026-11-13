package com.him.dungeons.schematic;

import org.bukkit.block.data.BlockData;
import java.util.List;
import java.util.Map;

/**
 * 原理图数据模型（与格式无关）
 */
public class NbtSchematic {

    /** 格式版本：2 或 3 */
    public int version;

    /** 维度 */
    public int width;
    public int height;
    public int length;

    /** 调色板：索引 → BlockData */
    public BlockData[] palette;

    /** 方块数据索引（YZX 顺序，varint 解码后） */
    public int[] blockData;

    /** 方块实体（位置 + NBT 数据） */
    public List<BlockEntityData> blockEntities;

    /** 偏移量（可选） */
    public int offsetX, offsetY, offsetZ;

    /** 获取指定坐标的调色板索引 */
    public int getBlockIndex(int x, int y, int z) {
        return (y * length + z) * width + x;
    }

    /** 获取指定坐标的 BlockData */
    public BlockData getBlockData(int x, int y, int z) {
        int idx = getBlockIndex(x, y, z);
        if (idx < 0 || idx >= blockData.length) return null;
        int paletteIdx = blockData[idx];
        if (paletteIdx < 0 || paletteIdx >= palette.length) return null;
        return palette[paletteIdx];
    }

    /** 方块实体数据 */
    public static class BlockEntityData {
        public final int x, y, z;
        public final String id;
        public final Map<String, Object> nbtData;

        public BlockEntityData(int x, int y, int z, String id, Map<String, Object> nbtData) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.id = id;
            this.nbtData = nbtData;
        }
    }
}