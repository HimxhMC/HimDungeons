package com.him.dungeons.util;

/**
 * @deprecated 已拆分并迁移：
 *  - 胜利数据：{@link com.him.dungeons.util.WinStorage}（YAML，可读）
 *  - 背包备份：{@link com.him.dungeons.storage.SqliteBackupStorage}（SQLite）
 *
 * 本类不再被任何代码引用，保留仅为兼容可能存在的旧引用。
 * 旧的 player_data.json 不会自动迁移 —— 若需要，可自行读一次然后调用
 * WinStorage.incrementWin / BackupStorage.save 迁入。
 */
@Deprecated
public final class PlayerDataManager {
    private PlayerDataManager() {}
}