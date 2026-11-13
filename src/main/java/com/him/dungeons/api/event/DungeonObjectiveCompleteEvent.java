package com.him.dungeons.api.event;

import com.him.dungeons.api.hook.DungeonContext;
import com.him.dungeons.api.hook.ObjectiveContext;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * 主目标完成（ObjectiveComplete）。
 * <p>通知性事件。由 {@code DungeonAPI.markObjectiveComplete} 触发。</p>
 */
public class DungeonObjectiveCompleteEvent extends HimDungeonsEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ObjectiveContext ctx;

    public DungeonObjectiveCompleteEvent(ObjectiveContext ctx) { this.ctx = ctx; }

    /** 触发者，可能为 null */
    public Player getInitiator()         { return ctx.initiator; }
    /** 完成原因/objectiveId */
    public String getReason()            { return ctx.reason; }
    public DungeonContext getDungeon()   { return ctx.dungeon; }
    public ObjectiveContext getContext() { return ctx; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()  { return HANDLERS; }
}