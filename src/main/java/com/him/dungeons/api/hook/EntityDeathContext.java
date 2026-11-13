package com.him.dungeons.api.hook;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * 地牢内任意实体死亡（含 Boss）。
 * <p>死亡事件无法"复活"，cancelled = 取消掉落物与经验。</p>
 */
public final class EntityDeathContext {
    public final DungeonContext dungeon;
    public final LivingEntity entity;
    /** 击杀者（可能为 null：摔死/岩浆/自然消失） */
    public final Player killer;
    /** true = 同时取消掉落物与 XP */
    public boolean cancelled;
    public boolean dropItems = true;
    public boolean dropExp   = true;

    public EntityDeathContext(DungeonContext dungeon, LivingEntity entity, Player killer) {
        this.dungeon = dungeon;
        this.entity = entity;
        this.killer = killer;
    }
}