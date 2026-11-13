package com.him.dungeons.util;
import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.Pos3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class WorldUtil {

    private WorldUtil() {}

    private static ChunkGenerator getVoidGenerator() {
        return new VoidChunkGenerator();
    }

    public static World createVoidWorld(String worldName) {
        if (worldName == null || worldName.trim().isEmpty()) {
            throw new IllegalArgumentException("World name cannot be empty");
        }
        // 优先走模板路径（约 100ms）
        World fromTemplate = tryCreateFromTemplateSync(worldName);
        if (fromTemplate != null) return fromTemplate;

        // 模板不可用 → 全新建（约 5s，仅首次或模板损坏时）
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            deleteWorldAndRelease(existing);
        }

        WorldCreator creator = new WorldCreator(worldName);
        creator.generateStructures(false);
        creator.generator(getVoidGenerator());
        creator.environment(World.Environment.NORMAL);

        World world = Bukkit.createWorld(creator);
        if (world == null) {
            throw new IllegalStateException("Failed to create void world: " + worldName);
        }

        world.setAutoSave(false);
        world.setPVP(false);
        world.setMonsterSpawnLimit(0);
        world.setAnimalSpawnLimit(0);
        world.setWaterAnimalSpawnLimit(0);
        world.setAmbientSpawnLimit(0);

        return world;
    }

    public static void applyWorldSettings(World world, FileConfiguration config) {
        if (world == null || config == null) return;

        long dungeonTime = ConfigUtil.getInstanceDungeonTime(config);
        boolean advanceTime = ConfigUtil.isInstanceAdvanceTime(config);
        world.setTime(dungeonTime);
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, advanceTime);

        String weather = ConfigUtil.getInstanceWeather(config);
        switch (weather.toLowerCase()) {
            case "clear":
                world.setStorm(false);
                world.setThundering(false);
                break;
            case "rain":
                world.setStorm(true);
                world.setThundering(false);
                break;
            case "thunder":
                world.setStorm(true);
                world.setThundering(true);
                break;
            default:
                world.setStorm(false);
                world.setThundering(false);
        }
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);

        String difficulty = ConfigUtil.getInstanceDifficulty(config);
        try {
            world.setDifficulty(org.bukkit.Difficulty.valueOf(difficulty.toUpperCase()));
        } catch (IllegalArgumentException e) {
            world.setDifficulty(org.bukkit.Difficulty.NORMAL);
        }

        List<String> allowMobs = ConfigUtil.getInstanceAllowMobs(config);
        boolean mobsEnabled = !allowMobs.isEmpty() && !allowMobs.contains("none");
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, mobsEnabled);
        if (!mobsEnabled) {
            world.setMonsterSpawnLimit(0);
            world.setAnimalSpawnLimit(0);
            world.setWaterAnimalSpawnLimit(0);
            world.setAmbientSpawnLimit(0);
        } else {
            world.setMonsterSpawnLimit(20);
            world.setAnimalSpawnLimit(10);
            world.setWaterAnimalSpawnLimit(5);
            world.setAmbientSpawnLimit(5);
        }

        boolean keepInventory = ConfigUtil.isInstanceKeepInventory(config);
        world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, keepInventory);
        world.setGameRule(org.bukkit.GameRule.SHOW_DEATH_MESSAGES, true);
        world.setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, false);
    }

    /**
     * 删除世界并清理磁盘文件（使用 World.getWorldFolder() 适配新版本路径）
     */
public static boolean deleteWorldAndRelease(World world) {
    if (world == null) return false;
    if (world.equals(Bukkit.getWorlds().get(0))) {
        Bukkit.getLogger().warning("[HimDungeons] 拒绝删除主世界: " + world.getName());
        return false;
    }
    String name = world.getName();
    world.getPlayers().forEach(p -> p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation()));
    Bukkit.unloadWorld(world, false);

    File dimFolder = getDimensionFolder(name);
    if (deleteDirectoryStrict(dimFolder)) return true;
    Bukkit.getScheduler().runTaskLater(HimDungeons.getInstance(),
            () -> deleteDirectoryStrict(dimFolder), 100L);
    return true;
}

    private static boolean deleteDirectoryStrict(File dir) {
        if (dir == null || !dir.exists()) return true;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) deleteDirectoryStrict(child);
            }
        }
        boolean ok = dir.delete();
        if (!ok) {
            // 加写权限后重试（Windows 上常见）
            try {
                dir.setWritable(true);
                ok = dir.delete();
            } catch (Exception ignored) {}
        }
        if (!ok) Bukkit.getLogger().warning("[HimDungeons] 删除失败: " + dir.getPath());
        return ok;
    }

/** 可靠地解析世界根目录（兼容 Paper 1.21+ 维度布局） */
private static File resolveWorldRoot(World world) {
    if (world == null) return null;
    try {
        File container = Bukkit.getWorldContainer().getCanonicalFile();
        File candidate = new File(container, world.getName()).getCanonicalFile();
        if (!candidate.getParentFile().equals(container)) {
            Bukkit.getLogger().warning("[HimDungeons] 世界根路径越界，拒绝处理: "
                    + candidate.getPath() + "（父级应为 " + container.getPath() + "）");
            return null;
        }
        return candidate;
    } catch (java.io.IOException e) {
        Bukkit.getLogger().warning("[HimDungeons] 路径规范化失败: " + e.getMessage());
        return null;
    }
}

    private static boolean deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return true;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) deleteDirectory(child);
            }
        }
        return dir.delete();
    }

    public static Location toLocation(Pos3 vec, World world) {
        if (vec == null || world == null) return null;
        return new Location(world, vec.getX(), vec.getY(), vec.getZ());
    }
    // ==================== 世界模板 ====================

    /** 模板世界目录：plugins/HimDungeons/world_template/ */
    public static File getWorldTemplateFolder(HimDungeons plugin) {
        return new File(plugin.getDataFolder(), "world_template");
    }

    /** 同步走模板路径（生成地牢时用；模板就绪时约 100ms） */
private static World tryCreateFromTemplateSync(String worldName) {
    HimDungeons plugin = HimDungeons.getInstance();
    if (plugin == null) return null;
    File templateDir = getWorldTemplateFolder(plugin);
    if (!isTemplateHealthy(templateDir)) return null;

    World existing = Bukkit.getWorld(worldName);
    if (existing != null) deleteWorldAndRelease(existing);

    File targetDir = getDimensionFolder(worldName);
    if (targetDir.exists()) deleteDirectoryStrict(targetDir);
    if (!copyDirectory(templateDir, targetDir)) return null;

    WorldCreator creator = new WorldCreator(worldName);
    creator.generateStructures(false);
    creator.generator(getVoidGenerator());
    creator.environment(World.Environment.NORMAL);
    World world = Bukkit.createWorld(creator);
    if (world == null) return null;

    applyVoidDefaults(world);
    return world;
}

public static void ensureWorldTemplate(HimDungeons plugin, Consumer<Boolean> callback) {
    File templateDir = getWorldTemplateFolder(plugin);
    if (isTemplateHealthy(templateDir)) {
        callback.accept(true);
        return;
    }
    if (templateDir.exists()) {
        plugin.getLogger().warning("[世界模板] 检测到损坏模板，清空重建: " + templateDir.getPath());
        deleteDirectoryStrict(templateDir);
    }
    plugin.getLogger().info("[世界模板] 首次初始化，创建模板世界（约 5 秒）...");

    String tmpName = "himdungeons_tpl_init_" + System.currentTimeMillis();
    World tmp;
    try {
        tmp = createVoidWorld(tmpName);
    } catch (Exception e) {
        plugin.getLogger().warning("[世界模板] 创建失败: " + e.getMessage());
        callback.accept(false);
        return;
    }
    if (tmp == null) { callback.accept(false); return; }

    // ★ 关键：unload 后所有数据已刷盘，用 getDimensionFolder 自己算路径
    tmp.save();
    Bukkit.unloadWorld(tmp, true);

    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        File srcFolder = getDimensionFolder(tmpName);
        if (!srcFolder.exists()) {
            plugin.getLogger().warning("[世界模板] 临时维度目录不存在: " + srcFolder.getPath());
            callback.accept(false);
            return;
        }

        final File finalSrc = srcFolder;
        final File finalTemplate = templateDir;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok = copyDirectory(finalSrc, finalTemplate) && isTemplateHealthy(finalTemplate);
            deleteDirectoryStrict(finalSrc);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ok) {
                    plugin.getLogger().info("[世界模板] 初始化完成: " + finalTemplate.getPath());
                } else {
                    plugin.getLogger().warning("[世界模板] 复制失败或不健康: " + finalTemplate.getPath());
                    deleteDirectoryStrict(finalTemplate);
                }
                callback.accept(ok);
            });
        });
    }, 10L);
}
public static boolean isTemplateHealthyPublic(File templateDir) {
    return isTemplateHealthy(templateDir);
}
public static boolean deleteDirectoryStrictPublic(File dir) {
    return deleteDirectoryStrict(dir);
}
private static boolean isTemplateHealthy(File templateDir) {
    if (templateDir == null || !templateDir.isDirectory()) return false;
    if (isUsingNewDimensionStorage()) {
        // 新版维度：必须有 data/minecraft 或 paper-world.yml
        return new File(templateDir, "data/minecraft").exists()
                || new File(templateDir, "paper-world.yml").exists();
    } else {
        return new File(templateDir, "level.dat").exists();
    }
}

    /** 异步走模板路径（补世界池时用，不阻塞主线程 IO） */
    public static void createWorldFromTemplateAsync(HimDungeons plugin, String targetName,
                                                    Consumer<World> callback) {
        File templateDir = getWorldTemplateFolder(plugin);
        if (!new File(templateDir, "level.dat").exists()) {
            // 模板不在，回退到同步创建
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(createVoidWorld(targetName)));
            return;
        }
        File targetDir = new File(Bukkit.getWorldContainer(), targetName);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (targetDir.exists()) deleteDirectory(targetDir);
            boolean ok = copyDirectory(templateDir, targetDir);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!ok) { callback.accept(null); return; }
                World existing = Bukkit.getWorld(targetName);
                if (existing != null) deleteWorldAndRelease(existing);

                WorldCreator creator = new WorldCreator(targetName);
                creator.generateStructures(false);
                creator.generator(getVoidGenerator());
                creator.environment(World.Environment.NORMAL);
                World world = Bukkit.createWorld(creator);
                if (world != null) {
                    world.setAutoSave(false);
                    world.setPVP(false);
                    world.setMonsterSpawnLimit(0);
                    world.setAnimalSpawnLimit(0);
                    world.setWaterAnimalSpawnLimit(0);
                    world.setAmbientSpawnLimit(0);
                }
                callback.accept(world);
            });
        });
    }

    /** 递归复制目录（Java NIO，保持目录结构） */
    private static boolean copyDirectory(File src, File dst) {
        if (src == null || !src.exists()) return false;
        try {
            Path srcPath = src.toPath();
            Path dstPath = dst.toPath();
            try (java.util.stream.Stream<Path> walk = Files.walk(srcPath)) {
                walk.forEach(srcFile -> {
                    try {
                        Path rel = srcPath.relativize(srcFile);
                        Path dstFile = dstPath.resolve(rel);
                        if (Files.isDirectory(srcFile)) {
                            Files.createDirectories(dstFile);
                        } else {
                            Files.createDirectories(dstFile.getParent());
                            Files.copy(srcFile, dstFile, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    // ==================== 世界池（不加载） ====================

    /** 加载已存在的世界文件夹（适配新版本路径） */
public static World loadFromFolder(String worldName) {
    World existing = Bukkit.getWorld(worldName);
    if (existing != null) return existing;
    WorldCreator creator = new WorldCreator(worldName);
    creator.generateStructures(false);
    creator.generator(getVoidGenerator());
    creator.environment(World.Environment.NORMAL);
    World world = Bukkit.createWorld(creator);
    if (world != null) applyVoidDefaults(world);
    return world;
}

    private static void applyVoidDefaults(World world) {
        world.setAutoSave(false);
        world.setPVP(false);
        world.setMonsterSpawnLimit(0);
        world.setAnimalSpawnLimit(0);
        world.setWaterAnimalSpawnLimit(0);
        world.setAmbientSpawnLimit(0);
    }


/** 池子文件夹根：plugins/HimDungeons/pool/ */
public static File getPoolFolder(HimDungeons plugin) {
    return new File(plugin.getDataFolder(), "pool");
}

public static List<String> listPoolWorldFolders(HimDungeons plugin) {
    List<String> result = new ArrayList<>();
    File poolDir = getPoolFolder(plugin);
    File[] dirs = poolDir.listFiles(File::isDirectory);
    if (dirs == null) return result;
    for (File d : dirs) {
        if (d.getName().startsWith(HimDungeons.POOL_WORLD_PREFIX)) {
            result.add(d.getName());
        }
    }
    return result;
}

public static void createPoolWorldFolderAsync(HimDungeons plugin, String targetName,
                                              Consumer<String> callback) {
    File templateDir = getWorldTemplateFolder(plugin);
    if (!isTemplateHealthy(templateDir)) {
        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
        return;
    }
    File poolDir = getPoolFolder(plugin);
    if (!poolDir.exists()) poolDir.mkdirs();
    File targetDir = new File(poolDir, targetName);
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        if (targetDir.exists()) deleteDirectoryStrict(targetDir);
        boolean ok = copyDirectory(templateDir, targetDir);
        Bukkit.getScheduler().runTask(plugin,
                () -> callback.accept(ok ? targetName : null));
    });
}


/** 公开的严格删除（供 HimDungeons 调用） */
public static boolean deleteWorldStrictPublic(File dir) {
    return deleteDirectoryStrict(dir);
}
public static boolean isUsingNewDimensionStorage() {
    try {
        // Paper 26.1+ 在 Server 上引入 getLevelDirectory，旧版没有
        org.bukkit.Server.class.getMethod("getLevelDirectory");
        return true;
    } catch (NoSuchMethodException e) {
        return false;
    }
}
/**
 * 维度文件夹基础位置：
 * - 新版：<mainWorld>/dimensions/minecraft/
 * - 旧版：worldContainer/
 */
public static File getDimensionBaseFolder() {
    World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    if (main == null) return Bukkit.getWorldContainer();
    if (isUsingNewDimensionStorage()) {
        try {
            File mainFolder = main.getWorldFolder().getCanonicalFile();
            return mainFolder.getParentFile();
        } catch (Exception e) {
            return Bukkit.getWorldContainer();
        }
    }
    return Bukkit.getWorldContainer();
}

/** 某个维度/世界在磁盘上的文件夹 */
public static File getDimensionFolder(String name) {
    return new File(getDimensionBaseFolder(), name);
}
// ==================== 池子借用 ====================

/**
 * 把插件目录下的池子世界文件夹复制为正式维度并加载。
 *
 * <p>流程：</p>
 * <ol>
 *   <li>从 {@code plugins/HimDungeons/pool/<poolName>} 复制到维度目录 {@code <base>/<targetName>}</li>
 *   <li>通过 {@link #loadFromFolder(String)} 加载为 World</li>
 *   <li>成功后删除源池子文件夹</li>
 * </ol>
 *
 * @param plugin     插件实例（用于获取 pool 目录）
 * @param poolName   池子文件夹名（形如 {@code himdungeons_pool_xxx}）
 * @param targetName 目标维度名（形如 {@code dungeon_xxx_ts}）
 * @return 加载后的 World，失败返回 null
 */
    public static World consumePoolWorldAs(HimDungeons plugin, String poolName, String targetName) {
        if (plugin == null || poolName == null || targetName == null) return null;

        File poolDir = new File(getPoolFolder(plugin), poolName);
        if (!poolDir.exists()) {
            plugin.getLogger().warning("[世界池] 源池子不存在: " + poolDir.getPath());
            return null;
        }

        World loadedPool = Bukkit.getWorld(poolName);
        if (loadedPool != null) Bukkit.unloadWorld(loadedPool, false);
        World loadedTarget = Bukkit.getWorld(targetName);
        if (loadedTarget != null) Bukkit.unloadWorld(loadedTarget, false);

        File targetDir = getDimensionFolder(targetName);
        if (targetDir.exists()) deleteDirectoryStrict(targetDir);

        if (!copyDirectory(poolDir, targetDir)) {
            plugin.getLogger().warning("[世界池] 复制失败: " + poolName + " -> " + targetName);
            return null;
        }

        World world = loadFromFolder(targetName);
        if (world == null) {
            plugin.getLogger().warning("[世界池] 加载失败: " + targetName);
            deleteDirectoryStrict(targetDir);
            return null;
        }

        // 复制成功，删除源池子（延时 + 重试，避免文件锁）
        boolean deleted = deleteDirectoryStrict(poolDir);
        if (!deleted) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (deleteDirectoryStrict(poolDir)) {
                    plugin.getLogger().info("[世界池] 延迟删除源池子: " + poolName);
                } else {
                    plugin.getLogger().warning("[世界池] 源池子删除失败: " + poolName);
                }
            }, 100L);
        }
        return world;
    }
}