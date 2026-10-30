package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.*;

/** 校验地牢房间目录与配置的一致性。 */
public final class RoomValidator {

    public static class Result {
        public final String dungeon;
        public final int totalRooms;
        public final List<String> errors = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public Result(String d, int total) { this.dungeon = d; this.totalRooms = total; }
    }

    private RoomValidator() {}

    public static Result validate(String dungeon) {
        File roomsDir = new File(HimDungeons.getInstance().getDataFolder(), dungeon + "/rooms");
        if (!roomsDir.exists() || !roomsDir.isDirectory()) {
            Result r = new Result(dungeon, 0);
            r.errors.add("no-rooms-dir:" + roomsDir.getPath());
            return r;
        }
        List<ResourceUtil.RoomEntry> entries = ResourceUtil.scanRooms(dungeon);
        Result result = new Result(dungeon, entries.size());

        Set<String> validNames = new HashSet<>();
        for (ResourceUtil.RoomEntry entry : entries) {
            validNames.add(entry.baseName);
            if (ResourceUtil.getRoomSchematicFlat(dungeon, entry.baseName) == null) {
                result.errors.add("missing-schem:" + entry.baseName);
            }
            if (ResourceUtil.getRoomDoorYamlFlat(dungeon, entry.baseName) == null) {
                result.errors.add("missing-door:" + entry.baseName);
            }
        }

        FileConfiguration config = ConfigUtil.getDungeonConfig(dungeon);
        if (config != null) {
            ConfigurationSection sc = config.getConfigurationSection("dungeonInstance.structureChoose");
            if (sc != null) {
                for (String key : sc.getKeys(false)) {
                    if (!validNames.contains(key)) result.warnings.add("orphan-weight:" + key);
                }
            }
        }
        return result;
    }
}