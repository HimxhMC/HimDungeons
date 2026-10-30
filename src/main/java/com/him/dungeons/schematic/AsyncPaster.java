package com.him.dungeons.schematic;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.BlockDataRotator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 异步原理图粘贴器。
 * 替代 WorldEdit 的 Operations.completeLegacy()。
 *
 * <p>流程：
 * <ol>
 *   <li>异步读取并解析原理图文件</li>
 *   <li>异步预计算所有方块位置和旋转后的 BlockData</li>
 *   <li>主线程分刻批量提交 setBlockData</li>
 * </ol>
 */
public final class AsyncPaster {

    private AsyncPaster() {}

    /** 每 tick 提交的方块数量（可根据服务器性能调整） */
    private static int getBlocksPerTick() { return com.him.dungeons.util.ConfigUtil.getPasteSpeed(); }

    /**
     * 异步粘贴原理图。
     *
     * @param world        目标世界
     * @param anchor       锚点位置
     * @param schematicFile 原理图文件
     * @param rotationTimes 顺时针旋转次数（0-3）
     * @param formatVersion 格式版本（2 或 3），传 0 自动检测
     * @param callback     完成回调
     */
    public static void pasteAsync(World world, Location anchor, File schematicFile,
                                  int rotationTimes, int formatVersion,
                                  Consumer<Boolean> callback) {
        // 1. 异步读取原理图
        Bukkit.getScheduler().runTaskAsynchronously(HimDungeons.getInstance(), () -> {
            NbtSchematic schem = SpongeSchematicReader.read(schematicFile, formatVersion);
            if (schem == null) {
                Bukkit.getScheduler().runTask(HimDungeons.getInstance(),
                        () -> callback.accept(false));
                return;
            }

            // 2. 异步预计算所有方块位置和数据
            List<BlockPlacement> placements = new ArrayList<>(schem.width * schem.height * schem.length);
            for (int y = 0; y < schem.height; y++) {
                for (int z = 0; z < schem.length; z++) {
                    for (int x = 0; x < schem.width; x++) {
                        BlockData data = schem.getBlockData(x, y, z);
                        if (data == null || data.getMaterial().isAir()) continue;

                        // 旋转 BlockData
                        BlockData rotatedData = BlockDataRotator.rotate(data, rotationTimes);

                        // 旋转相对坐标（整数运算，无三角函数）
                        int[] pos = rotate90Y(x, y, z, rotationTimes);

                        placements.add(new BlockPlacement(
                                anchor.getBlockX() + pos[0],
                                anchor.getBlockY() + pos[1],
                                anchor.getBlockZ() + pos[2],
                                rotatedData
                        ));
                    }
                }
            }

            // 3. 回到主线程分刻提交
            Bukkit.getScheduler().runTask(HimDungeons.getInstance(), () -> {
                submitInBatches(world, placements, 0, callback);
            });
        });
    }

    /** 分刻批量提交方块 */
    private static void submitInBatches(World world, List<BlockPlacement> placements,
                                         int startIndex, Consumer<Boolean> callback) {
        int end = Math.min(startIndex + getBlocksPerTick(), placements.size());
        for (int i = startIndex; i < end; i++) {
            BlockPlacement bp = placements.get(i);
            Block block = world.getBlockAt(bp.x, bp.y, bp.z);
            block.setBlockData(bp.data, false);
        }

        if (end < placements.size()) {
            Bukkit.getScheduler().runTaskLater(HimDungeons.getInstance(),
                    () -> submitInBatches(world, placements, end, callback), 1L);
        } else {
            callback.accept(true);
        }
    }

    /**
     * 90°整数倍坐标旋转，纯整数运算。
     * (x, y, z) → 顺时针旋转 times 次后的坐标
     */
    public static int[] rotate90Y(int x, int y, int z, int times) {
        times = ((times % 4) + 4) % 4;
        return switch (times) {
            case 1 -> new int[]{-z, y, x};
            case 2 -> new int[]{-x, y, -z};
            case 3 -> new int[]{z, y, -x};
            default -> new int[]{x, y, z};
        };
    }

    /** 方块放置数据 */
    private static class BlockPlacement {
        final int x, y, z;
        final BlockData data;

        BlockPlacement(int x, int y, int z, BlockData data) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
        }
    }
}