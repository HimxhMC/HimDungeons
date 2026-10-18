package com.him.dungeons.util;

import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public final class TransformUtil {

    private static final Map<String, Vector> FACING_VECTOR_MAP = new HashMap<>();

    static {
        FACING_VECTOR_MAP.put("x+", new Vector(1, 0, 0));
        FACING_VECTOR_MAP.put("x-", new Vector(-1, 0, 0));
        FACING_VECTOR_MAP.put("y+", new Vector(0, 1, 0));
        FACING_VECTOR_MAP.put("y-", new Vector(0, -1, 0));
        FACING_VECTOR_MAP.put("z+", new Vector(0, 0, 1));
        FACING_VECTOR_MAP.put("z-", new Vector(0, 0, -1));
    }

    private TransformUtil() {}

    public static Vector facingToVector(String facing) {
        if (facing == null) return new Vector(0, 0, 0);
        return FACING_VECTOR_MAP.getOrDefault(facing.toLowerCase(), new Vector(0, 0, 0));
    }

    public static boolean isHorizontalFacing(String facing) {
        if (facing == null) return false;
        String lower = facing.toLowerCase();
        return lower.startsWith("x") || lower.startsWith("z");
    }
public static Pos3 rotatePos(Pos3 vec, int times) {
    if (vec == null) return null;
    int[] r = rotateXYZ(vec.x, vec.y, vec.z, times);
    return new Pos3(r[0], r[1], r[2]);
}
    public static boolean isVerticalFacing(String facing) {
        if (facing == null) return false;
        return facing.toLowerCase().startsWith("y");
    }

    /**
     * 绕 Y 轴旋转朝向（顺时针，纯整数运算）。
     * 水平顺序：x+ → z- → x- → z+ → x+
     */
/**
 * 绕 Y 轴顺时针旋转朝向（与 BlockData.rotate(CLOCKWISE_90) 及 rotateXYZ 保持一致）。
 * 从上方看顺时针顺序：x+ → z+ → x- → z- → x+
 */
public static String rotateFacing(String facing, int times) {
    if (facing == null) return null;
    times = ((times % 4) + 4) % 4;
    if (times == 0) return facing;

    String lower = facing.toLowerCase();
    if (lower.startsWith("y")) return facing;

    // ★ 修正：顺时针顺序（原来写成逆时针了）
    String[] horizontalOrder = {"x+", "z+", "x-", "z-"};
    int idx = -1;
    for (int i = 0; i < horizontalOrder.length; i++) {
        if (horizontalOrder[i].equals(lower)) { idx = i; break; }
    }
    if (idx == -1) return facing;
    return horizontalOrder[(idx + times) % 4];
}

    /**
     * 绕 Y 轴旋转坐标（顺时针，整数运算，无三角函数）。
     * (x, y, z) → times 次顺时针 90° 旋转后的坐标
     */
    public static int[] rotateXYZ(int x, int y, int z, int times) {
        times = ((times % 4) + 4) % 4;
        return switch (times) {
            case 1 -> new int[]{-z, y, x};
            case 2 -> new int[]{-x, y, -z};
            case 3 -> new int[]{z, y, -x};
            default -> new int[]{x, y, z};
        };
    }

    public static Vector rotateVector(Vector vec, int times) {
        times = ((times % 4) + 4) % 4;
        if (times == 0 || vec == null) return vec.clone();
        int[] r = rotateXYZ(vec.getBlockX(), vec.getBlockY(), vec.getBlockZ(), times);
        return new Vector(r[0], r[1], r[2]);
    }

    public static boolean isValidFacing(String facing) {
        return facing != null && FACING_VECTOR_MAP.containsKey(facing.toLowerCase());
    }

    /**
     * 返回给定朝向的反向朝向。
     */
    public static String oppositeFacing(String facing) {
        if (facing == null) return null;
        return switch (facing) {
            case "x+" -> "x-";
            case "x-" -> "x+";
            case "y+" -> "y-";
            case "y-" -> "y+";
            case "z+" -> "z-";
            case "z-" -> "z+";
            default -> null;
        };
    }

    /**
     * 计算候选门需要旋转多少次，使其朝向与当前门相反。
     * @return 旋转次数 (0~3)，无法匹配返回 -1
     */
    public static int computeRequiredRotation(String currentFacing, String candOriginalFacing) {
        String opposite = oppositeFacing(currentFacing);
        if (opposite == null) return -1;
        for (int rot = 0; rot < 4; rot++) {
            String rotated = rotateFacing(candOriginalFacing, rot);
            if (rotated.equals(opposite)) return rot;
        }
        return -1;
    }
}