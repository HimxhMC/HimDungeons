package com.him.dungeons.util;

import org.bukkit.Location;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.him.dungeons.schematic.NbtSchematic;
import org.bukkit.block.data.BlockData;

/**
 * 选区管理器。
 * 存储玩家用木镐选择的两个点，替代 WorldEdit 的 Region 选区。
 */
public final class SelectionManager {

    private static final Map<UUID, Location> pos1Map = new ConcurrentHashMap<>();
    private static final Map<UUID, Location> pos2Map = new ConcurrentHashMap<>();

    private SelectionManager() {}

    public static void setPos1(UUID uuid, Location loc) {
        pos1Map.put(uuid, loc);
    }

    public static void setPos2(UUID uuid, Location loc) {
        pos2Map.put(uuid, loc);
    }

    public static Location getPos1(UUID uuid) {
        return pos1Map.get(uuid);
    }

    public static Location getPos2(UUID uuid) {
        return pos2Map.get(uuid);
    }

    public static void clear(UUID uuid) {
        pos1Map.remove(uuid);
        pos2Map.remove(uuid);
    }

    /**
     * 获取玩家的选区。两个点都已设置且世界相同时返回 Selection，否则返回 null。
     */
    public static Selection getSelection(UUID uuid) {
        Location p1 = pos1Map.get(uuid);
        Location p2 = pos2Map.get(uuid);
        if (p1 == null || p2 == null) return null;
        if (!p1.getWorld().equals(p2.getWorld())) return null;
        return new Selection(p1, p2);
    }

    /**
     * 选区数据。
     */
    public static class Selection {
        public final Location pos1;
        public final Location pos2;
        public final int minX, minY, minZ;
        public final int maxX, maxY, maxZ;
        public final int width, height, length;

        public Selection(Location p1, Location p2) {
            this.pos1 = p1;
            this.pos2 = p2;
            this.minX = Math.min(p1.getBlockX(), p2.getBlockX());
            this.minY = Math.min(p1.getBlockY(), p2.getBlockY());
            this.minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
            this.maxX = Math.max(p1.getBlockX(), p2.getBlockX());
            this.maxY = Math.max(p1.getBlockY(), p2.getBlockY());
            this.maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());
            this.width  = maxX - minX + 1;
            this.height = maxY - minY + 1;
            this.length = maxZ - minZ + 1;
        }

        /** 从选区构建 NbtSchematic（含方块实体） */
        public NbtSchematic toSchematic() {
            NbtSchematic schem = new NbtSchematic();
            schem.version = 3;
            schem.width = width;
            schem.height = height;
            schem.length = length;

            // 收集调色板
            java.util.Map<String, Integer> paletteMap = new java.util.LinkedHashMap<>();
            java.util.List<BlockData> paletteList = new java.util.ArrayList<>();
            int[] indices = new int[width * height * length];

            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int wx = minX + x, wy = minY + y, wz = minZ + z;
                        BlockData data = pos1.getWorld().getBlockAt(wx, wy, wz).getBlockData();
                        String key = data.getAsString();
                        Integer idx = paletteMap.get(key);
                        if (idx == null) {
                            idx = paletteList.size();
                            paletteMap.put(key, idx);
                            paletteList.add(data);
                        }
                        int bi = (y * length + z) * width + x;
                        indices[bi] = idx;
                    }
                }
            }

            schem.palette = paletteList.toArray(new BlockData[0]);
            schem.blockData = indices;

            // 收集方块实体（基础支持：容器物品 / 告示牌文字 / 刷怪笼 entity）
            schem.blockEntities = collectBlockEntities();
            return schem;
        }

        /** 基础方块实体收集：容器内容 / 告示牌文字 / 刷怪笼 entity */
        private java.util.List<NbtSchematic.BlockEntityData> collectBlockEntities() {
            java.util.List<NbtSchematic.BlockEntityData> out = new java.util.ArrayList<>();
            org.bukkit.World world = pos1.getWorld();
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int wx = minX + x, wy = minY + y, wz = minZ + z;
                        org.bukkit.Material mat = world.getBlockAt(wx, wy, wz).getType();
                        if (!isLikelyTileEntity(mat)) continue;

                        org.bukkit.block.BlockState state = world.getBlockAt(wx, wy, wz).getState();
                        if (!(state instanceof org.bukkit.block.TileState)) continue;

                        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
                        String id = null;

                        if (state instanceof org.bukkit.inventory.InventoryHolder holder) {
                            net.querz.nbt.tag.ListTag<net.querz.nbt.tag.CompoundTag> items =
                                    new net.querz.nbt.tag.ListTag<>(net.querz.nbt.tag.CompoundTag.class);
                            org.bukkit.inventory.ItemStack[] contents = holder.getInventory().getContents();
                            for (int i = 0; i < contents.length; i++) {
                                org.bukkit.inventory.ItemStack stack = contents[i];
                                if (stack == null || stack.getType() == org.bukkit.Material.AIR) continue;
                                net.querz.nbt.tag.CompoundTag item = new net.querz.nbt.tag.CompoundTag();
                                item.putByte("Slot", (byte) i);
                                item.putString("id", stack.getType().getKey().toString());
                                item.putByte("Count", (byte) stack.getAmount());
                                items.add(item);
                            }
                            if (items.size() > 0) {
                                data.put("Items", items);
                                id = state.getType().getKey().toString();
                            }
                        }
                        if (state instanceof org.bukkit.block.Sign sign) {
                            net.querz.nbt.tag.ListTag<net.querz.nbt.tag.StringTag> msgs =
                                    new net.querz.nbt.tag.ListTag<>(net.querz.nbt.tag.StringTag.class);
                            for (String line : sign.getLines()) {
                                msgs.add(new net.querz.nbt.tag.StringTag(line == null ? "" : line));
                            }
                            net.querz.nbt.tag.CompoundTag front = new net.querz.nbt.tag.CompoundTag();
                            front.put("messages", msgs);
                            data.put("front_text", front);
                            id = "minecraft:sign";
                        }
                        if (state instanceof org.bukkit.block.CreatureSpawner spawner) {
                            org.bukkit.entity.EntityType type = spawner.getSpawnedType();
                            if (type != null) {
                                net.querz.nbt.tag.CompoundTag ent = new net.querz.nbt.tag.CompoundTag();
                                ent.putString("id", type.getKey().toString());
                                net.querz.nbt.tag.CompoundTag spawnData = new net.querz.nbt.tag.CompoundTag();
                                spawnData.put("entity", ent);
                                data.put("SpawnData", spawnData);
                                id = "minecraft:mob_spawner";
                            }
                        }

                        if (id != null && !data.isEmpty()) {
                            out.add(new NbtSchematic.BlockEntityData(x, y, z, id, data));
                        }
                    }
                }
            }
            return out;
        }

        private static boolean isLikelyTileEntity(org.bukkit.Material mat) {
            if (mat == null) return false;
            String n = mat.name();
            return n.equals("CHEST") || n.equals("TRAPPED_CHEST") || n.equals("BARREL")
                    || n.contains("SHULKER_BOX")
                    || n.equals("DISPENSER") || n.equals("DROPPER") || n.equals("HOPPER")
                    || n.equals("FURNACE") || n.equals("BLAST_FURNACE") || n.equals("SMOKER")
                    || n.equals("BREWING_STAND") || n.equals("BEACON")
                    || n.equals("SPAWNER") || n.equals("MOB_SPAWNER")
                    || n.contains("_SIGN")
                    || n.equals("JUKEBOX") || n.equals("LECTERN") || n.equals("CONDUIT")
                    || n.equals("BELL") || n.equals("LODESTONE")
                    || n.equals("CAMPFIRE") || n.equals("SOUL_CAMPFIRE")
                    || n.equals("DECORATED_POT");
        }
    }
}