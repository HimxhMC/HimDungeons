package com.him.dungeons.util;

/**
 * 房间用途标签集。
 *
 * <p>语义：</p>
 * <ul>
 *   <li>{@code use} 段整体缺失 → 全部为 true（旧配置完全兼容）</li>
 *   <li>{@code use} 段存在但某字段缺失 → 该字段默认 true</li>
 *   <li>某字段显式为 {@code false} → 该位置不选它</li>
 * </ul>
 */
public final class RoomUse {

    public static final RoomUse ALL = new RoomUse(true, true, true, true, true);

    public final boolean inStart;
    public final boolean inMainPath;
    public final boolean inBranch;
    public final boolean inBoss;
    public final boolean inFallback;

    public RoomUse(boolean inStart, boolean inMainPath, boolean inBranch,
                   boolean inBoss, boolean inFallback) {
        this.inStart = inStart;
        this.inMainPath = inMainPath;
        this.inBranch = inBranch;
        this.inBoss = inBoss;
        this.inFallback = inFallback;
    }

    public boolean canBe(String pool) {
        if (pool == null) return true;
        return switch (pool.toLowerCase()) {
            case "start"    -> inStart;
            case "mainpath", "main" -> inMainPath;
            case "branch"   -> inBranch;
            case "boss"     -> inBoss;
            case "fallback" -> inFallback;
            default -> true;
        };
    }
}