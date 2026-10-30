package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 为地牢 Boss 维护屏幕顶部血条。 */
public final class BossBarManager implements Runnable {

    private final HimDungeons plugin;
    private final Map<String, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<String, String> bossNames = new ConcurrentHashMap<>();

    public BossBarManager(HimDungeons plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this, 20L, 10L);
    }

    public void create(String worldName) {
        FileConfiguration cfg = plugin.dungeonInstances.containsKey(worldName)
                ? ConfigUtil.getDungeonConfig(plugin.dungeonInstances.get(worldName).dungeonName)
                : null;
        if (cfg != null && !ConfigUtil.isInstanceBossBar(cfg)) return;
        BossBar bar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
        bars.put(worldName, bar);
    }

    public void setBossName(String worldName, String name) {
        bossNames.put(worldName, name == null ? "Boss" : name);
    }

    public void remove(String worldName) {
        BossBar bar = bars.remove(worldName);
        if (bar != null) bar.removeAll();
        bossNames.remove(worldName);
    }

    @Override
    public void run() {
        for (Map.Entry<String, BossBar> e : bars.entrySet()) {
            String world = e.getKey();
            BossBar bar = e.getValue();
            HimDungeons.BossData data = plugin.getBossData(world);
            if (data == null || data.entity == null || !data.entity.isValid() || data.entity.isDead()) {
                bar.removeAll();
                continue;
            }
            LivingEntity boss = data.entity;
            double hp = Math.max(0, boss.getHealth());
            double max = Math.max(1, boss.getMaxHealth());
            bar.setProgress(Math.min(1.0, hp / max));
            String name = bossNames.getOrDefault(world, "Boss");
            bar.setTitle(LanguageManager.get("boss.bar-title",
                    "name", name,
                    "hp", String.format("%.0f", hp),
                    "max", String.format("%.0f", max)));

            if (plugin.dungeonPlayers.containsKey(world)) {
                for (UUID uuid : plugin.dungeonPlayers.get(world)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline() && !bar.getPlayers().contains(p)) bar.addPlayer(p);
                }
            }
        }
    }
}