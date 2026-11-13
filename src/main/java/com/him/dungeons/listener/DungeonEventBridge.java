package com.him.dungeons.listener;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.api.event.*;
import com.him.dungeons.api.hook.DungeonHookRegistry;
import org.bukkit.Bukkit;

/**
 * 把内部 {@link DungeonHookRegistry} 的钩子转发为 Bukkit Event。
 *
 * <p>关键约定：可取消事件（Enter / Leave / BossSpawn / BossDefeat / Block*）必须
 * 把 {@code event.isCancelled()} 回写到对应 {@code Context.cancelled}，
 * 否则外部 {@code setCancelled(true)} 无效。</p>
 *
 * <p>应在 {@code onEnable()} 中调用一次；reload 时 {@code hookRegistry.clearAll()}
 * 会清空本桥的注册，需要重新 {@link #install(HimDungeons)}。</p>
 */
public final class DungeonEventBridge {

    private DungeonEventBridge() {}

    public static void install(HimDungeons plugin) {
        DungeonHookRegistry hooks = plugin.getHookRegistry();

        // ===== L3 玩家级 =====
        hooks.onBeforeEnter(ctx -> {
            PlayerEnterDungeonEvent e = new PlayerEnterDungeonEvent(ctx);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) ctx.cancelled = true;
        });

        hooks.onBeforeLeave(ctx -> {
            PlayerLeaveDungeonEvent e = new PlayerLeaveDungeonEvent(ctx);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) ctx.cancelled = true;
        });

        hooks.onFinish(ctx ->
                Bukkit.getPluginManager().callEvent(new DungeonFinishEvent(ctx)));

        hooks.onDungeonReady(ctx ->
                Bukkit.getPluginManager().callEvent(new DungeonReadyEvent(ctx)));

        // ===== L4 Boss 级 =====
        hooks.onBossSpawn(ctx -> {
            DungeonBossSpawnEvent e = new DungeonBossSpawnEvent(ctx);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) {
                ctx.boss = null;
            } else if (e.getBoss() != ctx.boss) {
                ctx.boss = e.getBoss();
            }
        });

        hooks.onBossDefeat(ctx -> {
            DungeonBossDefeatEvent e = new DungeonBossDefeatEvent(ctx);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) ctx.cancelled = true;
        });

        // ===== L2 细粒度触发器 =====
        hooks.onBlockPlace(ctx -> {
            DungeonBlockPlaceEvent e = new DungeonBlockPlaceEvent(ctx);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) ctx.cancelled = true;
        });

        hooks.onBlockBreak(ctx -> {
            DungeonBlockBreakEvent e = new DungeonBlockBreakEvent(ctx);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) ctx.cancelled = true;
        });

        hooks.onBlockInteract(ctx -> {
            DungeonBlockInteractEvent e = new DungeonBlockInteractEvent(ctx);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) ctx.cancelled = true;
        });

        hooks.onEntityDeath(ctx -> {
            DungeonEntityDeathEvent e = new DungeonEntityDeathEvent(ctx);
            Bukkit.getPluginManager().callEvent(e);
            ctx.dropItems = e.isDropItems();
            ctx.dropExp   = e.isDropExp();
        });

        hooks.onRegionEnter(ctx ->
                Bukkit.getPluginManager().callEvent(new DungeonRegionEnterEvent(ctx)));

        hooks.onRegionLeave(ctx ->
                Bukkit.getPluginManager().callEvent(new DungeonRegionLeaveEvent(ctx)));

        // ===== L5 目标级 =====
        hooks.onObjectiveComplete(ctx ->
                Bukkit.getPluginManager().callEvent(new DungeonObjectiveCompleteEvent(ctx)));

        hooks.onObjectiveFail(ctx ->
                Bukkit.getPluginManager().callEvent(new DungeonObjectiveFailEvent(ctx)));
    }
}