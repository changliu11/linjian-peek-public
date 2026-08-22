package dev.linjian.peek;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class AppPrefs {
    public static final String PREFS = "linjian_peek";
    public static final String KEY_SERVER = "server_url";
    public static final String APP_VERSION_NAME = "0.3.5.0";
    public static final int APP_VERSION_CODE = 30500;
    public static final long STATE_UPLOAD_INTERVAL_MS = 10000L;
    public static final String KEY_TOKEN = "token";
    public static final String KEY_DEVICE = "device_id";
    public static final String KEY_INTERVAL = "poll_interval_ms";
    public static final String KEY_CITY = "life_city";
    public static final String KEY_WEATHER_NOTE = "life_weather_note";
    public static final String KEY_WEATHER_LOCATIONS = "weather_locations_lines";
    public static final String KEY_THEME = "ui_theme";
    public static final String KEY_ACTIVE_REMINDERS = "active_reminders_enabled";
    public static final String KEY_RULE_BATTERY = "rule_battery_enabled";
    public static final String KEY_BATTERY_THRESHOLD = "rule_battery_threshold";
    public static final String KEY_RULE_SCREEN = "rule_screen_enabled";
    public static final String KEY_SCREEN_THRESHOLD_MIN = "rule_screen_threshold_min";
    public static final String KEY_RULE_WATER = "rule_water_enabled";
    public static final String KEY_WATER_INTERVAL_MIN = "rule_water_interval_min";
    public static final String KEY_RULE_REST = "rule_rest_enabled";
    public static final String KEY_REST_INTERVAL_MIN = "rule_rest_interval_min";
    public static final String KEY_CYCLE_ENABLED = "cycle_enabled";
    public static final String KEY_LAST_PERIOD_START = "cycle_last_period_start";
    public static final String KEY_CYCLE_LENGTH = "cycle_length_days";
    public static final String KEY_PERIOD_LENGTH = "cycle_period_length_days";
    public static final String KEY_CYCLE_REMIND_BEFORE = "cycle_remind_before_days";
    public static final String KEY_USER_NICKNAME = "user_nickname";
    public static final String KEY_PARTNER_NICKNAME = "partner_nickname";

    public static final String KEY_FOREGROUND_POPUP = "foreground_popup_enabled";
    public static final String KEY_CUSTOM_APPS = "custom_apps_lines";
    public static final String KEY_HOME_MODE_ENABLED = "home_mode_enabled";
    public static final String KEY_HOME_MODE_FORCE = "home_mode_force";
    public static final String KEY_HOME_WATCH_PACKAGES = "home_mode_watch_packages";
    public static final String KEY_HOME_THRESHOLD_MIN = "home_mode_threshold_min";
    public static final String KEY_HOME_COOLDOWN_MIN = "home_mode_cooldown_min";
    public static final String KEY_HOME_TARGET_PACKAGE = "home_mode_target_package";
    public static final String DEFAULT_HOME_TARGET_PACKAGE = "com.openai.chatgpt";

    public static SharedPreferences get(Context ctx) { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /**
     * v0.3.4.6：不再按域名黑名单拦截 Render 地址。
     * 用户自己的服务名可能包含 rork、test、demo 等任意词，App 只按真实联网结果判断。
     */
    public static boolean isLegacyServer(String raw) {
        return false;
    }

    public static String legacyServerMessage() {
        return "服务器地址不再按名称拦截；请按实际连接结果检查网络、Render 状态、Token 和后端接口";
    }

    public static String cleanServer(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.equalsIgnoreCase("null")) return "";
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        String lower = s.toLowerCase(Locale.ROOT);
        String[] suffixes = new String[] { "/health", "/api/poll", "/api/state", "/api/screenshot", "/mcp" };
        for (String suffix : suffixes) {
            if (lower.endsWith(suffix)) {
                s = s.substring(0, s.length() - suffix.length());
                break;
            }
        }
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static boolean migrateLegacyConfig(Context ctx) {
        // v0.3.4.6：不迁移、不清空、不拦截任何 onrender.com 地址。
        return false;
    }

    public static String server(Context ctx) {
        return cleanServer(get(ctx).getString(KEY_SERVER, ""));
    }
    public static String token(Context ctx) { return get(ctx).getString(KEY_TOKEN, ""); }
    public static String device(Context ctx) { return get(ctx).getString(KEY_DEVICE, "android-phone"); }
    public static String userName(Context ctx) {
        String v = get(ctx).getString(KEY_USER_NICKNAME, "宝宝");
        return (v == null || v.trim().isEmpty()) ? "宝宝" : v.trim();
    }
    public static String partnerName(Context ctx) {
        String v = get(ctx).getString(KEY_PARTNER_NICKNAME, "老公");
        return (v == null || v.trim().isEmpty()) ? "老公" : v.trim();
    }

    public static String homeTargetPackage(Context ctx) {
        String raw = get(ctx).getString(KEY_HOME_TARGET_PACKAGE, DEFAULT_HOME_TARGET_PACKAGE);
        if (raw == null || raw.trim().isEmpty()) return DEFAULT_HOME_TARGET_PACKAGE;
        String resolved = packageForApp(ctx, raw.trim());
        return (resolved == null || resolved.trim().isEmpty()) ? DEFAULT_HOME_TARGET_PACKAGE : resolved.trim();
    }

    public static String homeTargetLabel(Context ctx) {
        String target = homeTargetPackage(ctx);
        for (Map.Entry<String, String> e : allApps(ctx).entrySet()) {
            if (target.equals(e.getValue())) return e.getKey();
        }
        return target;
    }

    public static String returnButtonText(Context ctx) {
        return "回" + partnerName(ctx) + "这儿";
    }

    public static String seeButtonText(Context ctx) {
        return "给" + partnerName(ctx) + "看一眼";
    }

    public static String saveHomeTarget(Context ctx, String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) return DEFAULT_HOME_TARGET_PACKAGE;
        String pkg = packageForApp(ctx, v);
        if (pkg != null && pkg.trim().length() > 0) return pkg.trim();
        return v;
    }

    public static int interval(Context ctx) { return Math.max(700, get(ctx).getInt(KEY_INTERVAL, 1500)); }

    public static Map<String, String> defaultApps() {
        LinkedHashMap<String, String> apps = new LinkedHashMap<>();
        apps.put("小红书", "com.xingin.xhs");
        apps.put("微信", "com.tencent.mm");
        apps.put("QQ", "com.tencent.mobileqq");
        apps.put("抖音", "com.ss.android.ugc.aweme");
        apps.put("ChatGPT", "com.openai.chatgpt");
        apps.put("Gemini", "com.google.android.apps.bard");
        apps.put("Claude", "com.anthropic.claude");
        apps.put("微博", "com.sina.weibo");
        apps.put("X", "com.twitter.android");
        apps.put("Speedcat", "");
        return apps;
    }

    public static Map<String, String> allApps(Context ctx) {
        LinkedHashMap<String, String> apps = new LinkedHashMap<>(defaultApps());
        String custom = get(ctx).getString(KEY_CUSTOM_APPS, "");
        String[] lines = custom.split("\\n");
        for (String line : lines) {
            if (line == null) continue;
            String s = line.trim();
            if (s.isEmpty() || !s.contains("|")) continue;
            String[] parts = s.split("\\|", 2);
            String alias = parts[0].trim();
            String pkg = parts.length > 1 ? parts[1].trim() : "";
            if (!alias.isEmpty() && isPackageLike(pkg)) apps.put(alias, pkg);
        }
        return apps;
    }

    public static void saveCustomApp(Context ctx, String alias, String pkg) {
        alias = alias == null ? "" : alias.trim();
        pkg = pkg == null ? "" : pkg.trim();
        if (alias.isEmpty() || !isPackageLike(pkg)) return;
        LinkedHashMap<String, String> custom = new LinkedHashMap<>();
        String old = get(ctx).getString(KEY_CUSTOM_APPS, "");
        for (String line : old.split("\\n")) {
            if (line == null || !line.contains("|")) continue;
            String[] parts = line.trim().split("\\|", 2);
            if (parts.length == 2 && !parts[0].trim().isEmpty() && isPackageLike(parts[1].trim())) custom.put(parts[0].trim(), parts[1].trim());
        }
        custom.put(alias, pkg);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : custom.entrySet()) sb.append(e.getKey()).append("|").append(e.getValue()).append("\n");
        get(ctx).edit().putString(KEY_CUSTOM_APPS, sb.toString()).putString("pkg_" + alias.toLowerCase(Locale.US), pkg).apply();
    }

    public static String knownAppsText(Context ctx) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : allApps(ctx).entrySet()) {
            sb.append(e.getKey()).append("  →  ").append(e.getValue().isEmpty() ? "未设置" : e.getValue()).append("\n");
        }
        return sb.toString().trim();
    }

    public static JSONObject knownAppsJson(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, String> e : allApps(ctx).entrySet()) o.put(e.getKey(), e.getValue());
        } catch (Exception ignored) { }
        return o;
    }

    public static String packageForApp(Context ctx, String app) {
        String raw = app == null ? "" : app.trim();
        if (isPackageLike(raw)) return raw;
        String key = raw.toLowerCase(Locale.US);
        String def;
        switch (key) {
            case "xiaohongshu": case "xhs": case "小红书": def = "com.xingin.xhs"; break;
            case "wechat": case "微信": def = "com.tencent.mm"; break;
            case "qq": def = "com.tencent.mobileqq"; break;
            case "douyin": case "抖音": def = "com.ss.android.ugc.aweme"; break;
            case "chatgpt": def = "com.openai.chatgpt"; break;
            case "gemini": case "bard": def = "com.google.android.apps.bard"; break;
            case "claude": def = "com.anthropic.claude"; break;
            case "weibo": case "微博": def = "com.sina.weibo"; break;
            case "x": case "twitter": def = "com.twitter.android"; break;
            case "speedcat": def = get(ctx).getString("pkg_speedcat", ""); break;
            default: def = "";
        }
        String custom = get(ctx).getString("pkg_" + key, def);
        if (custom != null && custom.trim().length() > 0) return custom.trim();
        for (Map.Entry<String, String> e : allApps(ctx).entrySet()) {
            if (e.getKey().equalsIgnoreCase(raw)) return e.getValue();
        }
        return def;
    }

    public static boolean isPackageLike(String value) {
        if (value == null) return false;
        String s = value.trim();
        return s.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    }
}
