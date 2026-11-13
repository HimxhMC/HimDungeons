package com.him.dungeons.api.event;

import com.him.dungeons.api.hook.DungeonContext;
import com.him.dungeons.api.hook.PlayerHookContext;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * 玩家通关结算完成、奖励已发放后（Finish）。
 * <p>通知性事件，不可取消。</p>
 */
public class DungeonFinishEvent extends HimDungeonsEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final PlayerHookContext ctx;

    public DungeonFinishEvent(PlayerHookContext ctx) { this.ctx = ctx; }

    public Player getPlayer()           { return ctx.player; }
    public DungeonContext getDungeon()  { return ctx.dungeon; }
    public String getDungeonName()      { return ctx.dungeon.dungeonName; }
    public PlayerHookContext getContext() { return ctx; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()  { return HANDLERS; }
}