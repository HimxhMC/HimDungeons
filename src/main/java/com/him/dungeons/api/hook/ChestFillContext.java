package com.him.dungeons.api.hook;

import com.him.dungeons.util.RoomInstance;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * 单条战利品填充上下文。
 *
 * <p>插件解析 chest.yml 中一条 loot 后、写入箱子前触发。
 * 外部可修改 {@link #item} / {@link #amount} / {@link #chance}，
 * 或设置 {@link #cancelled} = true 跳过该条。</p>
 */
public final class ChestFillContext {

    public final DungeonContext dungeon;
    public final RoomInstance room;
    public final Location worldLocation;

    public final Material originalItem;
    public final int originalAmount;
    public final int originalChance;

    /** 外部可写：true 跳过此条 */
    public boolean cancelled;
    public Material item;
    public int amount;
    public int chance;

    public ChestFillContext(DungeonContext dungeon, RoomInstance room, Location worldLocation,
                            Material item, int amount, int chance) {
        this.dungeon = dungeon;
        this.room = room;
        this.worldLocation = worldLocation;
        this.originalItem = item;
        this.originalAmount = amount;
        this.originalChance = chance;
        this.item = item;
        this.amount = amount;
        this.chance = chance;
    }
}