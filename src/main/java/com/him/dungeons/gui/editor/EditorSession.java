package com.him.dungeons.gui.editor;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每个玩家的编辑器会话，持有"未保存的配置副本"。
 * key = "root" 或地牢名。
 */
public final class EditorSession {

    public final UUID playerId;
    public final Map<String, FileConfiguration> working = new ConcurrentHashMap<>();

    public EditorSession(UUID playerId) {
        this.playerId = playerId;
    }

    public void clear() { working.clear(); }
}