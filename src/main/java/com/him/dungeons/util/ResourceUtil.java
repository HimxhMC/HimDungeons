package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ResourceUtil {

    private ResourceUtil() {}

    public static File getDungeonRoot(String dungeonName) {
        if (dungeonName == null) return null;
        File root = new File(HimDungeons.getInstance().getDataFolder(), dungeonName);
        return root.exists() ? root : null;
    }

    public static File getRoomFolder(String dungeonName, String roomType) {
        File root = getDungeonRoot(dungeonName);
        if (root == null || roomType == null) return null;
        File folder = new File(root, roomType);
        return (folder.exists() && folder.isDirectory()) ? folder : null;
    }

    public static File getRoomSchematic(String dungeonName, String roomType, String baseName) {
        File folder = getRoomFolder(dungeonName, roomType);
        if (folder == null) return null;
        File f = new File(folder, baseName + ".schem");
        if (f.exists()) return f;
        f = new File(folder, baseName + ".schematic");
        return f.exists() ? f : null;
    }

    public static File getRoomDoorYaml(String dungeonName, String roomType, String baseName) {
        File folder = getRoomFolder(dungeonName, roomType);
        if (folder == null) return null;
        File f = new File(folder, baseName + "_door.yml");
        return f.exists() ? f : null;
    }

    public static File getRoomChestYaml(String dungeonName, String roomType, String baseName) {
        File folder = getRoomFolder(dungeonName, roomType);
        if (folder == null) return null;
        File f = new File(folder, baseName + "_chest.yml");
        return f.exists() ? f : null;
    }

    public static File getRoomSpawnYaml(String dungeonName, String roomType, String baseName) {
        File folder = getRoomFolder(dungeonName, roomType);
        if (folder == null) return null;
        File f = new File(folder, baseName + "_spawn.yml");
        return f.exists() ? f : null;
    }

    public static List<String> listAvailableBaseNames(String dungeonName, String roomType) {
        List<String> names = new ArrayList<>();
        File folder = getRoomFolder(dungeonName, roomType);
        // 调试日志
        HimDungeons.getInstance().getLogger().info("[Debug] listAvailableBaseNames: 扫描文件夹 " + (folder == null ? "null" : folder.getAbsolutePath()));
        if (folder == null) {
            HimDungeons.getInstance().getLogger().info("[Debug] 文件夹不存在或不是目录");
            return names;
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".schem") || name.endsWith(".schematic"));
        if (files == null || files.length == 0) {
            HimDungeons.getInstance().getLogger().info("[Debug] 没有找到原理图文件");
            // 列出所有文件以便调试
            File[] all = folder.listFiles();
            if (all != null) {
                for (File f : all) {
                    HimDungeons.getInstance().getLogger().info("[Debug] 文件夹内容: " + f.getName());
                }
            }
            return names;
        }
        for (File f : files) {
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                names.add(name.substring(0, dot));
            }
        }
        HimDungeons.getInstance().getLogger().info("[Debug] 找到基础文件名: " + names);
        return names;
    }

    public static File getFallbackRoomSchematic(String dungeonName) {
        File root = getDungeonRoot(dungeonName);
        if (root == null) return null;
        File fallbackFolder = new File(root, "fallback");
        if (!fallbackFolder.exists() || !fallbackFolder.isDirectory()) return null;
        File wall = new File(fallbackFolder, "wall.schem");
        if (wall.exists()) return wall;
        wall = new File(fallbackFolder, "wall.schematic");
        if (wall.exists()) return wall;
        File floor = new File(fallbackFolder, "floor.schem");
        if (floor.exists()) return floor;
        floor = new File(fallbackFolder, "floor.schematic");
        if (floor.exists()) return floor;
        File[] files = fallbackFolder.listFiles((dir, name) -> name.endsWith(".schem") || name.endsWith(".schematic"));
        return (files != null && files.length > 0) ? files[0] : null;
    }
}