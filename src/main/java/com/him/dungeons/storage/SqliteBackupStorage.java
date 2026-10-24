package com.him.dungeons.storage;

import com.him.dungeons.HimDungeons;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SQLite 实现的背包备份存储。
 * 数据库路径：plugins/HimDungeons/backup.db
 *
 * 事务保证：restore 后的 DELETE 与 wins 写入完全解耦，互不影响。
 * 崩溃安全：WAL + NORMAL 同步模式，进程被 kill 也能恢复已提交数据。
 */
public class SqliteBackupStorage implements BackupStorage {

    private final HimDungeons plugin;
    private Connection conn;

    public SqliteBackupStorage(HimDungeons plugin) {
        this.plugin = plugin;
        init();
    }

    private synchronized void init() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();
            File dbFile = new File(dataFolder, "backup.db");
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS inventory_backup (
                        player_uuid  TEXT NOT NULL,
                        dungeon_name TEXT NOT NULL,
                        world_name   TEXT NOT NULL,
                        contents     TEXT NOT NULL,
                        armor        TEXT NOT NULL,
                        offhand      TEXT NOT NULL,
                        status       TEXT NOT NULL,
                        created_at   INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, dungeon_name)
                    )
                """);
                st.execute("CREATE INDEX IF NOT EXISTS idx_status ON inventory_backup(status)");
            }
            plugin.getLogger().info("背包数据库已初始化: " + dbFile.getAbsolutePath());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "初始化 SQLite 失败，备份功能将不可用", e);
        }
    }

    private synchronized Connection getConn() throws SQLException {
        if (conn == null || conn.isClosed()) {
            init();
        }
        return conn;
    }

    @Override
    public void save(UUID uuid, String dungeonName, String worldName, Player player) {
        PlayerInventory inv = player.getInventory();
        String contents = serialize(inv.getContents());
        String armor    = serialize(inv.getArmorContents());
        String offhand  = serialize(inv.getExtraContents());
        try (PreparedStatement ps = getConn().prepareStatement("""
            INSERT OR REPLACE INTO inventory_backup
              (player_uuid, dungeon_name, world_name, contents, armor, offhand, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, dungeonName);
            ps.setString(3, worldName);
            ps.setString(4, contents);
            ps.setString(5, armor);
            ps.setString(6, offhand);
            ps.setString(7, Status.ACTIVE.name());
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "保存背包备份失败: " + uuid, e);
        }
    }

    @Override
    public boolean restore(UUID uuid, String dungeonName, Player player) {
        try (PreparedStatement ps = getConn().prepareStatement(
                "SELECT contents, armor, offhand FROM inventory_backup WHERE player_uuid = ? AND dungeon_name = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, dungeonName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                PlayerInventory inv = player.getInventory();
                inv.setContents(deserialize(rs.getString("contents")));
                inv.setArmorContents(deserialize(rs.getString("armor")));
                inv.setExtraContents(deserialize(rs.getString("offhand")));
                player.updateInventory();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "恢复背包失败: " + uuid, e);
            return false;
        }
        // 恢复成功后才删除，绝不顺带清理其他数据
        delete(uuid, dungeonName);
        return true;
    }

    @Override
    public boolean hasBackup(UUID uuid, String dungeonName) {
        try (PreparedStatement ps = getConn().prepareStatement(
                "SELECT 1 FROM inventory_backup WHERE player_uuid = ? AND dungeon_name = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, dungeonName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void updateStatus(UUID uuid, String dungeonName, Status status) {
        try (PreparedStatement ps = getConn().prepareStatement(
                "UPDATE inventory_backup SET status = ? WHERE player_uuid = ? AND dungeon_name = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, uuid.toString());
            ps.setString(3, dungeonName);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "更新状态失败: " + uuid, e);
        }
    }

    @Override
    public List<BackupRecord> findAll() {
        List<BackupRecord> result = new ArrayList<>();
        try (PreparedStatement ps = getConn().prepareStatement(
                "SELECT player_uuid, dungeon_name, world_name, status, created_at FROM inventory_backup");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new BackupRecord(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("dungeon_name"),
                        rs.getString("world_name"),
                        Status.valueOf(rs.getString("status")),
                        rs.getLong("created_at")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "查询全部备份失败", e);
        }
        return result;
    }

    @Override
    public BackupRecord findForPlayer(UUID uuid) {
        try (PreparedStatement ps = getConn().prepareStatement(
                "SELECT player_uuid, dungeon_name, world_name, status, created_at FROM inventory_backup WHERE player_uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new BackupRecord(
                            uuid,
                            rs.getString("dungeon_name"),
                            rs.getString("world_name"),
                            Status.valueOf(rs.getString("status")),
                            rs.getLong("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "查询玩家备份失败: " + uuid, e);
        }
        return null;
    }

    @Override
    public void delete(UUID uuid, String dungeonName) {
        try (PreparedStatement ps = getConn().prepareStatement(
                "DELETE FROM inventory_backup WHERE player_uuid = ? AND dungeon_name = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, dungeonName);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "删除背包备份失败: " + uuid, e);
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            try {
                if (conn != null && !conn.isClosed()) conn.close();
            } catch (SQLException ignored) {}
            conn = null;
        }
    }

    // ==================== Base64 ItemStack 序列化 ====================

    private static String serialize(ItemStack[] items) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeObject(items);
            boos.flush();
            return Base64Coder.encodeLines(baos.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static ItemStack[] deserialize(String base64) {
        if (base64 == null || base64.isEmpty()) return new ItemStack[0];
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64Coder.decodeLines(base64));
             BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
            return (ItemStack[]) bois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
            return new ItemStack[0];
        }
    }
}