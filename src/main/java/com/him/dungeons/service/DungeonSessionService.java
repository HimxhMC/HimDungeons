package com.him.dungeons.service;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.generator.ConfigApplier;
import com.him.dungeons.storage.BackupStorage;
import com.him.dungeons.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地牢玩家生命周期统一服务。
 *
 * <p>所有"进入 / 离开 / 通关 / 离线 / 上线"路径都经过此服务，
 * 以保证备份存储与世界状态机保持一致。</p>
 */
public class DungeonSessionService {

    private static final long OFFLINE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final HimDungeons plugin;
    private final BackupStorage backupStorage;
    /** 离线待返回记录 */
    private final Map<UUID, OfflineRecord> offlineRecords = new ConcurrentHashMap<>();
    /** 各世界无人后 5 分钟清理任务 */
    private final Map<String, BukkitTask> cleanupTasks = new ConcurrentHashMap<>();

    public static class OfflineRecord {
        public final String worldName;
        public final String dungeonName;
        public final long offlineAt;
        public final Location lastLocation;

        public OfflineRecord(String worldName, String dungeonName, long offlineAt, Location lastLocation) {
            this.worldName = worldName;
            this.dungeonName = dungeonName;
            this.offlineAt = offlineAt;
            this.lastLocation = lastLocation == null ? null : lastLocation.clone();
        }
    }

    public DungeonSessionService(HimDungeons plugin, BackupStorage backupStorage) {
        this.plugin = plugin;
        this.backupStorage = backupStorage;
    }

    public BackupStorage getBackupStorage() { return backupStorage; }

    // ==================== 进入 / 重入 ====================

    /**
     * 玩家进入地牢实例（首次生成后 / 5 分钟内重入）。
     * 若 instance.deepCloneActive，会备份并清空玩家背包。
     */
public boolean enter(Player player, HimDungeons.DungeonInstance instance) {
    if (player == null || instance == null) return false;
    UUID uuid = player.getUniqueId();
    String worldName = instance.worldName;

    // ★ BeforeEnter（可否决）
    com.him.dungeons.api.hook.DungeonContext dungeonCtx = instance.hookContext;
    if (dungeonCtx != null) {
        com.him.dungeons.api.hook.PlayerHookContext phc =
                new com.him.dungeons.api.hook.PlayerHookContext(dungeonCtx, player);
        plugin.getHookRegistry().fireBeforeEnter(phc);
        if (phc.cancelled) return false;
    }

    cancelCleanup(worldName);
    offlineRecords.remove(uuid);

    if (instance.deepCloneActive) {
        backupStorage.save(uuid, instance.dungeonName, worldName, player);
        clearInventory(player);
    }

    plugin.setPlayerDungeonWorld(uuid, worldName);
    plugin.addPlayerToDungeon(worldName, uuid);
    instance.players.add(uuid);

    Location spawn = plugin.getPlayerCheckpoint(uuid);
    if (spawn == null || spawn.getWorld() == null || !worldName.equals(spawn.getWorld().getName())) {
        spawn = instance.spawnLocation;
    }
    if (spawn != null) player.teleport(spawn);

    // ★ AfterEnter
    if (dungeonCtx != null) {
        plugin.getHookRegistry().fireAfterEnter(
                new com.him.dungeons.api.hook.PlayerHookContext(dungeonCtx, player));
    }
    return true;
}

    // ==================== 主动离开 ====================

public void leave(Player player) {
    if (player == null) return;
    UUID uuid = player.getUniqueId();
    String worldName = plugin.getPlayerDungeonWorld(uuid);
    if (worldName == null) { MessageUtil.sendError(player, "你当前不在任何地牢中"); return; }
    HimDungeons.DungeonInstance instance = plugin.getDungeonInstanceByWorld(worldName);
    if (instance == null) { plugin.setPlayerDungeonWorld(uuid, null); return; }

    // ★ BeforeLeave（可否决）
    com.him.dungeons.api.hook.DungeonContext dungeonCtx = instance.hookContext;
    if (dungeonCtx != null) {
        com.him.dungeons.api.hook.PlayerHookContext phc =
                new com.him.dungeons.api.hook.PlayerHookContext(dungeonCtx, player);
        plugin.getHookRegistry().fireBeforeLeave(phc);
        if (phc.cancelled) { MessageUtil.sendMessage(player, "离开地牢被外部否决"); return; }
    }

    restoreInventory(player, instance);
    teleportLobby(player, instance);
    cleanupMarks(uuid, worldName, instance);
    if (instance.players.isEmpty() && !instance.isFinished) startCleanup(worldName);
    MessageUtil.sendMessage(player, "你已离开地牢");

    // ★ AfterLeave
    if (dungeonCtx != null) {
        plugin.getHookRegistry().fireAfterLeave(
                new com.him.dungeons.api.hook.PlayerHookContext(dungeonCtx, player));
    }
}

    // ==================== 通关 ====================

public void finish(Player player, HimDungeons.DungeonInstance instance, ConfigApplier applier) {
    if (player == null || instance == null) return;
    UUID uuid = player.getUniqueId();
    restoreInventory(player, instance);
    applier.grantRewards(player);
    teleportLobby(player, instance);
    cleanupMarks(uuid, instance.worldName, instance);

    // ★ Finish
    com.him.dungeons.api.hook.DungeonContext dungeonCtx = instance.hookContext;
    if (dungeonCtx != null) {
        plugin.getHookRegistry().fireFinish(
                new com.him.dungeons.api.hook.PlayerHookContext(dungeonCtx, player));
    }
}

    // ==================== 离线 ====================

public void onPlayerQuit(Player player) {
    if (player == null) return;
    UUID uuid = player.getUniqueId();
    String worldName = plugin.getPlayerDungeonWorld(uuid);
    if (worldName == null) return;
    HimDungeons.DungeonInstance instance = plugin.getDungeonInstanceByWorld(worldName);
    if (instance == null) return;

    restoreInventory(player, instance);
    offlineRecords.put(uuid, new OfflineRecord(
            worldName, instance.dungeonName,
            System.currentTimeMillis(), player.getLocation()
    ));
    if (!instance.isFinished && !hasAnyOnline(instance)) startCleanup(worldName);

    // ★ Offline
    com.him.dungeons.api.hook.DungeonContext dungeonCtx = instance.hookContext;
    if (dungeonCtx != null) {
        plugin.getHookRegistry().fireOffline(
                new com.him.dungeons.api.hook.PlayerHookContext(dungeonCtx, player));
    }
}

    // ==================== 上线 ====================

public void onPlayerJoin(Player player) {
    if (player == null) return;
    UUID uuid = player.getUniqueId();

    // 1. 离线记录 —— 5 分钟内回来就重入
    OfflineRecord record = offlineRecords.remove(uuid);
    if (record != null) {
        long elapsed = System.currentTimeMillis() - record.offlineAt;
        if (elapsed < OFFLINE_TIMEOUT_MS) {
            HimDungeons.DungeonInstance instance = plugin.getDungeonInstanceByWorld(record.worldName);
            World world = Bukkit.getWorld(record.worldName);
            if (instance != null && world != null) {
                cancelCleanup(record.worldName);
                if (enter(player, instance)) {
                    // ★ Online（重入成功）
                    com.him.dungeons.api.hook.DungeonContext dungeonCtx = instance.hookContext;
                    if (dungeonCtx != null) {
                        plugin.getHookRegistry().fireOnline(
                                new com.him.dungeons.api.hook.PlayerHookContext(dungeonCtx, player));
                    }
                    return;
                }
            }
        }
        plugin.setPlayerDungeonWorld(uuid, null);
        plugin.clearPlayerCheckpoint(uuid);
    }

    // 2. 崩溃恢复兜底
    BackupStorage.BackupRecord pending = backupStorage.findForPlayer(uuid);
    if (pending != null) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (backupStorage.restore(uuid, pending.dungeonName, player)) {
                player.updateInventory();
                plugin.getLogger().info("已为玩家 " + player.getName()
                        + " 恢复崩溃前备份（地牢: " + pending.dungeonName + "）");
            }
        }, 1L);
    }
}

    // ==================== 启动扫描 ====================

    public void restorePendingOnEnable() {
        int marked = 0;
        for (BackupStorage.BackupRecord rec : backupStorage.findAll()) {
            // 崩溃时残留的 ACTIVE 一律视为待恢复（玩家可能在地牢中，存档空背包）
            if (rec.status == BackupStorage.Status.ACTIVE) {
                backupStorage.updateStatus(rec.uuid, rec.dungeonName, BackupStorage.Status.PENDING_RESTORE);
                marked++;
            }
        }
        if (marked > 0) {
            plugin.getLogger().info("启动扫描：标记 " + marked + " 条崩溃残留备份为待恢复");
        }

        // 若有在线玩家（少见但保险），立即恢复
        for (BackupStorage.BackupRecord rec : backupStorage.findAll()) {
            Player player = Bukkit.getPlayer(rec.uuid);
            if (player != null && player.isOnline()) {
                if (backupStorage.restore(rec.uuid, rec.dungeonName, player)) {
                    player.updateInventory();
                }
            }
        }
    }

    // ==================== 辅助 ====================

    public void clearOfflineRecordsForWorld(String worldName) {
        offlineRecords.entrySet().removeIf(e -> e.getValue().worldName.equals(worldName));
    }

    public boolean isWaitingForReconnect(UUID uuid) {
        return offlineRecords.containsKey(uuid);
    }

    private boolean hasAnyOnline(HimDungeons.DungeonInstance instance) {
        for (UUID u : instance.players) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) return true;
        }
        return false;
    }

    private void restoreInventory(Player player, HimDungeons.DungeonInstance instance) {
        if (!instance.deepCloneActive) return;
        backupStorage.restore(player.getUniqueId(), instance.dungeonName, player);
        player.updateInventory();
    }

    private void clearInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setExtraContents(new ItemStack[0]);
        player.updateInventory();
    }

    private void teleportLobby(Player player, HimDungeons.DungeonInstance instance) {
        ConfigApplier applier = plugin.getConfigApplier(instance.worldName);
        Location lobby = null;
        if (applier != null) lobby = applier.getLobbyLocation(Bukkit.getWorlds().get(0));
        if (lobby == null) lobby = Bukkit.getWorlds().get(0).getSpawnLocation();
        player.teleport(lobby);
    }

    private void cleanupMarks(UUID uuid, String worldName, HimDungeons.DungeonInstance instance) {
        plugin.setPlayerDungeonWorld(uuid, null);
        plugin.clearPlayerCheckpoint(uuid);
        instance.players.remove(uuid);
        Set<UUID> dp = plugin.getDungeonPlayers(worldName);
        if (dp != null) dp.remove(uuid);
    }

    public void startCleanup(String worldName) {
        cancelCleanup(worldName);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            cleanupTasks.remove(worldName);
            HimDungeons.DungeonInstance instance = plugin.getDungeonInstanceByWorld(worldName);
            if (instance == null) return;
            if (hasAnyOnline(instance)) return;   // 有人回来了
            plugin.forceFinishDungeon(instance);
        }, 20L * 60 * 5);
        cleanupTasks.put(worldName, task);
    }

    public void cancelCleanup(String worldName) {
        BukkitTask task = cleanupTasks.remove(worldName);
        if (task != null) task.cancel();
    }
}