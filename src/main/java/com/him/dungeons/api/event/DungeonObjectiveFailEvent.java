package com.him.dungeons.api.event;

import com.him.dungeons.api.hook.DungeonContext;
import com.him.dungeons.api.hook.ObjectiveContext;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * 主目标失败（ObjectiveFail）。
 * <p>通知性事件。由超时 / {@code DungeonAPI.failDungeon} / forceEnd 触发。</p>
 */
public class DungeonObjectiveFailEvent extends HimDungeonsEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ObjectiveContext ctx;

    public DungeonObjectiveFailEvent(ObjectiveContext ctx) { this.ctx = ctx; }

    /** 触发者，可能为 null（超时由系统触发） */
    public Player getInitiator()         { return ctx.initiator; }
    /** 失败原因 */
    public String getReason()            { return ctx.reason; }
    public DungeonContext getDungeon()   { return ctx.dungeon; }
    public ObjectiveContext getContext() { return ctx; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()  { return HANDLERS; }
}