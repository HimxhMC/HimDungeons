package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class WorldEditUtil {

    private WorldEditUtil() {}

    // ==================== 粘贴 ====================\
    public static void pasteAndReleaseAsync(File schematicFile, Location anchor,
                                            int rotationTimes, Consumer<Boolean> callback) {
        if (schematicFile == null || anchor == null) {
            if (callback != null) callback.accept(false);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(HimDungeons.getInstance(), () -> {
            Clipboard clipboard = null;
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format == null) {
                if (callback != null) {
                    Bukkit.getScheduler().runTask(HimDungeons.getInstance(), () -> callback.accept(false));
                }
                return;
            }

            try (FileInputStream fis = new FileInputStream(schematicFile);
                 ClipboardReader reader = format.getReader(fis)) {
                clipboard = reader.read();
            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) {
                    Bukkit.getScheduler().runTask(HimDungeons.getInstance(), () -> callback.accept(false));
                }
                return;
            }

            final Clipboard finalClipboard = clipboard;
            // 异步线程先预计算有效方块边界，减少主线程IO耗时
            BlockVector3 actualMin = null;
            BlockVector3 origin = finalClipboard.getOrigin();
            for (BlockVector3 pos : finalClipboard.getRegion()) {
                BlockStateHolder state = finalClipboard.getBlock(pos);
                if (state != null && !state.getBlockType().getMaterial().isAir()) {
                    if (actualMin == null) {
                        actualMin = pos;
                    } else {
                        actualMin = BlockVector3.at(
                                Math.min(actualMin.getX(), pos.getX()),
                                Math.min(actualMin.getY(), pos.getY()),
                                Math.min(actualMin.getZ(), pos.getZ())
                        );
                    }
                }
            }
            BlockVector3 finalActualMin = actualMin != null ? actualMin.subtract(origin) : null;

            // 粘贴必须在主线程执行
            Bukkit.getScheduler().runTask(HimDungeons.getInstance(), () -> {
                try (EditSession editSession = WorldEdit.getInstance().newEditSession(
                        BukkitAdapter.adapt(anchor.getWorld()))) {

                    editSession.setFastMode(true);

                    ClipboardHolder holder = new ClipboardHolder(finalClipboard);
                    if (rotationTimes != 0) {
                        AffineTransform transform = new AffineTransform();
                        transform = transform.rotateY(Math.toRadians(rotationTimes * 90));
                        holder.setTransform(holder.getTransform().combine(transform));
                    }

                    // 核心修复：自动校准粘贴Y轴坐标，让结构最底部有效方块对齐传入的锚点Y值
                    double pasteY = anchor.getY();
                    if (finalActualMin != null) {
                        // 减去有效方块的最小Y偏移，抵消原理图冗余高度带来的错位
                        pasteY = anchor.getY() - finalActualMin.getY();
                    }

                    BlockVector3 pastePoint = BlockVector3.at(
                            anchor.getX(), pasteY, anchor.getZ()
                    );

                    Operation operation = holder.createPaste(editSession)
                            .to(pastePoint)
                            .ignoreAirBlocks(true)
                            .build();

                    Operations.completeLegacy(operation);
                    editSession.flushSession();

                    if (callback != null) callback.accept(true);

                } catch (WorldEditException e) {
                    e.printStackTrace();
                    if (callback != null) callback.accept(false);
                }
            });
        });
    }

    // ==================== 工具方法 ====================\
    public static BlockVector3 getClipboardSizeAndRelease(File schematicFile) {
        if (schematicFile == null || !schematicFile.exists()) return null;
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) return null;

        try (FileInputStream fis = new FileInputStream(schematicFile);
             ClipboardReader reader = format.getReader(fis)) {
            Clipboard clipboard = reader.read();
            return clipboard.getDimensions();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isSchematicValid(File schematicFile) {
        return getClipboardSizeAndRelease(schematicFile) != null;
    }

    /**
     * 获取原理图中实际非空气方块的最小和最大相对坐标（相对于原点0,0,0）
     * 返回数组 [min, max]，若原理图无方块则返回 null
     */
    public static BlockVector3[] getActualBounds(File schematicFile) {
        if (schematicFile == null || !schematicFile.exists()) return null;
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) return null;

        try (FileInputStream fis = new FileInputStream(schematicFile);
             ClipboardReader reader = format.getReader(fis)) {
            Clipboard clipboard = reader.read();
            BlockVector3 origin = clipboard.getOrigin(); // 应为 (0,0,0)
            BlockVector3 min = null;
            BlockVector3 max = null;
            for (BlockVector3 pos : clipboard.getRegion()) {
                BlockStateHolder state = clipboard.getBlock(pos);
                if (state != null && !state.getBlockType().getMaterial().isAir()) {
                    if (min == null) {
                        min = pos;
                        max = pos;
                    } else {
                        min = BlockVector3.at(
                            Math.min(min.getX(), pos.getX()),
                            Math.min(min.getY(), pos.getY()),
                            Math.min(min.getZ(), pos.getZ())
                        );
                        max = BlockVector3.at(
                            Math.max(max.getX(), pos.getX()),
                            Math.max(max.getY(), pos.getY()),
                            Math.max(max.getZ(), pos.getZ())
                        );
                    }
                }
            }
            if (min == null) return null;
            BlockVector3 relativeMin = min.subtract(origin);
            BlockVector3 relativeMax = max.subtract(origin);
            return new BlockVector3[]{relativeMin, relativeMax};
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== 保存原理图（完整） ====================\
    public static boolean saveSchematic(File file, Region region, com.sk89q.worldedit.world.World weWorld) {
        if (file == null || region == null || weWorld == null) return false;
        if (Bukkit.isPrimaryThread()) {
            return saveSchematicInternal(file, region, weWorld);
        } else {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean result = new AtomicBoolean(false);
            Bukkit.getScheduler().runTask(HimDungeons.getInstance(), () -> {
                result.set(saveSchematicInternal(file, region, weWorld));
                latch.countDown();
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            return result.get();
        }
    }

    private static boolean saveSchematicInternal(File file, Region region, com.sk89q.worldedit.world.World weWorld) {
        try {
            // 1. 创建剪贴板，基于 Region
            BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
            // 2. 强制原点为 (0,0,0)，使所有坐标变为相对偏移
            // clipboard.setOrigin(BlockVector3.ZERO);

            // 3. 使用 ForwardExtentCopy 复制方块（官方推荐方式）
            //    目标原点 = region.getMinimumPoint()，保持相对坐标正确
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                ForwardExtentCopy copy = new ForwardExtentCopy(
                        editSession,
                        region,
                        clipboard,
                        region.getMinimumPoint()
                );
                Operations.completeLegacy(copy);
            }

            // 4. 保存到文件（使用 Sponge Schematic 格式）
            try (FileOutputStream fos = new FileOutputStream(file);
                 ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(fos)) {
                writer.write(clipboard);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
