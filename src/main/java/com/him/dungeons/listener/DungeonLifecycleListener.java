package com.him.dungeons.listener;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.service.DungeonSessionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家进出地牢的生命周期监听（全部委托给 DungeonSessionService）。
 */
public class DungeonLifecycleListener implements Listener {

    private final HimDungeons plugin;

    public DungeonLifecycleListener(HimDungeons plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        plugin.getSessionService().onPlayerQuit(e.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent e) {
        plugin.getSessionService().onPlayerQuit(e.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        plugin.getSessionService().onPlayerJoin(e.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        String fromWorld = e.getFrom().getName();
        if (!plugin.isDungeonWorld(fromWorld)) return;
        HimDungeons.DungeonInstance instance = plugin.getDungeonInstanceByWorld(fromWorld);
        if (instance == null) return;
        // 玩家自己传送离开地牢世界 = 主动离开
        if (instance.players.contains(p.getUniqueId())) {
            plugin.getSessionService().leave(p);
        }
    }
}