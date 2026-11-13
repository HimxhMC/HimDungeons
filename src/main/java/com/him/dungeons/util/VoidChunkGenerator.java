package com.him.dungeons.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

/**
 * 持久化虚空区块生成器。
 *
 * <p>与匿名内部类不同，独立类可以被 Bukkit 记录在 level.dat 中，
 * 配合"模板世界复制"路径把 createWorld 从 5s 压到 ~100ms。</p>
 *
 * <p>关键优化：{@link #getFixedSpawnLocation} 直接返回固定坐标，
 * 跳过原版漫长的"安全出生点搜索"——这是创世 5s 的主因之一。</p>
 */
public class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        return createChunkData(world);
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        // 强制固定出生点：跳过安全出生点搜索
        return new Location(world, 0, 64, 0);
    }
}