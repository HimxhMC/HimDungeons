package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.HimDungeons.DungeonInstance;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间迷雾：根据玩家所在房间尺寸动态调整客户端渲染距离。
 *
 * <p>未开启 roomFog 的地牢不处理。玩家离开地牢时恢复服务器默认视距。</p>
 */
public final class RoomFogManager implements Runnable {

    private final HimDungeons plugin;
    private final Map<UUID, Integer> originalViewDistance = new ConcurrentHashMap<>();

    public RoomFogManager(HimDungeons plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this, 20L, 20L);
    }

    /** 玩家进入地牢时调用 */
    public void applyFor(Player player, DungeonInstance inst) {
        FileConfiguration cfg = ConfigUtil.getDungeonConfig(inst.dungeonName);
        if (cfg == null || !ConfigUtil.isInstanceRoomFog(cfg)) return;
        originalViewDistance.putIfAbsent(player.getUniqueId(), player.getViewDistance());
        MessageUtil.sendMessage(player, "session.fog-enter", "dungeon", inst.dungeonName);
    }

    /** 玩家离开地牢时调用 */
    public void restore(Player player) {
        Integer original = originalViewDistance.remove(player.getUniqueId());
        if (original != null && player.isOnline()) {
            try { player.setViewDistance(original); } catch (Exception ignored) {}
            MessageUtil.sendMessage(player, "session.fog-exit");
        }
    }

    @Override
    public void run() {
        for (DungeonInstance inst : plugin.dungeonInstances.values()) {
            FileConfiguration cfg = ConfigUtil.getDungeonConfig(inst.dungeonName);
            if (cfg == null || !ConfigUtil.isInstanceRoomFog(cfg)) continue;
            for (UUID uuid : inst.players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;
                adjust(p, inst);
            }
        }
    }

    private void adjust(Player player, DungeonInstance inst) {
        if (inst.placedRooms == null) return;
        Location loc = player.getLocation();
        int view = 4;
        for (RoomInstance r : inst.placedRooms) {
            if (AABBUtil.contains(r.aabb, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                double dx = Math.max(loc.getX() - r.aabb.minX, r.aabb.maxX - loc.getX());
                double dz = Math.max(loc.getZ() - r.aabb.minZ, r.aabb.maxZ - loc.getZ());
                int need = (int) Math.ceil(Math.max(dx, dz) / 16.0) + 2;
                view = Math.max(view, Math.min(need, 12));
                break;
            }
        }
        try {
            int current = player.getViewDistance();
            if (Math.abs(current - view) > 1) player.setViewDistance(view);
        } catch (Exception ignored) {}
    }
}