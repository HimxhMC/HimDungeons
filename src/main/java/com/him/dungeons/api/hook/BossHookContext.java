package com.him.dungeons.api.hook;

import com.him.dungeons.util.RoomInstance;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/**
 * Boss 生命周期钩子上下文。
 *
 * <p>{@code BossSpawn}：外部可替换 {@link #boss}，设为 null 表示取消生成。</p>
 * <p>{@code BossDefeat}：外部设置 {@link #cancelled} = true 可阻止本次通关结算。</p>
 */
public final class BossHookContext {
    public final DungeonContext dungeon;
    public final RoomInstance bossRoom;
    public final Location spawnLocation;
    public LivingEntity boss;
    public boolean cancelled;

    public BossHookContext(DungeonContext dungeon, RoomInstance bossRoom,
                           Location spawnLocation, LivingEntity boss) {
        this.dungeon = dungeon;
        this.bossRoom = bossRoom;
        this.spawnLocation = spawnLocation;
        this.boss = boss;
    }
}