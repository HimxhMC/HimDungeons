package com.him.dungeons.api.hook;

import com.him.dungeons.util.RoomInstance;
import org.bukkit.entity.Player;

/** 玩家进入/离开某个房间区域。 */
public final class RegionContext {
    public enum Phase { ENTER, LEAVE }

    public final DungeonContext dungeon;
    public final Player player;
    /** ENTER = 进入的房间；LEAVE = 离开的房间 */
    public final RoomInstance room;
    /** 对照房间：ENTER 时是来源，LEAVE 时是去向（可能为 null） */
    public final RoomInstance other;
    public final Phase phase;
    public boolean cancelled;

    public RegionContext(DungeonContext dungeon, Player player,
                         RoomInstance room, RoomInstance other, Phase phase) {
        this.dungeon = dungeon;
        this.player = player;
        this.room = room;
        this.other = other;
        this.phase = phase;
    }
}