package com.him.dungeons.util;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.util.List;
import java.util.Random;

public final class WorldUtil {

    private WorldUtil() {}

    private static ChunkGenerator getVoidGenerator() {
        return new ChunkGenerator() {
            @Override
            public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
                return createChunkData(world);
            }
        };
    }

    public static World createVoidWorld(String worldName) {
        if (worldName == null || worldName.trim().isEmpty()) {
            throw new IllegalArgumentException("World name cannot be empty");
        }
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
        String name = world.getName();

        // 踢出所有玩家
        world.getPlayers().forEach(p -> p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation()));

        if (!Bukkit.unloadWorld(world, false)) {
            // 尝试强制卸载
            try {
                Bukkit.getServer().unloadWorld(name, false);
            } catch (Exception ignored) {}
        }

        // 使用 World.getWorldFolder() 获取实际文件夹（1.16+ 支持）
        File worldFolder = world.getWorldFolder();
        if (worldFolder == null || !worldFolder.exists()) {
            // 降级尝试
            worldFolder = new File(Bukkit.getWorldContainer(), name);
        }
        return deleteDirectory(worldFolder);
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

    public static Location toLocation(BlockVector3 vec, World world) {
        if (vec == null || world == null) return null;
        return new Location(world, vec.getX(), vec.getY(), vec.getZ());
    }
}