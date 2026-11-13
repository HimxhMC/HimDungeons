package com.him.dungeons.schematic;

import com.him.dungeons.HimDungeons;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.Chunk;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
/**
 * NMS 快速粘贴器：通过反射直接写入 LevelChunkSection，绕过 Bukkit 的高度图与逐方块光源更新。
 *
 * <p>兼容性目标：Paper / Spigot 1.20.5 ~ 26.x（含 v1_20_R4 ~ v1_21_R5 及后续新版本）。</p>
 *
 * <p>仅在配置 {@code fastPaste.enabled=true} 时被调用；任何反射失败会抛出
 * {@link FastPasteUnavailableException}，调用方应回退到 {@link AsyncPaster} 的普通逻辑。</p>
 */
public final class NmsFastPaster {

    private NmsFastPaster() {}

    // ==================== 反射句柄（首次调用时初始化） ====================

    private static volatile boolean initialized = false;
    private static volatile boolean available = false;
    private static String detectedPackage = "";

    private static Method methodGetHandleWorld;   // CraftWorld#getHandle -> ServerLevel
    private static Method methodGetHandleChunk;   // CraftChunk#getHandle -> LevelChunk
    private static Method methodGetSections;      // ChunkAccess#getSections -> LevelChunkSection[]
    private static Method methodGetMinY;          // LevelHeightAccessor#getMinY -> int
    private static Method methodSetBlockState;    // LevelChunkSection#setBlockState(int,int,int,BlockState)
    private static Method methodGetLightEngine;   // Level#getLightEngine -> LevelLightEngine
    private static Method methodCheckBlock;       // LevelLightEngine#checkBlock(BlockPos)
    private static Constructor<?> ctorBlockPos;   // BlockPos(int,int,int)
    private static Method methodBlockDataGetState;// CraftBlockData#getState -> BlockState
    private static Method methodChunkSetUnsaved;  // LevelChunk#setUnsaved(boolean)（可选）
    private static Object chunkStatusFullStatic;  // 缓存 ChunkStatus.FULL 枚举常量
    private static Class<?> entityBlockClass;      // net.minecraft.world.level.block.EntityBlock
    private static Method methodGetBlock;          // BlockState#getBlock
    private static Method methodLevelChunkSetBlockState; // LevelChunk#setBlockState(BlockPos, BlockState, boolean)

    /** 反射不可用时抛出，调用方回退普通粘贴 */
    public static class FastPasteUnavailableException extends RuntimeException {
        public FastPasteUnavailableException(String msg, Throwable cause) { super(msg, cause); }
    }

    // ==================== 初始化 ====================

public static synchronized void tryInit() {
    if (initialized) return;
    initialized = true;
    try {
        detectedPackage = scanPackage();
        if (detectedPackage == null || detectedPackage.isEmpty()) {
            available = false;
            HimDungeons.getInstance().getLogger().info(
                    "[快速粘贴] 未检测到 craftbukkit 包路径，可能是非 CraftBukkit 系服务端，"
                    + "或 Paper 新版本改变了包结构。");
            diagnoseEnvironment();
            return;
        }
        HimDungeons.getInstance().getLogger().info(
                "[快速粘贴] 检测到 NMS 包路径: " + detectedPackage + "，开始初始化反射...");

        String cb = detectedPackage;
        Class<?> craftWorldClass = Class.forName(cb + ".CraftWorld");
        Class<?> craftChunkClass = Class.forName(cb + ".CraftChunk");
        Class<?> craftBlockDataClass = Class.forName(cb + ".block.data.CraftBlockData");

        Class<?> chunkAccessClass = Class.forName("net.minecraft.world.level.chunk.ChunkAccess");
        Class<?> levelChunkSectionClass = Class.forName("net.minecraft.world.level.chunk.LevelChunkSection");
        Class<?> blockStateClass = Class.forName("net.minecraft.world.level.block.state.BlockState");
        Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");

        methodGetHandleWorld = craftWorldClass.getMethod("getHandle");
try {
    // 1. 先尝试无参方法（兼容旧版）
    methodGetHandleChunk = craftChunkClass.getMethod("getHandle");
    chunkStatusFullStatic = null;
} catch (NoSuchMethodException e) {
    // 2. 新版需要传入 ChunkStatus 参数
    try {
        Class<?> chunkStatusClass = null;
        // 兼容 1.21.3+ 与 1.20.5 ~ 1.21.2 的包路径差异
        String[] statusClassPaths = {
                "net.minecraft.world.level.chunk.status.ChunkStatus", // 1.21.3+
                "net.minecraft.world.level.chunk.ChunkStatus"        // 1.20.5 ~ 1.21.2
        };
        for (String path : statusClassPaths) {
            try {
                chunkStatusClass = Class.forName(path);
                break;
            } catch (ClassNotFoundException ignored) {}
        }
        if (chunkStatusClass == null) {
            throw new ClassNotFoundException("ChunkStatus class not found in known packages");
        }

        methodGetHandleChunk = craftChunkClass.getMethod("getHandle", chunkStatusClass);

        // ★ 关键修正：ChunkStatus.FULL 是静态字段，不是枚举常量
        chunkStatusFullStatic = chunkStatusClass.getField("FULL").get(null);
        if (chunkStatusFullStatic == null) {
            throw new NoSuchFieldException("ChunkStatus.FULL field is null");
        }
    } catch (Exception ex) {
        throw new RuntimeException("Failed to resolve ChunkStatus.FULL or CraftChunk.getHandle(ChunkStatus)", ex);
    }
}
        methodGetSections = chunkAccessClass.getMethod("getSections");
        methodGetMinY = chunkAccessClass.getMethod("getMinY");
// ---- LevelChunkSection.setBlockState ----
// 优先尝试 (int, int, int, BlockState) 版本，失败则尝试带 boolean 的版本
try {
    methodSetBlockState = levelChunkSectionClass.getMethod("setBlockState",
            int.class, int.class, int.class, blockStateClass);
} catch (NoSuchMethodException e) {
    methodSetBlockState = levelChunkSectionClass.getMethod("setBlockState",
            int.class, int.class, int.class, blockStateClass, boolean.class);
}

        try {
            Class<?> levelClass = Class.forName("net.minecraft.world.level.Level");
            methodGetLightEngine = levelClass.getMethod("getLightEngine");
            Class<?> lightEngineClass = Class.forName("net.minecraft.world.level.lighting.LevelLightEngine");
            methodCheckBlock = lightEngineClass.getMethod("checkBlock", blockPosClass);
            ctorBlockPos = blockPosClass.getConstructor(int.class, int.class, int.class);
        } catch (Exception ignored) {
            methodGetLightEngine = null;
            methodCheckBlock = null;
            ctorBlockPos = null;
        }

        try {
            Class<?> levelChunkClass = Class.forName("net.minecraft.world.level.chunk.LevelChunk");
            methodChunkSetUnsaved = levelChunkClass.getMethod("setUnsaved", boolean.class);
        } catch (Exception ignored) {
            methodChunkSetUnsaved = null;
        }

        methodBlockDataGetState = craftBlockDataClass.getMethod("getState");
// ---- 获取 LevelChunk.setBlockState（用于TE方块） ----
        // ---- TE 方块判断所需 ----
        entityBlockClass = Class.forName("net.minecraft.world.level.block.EntityBlock");
        methodGetBlock = blockStateClass.getMethod("getBlock");

// ---- LevelChunk.setBlockState（用于 TE 方块），采用多级回退 ----
try {
    Class<?> levelChunkClass = Class.forName("net.minecraft.world.level.chunk.LevelChunk");

    // 方案1: 尝试 (BlockPos, BlockState, boolean)
    try {
        methodLevelChunkSetBlockState = levelChunkClass.getDeclaredMethod(
                "setBlockState", blockPosClass, blockStateClass, boolean.class);
        methodLevelChunkSetBlockState.setAccessible(true);
    } catch (NoSuchMethodException ignored) {
        // 方案2: 尝试 (BlockPos, BlockState, int)
        try {
            methodLevelChunkSetBlockState = levelChunkClass.getDeclaredMethod(
                    "setBlockState", blockPosClass, blockStateClass, int.class);
            methodLevelChunkSetBlockState.setAccessible(true);
        } catch (NoSuchMethodException ignored2) {
            // 方案3: 最后尝试用 public getMethod 找 boolean 版本
            methodLevelChunkSetBlockState = levelChunkClass.getMethod(
                    "setBlockState", blockPosClass, blockStateClass, boolean.class);
        }
    }
} catch (Exception e) {
    methodLevelChunkSetBlockState = null;
    HimDungeons.getInstance().getLogger().warning(
            "[快速粘贴] 未能获取 LevelChunk.setBlockState，TE方块将回退普通方式: " + e.getMessage());
}
// 为所有反射句柄关闭访问检查，大幅降低 invoke 开销
try {
    if (methodGetHandleWorld != null) methodGetHandleWorld.setAccessible(true);
    if (methodGetHandleChunk != null) methodGetHandleChunk.setAccessible(true);
    if (methodGetSections != null) methodGetSections.setAccessible(true);
    if (methodGetMinY != null) methodGetMinY.setAccessible(true);
    if (methodSetBlockState != null) methodSetBlockState.setAccessible(true);
    if (methodGetLightEngine != null) methodGetLightEngine.setAccessible(true);
    if (methodCheckBlock != null) methodCheckBlock.setAccessible(true);
    if (ctorBlockPos != null) ctorBlockPos.setAccessible(true);
    if (methodBlockDataGetState != null) methodBlockDataGetState.setAccessible(true);
    if (methodChunkSetUnsaved != null) methodChunkSetUnsaved.setAccessible(true);
        if (methodGetBlock != null) methodGetBlock.setAccessible(true);
    if (methodLevelChunkSetBlockState != null) methodLevelChunkSetBlockState.setAccessible(true);
} catch (Exception e) {
    HimDungeons.getInstance().getLogger().warning("[快速粘贴] setAccessible 部分失败: " + e.getMessage());
}
        available = true;
        
        HimDungeons.getInstance().getLogger().info("[快速粘贴] 初始化成功，NMS 包路径: " + detectedPackage);

    } catch (Throwable t) {
        available = false;
        HimDungeons.getInstance().getLogger().warning(
                "[快速粘贴] 初始化失败，将回退普通粘贴: "
                + t.getClass().getSimpleName() + " - " + t.getMessage());
        t.printStackTrace();
    }
}

/** 当包名无法识别时打印环境诊断信息 */
private static void diagnoseEnvironment() {
    try {
        HimDungeons plugin = HimDungeons.getInstance();
        plugin.getLogger().info("[快速粘贴][诊断] 服务端类: "
                + Bukkit.getServer().getClass().getName());
        plugin.getLogger().info("[快速粘贴][诊断] 服务端版本: " + Bukkit.getVersion());
        plugin.getLogger().info("[快速粘贴][诊断] Bukkit 版本: " + Bukkit.getBukkitVersion());
    } catch (Exception ignored) {}
}

    public static boolean isAvailable() { tryInit(); return available; }
    public static String getDetectedPackage() { tryInit(); return detectedPackage; }

    /** 扫描 org.bukkit.craftbukkit 下的子包（形如 v1_21_R3） */
/**
 * 返回服务端 CraftBukkit 类的完整包名。
 * 1.20.4 及以下: org.bukkit.craftbukkit.vX_Y_RZ
 * Paper 1.20.5+: org.bukkit.craftbukkit
 * Spigot 1.20.5+: org.bukkit.craftbukkit.vX_Y_RZ
 */
private static String scanPackage() {
    try {
        // 直接获取 CraftServer 类的完整包名，这是最可靠的方式。
        // 它本身就包含了版本号（如果存在的话）。
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        if (packageName != null && packageName.startsWith("org.bukkit.craftbukkit")) {
            // 验证一下这个包名下确实有 CraftServer 类，双重保险。
            Class.forName(packageName + ".CraftServer");
            return packageName;
        }
    } catch (Exception ignored) {
        // 如果上面的方法失败（极罕见情况），回退到旧版候选列表扫描。
    }
    // 回退方案：仅用于兼容极少数非标准服务端。对于 Paper/Spigot 1.20.5+，通常不会走到这里。
    String[] legacyCandidates = {
            "org.bukkit.craftbukkit.v1_21_R7", "org.bukkit.craftbukkit.v1_21_R6",
            "org.bukkit.craftbukkit.v1_21_R5", "org.bukkit.craftbukkit.v1_21_R4",
            "org.bukkit.craftbukkit.v1_21_R3", "org.bukkit.craftbukkit.v1_21_R2",
            "org.bukkit.craftbukkit.v1_21_R1", "org.bukkit.craftbukkit.v1_20_R4",
            "org.bukkit.craftbukkit.v1_20_R3", "org.bukkit.craftbukkit.v1_20_R2"
    };
    for (String candidate : legacyCandidates) {
        try {
            Class.forName(candidate + ".CraftServer");
            return candidate;
        } catch (ClassNotFoundException ignored) {}
    }
    return "";
}

/** 验证包名是否有效（CraftServer 存在） */
private static boolean verifyPackage(String pkg) {
    try {
        Class.forName("org.bukkit.craftbukkit." + pkg + ".CraftServer");
        return true;
    } catch (ClassNotFoundException e) {
        return false;
    }
}

    // ==================== 粘贴入口 ====================

    public static void pasteBatch(World world, List<AsyncPaster.PlacedBlockPublic> all,
                                  Consumer<Boolean> callback) {
        pasteBatch(world, all, null, callback);
    }

    /**
     * 带进度回调的粘贴。onProgress 接收 0~100：
     *   0~70  = chunk 加载阶段
     *   70    = chunk 全部就绪
     *   100   = 方块写入完成
     * onProgress 会在主线程被调用，可直接安全操作 GUI/消息。
     */
    public static void pasteBatch(World world, List<AsyncPaster.PlacedBlockPublic> all,
                                  IntConsumer onProgress, Consumer<Boolean> callback) {
        if (!isAvailable()) throw new FastPasteUnavailableException("NMS 快速粘贴不可用", null);

        try {
            // 1. 按 Chunk 分组
            Map<Long, List<AsyncPaster.PlacedBlockPublic>> byChunk = new HashMap<>();
            for (AsyncPaster.PlacedBlockPublic bp : all) {
                int cx = bp.x >> 4, cz = bp.z >> 4;
                long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(bp);
            }

            final int totalChunks = byChunk.size();
            final AtomicInteger loaded = new AtomicInteger(0);
            final int reportStep = Math.max(1, totalChunks / 10); // 约 10 次进度上报

            // 2. 异步预加载全部 chunk（urgent=true，插队加载减少排队等待）
            List<CompletableFuture<Chunk>> loadFutures = new ArrayList<>(totalChunks);
            for (Long key : byChunk.keySet()) {
                int cx = (int) (key >> 32);
                int cz = (int) (long) key;
                CompletableFuture<Chunk> f = loadChunkUrgent(world, cx, cz);
                if (onProgress != null) {
                    f.thenRun(() -> {
                        int n = loaded.incrementAndGet();
                        if (n % reportStep == 0 || n == totalChunks) {
                            int p = (int) (n * 70.0 / totalChunks);
                            Bukkit.getScheduler().runTask(HimDungeons.getInstance(),
                                    () -> onProgress.accept(p));
                        }
                    });
                }
                loadFutures.add(f);
            }

            CompletableFuture.allOf(loadFutures.toArray(new CompletableFuture[0]))
                    .whenComplete((v, err) -> Bukkit.getScheduler().runTask(
                            HimDungeons.getInstance(), () -> {
                                if (err != null) {
                                    throw new FastPasteUnavailableException(
                                            "chunk 预加载失败: " + err.getMessage(), err);
                                }
                                if (onProgress != null) onProgress.accept(70);
                                try {
                                    doPaste(world, byChunk, ok -> {
                                        if (onProgress != null) onProgress.accept(100);
                                        callback.accept(ok);
                                    });
                                } catch (Throwable t) {
                                    throw new FastPasteUnavailableException(
                                            "NMS 粘贴执行失败: " + t.getClass().getSimpleName()
                                                    + " - " + t.getMessage(), t);
                                }
                            }));
        } catch (Throwable t) {
            throw new FastPasteUnavailableException(
                    "NMS 粘贴调度失败: " + t.getClass().getSimpleName() + " - " + t.getMessage(), t);
        }
    }

    /** 带 urgent 标志的 chunk 加载；旧版 Paper 无此重载时自动回退 */
    private static volatile Boolean hasUrgentOverload = null;
    private static CompletableFuture<Chunk> loadChunkUrgent(World world, int cx, int cz) {
        if (hasUrgentOverload == null) {
            synchronized (NmsFastPaster.class) {
                if (hasUrgentOverload == null) {
                    try {
                        World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
                        hasUrgentOverload = Boolean.TRUE;
                    } catch (NoSuchMethodException e) {
                        hasUrgentOverload = Boolean.FALSE;
                    }
                }
            }
        }
        return hasUrgentOverload ? world.getChunkAtAsync(cx, cz, true)
                                 : world.getChunkAtAsync(cx, cz);
    }

/** 主线程实际粘贴阶段：chunk 已全部加载，纯内存写入 */
private static void doPaste(World world,
                            Map<Long, List<AsyncPaster.PlacedBlockPublic>> byChunk,
                            Consumer<Boolean> callback) throws Exception {
    long startNs = System.nanoTime();
    int totalBlocks = 0;

    Map<BlockData, Object> stateCache = new IdentityHashMap<>();
    Map<BlockData, Boolean> teCache = new IdentityHashMap<>();

    // 预先判断方法签名，避免在循环中重复判断
    int sectionSetBlockStateParamCount = methodSetBlockState.getParameterCount();
    Class<?>[] chunkSetBlockStateParamTypes = methodLevelChunkSetBlockState.getParameterTypes();
    boolean chunkSetBlockStateUsesInt = chunkSetBlockStateParamTypes[2] == int.class;

    // ★ 写入阶段顺便缓存 levelChunk，修复阶段复用，避免二次 getHandle
    Map<Long, Object> levelChunkCache = new HashMap<>(byChunk.size());

    // ============ 阶段 1：写入方块 ============
    for (Map.Entry<Long, List<AsyncPaster.PlacedBlockPublic>> entry : byChunk.entrySet()) {
        long key = entry.getKey();
        int cx = (int) (key >> 32);
        int cz = (int) (long) key;

        Object craftChunk = world.getChunkAt(cx, cz);   // 已预加载，O(1)
        Object levelChunk;
        if (methodGetHandleChunk.getParameterCount() == 0) {
            levelChunk = methodGetHandleChunk.invoke(craftChunk);
        } else {
            levelChunk = methodGetHandleChunk.invoke(craftChunk, chunkStatusFullStatic);
        }
        levelChunkCache.put(key, levelChunk);

        Object[] sections = (Object[]) methodGetSections.invoke(levelChunk);
        int minY = (int) methodGetMinY.invoke(levelChunk);
        int minSectionIdx = minY >> 4;

        for (AsyncPaster.PlacedBlockPublic bp : entry.getValue()) {
            int sectionIndex = (bp.y >> 4) - minSectionIdx;
            if (sectionIndex < 0 || sectionIndex >= sections.length) continue;
            Object section = sections[sectionIndex];
            if (section == null) continue;

            Object nmsState = stateCache.get(bp.data);
            if (nmsState == null) {
                nmsState = methodBlockDataGetState.invoke(bp.data);
                stateCache.put(bp.data, nmsState);
            }

            Boolean isTE = teCache.get(bp.data);
            if (isTE == null) {
                Object nmsBlock = methodGetBlock.invoke(nmsState);
                isTE = entityBlockClass.isInstance(nmsBlock);
                teCache.put(bp.data, isTE);
            }

            if (isTE) {
                Object pos = ctorBlockPos.newInstance(bp.x, bp.y, bp.z);
                if (chunkSetBlockStateUsesInt) {
                    methodLevelChunkSetBlockState.invoke(levelChunk, pos, nmsState, 0);
                } else {
                    methodLevelChunkSetBlockState.invoke(levelChunk, pos, nmsState, false);
                }
            } else {
                if (sectionSetBlockStateParamCount == 4) {
                    methodSetBlockState.invoke(section,
                            bp.x & 15, bp.y & 15, bp.z & 15, nmsState);
                } else {
                    methodSetBlockState.invoke(section,
                            bp.x & 15, bp.y & 15, bp.z & 15, nmsState, false);
                }
            }
            totalBlocks++;
        }

        if (methodChunkSetUnsaved != null) {
            try { methodChunkSetUnsaved.invoke(levelChunk, true); }
            catch (Exception ignored) {}
        }
    }

// ============ 阶段 2：全局修复（基于 Paper 1.21 确切签名） ============
// 2.1 重建 Heightmap（必须保留）
boolean heightmapOk = true;
for (Object lc : levelChunkCache.values()) {
    if (!rebuildHeightmaps(lc)) heightmapOk = false;
}

// 2.2 通过 ThreadedLevelLightEngine 的 relight 入口重算光照
boolean relightAttempted = false;
try {
    Object serverLevel = methodGetHandleWorld.invoke(world);
    // Paper 的 getLightEngine() 通常返回 ThreadedLevelLightEngine
    Object lightEngine = serverLevel.getClass().getMethod("getLightEngine").invoke(serverLevel);

    Class<?> chunkPosClass = Class.forName("net.minecraft.world.level.ChunkPos");
    Constructor<?> chunkPosCtor = chunkPosClass.getConstructor(int.class, int.class);

    // 构造 Set<ChunkPos>（公开 relight 签名要求 Set）
    Set<Object> chunkPosSet = new HashSet<>();
    for (Long key : levelChunkCache.keySet()) {
        int cx = (int) (key >> 32);
        int cz = (int) (long) key;
        chunkPosSet.add(chunkPosCtor.newInstance(cx, cz));
    }

    // 空回调，我们不依赖逐 chunk 通知
    Object noopConsumer = Proxy.newProxyInstance(
            Consumer.class.getClassLoader(),
            new Class[]{Consumer.class},
            (p, m, args) -> null
    );
    Object noopIntConsumer = Proxy.newProxyInstance(
            IntConsumer.class.getClassLoader(),
            new Class[]{IntConsumer.class},
            (p, m, args) -> null
    );

    // 1) 优先调用公开的 relight(Set<ChunkPos>, Consumer, IntConsumer)
    Method relight = null;
    try {
        relight = lightEngine.getClass().getMethod(
                "relight", Set.class, Consumer.class, IntConsumer.class);
    } catch (NoSuchMethodException ignored) {}

    if (relight != null) {
        relight.setAccessible(true);
        relight.invoke(lightEngine, chunkPosSet, noopConsumer, noopIntConsumer);
        relightAttempted = true;
        HimDungeons.getInstance().getLogger().info(
                "[快速粘贴] ThreadedLevelLightEngine.relight 已发起，Chunk 数: " + chunkPosSet.size());
    } else {
        // 2) 回退到 starlight$serverRelightChunks(Collection<ChunkPos>, Consumer, IntConsumer)
        Method starlightRelight = null;
        try {
            starlightRelight = lightEngine.getClass().getMethod(
                    "starlight$serverRelightChunks", Collection.class, Consumer.class, IntConsumer.class);
        } catch (NoSuchMethodException ignored) {}

        if (starlightRelight != null) {
            starlightRelight.setAccessible(true);
            starlightRelight.invoke(lightEngine, chunkPosSet, noopConsumer, noopIntConsumer);
            relightAttempted = true;
            HimDungeons.getInstance().getLogger().info(
                    "[快速粘贴] starlight$serverRelightChunks 已发起，Chunk 数: " + chunkPosSet.size());
        }
    }
} catch (Throwable t) {
    HimDungeons.getInstance().getLogger().warning(
            "[快速粘贴] relight 调用失败: " + t.getClass().getSimpleName()
                    + " - " + t.getMessage());
}

// 2.3 如果所有 relight 入口都不可用，标记世界待重载（由调用方消费）
if (!relightAttempted) {
    world.setMetadata("dungeon_need_reload_for_light",
            new FixedMetadataValue(HimDungeons.getInstance(), true));
    HimDungeons.getInstance().getLogger().warning(
            "[快速粘贴] 未能通过 relight 重算光照，已标记世界待重载");
}

    long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
    double rate = elapsedMs > 0 ? totalBlocks * 1000.0 / elapsedMs : totalBlocks;
    HimDungeons.getInstance().getLogger().info(String.format(
            "[快速粘贴] %d 方块 / %d chunk 完成，耗时 %dms（%.0f 方块/秒），Heightmap %s",
            totalBlocks, byChunk.size(), elapsedMs, rate,
            heightmapOk ? "OK" : "部分失败"));

    if (callback != null) callback.accept(true);
}
/**
 * 重建 chunk 的全部 Heightmap。
 * 快速通道直接写 LevelChunkSection，绕过了原版 Heightmap 维护，
 * 不重建会导致：亡灵头顶有方块仍燃烧、白天不刷怪、手动搭方块也无效。
 */
@SuppressWarnings({"unchecked", "rawtypes"})
private static boolean rebuildHeightmaps(Object levelChunk) {
    try {
        Class<?> heightmapClass = Class.forName("net.minecraft.world.level.levelgen.Heightmap");
        Class<?> typesClass = Class.forName("net.minecraft.world.level.levelgen.Heightmap$Types");

        Object types = java.util.EnumSet.of(
                (Enum) Enum.valueOf((Class) typesClass, "MOTION_BLOCKING"),
                (Enum) Enum.valueOf((Class) typesClass, "MOTION_BLOCKING_NO_LEAVES"),
                (Enum) Enum.valueOf((Class) typesClass, "OCEAN_FLOOR"),
                (Enum) Enum.valueOf((Class) typesClass, "WORLD_SURFACE")
        );

        Method prime = null;
        for (Method m : heightmapClass.getMethods()) {
            if (m.getName().equals("primeHeightmaps") && m.getParameterCount() == 2) {
                prime = m;
                break;
            }
        }
        if (prime == null) {
            if (heightmapWarned.compareAndSet(false, true)) {
                HimDungeons.getInstance().getLogger().warning(
                        "[快速粘贴] 未找到 Heightmap.primeHeightmaps，头顶遮蔽与刷怪判定将失效");
            }
            return false;
        }
        prime.setAccessible(true);
        prime.invoke(null, levelChunk, types);
        return true;
    } catch (Throwable t) {
        if (heightmapWarned.compareAndSet(false, true)) {
            HimDungeons.getInstance().getLogger().warning(
                    "[快速粘贴] Heightmap 重建失败: " + t.getClass().getSimpleName()
                            + " - " + t.getMessage());
        }
        return false;
    }
}

/** 标记 chunk 的光照为「不可信」，触发引擎重新计算。失败静默，不阻塞流程。 */
private static void trySetLightCorrect(Object levelChunk, boolean correct) {
    try {
        Method m = levelChunk.getClass().getMethod("setLightCorrect", boolean.class);
        m.setAccessible(true);
        m.invoke(levelChunk, correct);
    } catch (Throwable ignored) {
        // 部分 Paper/Starlight 版本没有此方法或签名不同，
        // 后续 updateSectionStatus + runUpdates 仍会推动重算
    }
}

/**
 * 把 chunk 内所有 section 标记为待更新。
 * 不判断 hasOnlyAir —— 快速粘贴前是空气的 section，粘贴后可能不是，
 * 判断会漏掉必要的更新。
 */
private static void markAllSectionsForUpdate(Object lightEngine, Object levelChunk,
                                             int cx, int cz) {
    try {
        Class<?> sectionPosClass = Class.forName("net.minecraft.core.SectionPos");
        Method ofMethod = sectionPosClass.getMethod("of", int.class, int.class, int.class);
        Method updateStatus = lightEngine.getClass().getMethod(
                "updateSectionStatus", sectionPosClass, boolean.class);

        Object[] sections = (Object[]) methodGetSections.invoke(levelChunk);
        int minY = (int) methodGetMinY.invoke(levelChunk);
        int minSectionIdx = minY >> 4;

        for (int i = 0; i < sections.length; i++) {
            Object sectionPos = ofMethod.invoke(null, cx, minSectionIdx + i, cz);
            updateStatus.invoke(lightEngine, sectionPos, false);
        }
    } catch (Throwable ignored) {
        // 反射失败不影响主流程，兜底队列会补
    }
}
    /**
     * 通知光照引擎重算指定 chunk 的天空光照。
     *
     * <p>1.20.6 没有直接“relight 一个 chunk”的公开 API，这里用两种方式组合：
     * <ol>
     *   <li>调用 {@code LevelLightEngine#updateSectionStatus(SectionPos, boolean)}
     *       把所有 section 标记为“非空”，触发光照引擎重新传播；</li>
     *   <li>若可用，调用 {@code ThreadedLevelLightEngine#relightChunks(Collection, Consumer)}
     *       或走 ticket 队列，强制完整重算。</li>
     * </ol>
     * </p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void notifyLightEngine(Object world, Object levelChunk, int cx, int cz) {
        try {
            // 获取 ServerLevel#getChunkSource() -> getLightEngine()
            Object serverLevel = methodGetHandleWorld.invoke(world);
            Object chunkSource = serverLevel.getClass().getMethod("getChunkSource").invoke(serverLevel);
            Object lightEngine = chunkSource.getClass().getMethod("getLightEngine").invoke(chunkSource);

            // ---- 方式 1：updateSectionStatus(SectionPos, isEmpty=false) ----
            // 把每个非空 section 标记为“非空”，光照引擎会重新传播天空光
            try {
                Class<?> sectionPosClass = Class.forName("net.minecraft.core.SectionPos");
                java.lang.reflect.Method ofMethod = sectionPosClass.getMethod(
                        "of", int.class, int.class, int.class);
                java.lang.reflect.Method updateStatus = lightEngine.getClass().getMethod(
                        "updateSectionStatus", sectionPosClass, boolean.class);

                Object sections = methodGetSections.invoke(levelChunk);
                int minY = (int) methodGetMinY.invoke(levelChunk);
                int minSectionIdx = minY >> 4;
                for (int i = 0; i < java.lang.reflect.Array.getLength(sections); i++) {
                    Object section = java.lang.reflect.Array.get(sections, i);
                    if (section == null) continue;
                    // 忽略全空气 section
                    boolean isEmpty;
                    try {
                        isEmpty = (boolean) section.getClass().getMethod("hasOnlyAir").invoke(section);
                    } catch (NoSuchMethodException e) {
                        isEmpty = false;
                    }
                    if (isEmpty) continue;
                    Object sectionPos = ofMethod.invoke(null, cx, minSectionIdx + i, cz);
                    updateStatus.invoke(lightEngine, sectionPos, false);
                }
            } catch (Throwable ignored) {
                // 旧版本或 Paper 改写过光照引擎时可能没有这个方法，走方式 2
            }

            // ---- 方式 2：relightChunks / ticket 队列 ----
            // ThreadedLevelLightEngine#relightChunks(Collection<ChunkPos>, Consumer<ChunkPos>)
            try {
                Class<?> chunkPosClass = Class.forName("net.minecraft.world.level.ChunkPos");
                Object chunkPos = chunkPosClass.getConstructor(int.class, int.class)
                        .newInstance(cx, cz);
                java.lang.reflect.Method relightChunks = null;
                for (java.lang.reflect.Method m : lightEngine.getClass().getMethods()) {
                    if (m.getName().equals("relightChunks") && m.getParameterCount() == 2) {
                        relightChunks = m;
                        break;
                    }
                }
                if (relightChunks != null) {
                    // 第二个参数是 Consumer<ChunkPos>，传一个空消费者
                    Class<?> consumerClass = relightChunks.getParameterTypes()[1];
                    Object noop = java.lang.reflect.Proxy.newProxyInstance(
                            consumerClass.getClassLoader(),
                            new Class[]{consumerClass},
                            (proxy, method, args) -> null);
                    relightChunks.invoke(lightEngine, java.util.Collections.singletonList(chunkPos), noop);
                }
            } catch (Throwable ignored) {
                // 部分服务端（尤其 Paper 的 Starlight）没有 relightChunks，忽略
            }
            // ---- 方式 3（关键）：强制消化光照更新队列 ----
            // updateSectionStatus 只是把 section 标记为待更新，
            // 天空光/方块光的实际重算被排入 LevelLightEngine 内部队列。
            // 必须调用 runUpdates 强制立即执行，否则光照值停留在旧状态：
            // 骷髅头顶有方块仍按 skyLight=15 燃烧，白天自然刷怪也因光照≥8被禁。
            //
            // 优先 runUpdates(int, boolean, boolean)（原版 1.20.5+ 签名）
            try {
                java.lang.reflect.Method runUpdates = null;
                for (java.lang.reflect.Method m : lightEngine.getClass().getMethods()) {
                    if (m.getName().equals("runUpdates") && m.getParameterCount() == 3) {
                        runUpdates = m;
                        break;
                    }
                }
                if (runUpdates != null) {
                    // Integer.MAX_VALUE = 不限制单次处理量；true,true = 同时处理天空光与方块光
                    runUpdates.invoke(lightEngine, Integer.MAX_VALUE, true, true);
                } else {
                    // 回退：runLightUpdates() 无参版本（部分映射名）
                    for (java.lang.reflect.Method m : lightEngine.getClass().getMethods()) {
                        if (m.getName().equals("runLightUpdates") && m.getParameterCount() == 0) {
                            m.invoke(lightEngine);
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // 极端情况下反射失败，setLightCorrect(false) 已在方式 2b 中兜底，
                // 但天空光可能仍滞后，需在日志中确认
            }
        } catch (Throwable t) {
            if (lightWarned.compareAndSet(false, true)) {
                HimDungeons.getInstance().getLogger().warning(
                        "[快速粘贴] 光照引擎通知失败: " + t.getClass().getSimpleName()
                                + " - " + t.getMessage()
                                + "（骷髅可能仍燃烧，请确认服务端光照引擎类型）");
            }
        }
    }
    private static final java.util.concurrent.atomic.AtomicBoolean lightWarned =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    /** 把扫描结果写回 config（由 HimDungeons 在启动时调用） */
    public static void saveDetectedPackageToConfig(HimDungeons plugin) {
        String pkg = getDetectedPackage();
        if (pkg == null || pkg.isEmpty()) return;
        org.bukkit.configuration.file.FileConfiguration cfg =
                com.him.dungeons.util.ConfigUtil.getGlobalConfig();
        if (!pkg.equals(cfg.getString("fastPaste.detectedPackage", ""))) {
            cfg.set("fastPaste.detectedPackage", pkg);
            com.him.dungeons.util.ConfigUtil.saveGlobalConfig(cfg);
        }
    }
    /**
     * 重建 chunk 的全部 Heightmap。
     *
     * <p>NMS 直接写 LevelChunkSection 绕过了原版 heightmap 维护，
     * 导致 MOTION_BLOCKING / WORLD_SURFACE 仍停留在“全是空气”的状态：
     * 亡灵生物头顶有方块仍燃烧，且白天整个地牢不刷怪。</p>
     *
     * <p>1.20.6 没有 ChunkAccess#refreshHeightmaps()，唯一正确入口是
     * {@code Heightmap.primeHeightmaps(ChunkAccess, Set<Heightmap.Types>)}。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void refreshChunkHeightmaps(Object levelChunk) {
        try {
            Class<?> heightmapClass = Class.forName("net.minecraft.world.level.levelgen.Heightmap");
            Class<?> typesClass = Class.forName("net.minecraft.world.level.levelgen.Heightmap$Types");

            // 必须同时重建 MOTION_BLOCKING（刷怪/碰撞）和 WORLD_SURFACE（天空判定）
            // 否则只修好燃烧但刷怪仍然异常，或反之。
            Object types = java.util.EnumSet.of(
                    (Enum) Enum.valueOf((Class) typesClass, "MOTION_BLOCKING"),
                    (Enum) Enum.valueOf((Class) typesClass, "MOTION_BLOCKING_NO_LEAVES"),
                    (Enum) Enum.valueOf((Class) typesClass, "OCEAN_FLOOR"),
                    (Enum) Enum.valueOf((Class) typesClass, "WORLD_SURFACE")
            );

            java.lang.reflect.Method prime = null;
            for (java.lang.reflect.Method m : heightmapClass.getMethods()) {
                if (m.getName().equals("primeHeightmaps") && m.getParameterCount() == 2) {
                    prime = m;
                    break;
                }
            }
            if (prime == null) {
                if (heightmapWarned.compareAndSet(false, true)) {
                    HimDungeons.getInstance().getLogger().warning(
                            "[快速粘贴] 未找到 Heightmap.primeHeightmaps，头顶遮蔽与刷怪判定将失效");
                }
                return;
            }
            prime.setAccessible(true);
            prime.invoke(null, levelChunk, types);
        } catch (Throwable t) {
            if (heightmapWarned.compareAndSet(false, true)) {
                HimDungeons.getInstance().getLogger().warning(
                        "[快速粘贴] Heightmap 重建失败: " + t.getClass().getSimpleName()
                                + " - " + t.getMessage()
                                + "（骷髅可能燃烧、地牢白天可能不刷怪）");
            }
        }
    }
    private static final java.util.concurrent.atomic.AtomicBoolean heightmapWarned =
            new java.util.concurrent.atomic.AtomicBoolean(false);
// 待刷新的区块坐标队列（全局，由 doPaste 填充）
private static final Queue<long[]> pendingLightRefresh = new ConcurrentLinkedQueue<>();

/**
 * 将区块加入光照刷新队列（线程安全，可在任意线程调用）
 */
public static void enqueueLightRefresh(int cx, int cz) {
    pendingLightRefresh.offer(new long[]{cx, cz});
}

/**
 * 启动分刻光照刷新任务（主线程调用）
 * 每 tick 处理一个区块，处理完即强制消化光照队列
 */
public static void startLightRefreshTask(World world) {
    new org.bukkit.scheduler.BukkitRunnable() {
        @Override
        public void run() {
            long[] pos = pendingLightRefresh.poll();
            if (pos == null) {
                cancel(); // 队列空了，任务结束
                return;
            }
            int cx = (int) pos[0];
            int cz = (int) pos[1];
            try {
                // 1. 获取 chunk 和光照引擎
                Object craftChunk = world.getChunkAt(cx, cz);
                Object levelChunk = methodGetHandleChunk.getParameterCount() == 0
                        ? methodGetHandleChunk.invoke(craftChunk)
                        : methodGetHandleChunk.invoke(craftChunk, chunkStatusFullStatic);

                Object serverLevel = methodGetHandleWorld.invoke(world);
                Object chunkSource = serverLevel.getClass().getMethod("getChunkSource").invoke(serverLevel);
                Object lightEngine = chunkSource.getClass().getMethod("getLightEngine").invoke(chunkSource);

                // 2. 标记所有 section 为待更新
                markSectionsForUpdate(lightEngine, levelChunk, cx, cz);

                // 3. ★ 关键：强制消化光照更新队列
                forceRunLightUpdates(lightEngine);

            } catch (Throwable t) {
                HimDungeons.getInstance().getLogger().warning(
                        "[光照刷新] 区块 (" + cx + "," + cz + ") 刷新失败: " + t.getMessage());
            }
        }
    }.runTaskTimer(HimDungeons.getInstance(), 1L, 1L); // 每 tick 处理一个
}

/**
 * 标记 chunk 内所有非空气 section 为待更新
 */
private static void markSectionsForUpdate(Object lightEngine, Object levelChunk, int cx, int cz) throws Exception {
    Class<?> sectionPosClass = Class.forName("net.minecraft.core.SectionPos");
    Method ofMethod = sectionPosClass.getMethod("of", int.class, int.class, int.class);
    Method updateStatus = lightEngine.getClass().getMethod("updateSectionStatus", sectionPosClass, boolean.class);

    Object[] sections = (Object[]) methodGetSections.invoke(levelChunk);
    int minY = (int) methodGetMinY.invoke(levelChunk);
    int minSectionIdx = minY >> 4;

    for (int i = 0; i < sections.length; i++) {
        if (sections[i] == null) continue;
        Object sectionPos = ofMethod.invoke(null, cx, minSectionIdx + i, cz);
        updateStatus.invoke(lightEngine, sectionPos, false);
    }
}

/**
 * ★ 核心：强制调用光照引擎的更新方法，清空队列
 */
private static void forceRunLightUpdates(Object lightEngine) throws Exception {
    for (Method m : lightEngine.getClass().getMethods()) {
        if (m.getName().equals("runUpdates") && m.getParameterCount() == 3) {
            m.invoke(lightEngine, Integer.MAX_VALUE, true, true);
            return;
        }
    }
    // 回退到 runLightUpdates() —— 部分映射版本
    for (Method m : lightEngine.getClass().getMethods()) {
        if (m.getName().equals("runLightUpdates") && m.getParameterCount() == 0) {
            m.invoke(lightEngine);
            return;
        }
    }
}
}