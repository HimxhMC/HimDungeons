package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多语言消息管理器。
 *
 * <p>职责：
 * <ul>
 *   <li>从 {@code plugins/HimDungeons/lang/<code>.yml} 加载全部语言</li>
 *   <li>查询时按 {@code 当前语言 → en_US → 全部语言 → key 原文} 回退</li>
 *   <li>{@code &} → {@code §} 颜色码转换</li>
 *   <li>{@code %name%} 占位符替换，{@code %%} 转义为字面 {@code %}</li>
 *   <li>客户端 locale 自动匹配（需在 config.yml 开启 {@code language.useClientLocale}）</li>
 *   <li>msgPrefix 读取与颜色缓存</li>
 * </ul>
 *
 * <p>语言文件缺失某个 key 时：
 * 首次访问记录一条 WARN（避免刷屏），返回 key 原文给玩家看，
 * 便于管理员直接定位缺失项。</p>
 */
public final class LanguageManager {

    private static HimDungeons plugin;
    private static String defaultLang = "en_US";
    private static boolean useClientLocale = false;
    private static String rawPrefix = "";
    private static String coloredPrefix = "";

    private static final Map<String, Map<String, String>> LANGS = new ConcurrentHashMap<>();
    private static final Set<String> WARNED_MISSING = ConcurrentHashMap.newKeySet();

    private LanguageManager() {}

    // ==================== 初始化 / 重载 ====================

    public static void init(HimDungeons p) {
        plugin = p;
        reload();
    }

    public static void reload() {
        LANGS.clear();
        WARNED_MISSING.clear();
        if (plugin == null) return;

        defaultLang = ConfigUtil.getGlobalLang();
        useClientLocale = ConfigUtil.isUseClientLocale();

        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists() && !langDir.mkdirs()) {
            plugin.getLogger().warning("[i18n] 无法创建语言目录: " + langDir.getPath());
        }

        File[] files = langDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                String code = f.getName().substring(0, f.getName().length() - 4);
                try {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
                    Map<String, String> flat = new HashMap<>();
                    flatten(yaml, "", flat);
                    LANGS.put(code, flat);
                } catch (Exception e) {
                    plugin.getLogger().warning("[i18n] 加载语言文件失败: " + f.getName() + " - " + e.getMessage());
                }
            }
        }

        if (LANGS.isEmpty()) {
            plugin.getLogger().warning("[i18n] 未找到任何语言文件，所有消息将回退为 key 原文");
        } else if (!LANGS.containsKey(defaultLang)) {
            plugin.getLogger().warning("[i18n] 默认语言 '" + defaultLang
                    + "' 未找到，可用: " + LANGS.keySet());
        }

        // 前缀（msgPrefix 在本次启用）
        String p = ConfigUtil.getGlobalMsgPrefix();
        if (p == null) p = "";
        rawPrefix = p;
        coloredPrefix = p.isEmpty() ? "" : ChatColor.translateAlternateColorCodes('&', p);
    }

    // ==================== 查询 ====================

    public static String getPrefix() {
        return coloredPrefix;
    }

    public static String getRawPrefix() {
        return rawPrefix;
    }

    public static boolean isUseClientLocale() {
        return useClientLocale;
    }

    /** 使用默认语言查询 */
    public static String get(String key, Object... kv) {
        return getInternal(defaultLang, key, kv);
    }

    /** 使用玩家客户端 locale（若开启）或默认语言查询 */
    public static String get(Player player, String key, Object... kv) {
        return getInternal(resolveLang(player), key, kv);
    }

    // ==================== 内部实现 ====================

    private static String getInternal(String lang, String key, Object... kv) {
        if (key == null || key.isEmpty()) return "";
        String text = lookup(lang, key);
        if (text == null) text = lookup("en_US", key);
        if (text == null) {
            for (Map<String, String> map : LANGS.values()) {
                String v = map.get(key);
                if (v != null) { text = v; break; }
            }
        }
        if (text == null) {
            if (WARNED_MISSING.add(key) && plugin != null) {
                plugin.getLogger().warning("[i18n] Missing lang key: " + key);
            }
            return key;
        }
        text = ChatColor.translateAlternateColorCodes('&', text);
        return replacePlaceholders(text, kv);
    }

    private static String replacePlaceholders(String text, Object... kv) {
        if (kv == null || kv.length == 0) {
            return text.replace("%%", "%");
        }
        String result = text;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = String.valueOf(kv[i]);
            Object v = kv[i + 1];
            String val = (v == null) ? "null" : String.valueOf(v);
            result = result.replace("%" + k + "%", val);
        }
        return result.replace("%%", "%");
    }

    private static String resolveLang(Player player) {
        if (!useClientLocale || player == null) return defaultLang;
        try {
            String locale = player.getLocale();
            if (locale == null || locale.isEmpty()) return defaultLang;
            if (LANGS.containsKey(locale)) return locale;
            for (String code : LANGS.keySet()) {
                if (code.equalsIgnoreCase(locale)) return code;
            }
            String langPart = locale.split("_")[0].toLowerCase();
            for (String code : LANGS.keySet()) {
                if (code.toLowerCase().startsWith(langPart + "_")
                        || code.equalsIgnoreCase(langPart)) {
                    return code;
                }
            }
        } catch (Throwable ignored) {}
        return defaultLang;
    }

    private static String lookup(String lang, String key) {
        if (lang == null) return null;
        Map<String, String> map = LANGS.get(lang);
        return map == null ? null : map.get(key);
    }

    private static void flatten(ConfigurationSection sec, String prefix, Map<String, String> out) {
        for (String key : sec.getKeys(false)) {
            Object val = sec.get(key);
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            if (val instanceof ConfigurationSection s) {
                flatten(s, fullKey, out);
            } else if (val != null) {
                out.put(fullKey, String.valueOf(val));
            }
        }
    }
}