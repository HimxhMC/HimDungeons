package com.him.dungeons.schematic;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.BlockDataRotator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 异步原理图粘贴器。
 * 替代 WorldEdit 的 Operations.completeLegacy()。
 *
 * <p>流程：
 * <ol>
 *   <li>异步读取并解析原理图文件</li>
 *   <li>异步预计算所有方块位置和旋转后的 BlockData</li>
 *   <li>主线程分刻批量提交 setBlockData</li>
 * </ol>
 */
public final class AsyncPaster {

    private AsyncPaster() {}

    /** 每 tick 提交的方块数量（可根据服务器性能调整） */
    private static int getBlocksPerTick() { return com.him.dungeons.util.ConfigUtil.getPasteSpeed(); }

    /**
     * 异步粘贴原理图。
     *
     * @param world        目标世界
     * @param anchor       锚点位置
     * @param schematicFile 原理图文件
     * @param rotationTimes 顺时针旋转次数（0-3）
     * @param formatVersion 格式版本（2 或 3），传 0 自动检测
     * @param callback     完成回调
     */
    public static void pasteAsync(World world, Location anchor, File schematicFile,
                                  int rotationTimes, int formatVersion,
                                  Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(HimDungeons.getInstance(), () -> {
            NbtSchematic schem = SpongeSchematicReader.read(schematicFile, formatVersion);
            if (schem == null) {
                Bukkit.getScheduler().runTask(HimDungeons.getInstance(),
                        () -> callback.accept(false));
                return;
            }

            List<BlockPlacement> placements = new ArrayList<>(schem.width * schem.height * schem.length);
            for (int y = 0; y < schem.height; y++) {
                for (int z = 0; z < schem.length; z++) {
                    for (int x = 0; x < schem.width; x++) {
                        BlockData data = schem.getBlockData(x, y, z);
                        if (data == null || data.getMaterial().isAir()) continue;
                        BlockData rotatedData = BlockDataRotator.rotate(data, rotationTimes);
                        int[] pos = rotate90Y(x, y, z, rotationTimes);
                        placements.add(new BlockPlacement(
                                anchor.getBlockX() + pos[0],
                                anchor.getBlockY() + pos[1],
                                anchor.getBlockZ() + pos[2],
                                rotatedData
                        ));
                    }
                }
            }

            // 收集方块实体
            List<BlockEntityPlacement> blockEntities = new ArrayList<>();
            if (schem.blockEntities != null) {
                for (NbtSchematic.BlockEntityData be : schem.blockEntities) {
                    int[] pos = rotate90Y(be.x, be.y, be.z, rotationTimes);
                    blockEntities.add(new BlockEntityPlacement(
                            anchor.getBlockX() + pos[0],
                            anchor.getBlockY() + pos[1],
                            anchor.getBlockZ() + pos[2],
                            be.id, be.nbtData));
                }
            }
            final List<BlockEntityPlacement> finalBE = blockEntities;

            Bukkit.getScheduler().runTask(HimDungeons.getInstance(), () ->
                    submitInBatches(world, placements, 0, ok -> {
                        if (ok) applyBlockEntities(world, finalBE);
                        callback.accept(ok);
                    }));
        });
    }

    /** 分刻批量提交方块 */
    private static void submitInBatches(World world, List<BlockPlacement> placements,
                                         int startIndex, Consumer<Boolean> callback) {
        int end = Math.min(startIndex + getBlocksPerTick(), placements.size());
        for (int i = startIndex; i < end; i++) {
            BlockPlacement bp = placements.get(i);
            Block block = world.getBlockAt(bp.x, bp.y, bp.z);
            block.setBlockData(bp.data, false);
        }

        if (end < placements.size()) {
            Bukkit.getScheduler().runTaskLater(HimDungeons.getInstance(),
                    () -> submitInBatches(world, placements, end, callback), 1L);
        } else {
            callback.accept(true);
        }
    }

    /**
     * 90°整数倍坐标旋转，纯整数运算。
     * (x, y, z) → 顺时针旋转 times 次后的坐标
     */
    public static int[] rotate90Y(int x, int y, int z, int times) {
        times = ((times % 4) + 4) % 4;
        return switch (times) {
            case 1 -> new int[]{-z, y, x};
            case 2 -> new int[]{-x, y, -z};
            case 3 -> new int[]{z, y, -x};
            default -> new int[]{x, y, z};
        };
    }

    /** 方块放置数据 */
    private static class BlockPlacement {
        final int x, y, z;
        final BlockData data;

        BlockPlacement(int x, int y, int z, BlockData data) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
        }
    }
    // ==================== 批量粘贴（多房间一次性提交） ====================

    /** 一次批量粘贴中，单个房间的请求参数 */
    public static final class BatchRequest {
        public final Location anchor;
        public final File schematicFile;
        public final int rotationTimes;
        public final int formatVersion;
        /** 任意标记，回调时原样返回（可用于携带 RoomInstance） */
        public final Object tag;

        public BatchRequest(Location anchor, File schematicFile, int rotationTimes, int formatVersion) {
            this(anchor, schematicFile, rotationTimes, formatVersion, null);
        }

        public BatchRequest(Location anchor, File schematicFile, int rotationTimes, int formatVersion, Object tag) {
            this.anchor = anchor;
            this.schematicFile = schematicFile;
            this.rotationTimes = rotationTimes;
            this.formatVersion = formatVersion;
            this.tag = tag;
        }
    }



    private static final class RoomBoundary {
        final int startIdx, endIdx;
        RoomBoundary(int s, int e) { this.startIdx = s; this.endIdx = e; }
    }

    /**
     * 批量粘贴多个房间：一次性异步读取全部 schem + 一次性在主线程分 tick 提交。
     * 相比串行 pasteAsync，消除了每个房间 3 次线程调度往返（每房约省 100~150ms）。
     *
     * @param requests   房间粘贴请求列表（顺序即回调顺序）
     * @param onRoomDone 每个房间粘贴完成时回调（主线程），参数 (request, schem)，
     *                   schem 可能为 null（该房 schem 读取失败）
     * @param onComplete 全部完成回调（主线程），true = 无 schem 读取失败
     */
    public static void pasteBatchAsync(World world, List<BatchRequest> requests,
                                       BiConsumer<BatchRequest, NbtSchematic> onRoomDone,
                                       Consumer<Boolean> onComplete) {
        if (requests == null || requests.isEmpty()) {
            if (onComplete != null) onComplete.accept(true);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(HimDungeons.getInstance(), () -> {
            List<PlacedBlockPublic> all = new ArrayList<>();
            List<BlockEntityPlacement> allBlockEntities = new ArrayList<>();
            List<RoomBoundary> bounds = new ArrayList<>();
            List<NbtSchematic> schems = new ArrayList<>();
            boolean anyFail = false;

            for (BatchRequest req : requests) {
                int startIdx = all.size();
                NbtSchematic schem = SpongeSchematicReader.read(req.schematicFile, req.formatVersion);
                if (schem == null) {
                    anyFail = true;
                    schems.add(null);
                    bounds.add(new RoomBoundary(startIdx, startIdx));
                    continue;
                }
                schems.add(schem);
                for (int y = 0; y < schem.height; y++) {
                    for (int z = 0; z < schem.length; z++) {
                        for (int x = 0; x < schem.width; x++) {
                            BlockData data = schem.getBlockData(x, y, z);
                            if (data == null || data.getMaterial().isAir()) continue;
                            BlockData rotated = BlockDataRotator.rotate(data, req.rotationTimes);
                            int[] pos = rotate90Y(x, y, z, req.rotationTimes);
                            all.add(new PlacedBlockPublic(
                                    req.anchor.getBlockX() + pos[0],
                                    req.anchor.getBlockY() + pos[1],
                                    req.anchor.getBlockZ() + pos[2],
                                    rotated
                            ));
                        }
                    }
                }
                // 收集方块实体（坐标按房间 rotation 旋转）
                if (schem.blockEntities != null) {
                    for (NbtSchematic.BlockEntityData be : schem.blockEntities) {
                        int[] pos = rotate90Y(be.x, be.y, be.z, req.rotationTimes);
                        allBlockEntities.add(new BlockEntityPlacement(
                                req.anchor.getBlockX() + pos[0],
                                req.anchor.getBlockY() + pos[1],
                                req.anchor.getBlockZ() + pos[2],
                                be.id, be.nbtData
                        ));
                    }
                }
                bounds.add(new RoomBoundary(startIdx, all.size()));
            }

            final boolean finalAnyFail = anyFail;
            // 尝试快速粘贴
            boolean fastPasteEnabled = com.him.dungeons.util.ConfigUtil.isFastPasteEnabled();
            if (fastPasteEnabled && NmsFastPaster.isAvailable()) {
                try {
                    Bukkit.getScheduler().runTask(HimDungeons.getInstance(), () -> {
                        try {
                            NmsFastPaster.pasteBatch(world, all, ok -> {
                                applyBlockEntities(world, allBlockEntities);
                                for (int i = 0; i < requests.size(); i++) {
                                    onRoomDone.accept(requests.get(i), schems.get(i));
                                }
                                onComplete.accept(ok && !finalAnyFail);
                            });
                        } catch (NmsFastPaster.FastPasteUnavailableException e) {
                            HimDungeons.getInstance().getLogger().warning(
                                    "[快速粘贴] 执行失败，回退普通粘贴: " + e.getMessage());
                            submitBatch(world, all, bounds, requests, schems, 0, 0, onRoomDone,
                                    ok -> {
                                        if (ok) applyBlockEntities(world, allBlockEntities);
                                        onComplete.accept(ok && !finalAnyFail);
                                    });
                        }
                    });
                    return;
                } catch (Exception e) {
                    HimDungeons.getInstance().getLogger().warning(
                            "[快速粘贴] 调度失败，回退普通粘贴: " + e.getMessage());
                }
            }

            // 普通粘贴（原有逻辑）
            Bukkit.getScheduler().runTask(HimDungeons.getInstance(), () ->
                    submitBatch(world, all, bounds, requests, schems, 0, 0, onRoomDone,
                            ok -> {
                                if (ok) applyBlockEntities(world, allBlockEntities);
                                onComplete.accept(ok && !finalAnyFail);
                            }));
        });
    }

    private static void submitBatch(World world, List<PlacedBlockPublic> all,
                                    List<RoomBoundary> bounds,
                                    List<BatchRequest> requests, List<NbtSchematic> schems,
                                    int startIdx, int roomIdx,
                                    BiConsumer<BatchRequest, NbtSchematic> onRoomDone,
                                    Consumer<Boolean> onComplete) {
        int perTick = getBlocksPerTick();
        int end = Math.min(startIdx + perTick, all.size());
        for (int i = startIdx; i < end; i++) {
            PlacedBlockPublic bp = all.get(i);
            world.getBlockAt(bp.x, bp.y, bp.z).setBlockData(bp.data, false);
        }

        int ri = roomIdx;
        while (ri < bounds.size() && bounds.get(ri).endIdx <= end) {
            if (onRoomDone != null) onRoomDone.accept(requests.get(ri), schems.get(ri));
            ri++;
        }

        if (end < all.size()) {
            final int s = end, r = ri;
            Bukkit.getScheduler().runTaskLater(HimDungeons.getInstance(),
                    () -> submitBatch(world, all, bounds, requests, schems, s, r, onRoomDone, onComplete), 1L);
        } else {
            if (onComplete != null) onComplete.accept(true);
        }
    }
    // ==================== 方块实体（BlockEntities）支持 ====================

    /** 方块实体放置数据（世界坐标 + 原始 NBT 数据） */
    public static final class BlockEntityPlacement {
        public final int x, y, z;
        /** 形如 "minecraft:chest" / "minecraft:sign" / "minecraft:mob_spawner" */
        public final String id;
        /** 原始 NBT 数据；value 为 net.querz.nbt.tag.Tag 或基础类型 */
        public final java.util.Map<String, Object> nbtData;

        public BlockEntityPlacement(int x, int y, int z, String id,
                                    java.util.Map<String, Object> nbtData) {
            this.x = x; this.y = y; this.z = z;
            this.id = id;
            this.nbtData = nbtData;
        }
    }

    /**
     * 把 schem 里解析出的方块实体应用到已粘贴的世界（必须在主线程调用）。
     *
     * <p>Bukkit 没有公开的通用 NBT → TileState 接口，这里按方块类型走各自的
     * Bukkit API：容器内容 / 告示牌文字 / 刷怪笼 entity。未覆盖类型会静默跳过。</p>
     */
    public static void applyBlockEntities(World world, List<BlockEntityPlacement> list) {
        if (world == null || list == null || list.isEmpty()) return;
        for (BlockEntityPlacement be : list) {
            try {
                org.bukkit.block.BlockState state = world.getBlockAt(be.x, be.y, be.z).getState();
                if (!(state instanceof org.bukkit.block.TileState)) continue;
                applyOne(state, be);
            } catch (Throwable t) {
                com.him.dungeons.HimDungeons.getInstance().getLogger().warning(
                        "[方块实体] 应用失败 " + be.id
                                + " @(" + be.x + "," + be.y + "," + be.z + "): " + t.getMessage());
            }
        }
    }

    private static void applyOne(org.bukkit.block.BlockState state, BlockEntityPlacement be) {
        // 容器类：箱子、木桶、潜影盒、发射器、投掷器、漏斗、熔炉等
        if (state instanceof org.bukkit.inventory.InventoryHolder holder) {
            org.bukkit.inventory.ItemStack[] items = extractItems(be.nbtData);
            if (items != null) {
                holder.getInventory().setContents(items);
                state.update(true, false);
            }
            return;
        }
        // 告示牌
        if (state instanceof org.bukkit.block.Sign sign) {
            if (applySign(sign, be.nbtData)) {
                sign.update(true, false);
            }
            return;
        }
        // 刷怪笼
        if (state instanceof org.bukkit.block.CreatureSpawner spawner) {
            if (applySpawner(spawner, be.nbtData)) {
                spawner.update(true, false);
            }
        }
    }

    /** 从 Sponge NBT 的 Items 列表还原成 ItemStack 数组（不含附魔/名字等复杂 tag） */
    private static org.bukkit.inventory.ItemStack[] extractItems(
            java.util.Map<String, Object> nbtData) {
        if (nbtData == null) return null;
        Object raw = nbtData.get("Items");
        if (!(raw instanceof net.querz.nbt.tag.ListTag<?> listTag)) return null;

        int size = 27;
        for (Object o : listTag) {
            if (o instanceof net.querz.nbt.tag.CompoundTag c) {
                int slot = c.getByte("Slot") & 0xFF;
                if (slot + 1 > size) size = Math.min(slot + 1, 54);
            }
        }
        org.bukkit.inventory.ItemStack[] out = new org.bukkit.inventory.ItemStack[size];
        for (Object o : listTag) {
            if (!(o instanceof net.querz.nbt.tag.CompoundTag c)) continue;
            int slot = c.getByte("Slot") & 0xFF;
            String id = c.getString("id");
            if (id == null) continue;
            byte count = c.getByte("Count");
            if (count <= 0) count = 1;
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(id);
            if (mat == null) continue;
            if (slot < 0 || slot >= size) continue;
            out[slot] = new org.bukkit.inventory.ItemStack(mat, count);
        }
        return out;
    }

    /** 告示牌文字（新版 front_text / 老版 Text1..Text4） */
    private static boolean applySign(org.bukkit.block.Sign sign,
                                     java.util.Map<String, Object> nbtData) {
        if (nbtData == null) return false;
        Object raw = nbtData.get("front_text");
        if (!(raw instanceof net.querz.nbt.tag.CompoundTag front)) {
            boolean any = false;
            for (int i = 0; i < 4; i++) {
                Object t = nbtData.get("Text" + (i + 1));
                if (t != null) { sign.setLine(i, stripJson(String.valueOf(t))); any = true; }
            }
            return any;
        }
        Object msgs = front.get("messages");
        if (!(msgs instanceof net.querz.nbt.tag.ListTag<?> list)) return false;
        int i = 0;
        for (Object o : list) {
            if (i >= 4) break;
            sign.setLine(i++, stripJson(String.valueOf(o)));
        }
        return true;
    }

    private static String stripJson(String s) {
        if (s == null) return "";
        s = s.trim();
        int t = s.indexOf("\"text\":\"");
        if (t >= 0) {
            int start = t + 8;
            int end = s.indexOf('"', start);
            if (end > start) return s.substring(start, end);
        }
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** 刷怪笼：只应用 entity type */
    private static boolean applySpawner(org.bukkit.block.CreatureSpawner spawner,
                                        java.util.Map<String, Object> nbtData) {
        if (nbtData == null) return false;
        Object spawnData = nbtData.get("SpawnData");
        if (spawnData instanceof net.querz.nbt.tag.CompoundTag c) {
            Object entity = c.get("entity");
            if (entity instanceof net.querz.nbt.tag.CompoundTag ec) {
                String id = ec.getString("id");
                if (id != null) return trySetSpawnerType(spawner, id);
            }
        }
        if (spawnData instanceof net.querz.nbt.tag.StringTag st) {
            return trySetSpawnerType(spawner, st.getValue());
        }
        return false;
    }

    private static boolean trySetSpawnerType(org.bukkit.block.CreatureSpawner spawner, String raw) {
        try {
            String name = raw.replace("minecraft:", "");
            org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.fromName(name);
            if (type != null) { spawner.setSpawnedType(type); return true; }
        } catch (Exception ignored) {}
        return false;
    }

    /** 世界坐标 + 旋转后的 BlockData，供快速粘贴模块读取 */
    public static final class PlacedBlockPublic {
        public final int x, y, z;
        public final BlockData data;

        public PlacedBlockPublic(int x, int y, int z, BlockData data) {
            this.x = x; this.y = y; this.z = z; this.data = data;
        }
    }
    /**
     * 只计算不粘贴：读取 schem + 旋转 → 返回 PlacedBlockPublic 列表。
     * 用于池子预计算。
     */
    public static void computeBatchAsync(List<BatchRequest> requests,
                                         BiConsumer<BatchRequest, NbtSchematic> onRoomComputed,
                                         Consumer<List<PlacedBlockPublic>> onComplete) {
        Bukkit.getScheduler().runTaskAsynchronously(HimDungeons.getInstance(), () -> {
            List<PlacedBlockPublic> all = new ArrayList<>();
            List<NbtSchematic> schems = new ArrayList<>();
            for (BatchRequest req : requests) {
                NbtSchematic schem = SpongeSchematicReader.read(req.schematicFile, req.formatVersion);
                schems.add(schem);
                if (schem == null) continue;
                for (int y = 0; y < schem.height; y++) {
                    for (int z = 0; z < schem.length; z++) {
                        for (int x = 0; x < schem.width; x++) {
                            BlockData data = schem.getBlockData(x, y, z);
                            if (data == null || data.getMaterial().isAir()) continue;
                            BlockData rotated = BlockDataRotator.rotate(data, req.rotationTimes);
                            int[] pos = rotate90Y(x, y, z, req.rotationTimes);
                            all.add(new PlacedBlockPublic(
                                    req.anchor.getBlockX() + pos[0],
                                    req.anchor.getBlockY() + pos[1],
                                    req.anchor.getBlockZ() + pos[2],
                                    rotated
                            ));
                        }
                    }
                }
            }
            final List<PlacedBlockPublic> finalAll = all;
            final List<NbtSchematic> finalSchems = schems;
            Bukkit.getScheduler().runTask(HimDungeons.getInstance(), () -> {
                if (onRoomComputed != null) {
                    for (int i = 0; i < requests.size(); i++) {
                        onRoomComputed.accept(requests.get(i), finalSchems.get(i));
                    }
                }
                onComplete.accept(finalAll);
            });
        });
    }
}