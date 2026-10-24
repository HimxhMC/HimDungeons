package com.him.dungeons.listener;

import com.him.dungeons.HimDungeons;
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

    public DungeonBossListener(HimDungeons plugin) {
        this.plugin = plugin;
    }

@EventHandler
public void onBossDeath(EntityDeathEvent e) {
    LivingEntity entity = e.getEntity();
    for (String worldName : plugin.dungeonBosses.keySet()) {
        HimDungeons.BossData data = plugin.getBossData(worldName);
        if (data == null || data.entity == null) continue;
        if (data.entity.getUniqueId().equals(entity.getUniqueId())) {

            // ★ BossDefeat（可否决结算）
            HimDungeons.DungeonInstance instance = plugin.getDungeonInstanceByWorld(worldName);
            com.him.dungeons.api.hook.DungeonContext dungeonCtx =
                    instance != null ? instance.hookContext : null;
            if (dungeonCtx != null) {
                com.him.dungeons.api.hook.BossHookContext bhc =
                        new com.him.dungeons.api.hook.BossHookContext(
                                dungeonCtx, data.bossRoom, data.spawnLocation, data.entity);
                plugin.getHookRegistry().fireBossDefeat(bhc);
                if (bhc.cancelled) {
                    plugin.getLogger().info("[Hook] Boss 死亡结算被外部否决: " + worldName);
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
    com.him.dungeons.api.hook.DungeonContext dungeonCtx =
            instance != null ? instance.hookContext : null;

    // ★ BeforeDestroy
    if (dungeonCtx != null) plugin.getHookRegistry().fireBeforeDestroy(dungeonCtx);

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

    World world = Bukkit.getWorld(worldName);
    if (world != null) WorldUtil.deleteWorldAndRelease(world);
    plugin.unregisterDungeonWorld(worldName);
    plugin.dungeonInstances.remove(worldName);
    plugin.dungeonNameToWorld.remove(data.dungeonName);
    plugin.dungeonBosses.remove(worldName);
    plugin.dungeonPlayers.remove(worldName);
    plugin.configAppliers.remove(worldName);
    plugin.cancelCleanupTask(worldName);
    plugin.getSessionService().clearOfflineRecordsForWorld(worldName);

    // ★ AfterDestroy
    if (dungeonCtx != null) plugin.getHookRegistry().fireAfterDestroy(dungeonCtx);
}
}