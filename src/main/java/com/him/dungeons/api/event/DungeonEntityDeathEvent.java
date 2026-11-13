package com.him.dungeons.api.event;

import com.him.dungeons.api.hook.DungeonContext;
import com.him.dungeons.api.hook.EntityDeathContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * 地牢内任意实体死亡（EntityDeath）。
 * <p>死亡本身不可取消；通过 {@link #setDropItems(boolean)} / {@link #setDropExp(boolean)} 控制掉落。</p>
 */
public class DungeonEntityDeathEvent extends HimDungeonsEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final EntityDeathContext ctx;
    private boolean dropItems;
    private boolean dropExp;

    public DungeonEntityDeathEvent(EntityDeathContext ctx) {
        this.ctx = ctx;
        this.dropItems = ctx.dropItems;
        this.dropExp = ctx.dropExp;
    }

    public LivingEntity getEntity()      { return ctx.entity; }
    /** 击杀者，可能为 null（摔死/岩浆/自然消失） */
    public Player getKiller()            { return ctx.killer; }
    public DungeonContext getDungeon()   { return ctx.dungeon; }
    public boolean isDropItems()         { return dropItems; }
    public void setDropItems(boolean v)  { this.dropItems = v; }
    public boolean isDropExp()           { return dropExp; }
    public void setDropExp(boolean v)    { this.dropExp = v; }
    public EntityDeathContext getContext() { return ctx; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()  { return HANDLERS; }
}