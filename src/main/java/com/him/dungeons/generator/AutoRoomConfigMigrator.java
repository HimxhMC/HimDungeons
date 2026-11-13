package com.him.dungeons.generator;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.LanguageManager;
import com.him.dungeons.util.RoomUse;
import com.him.dungeons.util.YamlDoorUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;

/**
 * 启动期自动迁移：把 {@code _door.yml / _chest.yml / _spawn.yml / _checkpoint.yml}
 * <b>全部内容</b>合并到 {@code <base>.yml}，并补上 type / use。
 *
 * <p>如果 {@code <base>.yml} 已存在 → 跳过（不覆盖，用户可能已手动编辑过）。</p>
 * <p>旧文件不删，用户可确认后手动清理。</p>
 */
public final class AutoRoomConfigMigrator {

    private AutoRoomConfigMigrator() {}

    public static int migrateAll(HimDungeons plugin, boolean verbose) {
        File dataFolder = plugin.getDataFolder();
        File[] dungeons = dataFolder.listFiles(File::isDirectory);
        if (dungeons == null) return 0;

        int total = 0;
        for (File dungeonDir : dungeons) {
            if (!new File(dungeonDir, "config.yml").exists()) continue;
            File roomsDir = new File(dungeonDir, "rooms");
            if (!roomsDir.exists()) continue;
            total += migrateRooms(dungeonDir.getName(), roomsDir, roomsDir);
        }
        if (verbose && total > 0) {
            plugin.getLogger().info(LanguageManager.get("log.room.auto-migrate-done",
                    "count", total));
        }
        return total;
    }

    private static int migrateRooms(String dungeonName, File root, File current) {
        int count = 0;
        File[] files = current.listFiles();
        if (files == null) return 0;

        // 收集当前目录所有 schem 基名
        Set<String> stems = new LinkedHashSet<>();
        for (File f : files) {
            if (f.isDirectory()) {
                count += migrateRooms(dungeonName, root, f);
                continue;
            }
            String name = f.getName();
            if (name.endsWith(".schem") || name.endsWith(".schematic")) {
                stems.add(name.substring(0, name.lastIndexOf('.')));
            }
        }

        for (String stem : stems) {
            if (tryMigrateOne(current, root, stem)) count++;
        }
        return count;
    }

    private static boolean tryMigrateOne(File parent, File root, String stem) {
        File single = new File(parent, stem + ".yml");
        if (single.exists()) return false;

        // 推导完整 baseName（含子目录前缀）
        String relative = root.toURI().relativize(parent.toURI()).getPath()
                .replace(File.separatorChar, '/');
        String fullName = relative.isEmpty() ? stem : relative + stem;

        YamlConfiguration y = new YamlConfiguration();

        // 1. type / use
        y.set("type", RoomConfigReader.inferTypeFromPrefix(fullName));
        RoomUse use = RoomConfigReader.inferUseFromPrefix(fullName);
        y.set("use.inStart",    use.inStart);
        y.set("use.inMainPath", use.inMainPath);
        y.set("use.inBranch",   use.inBranch);
        y.set("use.inBoss",     use.inBoss);
        y.set("use.inFallback", use.inFallback);

        // 2. doors
        File doorFile = new File(parent, stem + "_door.yml");
        if (doorFile.exists()) {
            YamlConfiguration dy = YamlConfiguration.loadConfiguration(doorFile);
            List<?> list = dy.getList("doors");
            if (list == null) list = dy.getList("");
            y.set("doors", list != null ? list : new ArrayList<>());
        }

        // 3. chests
        File chestFile = new File(parent, stem + "_chest.yml");
        if (chestFile.exists()) {
            YamlConfiguration cy = YamlConfiguration.loadConfiguration(chestFile);
            List<?> list = cy.getList("chests");
            if (list == null) list = cy.getList("");
            y.set("chests", list != null ? list : new ArrayList<>());
        }

        // 4. spawn
        File spawnFile = new File(parent, stem + "_spawn.yml");
        if (spawnFile.exists()) {
            Vector v = YamlDoorUtil.parseSpawnPoint(spawnFile);
            if (v != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("x", v.getBlockX());
                m.put("y", v.getBlockY());
                m.put("z", v.getBlockZ());
                y.set("spawn", m);
            }
        }

        // 5. checkpoint
        File cpFile = new File(parent, stem + "_checkpoint.yml");
        if (cpFile.exists()) {
            Vector v = YamlDoorUtil.parseSpawnPoint(cpFile);
            if (v != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("x", v.getBlockX());
                m.put("y", v.getBlockY());
                m.put("z", v.getBlockZ());
                y.set("checkpoint", m);
            }
        }

        try {
            y.save(single);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}