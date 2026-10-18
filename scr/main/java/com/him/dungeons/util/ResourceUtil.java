package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.generator.ConfigApplier;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public final class ResourceUtil {

    private ResourceUtil() {}

    /**
     * 房间条目（扁平化扫描结果）
     */
    public static class RoomEntry {
        public final String baseName;   // 相对于 rooms/ 的路径，如 "a/b"
        public final boolean isStart;
        public final boolean isBoss;
        public final boolean isFallback;
        public int weight;              // 从 config 读取，默认 50

        public RoomEntry(String baseName) {
            this.baseName = baseName;
            String lower = baseName.toLowerCase();
            this.isStart = lower.startsWith("start");
            this.isBoss = lower.startsWith("boss");
            this.isFallback = lower.startsWith("fallback");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RoomEntry that = (RoomEntry) o;
            return Objects.equals(baseName, that.baseName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(baseName);
        }
    }

    /**
     * 扫描地牢 rooms 目录，返回所有有效房间条目（递归扫描子目录）
     */
    public static List<RoomEntry> scanRooms(String dungeonName) {
        List<RoomEntry> entries = new ArrayList<>();
        File roomsDir = new File(HimDungeons.getInstance().getDataFolder(), dungeonName + "/rooms");
        if (!roomsDir.exists() || !roomsDir.isDirectory()) {
            return entries;
        }
        collectRooms(roomsDir, roomsDir, entries);
        return entries;
    }

    private static void collectRooms(File root, File current, List<RoomEntry> out) {
        File[] files = current.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectRooms(root, f, out);
            } else if (f.isFile() && (f.getName().endsWith(".schem") || f.getName().endsWith(".schematic"))) {
                String baseName = f.getName();
                baseName = baseName.substring(0, baseName.lastIndexOf('.'));
                File doorFile = new File(f.getParent(), baseName + "_door.yml");
                if (doorFile.exists()) {
                    String relative = root.toURI().relativize(f.getParentFile().toURI()).getPath();
                    relative = relative.replace(File.separatorChar, '/');
                    if (relative.isEmpty()) {
                        out.add(new RoomEntry(baseName));
                    } else {
                        out.add(new RoomEntry(relative + "/" + baseName));
                    }
                }
            }
        }
    }

    /**
     * 获取指定前缀的房间列表，并注入 config 中的权重
     * @param prefix 可为 null（普通房间），或 "start","boss","fallback"
     */
    public static List<RoomEntry> getRoomsByPrefix(String dungeonName, String prefix,
                                                   FileConfiguration config,
                                                   ConfigApplier applier) {
        List<RoomEntry> all = scanRooms(dungeonName);
        List<RoomEntry> filtered = all.stream()
                .filter(e -> {
                    if (prefix == null) {
                        return !e.isStart && !e.isBoss && !e.isFallback;
                    } else {
                        return e.baseName.toLowerCase().startsWith(prefix.toLowerCase());
                    }
                })
                .collect(Collectors.toList());

        for (RoomEntry entry : filtered) {
            int weight = config.getInt("dungeonInstance.structureChoose." + entry.baseName, 50);
            entry.weight = Math.max(weight, 0);
        }
        return filtered;
    }

    // ---------- 扁平化资源定位 ----------
    public static File getRoomSchematicFlat(String dungeonName, String baseName) {
        File roomsDir = new File(HimDungeons.getInstance().getDataFolder(), dungeonName + "/rooms");
        String path = baseName.replace('/', File.separatorChar);
        File schem = new File(roomsDir, path + ".schem");
        if (schem.exists()) return schem;
        schem = new File(roomsDir, path + ".schematic");
        return schem.exists() ? schem : null;
    }

    public static File getRoomDoorYamlFlat(String dungeonName, String baseName) {
        File roomsDir = new File(HimDungeons.getInstance().getDataFolder(), dungeonName + "/rooms");
        String path = baseName.replace('/', File.separatorChar);
        File door = new File(roomsDir, path + "_door.yml");
        return door.exists() ? door : null;
    }

    public static File getRoomChestYamlFlat(String dungeonName, String baseName) {
        File roomsDir = new File(HimDungeons.getInstance().getDataFolder(), dungeonName + "/rooms");
        String path = baseName.replace('/', File.separatorChar);
        File chest = new File(roomsDir, path + "_chest.yml");
        return chest.exists() ? chest : null;
    }

    public static File getRoomSpawnYamlFlat(String dungeonName, String baseName) {
        File roomsDir = new File(HimDungeons.getInstance().getDataFolder(), dungeonName + "/rooms");
        String path = baseName.replace('/', File.separatorChar);
        File spawn = new File(roomsDir, path + "_spawn.yml");
        return spawn.exists() ? spawn : null;
    }
public static File getRoomCheckpointYaml(String dungeonName, String baseName) {
    File roomsDir = new File(HimDungeons.getInstance().getDataFolder(), dungeonName + "/rooms");
    String path = baseName.replace('/', File.separatorChar);
    File check = new File(roomsDir, path + "_checkpoint.yml");
    return check.exists() ? check : null;
}
}