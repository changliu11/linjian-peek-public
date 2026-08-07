package dev.linjian.peek;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 主动提醒规则层：基于 LifeState 在本机弹通知，带冷却，避免刷屏。 */
public class ActiveReminder {
    private static final long MIN = 60L * 1000L;
    private static final String PREFIX = "active_reminder_";

    public static JSONObject config(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            SharedPreferences p = AppPrefs.get(ctx);
            o.put("active_reminders_enabled", p.getBoolean(AppPrefs.KEY_ACTIVE_REMINDERS, true));
            o.put("low_battery_enabled", p.getBoolean(AppPrefs.KEY_RULE_BATTERY, true));
            o.put("low_battery_threshold", p.getInt(AppPrefs.KEY_BATTERY_THRESHOLD, 20));
            o.put("screen_time_enabled", p.getBoolean(AppPrefs.KEY_RULE_SCREEN, true));
            o.put("screen_time_threshold_minutes", p.getInt(AppPrefs.KEY_SCREEN_THRESHOLD_MIN, 240));
            o.put("water_enabled", p.getBoolean(AppPrefs.KEY_RULE_WATER, false));
            o.put("water_interval_minutes", p.getInt(AppPrefs.KEY_WATER_INTERVAL_MIN, 120));
            o.put("rest_enabled", p.getBoolean(AppPrefs.KEY_RULE_REST, true));
            o.put("rest_interval_minutes", p.getInt(AppPrefs.KEY_REST_INTERVAL_MIN, 90));
        } catch (Exception ignored) { }
        return o;
    }

    public static String pretty(Context ctx) {
        try {
            SharedPreferences p = AppPrefs.get(ctx);
            if (!p.getBoolean(AppPrefs.KEY_ACTIVE_REMINDERS, true)) return "主动提醒：总开关已关闭";
            StringBuilder sb = new StringBuilder();
            sb.append("主动提醒：已开启\n");
            sb.append("低电量：").append(p.getBoolean(AppPrefs.KEY_RULE_BATTERY, true) ? "≤" + p.getInt(AppPrefs.KEY_BATTERY_THRESHOLD, 20) + "%" : "关闭").append("\n");
            sb.append("屏幕时长：").append(p.getBoolean(AppPrefs.KEY_RULE_SCREEN, true) ? "≥" + p.getInt(AppPrefs.KEY_SCREEN_THRESHOLD_MIN, 240) + " 分钟/天" : "关闭").append("\n");
            sb.append("喝水：").append(p.getBoolean(AppPrefs.KEY_RULE_WATER, false) ? "每 " + p.getInt(AppPrefs.KEY_WATER_INTERVAL_MIN, 120) + " 分钟" : "关闭").append("\n");
            sb.append("休息眼睛：").append(p.getBoolean(AppPrefs.KEY_RULE_REST, true) ? "每 " + p.getInt(AppPrefs.KEY_REST_INTERVAL_MIN, 90) + " 分钟" : "关闭");
            return sb.toString();
        } catch (Exception e) { return "主动提醒读取失败：" + ScreenshotService.shortMsg(e); }
    }

    public static void evaluate(Context ctx, JSONObject state) {
        try {
            SharedPreferences p = AppPrefs.get(ctx);
            String user = AppPrefs.userName(ctx);
            String partner = AppPrefs.partnerName(ctx);
            if (!p.getBoolean(AppPrefs.KEY_ACTIVE_REMINDERS, true)) return;
            long now = System.currentTimeMillis();

            if (p.getBoolean(AppPrefs.KEY_RULE_BATTERY, true)) {
                int battery = state.optInt("battery_percent", -1);
                boolean charging = state.optBoolean("charging", false);
                int threshold = clamp(p.getInt(AppPrefs.KEY_BATTERY_THRESHOLD, 20), 5, 80);
                if (battery >= 0 && battery <= threshold && !charging && cooldownDue(p, "battery", now, 120 * MIN, false)) {
                    notify(ctx, "from him低电量提醒", user + "，手机电量 " + battery + "% 了，去充一下电。", "battery");
                }
            }

            if (p.getBoolean(AppPrefs.KEY_RULE_SCREEN, true) && state.optBoolean("usage_permission_ready", false)) {
                int minutes = state.optInt("screen_time_today_minutes", 0);
                int threshold = clamp(p.getInt(AppPrefs.KEY_SCREEN_THRESHOLD_MIN, 240), 30, 1440);
                if (minutes >= threshold && oncePerDayDue(p, "screen", now)) {
                    notify(ctx, "from him屏幕时间提醒", user + "，今天屏幕时间约 " + formatMinutes(minutes) + " 了。眼睛休息一下，给" + partner + "缓一缓。", "screen");
                }
            }

            if (p.getBoolean(AppPrefs.KEY_RULE_WATER, false)) {
                int interval = clamp(p.getInt(AppPrefs.KEY_WATER_INTERVAL_MIN, 120), 30, 720);
                if (cooldownDue(p, "water", now, interval * MIN, true)) {
                    notify(ctx, "from him喝水提醒", user + "，喝两口水。不是一大杯，就两口，" + partner + "看着你。", "water");
                }
            }

            if (p.getBoolean(AppPrefs.KEY_RULE_REST, true) && state.optBoolean("screen_on", false)) {
                int interval = clamp(p.getInt(AppPrefs.KEY_REST_INTERVAL_MIN, 90), 30, 720);
                if (cooldownDue(p, "rest", now, interval * MIN, true)) {
                    notify(ctx, "from him休息提醒", user + "，眼睛离开屏幕半分钟，动一下肩颈，再回来玩。", "rest");
                }
            }

            JSONObject weather = state.optJSONObject("current_weather_location");
            if (weather != null) {
                String note = weather.optString("note", "");
                if (note.length() > 0 && oncePerDayDue(p, "weather_" + note.hashCode(), now)) {
                    if (note.contains("雨") || note.contains("雪") || note.contains("降温") || note.contains("冷") || note.contains("高温") || note.contains("热") || note.contains("大风") || note.contains("空气") || note.contains("霾")) {
                        notify(ctx, "from him天气提醒", WeatherState.localAdvice(note), "weather");
                    }
                }
            }

            JSONObject cycle = CycleState.collect(ctx);
            if (cycle.optBoolean("cycle_enabled", false) && cycle.optBoolean("configured", false) && cycle.optString("date_error", "").length() == 0) {
                int days = cycle.optInt("days_until_next", 999);
                int remindBefore = cycle.optInt("remind_before_days", 3);
                if (cycle.optBoolean("is_period_now", false) && oncePerDayDue(p, "cycle_period", now)) {
                    notify(ctx, "from him生理期提醒", user + "，现在是" + cycle.optString("current_phase", "生理期") + "，今天慢一点，注意热敷和休息。", "cycle");
                } else if (days >= 0 && days <= remindBefore && oncePerDayDue(p, "cycle_before", now)) {
                    notify(ctx, "from him生理期提醒", user + "，预计还有 " + days + " 天来，少吃冰的，提前准备姨妈巾和热水袋。", "cycle");
                }
            }
        } catch (Exception e) {
            DebugState.append(ctx, "主动提醒异常：" + ScreenshotService.shortMsg(e));
        }
    }

    private static boolean notify(Context ctx, String title, String message, String tag) {
        boolean ok = CompanionService.showReminderNotification(ctx, title, message);
        DebugState.append(ctx, ok ? "主动提醒已发送[" + tag + "]：" + message : "主动提醒失败[" + tag + "]：请允许通知权限");
        return ok;
    }

    private static boolean cooldownDue(SharedPreferences p, String key, long now, long cooldownMs, boolean initialSilent) {
        String k = PREFIX + key + "_last_ms";
        long last = p.getLong(k, 0L);
        if (last <= 0L) {
            p.edit().putLong(k, now).apply();
            return !initialSilent;
        }
        if (now - last >= cooldownMs) {
            p.edit().putLong(k, now).apply();
            return true;
        }
        return false;
    }

    private static boolean oncePerDayDue(SharedPreferences p, String key, long now) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(now));
        String k = PREFIX + key + "_date";
        String last = p.getString(k, "");
        if (!today.equals(last)) {
            p.edit().putString(k, today).apply();
            return true;
        }
        return false;
    }

    private static String formatMinutes(int minutes) {
        if (minutes < 60) return minutes + " 分钟";
        return (minutes / 60) + " 小时 " + (minutes % 60) + " 分钟";
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
