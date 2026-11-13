package com.him.dungeons.debug;

import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/** 单个玩家的可视化会话，持有当前所有显示实体。 */
public final class VisualizeSession {

    final List<Entity> entities = new ArrayList<>();

    // 缓存上次选区边界，避免同一点击重复重建
    int lastMinX = Integer.MIN_VALUE, lastMinY = Integer.MIN_VALUE, lastMinZ = Integer.MIN_VALUE;
    int lastMaxX = Integer.MIN_VALUE, lastMaxY = Integer.MIN_VALUE, lastMaxZ = Integer.MIN_VALUE;

    void add(Entity e)                { if (e != null) entities.add(e); }
    void addAll(List<? extends Entity> list) { if (list != null) entities.addAll(list); }

    void clear() {
        for (Entity e : entities) {
            if (e != null && e.isValid()) e.remove();
        }
        entities.clear();
        lastMinX = lastMinY = lastMinZ = Integer.MIN_VALUE;
        lastMaxX = lastMaxY = lastMaxZ = Integer.MIN_VALUE;
    }
}