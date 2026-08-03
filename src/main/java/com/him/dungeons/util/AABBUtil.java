package com.him.dungeons.util;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
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

    // 从 Region 构建 AABB（世界坐标）
    public static AABB fromRegion(Region region) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
    }

    // 从位置和尺寸构建（偏移）
    public static AABB build(Location anchor, BlockVector3 size) {
        double x = anchor.getX(), y = anchor.getY(), z = anchor.getZ();
        return new AABB(x, y, z, x + size.getX(), y + size.getY(), z + size.getZ());
    }
}