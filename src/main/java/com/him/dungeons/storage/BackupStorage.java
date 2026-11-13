package com.him.dungeons.storage;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * 背包备份存储抽象接口。
 * 实现：SQLite（当前）、可替换为 H2/MySQL。
 */
public interface BackupStorage {

    enum Status {
        /** 玩家在地牢中，背包被清空托管在数据库 */
        ACTIVE,
        /** 玩家离线/崩溃，背包待恢复到玩家档案 */
        PENDING_RESTORE
    }

    /** 备份玩家当前背包（主背包 + 盔甲 + 副手），状态为 ACTIVE */
    void save(UUID uuid, String dungeonName, String worldName, Player player);

    /**
     * 恢复玩家背包并删除数据库行。
     * @return true 表示有备份并已恢复，false 表示无备份
     */
    boolean restore(UUID uuid, String dungeonName, Player player);

    boolean hasBackup(UUID uuid, String dungeonName);

    void updateStatus(UUID uuid, String dungeonName, Status status);

    /** 扫描全部记录（启动时用） */
    List<BackupRecord> findAll();

    /** 查询指定玩家的记录 */
    BackupRecord findForPlayer(UUID uuid);

    void delete(UUID uuid, String dungeonName);

    void close();

    /** 备份记录元信息（不含背包内容） */
    class BackupRecord {
        public final UUID uuid;
        public final String dungeonName;
        public final String worldName;
        public final Status status;
        public final long createdAt;

        public BackupRecord(UUID uuid, String dungeonName, String worldName,
                            Status status, long createdAt) {
            this.uuid = uuid;
            this.dungeonName = dungeonName;
            this.worldName = worldName;
            this.status = status;
            this.createdAt = createdAt;
        }
    }
}