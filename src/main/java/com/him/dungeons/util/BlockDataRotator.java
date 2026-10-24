package com.him.dungeons.util;

import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;

/**
 * 方块数据旋转工具。
 * 使用 Bukkit 原生 {@link BlockData#rotate(StructureRotation)} 方法，
 * 替代 WorldEdit 的 AffineTransform，避免浮点误差。
 *
 * <p>该方法会自动处理所有可旋转属性：
 * Directional(facing)、Orientable(axis)、Rotatable(rotation)、
 * Stairs(shape)、Rails(shape) 等。</p>
 *
 * <p>注意：告示牌的正反面文字不会被修改，只修改放置方向（rotation）。</p>
 */
public final class BlockDataRotator {

    private BlockDataRotator() {}

    /**
     * 旋转 BlockData。
     *
     * @param data          原始方块数据
     * @param rotationTimes 顺时针旋转次数（0-3）
     * @return 旋转后的方块数据（新实例，不修改原对象）
     */
    public static BlockData rotate(BlockData data, int rotationTimes) {
        if (data == null) return null;
        StructureRotation rotation = switch (((rotationTimes % 4) + 4) % 4) {
            case 1 -> StructureRotation.CLOCKWISE_90;
            case 2 -> StructureRotation.CLOCKWISE_180;
            case 3 -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
        if (rotation == StructureRotation.NONE) return data;
        BlockData copy = data.clone();
        copy.rotate(rotation);
        return copy;
    }

    /**
     * 将朝向字符串（如 "x+"）旋转。
     * 使用整数运算，不涉及三角函数。
     */
    public static String rotateFacing(String facing, int times) {
        if (facing == null) return null;
        times = ((times % 4) + 4) % 4;
        if (times == 0) return facing;
        String lower = facing.toLowerCase();
        if (lower.startsWith("y")) return facing;
        // 顺时针顺序：x+ → z- → x- → z+ → x+
        String[] order = {"x+", "z-", "x-", "z+"};
        int idx = -1;
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(lower)) { idx = i; break; }
        }
        if (idx == -1) return facing;
        return order[(idx + times) % 4];
    }
}