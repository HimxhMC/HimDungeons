package com.him.dungeons.listener;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.LanguageManager;
import com.him.dungeons.util.WorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Set;
import java.util.UUID;

public class DungeonBossListener implements Listener {

    private final HimDungeons plugin;

    public DungeonBossListener(HimDungeons plugin) { this.plugin = plugin; }

    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        LivingEntity entity = e.getEntity();
        for (String worldName : plugin.dungeonBosses.keySet()) {
            HimDungeons.BossData data = plugin.getBossData(worldName);
            if (data == null || data.entity == null) continue;
            if (data.entity.getUniqueId().equals(entity.getUniqueId())) {
                HimDungeons.DungeonInstance instance = plugin.getDungeonInstanceByWorld(worldName);
                com.him.dungeons.api.hook.DungeonContext ctx =
                        instance != null ? instance.hookContext : null;
                if (ctx != null) {
                    com.him.dungeons.api.hook.BossHookContext bhc =
                            new com.him.dungeons.api.hook.BossHookContext(
                                    ctx, data.bossRoom, data.spawnLocation, data.entity);
                    plugin.getHookRegistry().fireBossDefeat(bhc);
                    if (bhc.cancelled) {
                        plugin.getLogger().info(LanguageManager.get(
                                "log.hook.boss-defeat-vetoed", "world", worldName));
                        return;
                    }

                    // ★ 标记 Boss 已击败
                    ctx.bossDefeated = true;
                    ctx.attrs.put("boss_defeated", true);

                    // ★ 检查是否需要等待额外目标
                    org.bukkit.configuration.file.FileConfiguration cfg =
                            com.him.dungeons.util.ConfigUtil.getDungeonConfig(data.dungeonName);
                    boolean requireObjective = cfg != null
                            && com.him.dungeons.util.ConfigUtil.isInstanceRequireObjective(cfg);

                    if (requireObjective && !ctx.objectiveComplete) {
                        plugin.getLogger().info("[地牢] " + data.dungeonName
                                + " Boss 已死，等待额外目标完成");
                        if (instance != null) {
                            for (java.util.UUID uuid : instance.players) {
                                org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(uuid);
                                if (p != null && p.isOnline()) {
                                    com.him.dungeons.util.MessageUtil.sendMessage(
                                            p, "objective.boss-defeated-wait");
                                }
                            }
                        }
                        return;
                    }
                }

                finishDungeon(instance, data.applier);
                break;
            }
        }
    }

    /** 胜利结算（Boss 死亡 / DungeonAPI.markObjectiveComplete 触发）。 */
    public void finishDungeon(HimDungeons.DungeonInstance instance,
                              com.him.dungeons.generator.ConfigApplier applier) {
        if (instance == null || instance.isFinished) return;
        String worldName = instance.worldName;
        com.him.dungeons.api.hook.DungeonContext ctx = instance.hookContext;

        if (ctx != null) plugin.getHookRegistry().fireBeforeDestroy(ctx);

        instance.isFinished = true;
        // ★ 记录成功通关
        plugin.getRunRecordService().record(instance, true, null);
        plugin.cancelCleanupTask(worldName);

        java.util.Set<java.util.UUID> players = plugin.getDungeonPlayers(worldName);
        if (players != null) {
            for (java.util.UUID uuid : new java.util.HashSet<>(players)) {
                org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline() && applier != null) {
                    plugin.getSessionService().finish(p, instance, applier);
                }
            }
        }

        if (plugin.getBossBarManager() != null) plugin.getBossBarManager().remove(worldName);
        HimDungeons.BossData data = plugin.dungeonBosses.get(worldName);
        if (plugin.getBossPhaseManager() != null && data != null && data.entity != null) {
            plugin.getBossPhaseManager().removeBoss(data.entity);
        }
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world != null) com.him.dungeons.util.WorldUtil.deleteWorldAndRelease(world);
        plugin.removeWorldPrefix(worldName);
        plugin.unregisterDungeonWorld(worldName);
        plugin.dungeonInstances.remove(worldName);
        plugin.dungeonNameToWorld.remove(instance.dungeonName);
        plugin.dungeonBosses.remove(worldName);
        plugin.dungeonPlayers.remove(worldName);
        plugin.configAppliers.remove(worldName);
        plugin.cancelCleanupTask(worldName);
        plugin.getSessionService().clearOfflineRecordsForWorld(worldName);

        if (ctx != null) plugin.getHookRegistry().fireAfterDestroy(ctx);
    }
}