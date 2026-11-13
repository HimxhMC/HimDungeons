package com.him.dungeons.util;

import com.him.dungeons.util.Pos3;
import org.bukkit.Location;

public final class AABBUtil {

    private AABBUtil() {}

    public static class AABB {
        public final double minX, minY, minZ;
        public final double maxX, maxY, maxZ;

        public AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }

        public static boolean overlaps(AABB a, AABB b) {
            if (a == null || b == null) return false;
            return a.minX < b.maxX && a.maxX > b.minX &&
                   a.minY < b.maxY && a.maxY > b.minY &&
                   a.minZ < b.maxZ && a.maxZ > b.minZ;
        }

        public static double minDistance(AABB a, AABB b) {
            if (a == null || b == null) return Double.MAX_VALUE;
            if (overlaps(a, b)) return 0;
            double dx = 0;
            if (a.maxX < b.minX) dx = b.minX - a.maxX;
            else if (b.maxX < a.minX) dx = a.minX - b.maxX;
            double dy = 0;
            if (a.maxY < b.minY) dy = b.minY - a.maxY;
            else if (b.maxY < a.minY) dy = a.minY - b.maxY;
            double dz = 0;
            if (a.maxZ < b.minZ) dz = b.minZ - a.maxZ;
            else if (b.maxZ < a.minZ) dz = a.minZ - b.maxZ;
            return Math.sqrt(dx*dx + dy*dy + dz*dz);
        }
    }

    // 从位置和尺寸构建（偏移）
    public static AABB build(Location anchor, Pos3 size) {
        double x = anchor.getX(), y = anchor.getY(), z = anchor.getZ();
        return new AABB(x, y, z, x + size.getX(), y + size.getY(), z + size.getZ());
    }

    public static boolean contains(AABB aabb, int x, int y, int z) {
        return x >= aabb.minX && x <= aabb.maxX &&
               y >= aabb.minY && y <= aabb.maxY &&
               z >= aabb.minZ && z <= aabb.maxZ;
    }
/**
 * 根据"未旋转的尺寸"和"旋转次数"构建包围盒。
 * 会正确处理旋转后向负方向延伸的情况。
 */
public static AABB buildRotated(Location anchor, Pos3 size, int rot) {
    Pos3 rotated = TransformUtil.rotatePos(size, rot);
    int minX = Math.min(0, rotated.getX());
    int minY = Math.min(0, rotated.getY());
    int minZ = Math.min(0, rotated.getZ());
    int maxX = Math.max(0, rotated.getX());
    int maxY = Math.max(0, rotated.getY());
    int maxZ = Math.max(0, rotated.getZ());
    double ax = anchor.getX(), ay = anchor.getY(), az = anchor.getZ();
    return new AABB(ax + minX, ay + minY, az + minZ,
                    ax + maxX, ay + maxY, az + maxZ);
}
}