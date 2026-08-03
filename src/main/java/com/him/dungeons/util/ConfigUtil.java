package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 配置管理工具类（封装所有配置键，调用方无需关心路径字符串）
 * <p>
 * 全局配置：plugins/HimDungeons/config.yml
 * 实例配置：plugins/HimDungeons/&lt;地牢名&gt;/config.yml
 * </p>
 * <p>
 * 配置规范参考：https://mczfw.com/blog/26408.html
 * </p>
 */
public final class ConfigUtil {

    private static final String GLOBAL_CONFIG_NAME = "config.yml";
    private static final String DUNGEON_CONFIG_NAME = "config.yml";

    // ======================== 全局配置键 ========================
    private static final String GLOBAL_LANG = "lang";
    private static final String GLOBAL_MSG_PREFIX = "msgPrefix";
    private static final String GLOBAL_GENERATE_COOLDOWN = "generateCooldown";

    // ======================== 实例配置根节点 ========================
    private static final String INST_ROOT = "dungeonInstance";

    // --- 基础开关与信息 ---
    private static final String INST_ENABLE = "enable";
    private static final String INST_DISPLAY_NAME = "displayName";
    private static final String INST_FILE_NAME = "fileName";
    private static final String INST_MSG_PREFIX = "msgPrefix";

    // --- 生成算法与结构 ---
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

    // --- 游戏规则与环境 ---
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

    // --- 权限与控制 ---
    private static final String INST_COMMANDS_ALLOW_LIST_TYPE = "commandsAllowListType";
    private static final String INST_COMMANDS_ALLOW = "commandsAllow";
    private static final String INST_BUILD_SETTINGS_LIST_TYPE = "buildSettingsListType";
    private static final String INST_BUILD_SETTINGS = "buildSettings";
    private static final String INST_BREAK_SETTINGS_LIST_TYPE = "breakSettingsListType";
    private static final String INST_BREAK_SETTINGS = "breakSettings";

    // --- 结算与奖励 ---
    private static final String INST_LOBBY = "lobby";
    private static final String INST_REWARDS = "rewards";
    private static final String INST_REWARDS_VAULT = "vault";
    private static final String INST_REWARDS_PLAYER_POINTS = "playerPoints";
    private static final String INST_REWARDS_CONSOLE_COMMANDS = "consoleCommands";

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
        return getGlobalConfig().getString(GLOBAL_LANG, "zh_CN");
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

    /**
     * 获取结构生成权重配置
     */
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

    /**
     * 获取 start 到 boss 房间的主干房间数量范围
     */
    public static int[] getInstanceStartToBossRoomAmount(FileConfiguration config) {
        ConfigurationSection sec = getInstanceSection(config);
        if (sec == null) return new int[]{10, 10};
        String value = sec.getString(INST_START_TO_BOSS_ROOM_AMOUNT, "10 10");
        String[] parts = value.split("\\\\s+");
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

    // ========================= 游戏规则与环境 =========================

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

    // ========================= 权限与控制 =========================

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

    // ========================= 结算与奖励 =========================

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
}