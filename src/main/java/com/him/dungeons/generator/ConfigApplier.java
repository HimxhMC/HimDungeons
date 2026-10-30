package com.him.dungeons.generator;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.*;
import com.him.dungeons.schematic.*;
import org.bukkit.configuration.file.FileConfiguration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import java.lang.reflect.Method;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class ConfigApplier {

    private final HimDungeons plugin;
    private final String dungeonName;
    private final Player player;
    private final FileConfiguration config;
    private final Random random = new Random();

    private final Map<String, Integer> usedRoomTemplates = new HashMap<>();
    private final Set<String> usedDoorPairs = new HashSet<>();
    private final Map<RoomInstance, List<Pos3>> forbiddenBlockPositions = new HashMap<>();
    private Map<String, String> selectedBoss = null;

    public ConfigApplier(HimDungeons plugin, String dungeonName, Player player, FileConfiguration config) {
        this.plugin = plugin;
        this.dungeonName = dungeonName;
        this.player = player;
        this.config = config;
    }

    public void prepare() {
        usedRoomTemplates.clear();
        usedDoorPairs.clear();
        forbiddenBlockPositions.clear();
        selectedBoss = null;
        DebugUtil.debug(player, "ConfigApplier 已重置");
    }

    public Map<String, Integer> getCustomWeights() {
        List<List<Object>> custom = ConfigUtil.getInstanceGeneratorChoose(config);
        if (custom == null || custom.isEmpty()) return null;
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (List<Object> entry : custom) {
            if (entry.size() >= 2) {
                String type = entry.get(0).toString();
                int weight = ((Number) entry.get(1)).intValue();
                if (weight > 0) weights.put(type, weight);
            }
        }
        return weights.isEmpty() ? null : weights;
    }

    public List<String> filterRoomTemplates(String type, List<String> baseNames) {
        if (baseNames == null || baseNames.isEmpty()) return baseNames;
        boolean reuse = ConfigUtil.isInstanceAllowRoomReuse(config);
        if (!reuse) {
            baseNames = baseNames.stream()
                    .filter(name -> usedRoomTemplates.getOrDefault(name, 0) == 0)
                    .collect(Collectors.toList());
        }
        return baseNames;
    }

    public void recordRoomUsed(String baseName) {
        usedRoomTemplates.put(baseName, usedRoomTemplates.getOrDefault(baseName, 0) + 1);
        DebugUtil.debug(player, "记录房间模板使用: %s (已用 %d 次)", baseName, usedRoomTemplates.get(baseName));
    }

    public boolean canPairDoors(String roomAId, int idxA, String roomBId, int idxB) {
        boolean reuse = ConfigUtil.isInstanceAllowDoorReuse(config);
        String pairKey = roomAId + ":" + idxA + "|" + roomBId + ":" + idxB;
        String reverseKey = roomBId + ":" + idxB + "|" + roomAId + ":" + idxA;
        if (!reuse) {
            if (usedDoorPairs.contains(pairKey) || usedDoorPairs.contains(reverseKey)) {
                return false;
            }
        }
        usedDoorPairs.add(pairKey);
        usedDoorPairs.add(reverseKey);
        return true;
    }

    public Map<String, String> selectBoss() {
        if (selectedBoss != null) return selectedBoss;
        List<String> types = ConfigUtil.getInstanceBossTypeList(config);
        List<String> nbts = ConfigUtil.getInstanceBossNbtList(config);
        int size = Math.min(types.size(), nbts.size());
        if (size == 0) {
            selectedBoss = Map.of("type", "zombie", "nbt", "{Health:100.0f}");
        } else {
            int idx = random.nextInt(size);
            selectedBoss = Map.of("type", types.get(idx), "nbt", nbts.get(idx));
        }
        DebugUtil.debug(player, "Boss 选定: %s", selectedBoss);
        return selectedBoss;
    }

    public void storeForbiddenBlocks(RoomInstance room) {
        List<String> blockNames = ConfigUtil.getInstanceBlockRoomGenerateBlocks(config);
        if (blockNames.isEmpty()) return;
        File schematicFile = ResourceUtil.getRoomSchematicFlat(dungeonName, room.baseName);
        if (schematicFile == null) return;

        NbtSchematic schem = SpongeSchematicReader.read(
                schematicFile, ConfigUtil.getSchematicReadFormat());
        if (schem == null) return;

        List<Pos3> coords = new ArrayList<>();
        for (int y = 0; y < schem.height; y++) {
            for (int z = 0; z < schem.length; z++) {
                for (int x = 0; x < schem.width; x++) {
                    org.bukkit.block.data.BlockData data = schem.getBlockData(x, y, z);
                    if (data == null) continue;
                    if (blockNames.contains(data.getMaterial().name())) {
                        coords.add(new Pos3(x, y, z));
                    }
                }
            }
        }
        if (!coords.isEmpty()) {
            forbiddenBlockPositions.put(room, coords);
            DebugUtil.debug(player, "房间 %s 含 %d 个阻止方块", room.roomType, coords.size());
        }
    }

    public boolean checkForbiddenBlocks(Location candidateAnchor, int rotation, Pos3 candidateSize) {
        if (forbiddenBlockPositions.isEmpty()) return true;
        AABBUtil.AABB candidateAABB = AABBUtil.buildRotated(candidateAnchor, candidateSize, rotation);
        for (Map.Entry<RoomInstance, List<Pos3>> entry : forbiddenBlockPositions.entrySet()) {
            RoomInstance placed = entry.getKey();
            List<Pos3> coords = entry.getValue();
            for (Pos3 rel : coords) {
                Pos3 rotatedRel = TransformUtil.rotatePos(rel, placed.rotationTimes);
                Location worldLoc = placed.anchor.clone().add(rotatedRel.getX(), rotatedRel.getY(), rotatedRel.getZ());
                double x = worldLoc.getX(), y = worldLoc.getY(), z = worldLoc.getZ();
                if (candidateAABB.minX <= x && x <= candidateAABB.maxX &&
                        candidateAABB.minY <= y && y <= candidateAABB.maxY &&
                        candidateAABB.minZ <= z && z <= candidateAABB.maxZ) {
                    DebugUtil.debug(player, "候选位置与阻止方块冲突: %s", worldLoc);
                    return false;
                }
            }
        }
        return true;
    }

    public ItemStack[] deepCloneInventory(Player player) {
        if (!ConfigUtil.isInstanceDeepCloneInventory(config)) {
            return player.getInventory().getContents();
        }
        PlayerInventory inv = player.getInventory();
        ItemStack[] original = inv.getContents();
        ItemStack[] clone = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) {
                clone[i] = new ItemStack(original[i]);
            }
        }
        return clone;
    }

    public Location getLobbyLocation(World world) {
        String lobbyStr = ConfigUtil.getInstanceLobby(config);
        if (lobbyStr == null || lobbyStr.isEmpty()) return null;
        try {
            String[] parts = lobbyStr.split(",");
            if (parts.length >= 3) {
                double x = Double.parseDouble(parts[0]);
                double y = Double.parseDouble(parts[1]);
                double z = Double.parseDouble(parts[2]);
                float yaw = 0, pitch = 0;
                if (parts.length >= 5) {
                    yaw = Float.parseFloat(parts[3]);
                    pitch = Float.parseFloat(parts[4]);
                }
                World targetWorld = Bukkit.getWorld(parts.length >= 6 ? parts[5] : world.getName());
                if (targetWorld == null) targetWorld = world;
                return new Location(targetWorld, x, y, z, yaw, pitch);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean isCommandAllowed(String fullCommand) {
        String listType = ConfigUtil.getInstanceCommandsAllowListType(config);
        List<String> list = ConfigUtil.getInstanceCommandsAllow(config);
        String cmd = fullCommand.startsWith("/") ? fullCommand.substring(1) : fullCommand;
        if (list.isEmpty()) {
            return listType.equalsIgnoreCase("black");
        }
        boolean contains = list.contains(cmd);
        return listType.equalsIgnoreCase("white") ? contains : !contains;
    }

    public boolean isBuildAllowed(Material material) {
        String listType = ConfigUtil.getInstanceBuildSettingsListType(config);
        List<String> list = ConfigUtil.getInstanceBuildSettings(config);
        return listType.equalsIgnoreCase("white") ? list.contains(material.name()) : !list.contains(material.name());
    }

    public boolean isBreakAllowed(Material material) {
        String listType = ConfigUtil.getInstanceBreakSettingsListType(config);
        List<String> list = ConfigUtil.getInstanceBreakSettings(config);
        return listType.equalsIgnoreCase("white") ? list.contains(material.name()) : !list.contains(material.name());
    }

public void grantRewards(Player player, HimDungeons.DungeonInstance instance) {
    boolean firstClear = WinStorage.getWins(dungeonName, player.getUniqueId()) == 0;
    int playerCount = instance == null ? 1 : Math.max(1, instance.players.size());
    String shareMode = ConfigUtil.getInstanceShareMode(config);
    double shareFactor = "split".equals(shareMode) ? 1.0 / playerCount : 1.0;
    double multiplier = getPermissionMultiplier(player);

    // ===== Vault 基础奖励 =====
    if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
        try {
            var reg = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (reg != null) {
                net.milkbowl.vault.economy.Economy eco = reg.getProvider();
                Map<String, Double> rewards = ConfigUtil.getInstanceVaultRewards(config);
                for (Map.Entry<String, Double> e : rewards.entrySet()) {
                    if (e.getKey().equalsIgnoreCase("money")) {
                        double amt = e.getValue() * shareFactor * multiplier;
                        eco.depositPlayer(player, amt);
                        MessageUtil.sendMessage(player, "reward.vault", "amount", amt);
                    }
                }
                // 首通额外
                if (firstClear) {
                    Map<String, Double> fc = ConfigUtil.getInstanceFirstClearVault(config);
                    for (Map.Entry<String, Double> e : fc.entrySet()) {
                        if (e.getKey().equalsIgnoreCase("money")) {
                            double amt = e.getValue() * multiplier;
                            eco.depositPlayer(player, amt);
                            MessageUtil.sendMessage(player, "reward.vault", "amount", amt);
                        }
                    }
                }
            }
        } catch (Exception e) {
            DebugUtil.debug(player, "Vault 奖励失败: " + e.getMessage());
        }
    }

    // ===== PlayerPoints =====
    if (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
        try {
            Object plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            Object api = plugin.getClass().getMethod("getAPI").invoke(plugin);
            if (api != null) {
                Map<String, Integer> pp = ConfigUtil.getInstancePlayerPointsRewards(config);
                for (Map.Entry<String, Integer> e : pp.entrySet()) {
                    if (e.getKey().equalsIgnoreCase("points") || e.getKey().equalsIgnoreCase("default")) {
                        int amt = (int) Math.floor(e.getValue() * shareFactor * multiplier);
                        if (givePoints(api, player, amt))
                            MessageUtil.sendMessage(player, "reward.player-points", "amount", amt);
                    }
                }
                if (firstClear) {
                    Map<String, Integer> fc = ConfigUtil.getInstanceFirstClearPoints(config);
                    for (Map.Entry<String, Integer> e : fc.entrySet()) {
                        if (e.getKey().equalsIgnoreCase("points") || e.getKey().equalsIgnoreCase("default")) {
                            int amt = (int) Math.floor(e.getValue() * multiplier);
                            if (givePoints(api, player, amt))
                                MessageUtil.sendMessage(player, "reward.player-points", "amount", amt);
                        }
                    }
                }
            }
        } catch (Exception e) {
            DebugUtil.debug(player, "PlayerPoints 奖励失败: " + e.getMessage());
        }
    }

    // ===== 命令 =====
    for (String cmd : ConfigUtil.getInstanceConsoleCommands(config)) {
        try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", player.getName())); }
        catch (Exception e) { DebugUtil.debug(player, "命令失败: " + e.getMessage()); }
    }
    if (firstClear) {
        for (String cmd : ConfigUtil.getInstanceFirstClearCommands(config)) {
            try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", player.getName())); }
            catch (Exception ignored) {}
        }
    }

    // ===== 记录 =====
    WinStorage.incrementWin(dungeonName, player.getUniqueId(), player.getName());
    if (ConfigUtil.isInstanceTrackFastestTime(config) && instance != null && instance.startTime > 0) {
        long elapsed = System.currentTimeMillis() - instance.startTime;
        WinStorage.recordFastestTime(dungeonName, player.getUniqueId(), elapsed);
    }

    MessageUtil.sendMessage(player, "reward.finished");
}

/** 权限倍率：遍历 rewards.permissionMultiplier，取玩家拥有的最高倍率 */
private double getPermissionMultiplier(Player player) {
    double best = 1.0;
    for (Map.Entry<String, Double> e : ConfigUtil.getInstancePermissionMultiplier(config).entrySet()) {
        if (player.hasPermission(e.getKey())) best = Math.max(best, e.getValue());
    }
    return best;
}

/** 失败惩罚（超时 / 强制结束） */
public void applyFailurePenalty(FileConfiguration config, HimDungeons.DungeonInstance instance) {
    if (instance == null) return;
    Map<String, Double> vault = ConfigUtil.getInstanceFailureVault(config);
    Map<String, Integer> points = ConfigUtil.getInstanceFailurePoints(config);
    List<String> cmds = ConfigUtil.getInstanceFailureCommands(config);
    if (vault.isEmpty() && points.isEmpty() && cmds.isEmpty()) return;

    for (UUID uuid : instance.players) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) continue;

        if (!vault.isEmpty() && Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try {
                var reg = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
                if (reg != null) {
                    double total = vault.values().stream().mapToDouble(Double::doubleValue).sum();
                    if (total > 0) reg.getProvider().withdrawPlayer(p, total);
                }
            } catch (Exception ignored) {}
        }
        if (!points.isEmpty() && Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
            try {
                Object plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
                Object api = plugin.getClass().getMethod("getAPI").invoke(plugin);
                if (api != null) {
                    int total = points.values().stream().mapToInt(Integer::intValue).sum();
                    takePoints(api, p, total);
                }
            } catch (Exception ignored) {}
        }
        for (String cmd : cmds) {
            try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName())); }
            catch (Exception ignored) {}
        }
    }
}

/** PlayerPoints 帮助方法（String 优先，UUID 回退） */
private static boolean givePoints(Object api, Player p, int amount) {
    if (amount <= 0) return false;
    try {
        api.getClass().getMethod("give", String.class, int.class).invoke(api, p.getName(), amount);
        return true;
    } catch (NoSuchMethodException e) {
        try {
            api.getClass().getMethod("give", UUID.class, int.class).invoke(api, p.getUniqueId(), amount);
            return true;
        } catch (Exception ex) { return false; }
    } catch (Exception e) { return false; }
}

private static boolean takePoints(Object api, Player p, int amount) {
    if (amount <= 0) return true;
    try {
        Object r = api.getClass().getMethod("take", String.class, int.class).invoke(api, p.getName(), amount);
        return !(r instanceof Boolean) || (Boolean) r;
    } catch (NoSuchMethodException e) {
        try {
            Object r = api.getClass().getMethod("take", UUID.class, int.class).invoke(api, p.getUniqueId(), amount);
            return !(r instanceof Boolean) || (Boolean) r;
        } catch (Exception ex) { return false; }
    } catch (Exception e) { return false; }
}

    public boolean isRoomReuseAllowed() {
        return ConfigUtil.isInstanceAllowRoomReuse(config);
    }

    public boolean isSlimefunAllowed() {
        return ConfigUtil.isInstanceAllowSlimefun(config);
    }

    public boolean isMythicMobsAllowed() {
        return ConfigUtil.isInstanceUseMythicMobs(config);
    }

    public boolean isInteractionAllowed(Material material) {
        if (material == null) return true;
        String listType = ConfigUtil.getInstanceInteractionSettingsListType(config);
        List<String> list = ConfigUtil.getInstanceInteractionSettings(config);
        boolean contains = list.contains(material.name());
        return listType.equalsIgnoreCase("white") ? contains : !contains;
    }

    public ConfigUtil.ExplosionMode getTntExplosionMode() {
        return ConfigUtil.getInstanceTntExplosion(config);
    }

    public ConfigUtil.ExplosionMode getEntityExplosionMode() {
        return ConfigUtil.getInstanceEntityExplosion(config);
    }

    public ConfigUtil.ExplosionMode getBlockExplosionMode() {
        return ConfigUtil.getInstanceBlockExplosion(config);
    }
}