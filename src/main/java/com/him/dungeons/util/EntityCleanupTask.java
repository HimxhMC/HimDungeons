package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;

/** 定期清理地牢世界中的掉落物、箭矢、经验球。 */
public final class EntityCleanupTask implements Runnable {

    private final HimDungeons plugin;
    public EntityCleanupTask(HimDungeons plugin) { this.plugin = plugin; }

    @Override
    public void run() {
        for (String worldName : plugin.dungeonInstances.keySet()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;
            for (Entity e : world.getEntities()) {
                if (e instanceof Item || e instanceof Arrow || e instanceof ExperienceOrb) {
                    e.remove();
                }
            }
        }
    }
}