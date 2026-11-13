package com.him.dungeons.api.event;

import com.him.dungeons.api.hook.DungeonContext;
import com.him.dungeons.util.RoomInstance;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.HandlerList;

import java.util.List;

/**
 * 地牢实例就绪、玩家传送前（DungeonReady）。
 * <p>此时 {@code world} / {@code placedRooms} / {@code spawnLocation} 已填充。不可取消。</p>
 */
public class DungeonReadyEvent extends HimDungeonsEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonContext ctx;

    public DungeonReadyEvent(DungeonContext ctx) { this.ctx = ctx; }

    public DungeonContext getDungeon()        { return ctx; }
    public String getDungeonName()            { return ctx.dungeonName; }
    public World getWorld()                   { return ctx.world; }
    public List<RoomInstance> getRooms()      { return ctx.placedRooms; }
    public Location getSpawnLocation()        { return ctx.spawnLocation; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()  { return HANDLERS; }
}