package com.him.dungeons.generator;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.RoomUse;
import com.him.dungeons.util.YamlDoorUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RoomConfigReader {

    private RoomConfigReader() {}

    private static final Map<String, Map<String, CachedEntry>> CACHE = new ConcurrentHashMap<>();

    private static final class CachedEntry {
        final RoomConfig config;
        final long mtime;
        CachedEntry(RoomConfig c, long t) { this.config = c; this.mtime = t; }
    }

    public static RoomConfig read(String dungeonName, String baseName) {
        Map<String, CachedEntry> perDungeon =
                CACHE.computeIfAbsent(dungeonName, k -> new ConcurrentHashMap<>());

        long mtime = computeMtime(dungeonName, baseName);
        CachedEntry cached = perDungeon.get(baseName);
        if (cached != null && cached.mtime == mtime) return cached.config;

        RoomConfig loaded = read0(dungeonName, baseName);
        perDungeon.put(baseName, new CachedEntry(loaded, mtime));
        return loaded;
    }

    public static void invalidate(String dungeonName) {
        if (dungeonName != null) CACHE.remove(dungeonName);
    }
    public static void invalidateAll() { CACHE.clear(); }

    /** mtime 取所有相关文件的最大值，改任何一处都会触发重读 */
    private static long computeMtime(String dungeonName, String baseName) {
        long max = 0;
        for (String suffix : new String[]{".yml", "_door.yml", "_chest.yml",
                "_spawn.yml", "_checkpoint.yml"}) {
            File f = legacyFile(dungeonName, baseName, suffix);
            if (f != null) max = Math.max(max, f.lastModified());
        }
        return max;
    }

    /** 获取存在的旧文件；不存在返回 null */
    private static File legacyFile(String dungeonName, String baseName, String suffix) {
        HimDungeons p = HimDungeons.getInstance();
        if (p == null) return null;
        File roomsDir = new File(p.getDataFolder(), dungeonName + "/rooms");
        File f = new File(roomsDir, baseName.replace('/', File.separatorChar) + suffix);
        return f.exists() ? f : null;
    }

    // ==================== 读取 ====================

    private static RoomConfig read0(String dungeonName, String baseName) {
        File single = legacyFile(dungeonName, baseName, ".yml");
        if (single != null) return readSingle(dungeonName, single, baseName);

        // ===== 旧格式：按前缀推断 type + use =====
        File doorFile       = legacyFile(dungeonName, baseName, "_door.yml");
        File chestFile      = legacyFile(dungeonName, baseName, "_chest.yml");
        File spawnFile      = legacyFile(dungeonName, baseName, "_spawn.yml");
        File checkpointFile = legacyFile(dungeonName, baseName, "_checkpoint.yml");

        List<YamlDoorUtil.DoorAnchor> doors = doorFile != null
                ? YamlDoorUtil.parseDoorAnchors(doorFile) : List.of();
        List<Map<String, Object>> chests = chestFile != null
                ? YamlDoorUtil.parseChestLoot(chestFile) : List.of();
        Vector spawn = spawnFile != null ? YamlDoorUtil.parseSpawnPoint(spawnFile) : null;
        Vector cp    = checkpointFile != null ? YamlDoorUtil.parseSpawnPoint(checkpointFile) : null;

        return new RoomConfig(
                baseName,
                inferTypeFromPrefix(baseName),
                inferUseFromPrefix(baseName),
                doors, chests, spawn, cp);
    }

    private static RoomConfig readSingle(String dungeonName, File file, String baseName) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        String type = yaml.getString("type");
        if (type == null || type.isEmpty()) type = inferTypeFromPrefix(baseName);

        RoomUse use = parseUse(yaml.getConfigurationSection("use"));

        // 混合读取：yml 有就用 yml；没有回退旧 _*.yml
        List<YamlDoorUtil.DoorAnchor> doors;
        if (yaml.contains("doors")) {
            doors = YamlDoorUtil.parseDoorList(yaml.getList("doors"));
        } else {
            File legacy = legacyFile(dungeonName, baseName, "_door.yml");
            doors = legacy != null ? YamlDoorUtil.parseDoorAnchors(legacy) : List.of();
        }

        List<Map<String, Object>> chests;
        if (yaml.contains("chests")) {
            chests = YamlDoorUtil.parseChestList(yaml.getList("chests"));
        } else {
            File legacy = legacyFile(dungeonName, baseName, "_chest.yml");
            chests = legacy != null ? YamlDoorUtil.parseChestLoot(legacy) : List.of();
        }

        Vector spawn = yaml.contains("spawn")
                ? readVector(yaml, "spawn")
                : (legacyFile(dungeonName, baseName, "_spawn.yml") != null
                        ? YamlDoorUtil.parseSpawnPoint(legacyFile(dungeonName, baseName, "_spawn.yml"))
                        : null);

        Vector cp = yaml.contains("checkpoint")
                ? readVector(yaml, "checkpoint")
                : (legacyFile(dungeonName, baseName, "_checkpoint.yml") != null
                        ? YamlDoorUtil.parseSpawnPoint(legacyFile(dungeonName, baseName, "_checkpoint.yml"))
                        : null);

        return new RoomConfig(baseName, type, use, doors, chests, spawn, cp);
    }

    private static RoomUse parseUse(ConfigurationSection sec) {
        if (sec == null) return RoomUse.ALL;
        return new RoomUse(
                sec.getBoolean("inStart",    true),
                sec.getBoolean("inMainPath", true),
                sec.getBoolean("inBranch",   true),
                sec.getBoolean("inBoss",     true),
                sec.getBoolean("inFallback", true));
    }

    private static Vector readVector(YamlConfiguration yaml, String key) {
        if (yaml.isConfigurationSection(key)) {
            ConfigurationSection sec = yaml.getConfigurationSection(key);
            if (sec == null || !sec.contains("x")) return null;
            return new Vector(sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"));
        }
        if (yaml.contains(key + ".x")) {
            return new Vector(yaml.getDouble(key + ".x"),
                    yaml.getDouble(key + ".y"), yaml.getDouble(key + ".z"));
        }
        return null;
    }

    public static String inferTypeFromPrefix(String baseName) {
        if (baseName == null) return "normal";
        String lower = baseName.toLowerCase();
        if (lower.startsWith("start"))    return "start";
        if (lower.startsWith("boss"))     return "boss";
        if (lower.startsWith("fallback")) return "fallback";
        if (lower.contains("/"))          return lower.substring(0, lower.indexOf('/'));
        return "normal";
    }

    public static RoomUse inferUseFromPrefix(String baseName) {
        if (baseName == null) return RoomUse.ALL;
        String lower = baseName.toLowerCase();
        boolean s  = lower.startsWith("start");
        boolean b  = lower.startsWith("boss");
        boolean fb = lower.startsWith("fallback");
        boolean normal = !s && !b && !fb;
        return new RoomUse(s, normal, normal, b, fb);
    }
}