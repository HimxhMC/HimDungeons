package com.him.dungeons.listener;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.ConfigUtil;
import com.him.dungeons.util.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Boss 分阶段：监听 EntityDamageEvent，按血量阈值依次执行配置中的命令/音效/Title。
 */
public class BossPhaseManager implements Listener {

    private final HimDungeons plugin;
    /** Boss UUID → 当前已触发的阶段索引 */
    private final Map<UUID, Integer> phaseIndex = new ConcurrentHashMap<>();

    public BossPhaseManager(HimDungeons plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void initBoss(LivingEntity boss) {
        if (boss == null) return;
        phaseIndex.put(boss.getUniqueId(), 0);
    }

    public void removeBoss(LivingEntity boss) {
        if (boss == null) return;
        phaseIndex.remove(boss.getUniqueId());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity boss)) return;
        Integer idx = phaseIndex.get(boss.getUniqueId());
        if (idx == null) return;

        // 找到对应的 BossData
        HimDungeons.BossData data = null;
        String worldName = null;
        for (Map.Entry<String, HimDungeons.BossData> entry : plugin.dungeonBosses.entrySet()) {
            HimDungeons.BossData d = entry.getValue();
            if (d != null && d.entity != null && d.entity.getUniqueId().equals(boss.getUniqueId())) {
                data = d; worldName = entry.getKey(); break;
            }
        }
        if (data == null) return;

        FileConfiguration cfg = ConfigUtil.getDungeonConfig(data.dungeonName);
        if (cfg == null || !ConfigUtil.isInstanceBossPhaseEnabled(cfg)) return;

        List<Map<?, ?>> phases = ConfigUtil.getInstanceBossPhases(cfg);
        if (phases.isEmpty() || idx >= phases.size()) return;

        double newHp = Math.max(0, boss.getHealth() - e.getFinalDamage());
        double hpPercent = boss.getMaxHealth() > 0 ? newHp / boss.getMaxHealth() * 100 : 0;

        int current = idx;
        while (current < phases.size()) {
            Map<?, ?> phase = phases.get(current);
            Number threshold = toNumber(phase.get("threshold"));
            if (threshold == null || hpPercent > threshold.doubleValue()) break;

            triggerPhase(worldName, data, phase, current + 1);
            current++;
        }
        phaseIndex.put(boss.getUniqueId(), current);
    }

    private void triggerPhase(String worldName, HimDungeons.BossData data, Map<?, ?> phase, int phaseNumber) {
        String bossName = data.entity.getType().name();
        String title = phase.get("title") == null ? null : phase.get("title").toString();
        String subtitle = phase.get("subtitle") == null ? "" : phase.get("subtitle").toString();
        String sound = phase.get("sound") == null ? null : phase.get("sound").toString();
        List<String> commands = new ArrayList<>();
        Object cmdsObj = phase.get("commands");
        if (cmdsObj instanceof List<?> list) {
            for (Object o : list) if (o != null) commands.add(o.toString());
        }

        for (UUID uuid : plugin.dungeonPlayers.getOrDefault(worldName, Collections.emptySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            if (title != null && !title.isEmpty()) {
                String text = ChatColor.translateAlternateColorCodes('&',
                        title.replace("%name%", bossName).replace("%phase%", String.valueOf(phaseNumber)));
                String sub = ChatColor.translateAlternateColorCodes('&',
                        subtitle.replace("%name%", bossName).replace("%phase%", String.valueOf(phaseNumber)));
                p.sendTitle(text, sub, 10, 60, 10);
            }
            if (sound != null && !sound.isEmpty()) {
                try {
                    Sound s = Sound.valueOf(sound.toUpperCase());
                    p.playSound(p.getLocation(), s, 1f, 1f);
                } catch (Exception ignored) {}
            }
        }

        for (String cmd : commands) {
            String finalCmd = cmd.replace("%boss%", bossName)
                    .replace("%world%", worldName)
                    .replace("%phase%", String.valueOf(phaseNumber));
            try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd); }
            catch (Exception ignored) {}
        }
    }

    private Number toNumber(Object o) {
        if (o instanceof Number n) return n;
        if (o != null) { try { return Double.parseDouble(o.toString()); } catch (Exception ignored) {} }
        return null;
    }
}