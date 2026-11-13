package com.him.dungeons.api.event;

import com.him.dungeons.api.hook.BossHookContext;
import com.him.dungeons.api.hook.DungeonContext;
import com.him.dungeons.util.RoomInstance;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.HandlerList;

/**
 * Boss 已生成、注册前（BossSpawn）。
 * <p>取消 = 取消本次 Boss 生成；或通过 {@link #setBoss(LivingEntity)} 替换实体。</p>
 */
public class DungeonBossSpawnEvent extends CancellableHimDungeonsEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BossHookContext ctx;
    private LivingEntity boss;

    public DungeonBossSpawnEvent(BossHookContext ctx) {
        this.ctx = ctx;
        this.boss = ctx.boss;
    }

    public DungeonContext getDungeon()      { return ctx.dungeon; }
    public RoomInstance getBossRoom()       { return ctx.bossRoom; }
    public Location getSpawnLocation()      { return ctx.spawnLocation; }
    public LivingEntity getBoss()           { return boss; }
    public void setBoss(LivingEntity boss)  { this.boss = boss; }
    public BossHookContext getContext()     { return ctx; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()  { return HANDLERS; }
}