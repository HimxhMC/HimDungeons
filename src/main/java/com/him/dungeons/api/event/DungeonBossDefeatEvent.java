package com.him.dungeons.api.event;

import com.him.dungeons.api.hook.BossHookContext;
import com.him.dungeons.api.hook.DungeonContext;
import com.him.dungeons.util.RoomInstance;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.HandlerList;

/**
 * Boss 死亡、通关结算前（BossDefeat）。
 * <p>取消 = 阻止本次通关结算（例如多阶段 Boss 想续场）。</p>
 */
public class DungeonBossDefeatEvent extends CancellableHimDungeonsEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BossHookContext ctx;

    public DungeonBossDefeatEvent(BossHookContext ctx) { this.ctx = ctx; }

    public DungeonContext getDungeon()  { return ctx.dungeon; }
    public RoomInstance getBossRoom()   { return ctx.bossRoom; }
    public Location getSpawnLocation()  { return ctx.spawnLocation; }
    public LivingEntity getBoss()       { return ctx.boss; }
    public BossHookContext getContext() { return ctx; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()  { return HANDLERS; }
}