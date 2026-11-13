package com.him.dungeons.generator;

import com.him.dungeons.schematic.AsyncPaster;
import com.him.dungeons.util.RoomInstance;
import org.bukkit.Location;

import java.util.Collections;
import java.util.List;

/** 池子世界预计算的结果缓存（不依赖 World，纯数据） */
public final class PrecomputedDungeon {

    public final String dungeonName;
    /** 全部方块（世界坐标 + 已旋转的 BlockData） */
    public final List<AsyncPaster.PlacedBlockPublic> blocks;
    /** 全部方块实体（世界坐标 + 原始 NBT 数据，已旋转） */
    public final List<AsyncPaster.BlockEntityPlacement> blockEntities;
    /** 房间列表（用于启动时重建 DungeonInstance） */
    public final List<RoomInstance> placedRooms;
    /** 起始房出生点（相对世界坐标） */
    public final Location spawnLocation;
    public final long computedAt;

    public PrecomputedDungeon(String dungeonName,
                              List<AsyncPaster.PlacedBlockPublic> blocks,
                              List<AsyncPaster.BlockEntityPlacement> blockEntities,
                              List<RoomInstance> placedRooms,
                              Location spawnLocation) {
        this.dungeonName = dungeonName;
        this.blocks = blocks;
        this.blockEntities = blockEntities == null ? Collections.emptyList() : blockEntities;
        this.placedRooms = placedRooms;
        this.spawnLocation = spawnLocation;
        this.computedAt = System.currentTimeMillis();
    }

    /** 兼容旧签名（无 blockEntities） */
    public PrecomputedDungeon(String dungeonName,
                              List<AsyncPaster.PlacedBlockPublic> blocks,
                              List<RoomInstance> placedRooms,
                              Location spawnLocation) {
        this(dungeonName, blocks, Collections.emptyList(), placedRooms, spawnLocation);
    }
}