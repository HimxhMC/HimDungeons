package com.him.dungeons.api.event;

import com.him.dungeons.api.hook.DungeonContext;
import com.him.dungeons.api.hook.RegionContext;
import com.him.dungeons.util.RoomInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/** 玩家离开某个房间区域（RegionLeave）。通知性事件。 */
public class DungeonRegionLeaveEvent extends HimDungeonsEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final RegionContext ctx;

    public DungeonRegionLeaveEvent(RegionContext ctx) { this.ctx = ctx; }

    public Player getPlayer()           { return ctx.player; }
    public RoomInstance getRoom()       { return ctx.room; }
    /** 去向房间，可能为 null */
    public RoomInstance getToRoom()     { return ctx.other; }
    public DungeonContext getDungeon()  { return ctx.dungeon; }
    public RegionContext getContext()   { return ctx; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()  { return HANDLERS; }
}