package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.stream.Collectors;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 配置管理工具类（封装所有配置键，调用方无需关心路径字符串）
 */
public final class ConfigUtil {

    private static final String GLOBAL_CONFIG_NAME = "config.yml";
    private static final String DUNGEON_CONFIG_NAME = "config.yml";

    // ======================== 全局配置键 ========================
    private static final String GLOBAL_LANG = "lang";
    private static final String GLOBAL_MSG_PREFIX = "msgPrefix";
    private static final String GLOBAL_GENERATE_COOLDOWN = "generateCooldown";
    private static final String GLOBAL_SCAN_AND_REGISTER = "scanAndRegisterNewRooms";
    /** 是否按玩家客户端 locale 自动选择语言文件 */
    private static final String GLOBAL_LANGUAGE_USE_CLIENT_LOCALE = "language.useClientLocale";

    // ======================== 实例配置根节点 ========================
    private static final String INST_ROOT = "dungeonInstance";

    private static final String INST_ENABLE = "enable";
    private static final String INST_DISPLAY_NAME = "displayName";
    private static final String INST_FILE_NAME = "fileName";
    private static final String INST_MSG_PREFIX = "msgPrefix";

    private static final String INST_MIN_ROOM_AMOUNT = "minRoomAmount";
    private static final String INST_MAX_ROOM_AMOUNT = "maxRoomAmount";
    private static final String INST_DFS_MAX_DEEP = "dfsMaxDeep";
    private static final String INST_USE_BFS_CHECK = "useBfsCheck";
    private static final String INST_USE_AABB = "useAabb";
    private static final String INST_STRUCTURE_CHOOSE = "structureChoose";
    private static final String INST_DISCONNECTED_ROOM_DISTANCE = "disconnectedRoomDistance";
    private static final String INST_ALLOW_ROOM_REUSE = "allowRoomReuse";
    private static final String INST_ALLOW_DOOR_REUSE = "allowDoorReuse";
    private static final String INST_ALLOW_FALLBACK_DOOR_REUSE = "allowFallbackDoorReuse";
    private static final String INST_START_TO_BOSS_ROOM_AMOUNT = "startToBossRoomAmmount";
    private static final String INST_BLOCK_ROOM_GENERATE_BLOCKS = "blockRoomGenerateBlocks";
    private static final String INST_GENERATOR_CHOOSE = "generatorChoose";

    private static final String INST_ALLOW_PARTIES = "allowParties";
    private static final String INST_ALLOW_SLIMEFUN = "allowSlimefun";
    private static final String INST_BOSS_TYPE = "bossType";
    private static final String INST_BOSS_NBT = "bossNbt";
    private static final String INST_KEEP_INVENTORY = "keepInventory";
    private static final String INST_DEEP_CLONE_INVENTORY = "deepCloneInventory";
    private static final String INST_TIME = "time";
    private static final String INST_DUNGEON_TIME = "dungeonTime";
    private static final String INST_ADVANCE_TIME = "advanceTime";
    private static final String INST_WEATHER = "weather";
    private static final String INST_DIFFICULTY = "difficulty";
    private static final String INST_ALLOW_MOBS = "allowMobs";
    private static final String INST_USE_MYTHIC_MOBS = "useMythicMobs";
    private static final String INST_TNT_EXPLOSION = "tntExplosion";
    private static final String INST_ENTITY_EXPLOSION = "entityExplosion";
    private static final String INST_BLOCK_EXPLOSION = "blockExplosion";

    private static final String INST_COMMANDS_ALLOW_LIST_TYPE = "commandsAllowListType";
    private static final String INST_COMMANDS_ALLOW = "commandsAllow";
    private static final String INST_BUILD_SETTINGS_LIST_TYPE = "buildSettingsListType";
    private static final String INST_BUILD_SETTINGS = "buildSettings";
    private static final String INST_BREAK_SETTINGS_LIST_TYPE = "breakSettingsListType";
    private static final String INST_BREAK_SETTINGS = "breakSettings";

    private static final String INST_LOBBY = "lobby";
    private static final String INST_REWARDS = "rewards";
    private static final String INST_REWARDS_VAULT = "vault";
    private static final String INST_REWARDS_PLAYER_POINTS = "playerPoints";
    private static final String INST_REWARDS_CONSOLE_COMMANDS = "consoleCommands";

    private static final String INST_ENTER_COST = "enterCost";
    private static final String INST_INTERACTION_SETTINGS_LIST_TYPE = "interactionSettingsListType";
    private static final String INST_INTERACTION_SETTINGS = "interactionSettings";
    private static final String INST_USE_MYTHIC_MOB_FOR_BOSS = "useMythicMobForBoss";
    private static final String INST_MYTHIC_MOB_BOSS_NAME = "mythicMobBossName";

    public enum ExplosionMode {
        ALLOW, DAMAGE_ONLY, DENY
    }

    // ========================= 全局：language =========================

    public static boolean isUseClientLocale() {
        return getGlobalConfig().getBoolean(GLOBAL_LANGUAGE_USE_CLIENT_LOCALE, false);
    }

    public static boolean getGlobalScanAndRegister() {
        return getGlobalConfig().getBoolean(GLOBAL_SCAN_AND_REGISTER, true);
    }

    public static boolean setGlobalScanAndRegister(boolean v) {
        FileConfiguration c = getGlobalConfig();
        c.set(GLOBAL_SCAN_AND_REGISTER, v);
        return saveGlobalConfig(c);
    }

    public static String getInstanceInteractionSettingsListType(FileConfiguration c) {
        ConfigurationSection s = getInstanceSection(c);
        return s == null ? "white" : s.getString("interactionSettingsListType", "white");
    }

    public static List<String> getInstanceInteractionSettings(FileConfiguration c) {
        ConfigurationSection s = getInstanceSection(c);
        return s == null ? new ArrayList<>() : s.getStringList("interactionSettings");
    }

    public static boolean isInstanceUseMythicMobForBoss(FileConfiguration c) {
        ConfigurationSection s = getInstanceSection(c);
        return s != null && s.getBoolean("useMythicMobForBoss", false);
    }

    public static String getInstanceMythicMobBossName(FileConfiguration c) {
        ConfigurationSection s = getInstanceSection(c);
        return s == null ? "" : s.getString("mythicMobBossName", "");
    }

    private static ConfigurationSection getEnterCostSection(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return null;
        return sec.getConfigurationSection(INST_ENTER_COST);
    }

    public static Map<String, Double> getInstanceEnterVaultCost(FileConfiguration config) {
        Map<String, Double> costs = new LinkedHashMap<>();
        ConfigurationSection costSec = getEnterCostSection(config);
        if (costSec == null) return costs;
        ConfigurationSection vaultSec = costSec.getConfigurationSection(INST_REWARDS_VAULT);
        if (vaultSec == null) return costs;
        for (String key : vaultSec.getKeys(false)) {
            costs.put(key, vaultSec.getDouble(key, 0.0));
        }
        return costs;
    }

    public static Map<String, Integer> getInstanceEnterPlayerPointsCost(FileConfiguration config) {
        Map<String, Integer> costs = new LinkedHashMap<>();
        ConfigurationSection costSec = getEnterCostSection(config);
        if (costSec == null) return costs;
        ConfigurationSection ppSec = costSec.getConfigurationSection(INST_REWARDS_PLAYER_POINTS);
        if (ppSec == null) return costs;
        for (String key : ppSec.getKeys(false)) {
            costs.put(key, ppSec.getInt(key, 0));
        }
        return costs;
    }

    public static List<String> getInstanceEnterConsoleCommands(FileConfiguration config) {
        ConfigurationSection costSec = getEnterCostSection(config);
        if (costSec == null) return new ArrayList<>();
        return costSec.getStringList(INST_REWARDS_CONSOLE_COMMANDS);
    }

    private ConfigUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========================= 底层加载器 =========================

    public static FileConfiguration getGlobalConfig() {
        File configFile = new File(HimDungeons.getInstance().getDataFolder(), GLOBAL_CONFIG_NAME);
        if (!configFile.exists()) {
            HimDungeons.getInstance().saveDefaultConfig();
        }
        return YamlConfiguration.loadConfiguration(configFile);
    }

    public static boolean saveGlobalConfig(FileConfiguration config) {
        File configFile = new File(HimDungeons.getInstance().getDataFolder(), GLOBAL_CONFIG_NAME);
        try {
            config.save(configFile);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static FileConfiguration getDungeonConfig(String dungeonName) {
        if (dungeonName == null || dungeonName.trim().isEmpty()) return null;
        File dungeonFolder = new File(HimDungeons.getInstance().getDataFolder(), dungeonName);
        if (!dungeonFolder.exists() || !dungeonFolder.isDirectory()) return null;
        File configFile = new File(dungeonFolder, DUNGEON_CONFIG_NAME);
        if (!configFile.exists()) return null;
        return YamlConfiguration.loadConfiguration(configFile);
    }

    public static boolean saveDungeonConfig(String dungeonName, FileConfiguration config) {
        File dungeonFolder = new File(HimDungeons.getInstance().getDataFolder(), dungeonName);
        if (!dungeonFolder.exists() && !dungeonFolder.mkdirs()) return false;
        File configFile = new File(dungeonFolder, DUNGEON_CONFIG_NAME);
        try {
            config.save(configFile);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static ConfigurationSection getInstanceSection(FileConfiguration config) {
        if (config == null) return null;
        return config.getConfigurationSection(INST_ROOT);
    }

    // ========================= 全局配置 Getter =========================

    public static String getGlobalLang() {
        return getGlobalConfig().getString(GLOBAL_LANG, "en_US");
    }

    public static String getGlobalMsgPrefix() {
        return getGlobalConfig().getString(GLOBAL_MSG_PREFIX, "&a[HimDungeons] &r");
    }

    public static int getGlobalGenerateCooldown() {
        return getGlobalConfig().getInt(GLOBAL_GENERATE_COOLDOWN, 0);
    }

    // ========================= 实例配置 Getter =========================

    public static boolean isInstanceEnabled(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null || sec.getBoolean(INST_ENABLE, true);
    }

    public static String getInstanceDisplayName(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? "地牢" : sec.getString(INST_DISPLAY_NAME, "地牢");
    }

    public static String getInstanceFileName(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? "dungeon_01" : sec.getString(INST_FILE_NAME, "dungeon_01");
    }

    public static String getInstanceMsgPrefix(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return null;
        String prefix = sec.getString(INST_MSG_PREFIX);
        return (prefix == null || prefix.isEmpty()) ? null : prefix;
    }

    public static int getInstanceMinRoomAmount(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? 5 : sec.getInt(INST_MIN_ROOM_AMOUNT, 5);
    }

    public static int getInstanceMaxRoomAmount(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? 12 : sec.getInt(INST_MAX_ROOM_AMOUNT, 12);
    }

    public static int getInstanceDfsMaxDeep(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? 8 : sec.getInt(INST_DFS_MAX_DEEP, 8);
    }

    public static boolean isInstanceBfsCheckEnabled(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean(INST_USE_BFS_CHECK, false);
    }

    public static boolean isInstanceAabbEnabled(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null || sec.getBoolean(INST_USE_AABB, true);
    }

    public static Map<String, Integer> getInstanceStructureWeights(FileConfiguration config) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return weights;
        ConfigurationSection weightSec = sec.getConfigurationSection(INST_STRUCTURE_CHOOSE);
        if (weightSec == null) return weights;
        for (String key : weightSec.getKeys(false)) {
            weights.put(key, weightSec.getInt(key, 0));
        }
        return weights;
    }

    public static int getInstanceStructureWeight(FileConfiguration config, String roomType, int def) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return def;
        ConfigurationSection weightSec = sec.getConfigurationSection(INST_STRUCTURE_CHOOSE);
        if (weightSec == null) return def;
        return weightSec.getInt(roomType, def);
    }

    public static int getInstanceDisconnectedRoomDistance(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return 0;
        return sec.getInt(INST_DISCONNECTED_ROOM_DISTANCE, 0);
    }

    public static int[] getInstanceStartToBossRoomAmount(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return new int[]{10, 10};
        String value = sec.getString(INST_START_TO_BOSS_ROOM_AMOUNT, "10 10");
        String[] parts = value.split("\\s+");
        try {
            if (parts.length >= 2) {
                return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
            } else if (parts.length == 1) {
                int v = Integer.parseInt(parts[0]);
                return new int[]{v, v};
            }
        } catch (NumberFormatException ignored) {}
        return new int[]{10, 10};
    }

    public static boolean isInstanceAllowRoomReuse(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean(INST_ALLOW_ROOM_REUSE, false);
    }

    public static boolean isInstanceAllowDoorReuse(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean(INST_ALLOW_DOOR_REUSE, false);
    }

    public static boolean isInstanceAllowFallbackDoorReuse(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean(INST_ALLOW_FALLBACK_DOOR_REUSE, false);
    }

    public static List<String> getInstanceBlockRoomGenerateBlocks(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return new ArrayList<>();
        return sec.getStringList(INST_BLOCK_ROOM_GENERATE_BLOCKS);
    }

    public static List<List<Object>> getInstanceGeneratorChoose(FileConfiguration config) {
        List<List<Object>> result = new ArrayList<>();
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return result;
        List<?> raw = sec.getList(INST_GENERATOR_CHOOSE);
        if (raw == null) return result;
        for (Object item : raw) {
            if (item instanceof List<?>) {
                result.add((List<Object>) item);
            }
        }
        return result;
    }

    public static boolean isInstanceAllowParties(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean(INST_ALLOW_PARTIES, false);
    }

    public static boolean isInstanceAllowSlimefun(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean(INST_ALLOW_SLIMEFUN, false);
    }

    public static List<String> getInstanceBossTypeList(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return Collections.singletonList("zombie");
        List<String> list = sec.getStringList(INST_BOSS_TYPE);
        return list.isEmpty() ? Collections.singletonList("zombie") : list;
    }

    public static List<String> getInstanceBossNbtList(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return Collections.singletonList("{Health:100.0f}");
        List<String> list = sec.getStringList(INST_BOSS_NBT);
        return list.isEmpty() ? Collections.singletonList("{Health:100.0f}") : list;
    }

    public static boolean isInstanceKeepInventory(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean(INST_KEEP_INVENTORY, false);
    }

    public static boolean isInstanceDeepCloneInventory(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean(INST_DEEP_CLONE_INVENTORY, false);
    }

    public static int getInstanceTime(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? 0 : sec.getInt(INST_TIME, 0);
    }

    public static long getInstanceDungeonTime(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? 1200 : sec.getLong(INST_DUNGEON_TIME, 1200);
    }

    public static boolean isInstanceAdvanceTime(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean(INST_ADVANCE_TIME, false);
    }

    public static String getInstanceWeather(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? "clear" : sec.getString(INST_WEATHER, "clear");
    }

    public static String getInstanceDifficulty(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? "normal" : sec.getString(INST_DIFFICULTY, "normal");
    }

    public static List<String> getInstanceAllowMobs(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return Collections.singletonList("all");
        List<String> list = sec.getStringList(INST_ALLOW_MOBS);
        return list.isEmpty() ? Collections.singletonList("all") : list;
    }

    public static boolean isInstanceUseMythicMobs(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean(INST_USE_MYTHIC_MOBS, false);
    }

    public static ExplosionMode getInstanceTntExplosion(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return parseExplosionMode(sec == null ? null : sec.getString(INST_TNT_EXPLOSION, "deny"));
    }

    public static ExplosionMode getInstanceEntityExplosion(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return parseExplosionMode(sec == null ? null : sec.getString(INST_ENTITY_EXPLOSION, "deny"));
    }

    private static ExplosionMode parseExplosionMode(String raw) {
        if (raw == null) return ExplosionMode.DENY;
        return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
            case "allow" -> ExplosionMode.ALLOW;
            case "damage_only", "damage", "damage-only" -> ExplosionMode.DAMAGE_ONLY;
            default -> ExplosionMode.DENY;
        };
    }

    public static ExplosionMode getInstanceBlockExplosion(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return parseExplosionMode(sec == null ? null : sec.getString(INST_BLOCK_EXPLOSION, "deny"));
    }

    public static String getInstanceCommandsAllowListType(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? "white" : sec.getString(INST_COMMANDS_ALLOW_LIST_TYPE, "white");
    }

    public static List<String> getInstanceCommandsAllow(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return new ArrayList<>();
        return sec.getStringList(INST_COMMANDS_ALLOW);
    }

    public static String getInstanceBuildSettingsListType(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? "white" : sec.getString(INST_BUILD_SETTINGS_LIST_TYPE, "white");
    }

    public static List<String> getInstanceBuildSettings(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return new ArrayList<>();
        return sec.getStringList(INST_BUILD_SETTINGS);
    }

    public static String getInstanceBreakSettingsListType(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? "white" : sec.getString(INST_BREAK_SETTINGS_LIST_TYPE, "white");
    }

    public static List<String> getInstanceBreakSettings(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return new ArrayList<>();
        return sec.getStringList(INST_BREAK_SETTINGS);
    }

    public static String getInstanceLobby(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? null : sec.getString(INST_LOBBY);
    }

    private static ConfigurationSection getRewardsSection(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return null;
        return sec.getConfigurationSection(INST_REWARDS);
    }

    public static Map<String, Double> getInstanceVaultRewards(FileConfiguration config) {
        Map<String, Double> rewards = new LinkedHashMap<>();
        ConfigurationSection rewardSec = getRewardsSection(config);
        if (rewardSec == null) return rewards;
        ConfigurationSection vaultSec = rewardSec.getConfigurationSection(INST_REWARDS_VAULT);
        if (vaultSec == null) return rewards;
        for (String key : vaultSec.getKeys(false)) {
            rewards.put(key, vaultSec.getDouble(key, 0.0));
        }
        return rewards;
    }

    public static double getInstanceVaultReward(FileConfiguration config, String type, double def) {
        ConfigurationSection rewardSec = getRewardsSection(config);
        if (rewardSec == null) return def;
        ConfigurationSection vaultSec = rewardSec.getConfigurationSection(INST_REWARDS_VAULT);
        if (vaultSec == null) return def;
        return vaultSec.getDouble(type, def);
    }

    public static Map<String, Integer> getInstancePlayerPointsRewards(FileConfiguration config) {
        Map<String, Integer> rewards = new LinkedHashMap<>();
        ConfigurationSection rewardSec = getRewardsSection(config);
        if (rewardSec == null) return rewards;
        ConfigurationSection ppSec = rewardSec.getConfigurationSection(INST_REWARDS_PLAYER_POINTS);
        if (ppSec == null) return rewards;
        for (String key : ppSec.getKeys(false)) {
            rewards.put(key, ppSec.getInt(key, 0));
        }
        return rewards;
    }

    public static int getInstancePlayerPointsReward(FileConfiguration config, String type, int def) {
        ConfigurationSection rewardSec = getRewardsSection(config);
        if (rewardSec == null) return def;
        ConfigurationSection ppSec = rewardSec.getConfigurationSection(INST_REWARDS_PLAYER_POINTS);
        if (ppSec == null) return def;
        return ppSec.getInt(type, def);
    }

    public static List<String> getInstanceConsoleCommands(FileConfiguration config) {
        ConfigurationSection rewardSec = getRewardsSection(config);
        if (rewardSec == null) return new ArrayList<>();
        return rewardSec.getStringList(INST_REWARDS_CONSOLE_COMMANDS);
    }

    public static void refreshStructureWeights(String dungeonName) {
        FileConfiguration config = getDungeonConfig(dungeonName);
        if (config == null) return;

        List<ResourceUtil.RoomEntry> entries = ResourceUtil.scanRooms(dungeonName);
        Set<String> currentRoomKeys = entries.stream()
                .map(e -> e.baseName)
                .collect(Collectors.toSet());

        String path = "dungeonInstance.structureChoose";
        if (!config.isConfigurationSection(path)) {
            config.createSection(path);
        }
        ConfigurationSection section = config.getConfigurationSection(path);
        Set<String> existingKeys = section.getKeys(false);

        for (String key : existingKeys) {
            if (!currentRoomKeys.contains(key)) {
                section.set(key, null);
            }
        }

        for (ResourceUtil.RoomEntry entry : entries) {
            if (!section.contains(entry.baseName)) {
                section.set(entry.baseName, 50);
            }
        }

        saveDungeonConfig(dungeonName, config);
    }

    public static int getSchematicReadFormat() {
        String s = getGlobalConfig().getString("schematic.readFormat", "auto");
        return switch (s.toLowerCase()) {
            case "sponge_v2" -> 2;
            case "sponge_v3" -> 3;
            default -> 0;
        };
    }

    public static int getSchematicSaveFormat() {
        String s = getGlobalConfig().getString("schematic.saveFormat", "sponge_v3");
        return s.equalsIgnoreCase("sponge_v2") ? 2 : 3;
    }
    // ======================== 全局：新增 ========================

    public static int getConfigVersion() {
        return getGlobalConfig().getInt("configVersion", 1);
    }

    public static boolean setConfigVersion(int v) {
        FileConfiguration c = getGlobalConfig();
        c.set("configVersion", v);
        return saveGlobalConfig(c);
    }

    public static boolean isKeepTestWorld() {
        return getGlobalConfig().getBoolean("keepTestWorld", false);
    }

    public static int getPasteSpeed() {
        int v = getGlobalConfig().getInt("pasteSpeed", 2000);
        return Math.max(1, Math.min(v, 20000));
    }

    public static int getEntityCleanupInterval() {
        return Math.max(0, getGlobalConfig().getInt("entityCleanupInterval", 300));
    }

    public static boolean isDebugLogToFile() {
        return getGlobalConfig().getBoolean("debugLogToFile", false);
    }

    public static boolean isMetricsEnabled() {
        return getGlobalConfig().getBoolean("metrics", true);
    }

    public static boolean isUpdateCheckEnabled() {
        return getGlobalConfig().getBoolean("updateCheck", true);
    }

    // ======================== 实例：P0 ========================

    public static String getInstanceRespawnMode(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        String v = sec == null ? "checkpoint" : sec.getString("respawnMode", "checkpoint");
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "lobby", "checkpoint", "spectator" -> v.toLowerCase(Locale.ROOT);
            default -> "checkpoint";
        };
    }

    public static int getInstanceDeathCooldown(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? 15 : Math.max(0, sec.getInt("deathCooldown", 15));
    }

    public static int getInstancePlayerCooldown(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? 300 : Math.max(0, sec.getInt("playerCooldown", 300));
    }

    public static boolean isInstanceFriendlyFire(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean("friendlyFire", false);
    }

    public static boolean isInstanceAutoPickup(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null || sec.getBoolean("autoPickup", true);
    }

    public static int getInstanceTeleportInvulnerable(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? 3 : Math.max(0, sec.getInt("teleportInvulnerable", 3));
    }

    public static List<Integer> getInstanceCountdownWarn(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return List.of(300, 180, 60, 30, 10);
        List<Integer> list = sec.getIntegerList("countdownWarn");
        if (list == null || list.isEmpty()) return List.of(300, 180, 60, 30, 10);
        return list;
    }

    public static boolean isInstanceBossBar(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null || sec.getBoolean("bossBar", true);
    }

    public static boolean isInstancePartyGlow(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null || sec.getBoolean("partyGlow", true);
    }

    // ======================== 实例：P1 体验 ========================

    public static String getInstanceEnterTitle(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? "" : sec.getString("enterTitle", "");
    }

    public static String getInstanceLeaveTitle(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? "" : sec.getString("leaveTitle", "");
    }

    public static boolean isInstanceRoomProgress(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null || sec.getBoolean("roomProgress", true);
    }

    public static boolean isInstanceBossDirection(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean("bossDirection", false);
    }

    public static int getInstanceSoundVolume(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        int v = sec == null ? 100 : sec.getInt("soundVolume", 100);
        return Math.max(0, Math.min(v, 100));
    }

    public static boolean isInstanceAllowSpectatorAfterDeath(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null || sec.getBoolean("allowSpectatorAfterDeath", true);
    }

    // ======================== 实例：P1 难度 ========================

    public static boolean isInstanceScaleByPartySize(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null || sec.getBoolean("scaleByPartySize", true);
    }

    public static double getInstanceBossHealthMultiplierPerPlayer(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        double v = sec == null ? 1.5 : sec.getDouble("bossHealthMultiplierPerPlayer", 1.5);
        return Math.max(1.0, v);
    }

    public static double getInstanceMobSpawnRate(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        double v = sec == null ? 1.0 : sec.getDouble("mobSpawnRate", 1.0);
        return Math.max(0.0, v);
    }

    public static int getInstanceMaxMobsPerRoom(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null ? 30 : Math.max(1, sec.getInt("maxMobsPerRoom", 30));
    }

    public static String getInstanceTimeoutAction(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        String v = sec == null ? "forceEnd" : sec.getString("timeoutAction", "forceEnd");
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "extend" -> "extend";
            default -> "forceEnd";
        };
    }

    // ======================== 实例：P1 奖励 ========================

    public static Map<String, Double> getInstanceFirstClearVault(FileConfiguration config) {
        Map<String, Double> out = new LinkedHashMap<>();
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return out;
        ConfigurationSection fc = sec.getConfigurationSection("rewards.firstClearBonus.vault");
        if (fc == null) return out;
        for (String k : fc.getKeys(false)) out.put(k, fc.getDouble(k, 0.0));
        return out;
    }

    public static Map<String, Integer> getInstanceFirstClearPoints(FileConfiguration config) {
        Map<String, Integer> out = new LinkedHashMap<>();
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return out;
        ConfigurationSection fc = sec.getConfigurationSection("rewards.firstClearBonus.playerPoints");
        if (fc == null) return out;
        for (String k : fc.getKeys(false)) out.put(k, fc.getInt(k, 0));
        return out;
    }

    public static List<String> getInstanceFirstClearCommands(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return new ArrayList<>();
        return sec.getStringList("rewards.firstClearBonus.consoleCommands");
    }

    public static String getInstanceShareMode(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        String v = sec == null ? "each" : sec.getString("rewards.shareMode", "each");
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "split" -> "split";
            default -> "each";
        };
    }

    public static Map<String, Double> getInstancePermissionMultiplier(FileConfiguration config) {
        Map<String, Double> out = new LinkedHashMap<>();
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return out;
        ConfigurationSection pm = sec.getConfigurationSection("rewards.permissionMultiplier");
        if (pm == null) return out;
        for (String k : pm.getKeys(false)) out.put(k, pm.getDouble(k, 1.0));
        return out;
    }

    public static Map<String, Double> getInstanceFailureVault(FileConfiguration config) {
        Map<String, Double> out = new LinkedHashMap<>();
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return out;
        ConfigurationSection f = sec.getConfigurationSection("failurePenalty.vault");
        if (f == null) return out;
        for (String k : f.getKeys(false)) out.put(k, f.getDouble(k, 0.0));
        return out;
    }

    public static Map<String, Integer> getInstanceFailurePoints(FileConfiguration config) {
        Map<String, Integer> out = new LinkedHashMap<>();
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return out;
        ConfigurationSection f = sec.getConfigurationSection("failurePenalty.playerPoints");
        if (f == null) return out;
        for (String k : f.getKeys(false)) out.put(k, f.getInt(k, 0));
        return out;
    }

    public static List<String> getInstanceFailureCommands(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return new ArrayList<>();
        return sec.getStringList("failurePenalty.consoleCommands");
    }

    public static boolean isInstanceTrackFastestTime(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec == null || sec.getBoolean("stats.trackFastestTime", true);
    }

    // ======================== 实例：P3 ========================

    public static boolean isInstanceRoomFog(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean("roomFog", false);
    }

    public static boolean isInstanceMinimap(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean("minimap", false);
    }

    public static boolean isInstanceLeaderboard(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean("leaderboard", false);
    }

    public static boolean isInstanceBossPhaseEnabled(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean("bossPhase.enabled", false);
    }

    public static List<Map<?, ?>> getInstanceBossPhases(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return new ArrayList<>();
        List<Map<?, ?>> out = new ArrayList<>();
        List<?> raw = sec.getList("bossPhase.phases");
        if (raw == null) return out;
        for (Object o : raw) {
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                out.add(m);
            }
        }
        return out;
    }

    public static boolean isInstanceLootTableEnabled(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        return sec != null && sec.getBoolean("lootTable.enabled", false);
    }

    public static Map<String, Object> getInstanceLootTable(FileConfiguration config) {
        Map<String, Object> out = new LinkedHashMap<>();
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return out;
        ConfigurationSection lt = sec.getConfigurationSection("lootTable.tables");
        if (lt == null) return out;
        for (String k : lt.getKeys(false)) out.put(k, lt.get(k));
        return out;
    }
    public static List<String> getLootTableEntries(FileConfiguration config, String tableName) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return Collections.emptyList();
        List<String> raw = sec.getStringList("lootTable.tables." + tableName);
        return raw == null ? Collections.emptyList() : raw;
    }
}