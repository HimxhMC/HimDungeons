package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/** 配置版本迁移：为旧配置补齐新增键，不覆盖已有值。 */
public final class ConfigMigrator {

    private static final int TARGET_VERSION = 1;

    private ConfigMigrator() {}

    public static void migrate(HimDungeons plugin) {
        FileConfiguration global = ConfigUtil.getGlobalConfig();
        int current = global.getInt("configVersion", 0);
        if (current < TARGET_VERSION) {
            boolean changed = false;
            changed |= setIfMissing(global, "configVersion", TARGET_VERSION);
            changed |= setIfMissing(global, "language.useClientLocale", false);
            changed |= setIfMissing(global, "keepTestWorld", false);
            changed |= setIfMissing(global, "pasteSpeed", 2000);
            changed |= setIfMissing(global, "entityCleanupInterval", 300);
            changed |= setIfMissing(global, "debugLogToFile", false);
            changed |= setIfMissing(global, "metrics", true);
            changed |= setIfMissing(global, "updateCheck", true);
            changed |= setIfMissing(global, "schematic.readFormat", "auto");
            changed |= setIfMissing(global, "schematic.saveFormat", "sponge_v3");
            if (changed) {
                ConfigUtil.saveGlobalConfig(global);
                if (current > 0) {
                    plugin.getLogger().info(LanguageManager.get("log.config-migrated",
                            "from", current, "to", TARGET_VERSION));
                }
            }
        }

        File[] dirs = plugin.getDataFolder().listFiles(File::isDirectory);
        if (dirs == null) return;
        for (File dir : dirs) {
            File configFile = new File(dir, "config.yml");
            if (!configFile.exists()) continue;
            FileConfiguration dungeon = YamlConfiguration.loadConfiguration(configFile);
            if (dungeon.getConfigurationSection("dungeonInstance") == null) continue;
            if (addInstanceDefaults(dungeon)) {
                ConfigUtil.saveDungeonConfig(dir.getName(), dungeon);
            }
        }
    }

    private static boolean addInstanceDefaults(FileConfiguration c) {
        boolean changed = false;
        // P0
        changed |= setIfMissing(c, "dungeonInstance.respawnMode", "checkpoint");
        changed |= setIfMissing(c, "dungeonInstance.deathCooldown", 15);
        changed |= setIfMissing(c, "dungeonInstance.playerCooldown", 300);
        changed |= setIfMissing(c, "dungeonInstance.friendlyFire", false);
        changed |= setIfMissing(c, "dungeonInstance.autoPickup", true);
        changed |= setIfMissing(c, "dungeonInstance.teleportInvulnerable", 3);
        changed |= setIfMissing(c, "dungeonInstance.countdownWarn", List.of(300, 180, 60, 30, 10));
        changed |= setIfMissing(c, "dungeonInstance.bossBar", true);
        changed |= setIfMissing(c, "dungeonInstance.partyGlow", true);
        // P1
        changed |= setIfMissing(c, "dungeonInstance.enterTitle", "");
        changed |= setIfMissing(c, "dungeonInstance.leaveTitle", "");
        changed |= setIfMissing(c, "dungeonInstance.roomProgress", true);
        changed |= setIfMissing(c, "dungeonInstance.bossDirection", false);
        changed |= setIfMissing(c, "dungeonInstance.soundVolume", 100);
        changed |= setIfMissing(c, "dungeonInstance.allowSpectatorAfterDeath", true);
        changed |= setIfMissing(c, "dungeonInstance.scaleByPartySize", true);
        changed |= setIfMissing(c, "dungeonInstance.bossHealthMultiplierPerPlayer", 1.5);
        changed |= setIfMissing(c, "dungeonInstance.mobSpawnRate", 1.0);
        changed |= setIfMissing(c, "dungeonInstance.maxMobsPerRoom", 30);
        changed |= setIfMissing(c, "dungeonInstance.timeoutAction", "forceEnd");
        changed |= setIfMissing(c, "dungeonInstance.rewards.firstClearBonus.vault", new HashMap<>());
        changed |= setIfMissing(c, "dungeonInstance.rewards.firstClearBonus.playerPoints", new HashMap<>());
        changed |= setIfMissing(c, "dungeonInstance.rewards.firstClearBonus.consoleCommands", new ArrayList<>());
        changed |= setIfMissing(c, "dungeonInstance.rewards.shareMode", "each");
        changed |= setIfMissing(c, "dungeonInstance.rewards.permissionMultiplier", new HashMap<>());
        changed |= setIfMissing(c, "dungeonInstance.failurePenalty.vault", new HashMap<>());
        changed |= setIfMissing(c, "dungeonInstance.failurePenalty.playerPoints", new HashMap<>());
        changed |= setIfMissing(c, "dungeonInstance.failurePenalty.consoleCommands", new ArrayList<>());
        changed |= setIfMissing(c, "dungeonInstance.stats.trackFastestTime", true);
        // P3
        changed |= setIfMissing(c, "dungeonInstance.roomFog", false);
        changed |= setIfMissing(c, "dungeonInstance.minimap", false);
        changed |= setIfMissing(c, "dungeonInstance.leaderboard", false);
        changed |= setIfMissing(c, "dungeonInstance.bossPhase.enabled", false);
        changed |= setIfMissing(c, "dungeonInstance.bossPhase.phases", new ArrayList<>());
        changed |= setIfMissing(c, "dungeonInstance.lootTable.enabled", false);
        changed |= setIfMissing(c, "dungeonInstance.lootTable.tables", new HashMap<>());
        return changed;
    }

    private static boolean setIfMissing(FileConfiguration c, String path, Object def) {
        if (c.contains(path)) return false;
        c.set(path, def);
        return true;
    }
}