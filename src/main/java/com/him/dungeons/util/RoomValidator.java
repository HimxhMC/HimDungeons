package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.generator.RoomConfig;
import com.him.dungeons.generator.RoomConfigReader;
import com.him.dungeons.generator.RoomTypeRegistry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.*;

/** 校验地牢房间目录与配置的一致性（含 use 健康检查 / connect 引用完整性）。 */
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

        RoomTypeRegistry registry = RoomTypeRegistry.load(dungeon);
        Result result = new Result(dungeon, registry.all().size());

        // 1. 健康检查
        for (String err : registry.healthErrors()) result.errors.add(err);

        // 2. 每个房间的 schem / 门 / connect 引用完整性
        Set<String> validBaseNames = new HashSet<>();
        for (ResourceUtil.RoomEntry entry : registry.all()) {
            validBaseNames.add(entry.baseName);
            if (ResourceUtil.getRoomSchematicFlat(dungeon, entry.baseName) == null) {
                result.errors.add("missing-schem:" + entry.baseName);
            }

            RoomConfig config = RoomConfigReader.read(dungeon, entry.baseName);

            // 门 id 必须唯一
            Set<Integer> seenIds = new HashSet<>();
            for (YamlDoorUtil.DoorAnchor door : config.doors) {
                if (!seenIds.add(door.id)) {
                    result.warnings.add("duplicate-door-id:" + entry.baseName + ":" + door.id);
                }
                if (door.connect != null) {
                    for (YamlDoorUtil.ConnectEntry ce : door.connect.allow) {
                        checkConnectRef(ce, validBaseNames, entry.baseName, door.id, result);
                    }
                    for (YamlDoorUtil.ConnectEntry ce : door.connect.deny) {
                        checkConnectRef(ce, validBaseNames, entry.baseName, door.id, result);
                    }
                }
            }
        }

        // 3. structureChoose 孤儿权重
        FileConfiguration cfg = ConfigUtil.getDungeonConfig(dungeon);
        if (cfg != null) {
            ConfigurationSection sc = cfg.getConfigurationSection("dungeonInstance.structureChoose");
            if (sc != null) {
                for (String key : sc.getKeys(false)) {
                    if (!validBaseNames.contains(key)) {
                        result.warnings.add("orphan-weight:" + key);
                    }
                }
            }
        }

        return result;
    }

    /** connect 引用检查：baseName 必须存在；type 不做强制（可能被动态替换） */
    private static void checkConnectRef(YamlDoorUtil.ConnectEntry ce,
                                        Set<String> validBaseNames,
                                        String fromRoom, int fromDoorId,
                                        Result result) {
        if (ce.baseName != null && !validBaseNames.contains(ce.baseName)) {
            result.warnings.add("connect-ref-missing:"
                    + fromRoom + ":" + fromDoorId + "->" + ce.baseName);
        }
    }
}