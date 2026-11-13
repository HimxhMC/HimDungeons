package com.him.dungeons.generator;

import com.him.dungeons.util.ResourceUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 单个地牢的房间注册表。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>一次性扫描 {@code rooms/}，构建 {@link ResourceUtil.RoomEntry} 列表</li>
 *   <li>按 {@code use} 字段分池：start / boss / fallback / normal</li>
 *   <li>健康检查：start / boss / fallback 池必须至少各 1 个</li>
 * </ol>
 *
 * <p>语义：一个房间可同时出现在多个池（如 {@code inStart=true, inMainPath=true}）。
 * 若不想让 start 房间作为普通房间出现，请在 YAML 中显式配 {@code inMainPath: false}。</p>
 */
public final class RoomTypeRegistry {

    private final String dungeonName;
    private final List<ResourceUtil.RoomEntry> all;
    private final Map<String, List<ResourceUtil.RoomEntry>> byPool = new LinkedHashMap<>();
    private final List<String> healthErrors = new ArrayList<>();

    private RoomTypeRegistry(String dungeonName) {
        this.dungeonName = dungeonName;
        this.all = ResourceUtil.scanRooms(dungeonName);

        byPool.put("start",    all.stream().filter(e -> e.use.inStart)               .collect(Collectors.toList()));
        byPool.put("boss",     all.stream().filter(e -> e.use.inBoss)                .collect(Collectors.toList()));
        byPool.put("fallback", all.stream().filter(e -> e.use.inFallback)            .collect(Collectors.toList()));
        byPool.put("normal",   all.stream().filter(e -> e.use.inMainPath || e.use.inBranch).collect(Collectors.toList()));

        if (byPool.get("start").isEmpty())    healthErrors.add("no-start-room");
        if (byPool.get("boss").isEmpty())     healthErrors.add("no-boss-room");
        if (byPool.get("fallback").isEmpty()) healthErrors.add("no-fallback-room");
    }

    public static RoomTypeRegistry load(String dungeonName) {
        return new RoomTypeRegistry(dungeonName);
    }

    public String dungeonName()                       { return dungeonName; }
    public List<ResourceUtil.RoomEntry> all()         { return all; }
    public List<ResourceUtil.RoomEntry> byPool(String poolName) {
        return byPool.getOrDefault(poolName, List.of());
    }
    public List<String> healthErrors()                { return Collections.unmodifiableList(healthErrors); }
    public boolean healthy()                          { return healthErrors.isEmpty(); }
}