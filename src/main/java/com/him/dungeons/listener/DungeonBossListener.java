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
                }

                finishDungeon(worldName, data);
                break;
            }
        }
    }

    private void finishDungeon(String worldName, HimDungeons.BossData data) {
        HimDungeons.DungeonInstance instance = plugin.getDungeonInstanceByWorld(worldName);
        com.him.dungeons.api.hook.DungeonContext ctx =
                instance != null ? instance.hookContext : null;

        if (ctx != null) plugin.getHookRegistry().fireBeforeDestroy(ctx);

        if (instance != null) {
            instance.isFinished = true;
            plugin.cancelCleanupTask(worldName);
        }

        Set<UUID> players = plugin.getDungeonPlayers(worldName);
        if (players != null) {
            for (UUID uuid : players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline() && instance != null) {
                    plugin.getSessionService().finish(p, instance, data.applier);
                }
            }
        }

        // 清理 BossBar
        if (plugin.getBossBarManager() != null) plugin.getBossBarManager().remove(worldName);
        if (plugin.getBossPhaseManager() != null && instance != null) {
            // 注意：此时 Boss 已被击杀，但 data.entity 仍是旧引用
            if (data != null && data.entity != null) {
                plugin.getBossPhaseManager().removeBoss(data.entity);
            }
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) WorldUtil.deleteWorldAndRelease(world);
        plugin.removeWorldPrefix(worldName);
        plugin.unregisterDungeonWorld(worldName);
        plugin.dungeonInstances.remove(worldName);
        plugin.dungeonNameToWorld.remove(data.dungeonName);
        plugin.dungeonBosses.remove(worldName);
        plugin.dungeonPlayers.remove(worldName);
        plugin.configAppliers.remove(worldName);
        plugin.cancelCleanupTask(worldName);
        plugin.getSessionService().clearOfflineRecordsForWorld(worldName);

        if (ctx != null) plugin.getHookRegistry().fireAfterDestroy(ctx);
    }
}