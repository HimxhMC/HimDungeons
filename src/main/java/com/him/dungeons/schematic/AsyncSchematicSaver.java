package com.him.dungeons.schematic;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.SelectionManager;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.function.Consumer;

/**
 * 异步原理图保存器。
 * 替代 WorldEdit 的 saveSchematic。
 */
public final class AsyncSchematicSaver {

    private AsyncSchematicSaver() {}

    /**
     * 异步保存选区为原理图文件。
     *
     * @param selection 选区
     * @param file      目标文件
     * @param version   格式版本（2 或 3）
     * @param callback  完成回调
     */
    public static void saveAsync(SelectionManager.Selection selection, File file,
                                 int version, Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(HimDungeons.getInstance(), () -> {
            // 1. 在主线程读取世界方块数据（Bukkit 要求）
            //    但为了不阻塞，我们分批在主线程读取，然后异步写入
            Bukkit.getScheduler().runTask(HimDungeons.getInstance(), () -> {
                NbtSchematic schem = selection.toSchematic();

                // 2. 回到异步线程写入文件
                Bukkit.getScheduler().runTaskAsynchronously(HimDungeons.getInstance(), () -> {
                    boolean success = SpongeSchematicWriter.write(file, schem, version);
                    Bukkit.getScheduler().runTask(HimDungeons.getInstance(),
                            () -> callback.accept(success));
                });
            });
        });
    }
}