package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.schematic.AsyncPaster;
import com.him.dungeons.schematic.NbtSchematic;
import com.him.dungeons.schematic.SpongeSchematicReader;
import org.bukkit.Location;

import java.io.File;
import java.util.function.Consumer;

/**
 * 原理图工具类（已移除 WorldEdit 依赖）。
 * 使用 NBT 库直接读写 Sponge 原理图。
 */
public final class WorldEditUtil {

    private WorldEditUtil() {}

    /**
     * 异步粘贴原理图。
     * 替代旧的 WorldEdit pasteAndReleaseAsync。
     *
     * @param schematicFile 原理图文件
     * @param anchor        锚点位置
     * @param rotationTimes 顺时针旋转次数（0-3）
     * @param formatVersion 格式版本（2 或 3），传 0 自动检测
     * @param callback      完成回调
     */
    public static void pasteAndReleaseAsync(File schematicFile, Location anchor,
                                            int rotationTimes, int formatVersion,
                                            Consumer<Boolean> callback) {
        if (schematicFile == null || anchor == null) {
            if (callback != null) callback.accept(false);
            return;
        }
        AsyncPaster.pasteAsync(anchor.getWorld(), anchor, schematicFile,
                rotationTimes, formatVersion, callback);
    }

    /**
     * 获取原理图的尺寸（width, height, length）。
     * 返回 Pos3 保持兼容，但内部使用 NBT 库。
     */
    public static Pos3 getClipboardSizeAndRelease(File schematicFile) {
        NbtSchematic schem = SpongeSchematicReader.read(schematicFile, 0);
        if (schem == null) return null;
        return Pos3.at(schem.width, schem.height, schem.length);
    }

    /**
     * 检查原理图是否有效。
     */
    public static boolean isSchematicValid(File schematicFile) {
        return SpongeSchematicReader.read(schematicFile, 0) != null;
    }

    /**
     * 获取原理图中实际非空气方块的最小和最大相对坐标。
     */
    public static Pos3[] getActualBounds(File schematicFile) {
        NbtSchematic schem = SpongeSchematicReader.read(schematicFile, 0);
        if (schem == null) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int y = 0; y < schem.height; y++) {
            for (int z = 0; z < schem.length; z++) {
                for (int x = 0; x < schem.width; x++) {
                    org.bukkit.block.data.BlockData data = schem.getBlockData(x, y, z);
                    if (data != null && !data.getMaterial().isAir()) {
                        if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z;
                        if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z;
                    }
                }
            }
        }
        if (minX == Integer.MAX_VALUE) return null;
        return new Pos3[]{
                Pos3.at(minX, minY, minZ),
                Pos3.at(maxX, maxY, maxZ)
        };
    }
}