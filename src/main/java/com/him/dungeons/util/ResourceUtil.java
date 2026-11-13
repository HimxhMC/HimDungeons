package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.generator.ConfigApplier;
import com.him.dungeons.generator.RoomConfig;
import com.him.dungeons.generator.RoomConfigReader;
import com.him.dungeons.generator.RoomTypeRegistry;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public final class ResourceUtil {

    private ResourceUtil() {}

public static class RoomEntry {
    public final String baseName;
    /** 房间类型（自由文本，如 combat / treasure / shop） */
    public final String type;
    /** 用途集合 */
    public final RoomUse use;
    /** 派生字段：从 use 读取，供旧代码 path 兼容 */
    public final boolean isStart;
    public final boolean isBoss;
    public final boolean isFallback;
    public int weight;

    public RoomEntry(String baseName, String type, RoomUse use) {
        this.baseName = baseName;
        this.type = type != null ? type : "normal";
        this.use = use != null ? use : RoomUse.ALL;
        this.isStart = this.use.inStart;
        this.isBoss = this.use.inBoss;
        this.isFallback = this.use.inFallback;
    }

    /** 旧构造：无 use 信息时按前缀推断（保留给外部调用方） */
    public RoomEntry(String baseName) {
        this.baseName = baseName;
        String lower = baseName.toLowerCase();
        boolean s  = lower.startsWith("start");
        boolean b  = lower.startsWith("boss");
        boolean fb = lower.startsWith("fallback");
        this.type = s ? "start" : b ? "boss" : fb ? "fallback" : "normal";
        this.use = new RoomUse(s, !s && !b && !fb, !s && !b && !fb, b, fb);
        this.isStart = s;
        this.isBoss = b;
        this.isFallback = fb;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoomEntry that)) return false;
        return Objects.equals(baseName, that.baseName);
    }
    @Override public int hashCode() { return Objects.hash(baseName); }
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
        collectRooms(roomsDir, roomsDir, dungeonName, entries);
        return entries;
    }

private static void collectRooms(File root, File current, String dungeonName, List<RoomEntry> out) {
    File[] files = current.listFiles();
    if (files == null) return;
    for (File f : files) {
        if (f.isDirectory()) {
            collectRooms(root, f, dungeonName, out);
        } else if (f.isFile()
                && (f.getName().endsWith(".schem") || f.getName().endsWith(".schematic"))) {
            String baseName = f.getName().substring(0, f.getName().lastIndexOf('.'));

            File doorFile   = new File(f.getParent(), baseName + "_door.yml");
            File singleFile = new File(f.getParent(), baseName + ".yml");
            if (!doorFile.exists() && !singleFile.exists()) continue;

            String relative = root.toURI().relativize(f.getParentFile().toURI()).getPath()
                    .replace(File.separatorChar, '/');
            String fullName = relative.isEmpty() ? baseName : relative + baseName;

            RoomConfig config = RoomConfigReader.read(dungeonName, fullName);
            out.add(new RoomEntry(fullName, config.type, config.use));
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