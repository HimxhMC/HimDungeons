package com.him.dungeons.api.event;

import com.him.dungeons.api.hook.DungeonContext;
import com.him.dungeons.api.hook.PlayerHookContext;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/** 玩家即将离开地牢（BeforeLeave）。取消 = 否决离开。 */
public class PlayerLeaveDungeonEvent extends CancellableHimDungeonsEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final PlayerHookContext ctx;

    public PlayerLeaveDungeonEvent(PlayerHookContext ctx) { this.ctx = ctx; }

    public Player getPlayer()           { return ctx.player; }
    public DungeonContext getDungeon()  { return ctx.dungeon; }
    public String getDungeonName()      { return ctx.dungeon.dungeonName; }
    public PlayerHookContext getContext() { return ctx; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()  { return HANDLERS; }
}