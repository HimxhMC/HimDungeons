package com.him.dungeons.util;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
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

    public static boolean isVerticalFacing(String facing) {
        if (facing == null) return false;
        return facing.toLowerCase().startsWith("y");
    }

    /**
     * 绕Y轴旋转朝向（逆时针，与 WorldEdit AffineTransform 一致）
     * 水平顺序：x+ → z- → x- → z+ → x+
     */
    public static String rotateFacing(String facing, int times) {
        if (facing == null) return null;
        times = ((times % 4) + 4) % 4;
        if (times == 0) return facing;

        String lower = facing.toLowerCase();
        if (lower.startsWith("y")) return facing;

        // 逆时针顺序（对应 AffineTransform.rotateY 正角度）
        String[] horizontalOrder = {"x+", "z-", "x-", "z+"};
        int idx = -1;
        for (int i = 0; i < horizontalOrder.length; i++) {
            if (horizontalOrder[i].equals(lower)) {
                idx = i;
                break;
            }
        }
        if (idx == -1) return facing;
        return horizontalOrder[(idx + times) % 4];
    }

    /**
     * 绕Y轴旋转向量（逆时针，使用 AffineTransform 确保与粘贴一致）
     */
    public static BlockVector3 rotateBlockVector(BlockVector3 vec, int times) {
        times = ((times % 4) + 4) % 4;
        if (times == 0 || vec == null) return vec;
        AffineTransform transform = new AffineTransform();
        transform = transform.rotateY(Math.toRadians(times * 90));
        com.sk89q.worldedit.math.Vector3 rotated = transform.apply(vec.toVector3());
        return BlockVector3.at(
                Math.round(rotated.getX()),
                Math.round(rotated.getY()),
                Math.round(rotated.getZ())
        );
    }

    public static Vector rotateVector(Vector vec, int times) {
        times = ((times % 4) + 4) % 4;
        if (times == 0 || vec == null) return vec.clone();
        BlockVector3 blockVec = BlockVector3.at(vec.getX(), vec.getY(), vec.getZ());
        BlockVector3 rotated = rotateBlockVector(blockVec, times);
        return new Vector(rotated.getX(), rotated.getY(), rotated.getZ());
    }

    public static BlockVector3 locationToBlockVector(org.bukkit.Location loc) {
        if (loc == null) return null;
        return BlockVector3.at(loc.getX(), loc.getY(), loc.getZ());
    }

    public static boolean isValidFacing(String facing) {
        return facing != null && FACING_VECTOR_MAP.containsKey(facing.toLowerCase());
    }
    /**
 * 返回给定朝向的反向朝向
 * @param facing 朝向字符串（如 "x+"）
 * @return 反向朝向（如 "x-"），若输入无效则返回 null
 */
public static String oppositeFacing(String facing) {
    if (facing == null) return null;
    switch (facing) {
        case "x+": return "x-";
        case "x-": return "x+";
        case "y+": return "y-";
        case "y-": return "y+";
        case "z+": return "z-";
        case "z-": return "z+";
        default: return null;
    }
}
private int computeRequiredRotation(String currentFacing, String candOriginalFacing) {
    String opposite = TransformUtil.oppositeFacing(currentFacing);
    if (opposite == null) return -1; // 无法匹配（如垂直与水平）
    for (int rot = 0; rot < 4; rot++) {
        String rotated = TransformUtil.rotateFacing(candOriginalFacing, rot);
        if (rotated.equals(opposite)) {
            return rot;
        }
    }
    return -1;
}
}