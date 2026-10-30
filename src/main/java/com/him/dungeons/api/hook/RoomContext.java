package com.him.dungeons.api.hook;

import com.him.dungeons.util.RoomInstance;

/** 房间级通用上下文（放置 / 粘贴）。 */
public final class RoomContext {
    public final DungeonContext dungeon;
    public final RoomInstance room;
    public RoomContext(DungeonContext dungeon, RoomInstance room) {
        this.dungeon = dungeon;
        this.room = room;
    }
}