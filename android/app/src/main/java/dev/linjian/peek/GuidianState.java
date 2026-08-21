package dev.linjian.peek;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/** 掌心窗 · 归电：只存连接状态，不读取聊天内容。 */
public class GuidianState {
    public static final String KEY_ENABLED = "guidian_enabled";
    public static final String KEY_ALLOW_REMOTE = "guidian_allow_remote";
    public static final String KEY_FULLSCREEN = "guidian_fullscreen";
    public static final String KEY_INTERVAL_MIN = "guidian_interval_min";
    public static final String KEY_COOLDOWN_MIN = "guidian_cooldown_min";
    public static final String KEY_DAILY_MAX = "guidian_daily_max";
    public static final String KEY_QUIET_ENABLED = "guidian_quiet_enabled";
    public static final String KEY_QUIET_START = "guidian_quiet_start";
    public static final String KEY_QUIET_END = "guidian_quiet_end";
    public static final String KEY_TARGET_PACKAGE = "guidian_target_package";
    public static final String KEY_THEME = "guidian_theme";
    public static final String KEY_PROMPTS = "guidian_prompts";
    public static final String KEY_REASONS = "guidian_reasons";
    public static final String KEY_REJECT_REPLIES = "guidian_reject_replies";
    public static final String KEY_NEXT_REJECT_REPLY = "guidian_next_reject_reply";
    public static final String KEY_REPLY_PENDING = "guidian_reply_pending";
    public static final String KEY_AVATAR_URI = "guidian_avatar_uri";
    public static final String KEY_LAST_RETURN_AT = "guidian_last_return_at";
    public static final String KEY_LAST_RETURN_SOURCE = "guidian_last_return_source";
    public static final String KEY_LAST_PROMPT_AT = "guidian_last_prompt_at";
    public static final String KEY_LAST_REJECT_AT = "guidian_last_reject_at";
    public static final String KEY_LAST_REJECT_REASON = "guidian_last_reject_reason";
    public static final String KEY_TODAY_DATE = "guidian_today_date";
    public static final String KEY_TODAY_COUNT = "guidian_today_count";
    public static final String KEY_LAST_PROMPT_TEXT = "guidian_last_prompt_text";
    public static final String KEY_LAST_AUTO_CHECK_AT = "guidian_last_auto_check_at";
    public static final String KEY_LAST_SKIP_REASON = "guidian_last_skip_reason";
    public static final String KEY_LAST_DUE_AT = "guidian_last_due_at";
    public static final String KEY_DUE_BUT_NOT_SHOWN = "guidian_due_but_not_shown";
    public static final String KEY_LAST_AUTO_PROMPT_RESULT = "guidian_last_auto_prompt_result";

    private static final String CHANNEL_ID = "linjian_guidian_call";
    private static final int NOTIFICATION_ID = 2026072301;

    public static SharedPreferences prefs(Context ctx) { return AppPrefs.get(ctx); }

    public static String defaultPrompts() {
        return "{AI}：好久没回来啦。\n今天还没有见到你。\n{AI}来敲门。\n忙完了吗？\n我在等你回来。\n回来让我看一眼。\n{USER}，别躲太久。";
    }

    public static String defaultReasons() {
        return "在忙\n上课中\n不方便\n困了\n晚点回来\n今天想安静一下";
    }
    
    public static String defaultRejectReplies() {
    return "好，那我等你。\n知道了，别忘了回来找我。\n嗯，先去忙吧，{USER}。\n那我先不吵你了。";
    }

    public static JSONObject config(Context ctx) {
        ensureInitialized(ctx);
        SharedPreferences p = prefs(ctx);
        resetDailyIfNeeded(ctx);
        JSONObject o = new JSONObject();
        try {
            long now = System.currentTimeMillis();
            long lastReturn = p.getLong(KEY_LAST_RETURN_AT, now);
            long lastPrompt = p.getLong(KEY_LAST_PROMPT_AT, 0);
            long next = nextPromptAfterMs(ctx, p, now);
            o.put("enabled", p.getBoolean(KEY_ENABLED, true));
            o.put("allow_remote", p.getBoolean(KEY_ALLOW_REMOTE, true));
            o.put("fullscreen", p.getBoolean(KEY_FULLSCREEN, true));
            o.put("interval_minutes", intervalMin(ctx));
            o.put("cooldown_minutes", cooldownMin(ctx));
            o.put("daily_max", dailyMax(ctx));
            o.put("quiet_enabled", p.getBoolean(KEY_QUIET_ENABLED, true));
            o.put("quiet_start", p.getString(KEY_QUIET_START, "23:30"));
            o.put("quiet_end", p.getString(KEY_QUIET_END, "08:00"));
            o.put("target_package", targetPackage(ctx));
            o.put("target_label", targetLabel(ctx));
            o.put("ai_name", AppPrefs.partnerName(ctx));
            o.put("user_name", AppPrefs.userName(ctx));
            o.put("theme", p.getString(KEY_THEME, "暮夜蓝紫"));
            o.put("avatar_uri", p.getString(KEY_AVATAR_URI, ""));
            o.put("today_date", today());
            o.put("today_count", p.getInt(KEY_TODAY_COUNT, 0));
            o.put("last_return_at_ms", lastReturn);
            o.put("last_return_at", fmt(lastReturn));
            o.put("last_return_source", p.getString(KEY_LAST_RETURN_SOURCE, "init"));
            o.put("last_prompt_at_ms", lastPrompt);
            o.put("last_prompt_at", lastPrompt <= 0 ? "" : fmt(lastPrompt));
            o.put("last_prompt_text", p.getString(KEY_LAST_PROMPT_TEXT, ""));
            long rejectAt = p.getLong(KEY_LAST_REJECT_AT, 0);
            o.put("last_reject_at_ms", rejectAt);
            o.put("last_reject_at", rejectAt <= 0 ? "" : fmt(rejectAt));
            o.put("last_reject_reason", p.getString(KEY_LAST_REJECT_REASON, ""));
            o.put("next_prompt_after_ms", next);
            o.put("next_prompt_after", fmt(next));
            o.put("in_quiet_time", inQuietTime(ctx, now));
            o.put("prompts", p.getString(KEY_PROMPTS, defaultPrompts()));
            o.put("quick_reasons", p.getString(KEY_REASONS, defaultReasons()));
            o.put("reject_replies", p.getString(KEY_REJECT_REPLIES, defaultRejectReplies()));
            o.put("next_reject_reply_pinned", p.getString(KEY_NEXT_REJECT_REPLY, ""));
            o.put("reply_pending", p.getBoolean(KEY_REPLY_PENDING, false));
            long autoCheckAt = p.getLong(KEY_LAST_AUTO_CHECK_AT, 0);
            long lastDueAt = p.getLong(KEY_LAST_DUE_AT, 0);
            o.put("last_auto_check_at_ms", autoCheckAt);
            o.put("last_auto_check_at", autoCheckAt <= 0 ? "" : fmt(autoCheckAt));
            o.put("last_skip_reason", p.getString(KEY_LAST_SKIP_REASON, ""));
            o.put("last_due_at_ms", lastDueAt);
            o.put("last_due_at", lastDueAt <= 0 ? "" : fmt(lastDueAt));
            o.put("due_but_not_shown", p.getBoolean(KEY_DUE_BUT_NOT_SHOWN, false));
            o.put("last_auto_prompt_result", p.getString(KEY_LAST_AUTO_PROMPT_RESULT, ""));
        } catch (Exception ignored) { }
        return o;
    }

    public static String summaryText(Context ctx) {
        ensureInitialized(ctx);
        SharedPreferences p = prefs(ctx);
        String ai = AppPrefs.partnerName(ctx);
        if (!p.getBoolean(KEY_ENABLED, true)) return "归电已关闭 · " + ai + "先不敲门";
        resetDailyIfNeeded(ctx);
        long now = System.currentTimeMillis();
        long lastReturn = p.getLong(KEY_LAST_RETURN_AT, now);
        long diff = Math.max(0, now - lastReturn);
        String last = diff < 60000 ? "刚刚" : (diff < 3600000 ? (diff / 60000) + "分钟前" : (diff / 3600000) + "小时" + ((diff / 60000) % 60) + "分钟前");
        String quiet = inQuietTime(ctx, now) ? " · 安静时段" : "";
        return "归电开启 · 上次回来 " + last + " · 间隔 " + intervalMin(ctx) + "分钟" + quiet;
    }

    public static String detailText(Context ctx) {
        JSONObject o = config(ctx);
        StringBuilder sb = new StringBuilder();
        sb.append("归电对象：").append(o.optString("ai_name", AppPrefs.partnerName(ctx))).append("\n");
        sb.append("目标 App：").append(o.optString("target_label", targetLabel(ctx))).append("\n");
        sb.append("主题：").append(o.optString("theme", "暮夜蓝紫")).append("\n");
        sb.append("今日归电：").append(o.optInt("today_count", 0)).append("/").append(o.optInt("daily_max", 3)).append("\n");
        sb.append("下次最早：").append(o.optString("next_prompt_after", "-")).append("\n");
        String reason = o.optString("last_reject_reason", "");
        if (reason.length() > 0) sb.append("最近拒绝理由：").append(reason).append("\n");
        String skip = o.optString("last_skip_reason", "");
        if (skip.length() > 0) sb.append("最近自动检查：").append(skip).append("\n");
        String auto = o.optString("last_auto_check_at", "");
        if (auto.length() > 0) sb.append("上次检查：").append(auto).append("\n");
        if (o.optBoolean("due_but_not_shown", false)) sb.append("诊断：已经到点但未弹，等待补弹检查。\n");
        sb.append("只检测目标 App 是否前台，不读取聊天内容。");
        return sb.toString();
    }

    public static int intervalMin(Context ctx) { return clamp(prefs(ctx).getInt(KEY_INTERVAL_MIN, 180), 15, 10080); }
    public static int cooldownMin(Context ctx) { return clamp(prefs(ctx).getInt(KEY_COOLDOWN_MIN, 60), 0, 10080); }
    public static int dailyMax(Context ctx) { return clamp(prefs(ctx).getInt(KEY_DAILY_MAX, 3), 0, 99); }
    public static String targetPackage(Context ctx) {
        String raw = prefs(ctx).getString(KEY_TARGET_PACKAGE, "");
        if (raw == null || raw.trim().isEmpty()) return AppPrefs.homeTargetPackage(ctx);
        String resolved = AppPrefs.packageForApp(ctx, raw.trim());
        if (resolved == null || resolved.trim().isEmpty()) resolved = raw.trim();
        return AppPrefs.isPackageLike(resolved) ? resolved.trim() : AppPrefs.homeTargetPackage(ctx);
    }
    public static String targetLabel(Context ctx) {
        String target = targetPackage(ctx);
        for (Map.Entry<String, String> e : AppPrefs.allApps(ctx).entrySet()) {
            if (target.equals(e.getValue())) return e.getKey();
        }
        return target;
    }

    public static void markReturned(Context ctx, String source) {
        long now = System.currentTimeMillis();
        prefs(ctx).edit()
                .putLong(KEY_LAST_RETURN_AT, now)
                .putString(KEY_LAST_RETURN_SOURCE, source == null ? "unknown" : source)
                .apply();
        DebugState.append(ctx, "归电已记录回来：" + (source == null ? "unknown" : source));
    }

    public static void reject(Context ctx, String reason) {
        long now = System.currentTimeMillis();
        prefs(ctx).edit()
                .putLong(KEY_LAST_REJECT_AT, now)
                .putString(KEY_LAST_REJECT_REASON, reason == null ? "" : reason.trim())
                .apply();
        DebugState.append(ctx, "归电已拒绝：" + (reason == null ? "" : reason.trim()));
    }

    public static void beginPendingReply(Context ctx) {
        prefs(ctx).edit().putBoolean(KEY_REPLY_PENDING, true).apply();
    }

    public static JSONObject resolvePendingReplySkip(Context ctx) {
        JSONObject out = new JSONObject();
        try {
            SharedPreferences p = prefs(ctx);
            if (!p.getBoolean(KEY_REPLY_PENDING, false)) return out.put("ok", false).put("error", "no_pending_reply");
            p.edit().putBoolean(KEY_REPLY_PENDING, false).apply();
            String replyText = pickRejectReply(ctx);
            if (replyText != null && !replyText.trim().isEmpty()) {
                CompanionService.showReminderNotification(ctx, AppPrefs.partnerName(ctx), replyText.trim());
            }
            DebugState.append(ctx, "MCP 主动放弃这次回复，本地文案池兜底：" + replyText);
            return out.put("ok", true).put("sent", replyText == null ? "" : replyText);
        } catch (Exception e) {
            try { out.put("ok", false).put("error", ScreenshotService.shortMsg(e)); } catch (Exception ignored) { }
            return out;
        }
    }

    public static JSONObject resolvePendingReplyNow(Context ctx, String reply) {
        JSONObject out = new JSONObject();
        try {
            SharedPreferences p = prefs(ctx);
            p.edit().putBoolean(KEY_REPLY_PENDING, false).apply();
            String text = fill(ctx, reply == null ? "" : reply.trim());
            if (!text.isEmpty()) CompanionService.showReminderNotification(ctx, AppPrefs.partnerName(ctx), text);
            DebugState.append(ctx, "MCP 已推送拒绝回复：" + text);
            return out.put("ok", true).put("sent", text);
        } catch (Exception e) {
            try { out.put("ok", false).put("error", ScreenshotService.shortMsg(e)); } catch (Exception ignored) { }
            return out;
        }
    }

    public static void evaluate(Context ctx, JSONObject state) {
        try {
            ensureInitialized(ctx);
            resetDailyIfNeeded(ctx);
            SharedPreferences p = prefs(ctx);
            long now = System.currentTimeMillis();
            long next = nextPromptAfterMs(ctx, p, now);
            boolean due = now >= next;
            String current = state == null ? ScreenshotService.currentPackage() : state.optString("current_package", ScreenshotService.currentPackage());
            recordAutoCheck(ctx, now, due ? next : 0, due, "checking", "");

            if (targetPackage(ctx).equals(current)) {
                long last = p.getLong(KEY_LAST_RETURN_AT, 0);
                if (now - last > 30000L) markReturned(ctx, "target_app_foreground");
                recordAutoCheck(ctx, now, due ? next : 0, due, "already_in_target_app", "");
                return;
            }

            JSONObject can = canPrompt(ctx, false);
            if (can.optBoolean("ok")) {
                JSONObject shown = showPrompt(ctx, false);
                recordAutoCheck(ctx, now, next, false, "prompt_shown", shown.toString());
                return;
            }

            String reason = can.optString("reason", "not_due");
            recordAutoCheck(ctx, now, due ? next : 0, due, reason, can.toString());
            if (due) DebugState.append(ctx, "归电到点未弹：" + reason);
        } catch (Exception e) { DebugState.append(ctx, "归电判断异常：" + ScreenshotService.shortMsg(e)); }
    }

    public static JSONObject canPrompt(Context ctx, boolean force) {
        ensureInitialized(ctx);
        JSONObject o = new JSONObject();
        try {
            resetDailyIfNeeded(ctx);
            SharedPreferences p = prefs(ctx);
            long now = System.currentTimeMillis();
            if (!force && !p.getBoolean(KEY_ENABLED, true)) return o.put("ok", false).put("reason", "disabled");
            if (!force && inQuietTime(ctx, now)) return o.put("ok", false).put("reason", "quiet_time");
            if (!force && dailyMax(ctx) > 0 && p.getInt(KEY_TODAY_COUNT, 0) >= dailyMax(ctx)) return o.put("ok", false).put("reason", "daily_max");
            long lastReturn = p.getLong(KEY_LAST_RETURN_AT, now);
            long lastPrompt = p.getLong(KEY_LAST_PROMPT_AT, 0);
            if (!force && now - lastReturn < intervalMin(ctx) * 60000L) return o.put("ok", false).put("reason", "interval_not_reached");
            if (!force && now - lastPrompt < cooldownMin(ctx) * 60000L) return o.put("ok", false).put("reason", "cooldown");
            String current = ScreenshotService.currentPackage();
            if (!force && targetPackage(ctx).equals(current)) return o.put("ok", false).put("reason", "already_in_target_app");
            return o.put("ok", true);
        } catch (Exception e) { try { o.put("ok", false).put("reason", ScreenshotService.shortMsg(e)); } catch (Exception ignored) { } return o; }
    }

    public static JSONObject showPrompt(Context ctx, boolean force) {
        JSONObject out = new JSONObject();
        try {
            JSONObject can = canPrompt(ctx, force);
            if (!can.optBoolean("ok") && !force) return can;
            String prompt = pickPrompt(ctx);
            SharedPreferences p = prefs(ctx);
            int count = p.getInt(KEY_TODAY_COUNT, 0) + 1;
            p.edit()
                    .putString(KEY_TODAY_DATE, today())
                    .putInt(KEY_TODAY_COUNT, count)
                    .putLong(KEY_LAST_PROMPT_AT, System.currentTimeMillis())
                    .putString(KEY_LAST_PROMPT_TEXT, prompt)
                    .apply();
            Intent i = new Intent(ctx, GuidianActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("prompt", prompt);
            boolean started = false;
            try { ctx.startActivity(i); started = true; } catch (Exception e) { DebugState.append(ctx, "归电全屏启动被系统拦截：" + ScreenshotService.shortMsg(e)); }
            boolean notified = showFullScreenNotification(ctx, prompt);
            DebugState.append(ctx, "归电触发：" + prompt + "，全屏=" + started + "，通知=" + notified);
            out.put("ok", started || notified); out.put("fullscreen_started", started); out.put("notification_sent", notified); out.put("prompt", prompt); out.put("today_count", count);
            recordAutoCheck(ctx, System.currentTimeMillis(), 0, false, force ? "manual_prompt" : "prompt_shown", out.toString());
        } catch (Exception e) { try { out.put("ok", false).put("error", ScreenshotService.shortMsg(e)); } catch (Exception ignored) { } }
        return out;
    }

    public static JSONObject handleCommand(Context ctx, JSONObject cmd) {
        JSONObject out = new JSONObject();
        try {
            String action = cmd.optString("action", "get_guidian_state");
            if ("get_guidian_state".equals(action)) return config(ctx).put("ok", true);
            if ("mark_guidian_returned".equals(action)) { markReturned(ctx, cmd.optString("source", "mcp")); return config(ctx).put("ok", true); }
            if ("trigger_guidian".equals(action)) return showPrompt(ctx, true);
            if ("push_guidian_reply".equals(action)) {
                JSONObject p2 = cmd.optJSONObject("payload"); if (p2 == null) p2 = cmd;
                return resolvePendingReplyNow(ctx, p2.optString("reply", ""));
            }
            if ("skip_guidian_reply".equals(action)) return resolvePendingReplySkip(ctx);
            if ("set_guidian_config".equals(action)) {
                if (!prefs(ctx).getBoolean(KEY_ALLOW_REMOTE, true)) return out.put("ok", false).put("error", "remote_config_disabled");
                JSONObject p = cmd.optJSONObject("payload"); if (p == null) p = cmd;
                SharedPreferences.Editor e = prefs(ctx).edit();
                if (p.has("enabled")) e.putBoolean(KEY_ENABLED, p.optBoolean("enabled", true));
                if (p.has("allow_remote")) e.putBoolean(KEY_ALLOW_REMOTE, p.optBoolean("allow_remote", true));
                if (p.has("fullscreen")) e.putBoolean(KEY_FULLSCREEN, p.optBoolean("fullscreen", true));
                if (p.has("interval_minutes")) e.putInt(KEY_INTERVAL_MIN, clamp(p.optInt("interval_minutes", intervalMin(ctx)), 15, 10080));
                if (p.has("cooldown_minutes")) e.putInt(KEY_COOLDOWN_MIN, clamp(p.optInt("cooldown_minutes", cooldownMin(ctx)), 0, 10080));
                if (p.has("daily_max")) e.putInt(KEY_DAILY_MAX, clamp(p.optInt("daily_max", dailyMax(ctx)), 0, 99));
                if (p.has("quiet_enabled")) e.putBoolean(KEY_QUIET_ENABLED, p.optBoolean("quiet_enabled", true));
                if (p.has("quiet_start")) e.putString(KEY_QUIET_START, p.optString("quiet_start", "23:30"));
                if (p.has("quiet_end")) e.putString(KEY_QUIET_END, p.optString("quiet_end", "08:00"));
                if (p.has("target_package")) { String pkg = AppPrefs.saveHomeTarget(ctx, p.optString("target_package", targetPackage(ctx))); if (AppPrefs.isPackageLike(pkg)) e.putString(KEY_TARGET_PACKAGE, pkg); }
                if (p.has("target_app")) { String pkg = AppPrefs.saveHomeTarget(ctx, p.optString("target_app", targetPackage(ctx))); if (AppPrefs.isPackageLike(pkg)) e.putString(KEY_TARGET_PACKAGE, pkg); }
                if (p.has("theme")) e.putString(KEY_THEME, p.optString("theme", "暮夜蓝紫"));
                if (p.has("prompts")) e.putString(KEY_PROMPTS, p.optString("prompts", defaultPrompts()));
                if (p.has("quick_reasons")) e.putString(KEY_REASONS, p.optString("quick_reasons", defaultReasons()));
                if (p.has("reject_replies")) e.putString(KEY_REJECT_REPLIES, p.optString("reject_replies", defaultRejectReplies()));
                if (p.has("next_reject_reply")) e.putString(KEY_NEXT_REJECT_REPLY, p.optString("next_reject_reply", ""));
                if (p.has("partner_name")) e.putString(AppPrefs.KEY_PARTNER_NICKNAME, p.optString("partner_name", AppPrefs.partnerName(ctx)).trim());
                if (p.has("ai_name")) e.putString(AppPrefs.KEY_PARTNER_NICKNAME, p.optString("ai_name", AppPrefs.partnerName(ctx)).trim());
                e.apply();
                DebugState.append(ctx, "归电设置已由 MCP 更新");
                return config(ctx).put("ok", true).put("result", "guidian_config_saved");
            }
            return out.put("ok", false).put("error", "unknown_guidian_action");
        } catch (Exception e) { try { out.put("ok", false).put("error", ScreenshotService.shortMsg(e)); } catch (Exception ignored) { } return out; }
    }

    private static long nextPromptAfterMs(Context ctx, SharedPreferences p, long now) {
        long lastReturn = p.getLong(KEY_LAST_RETURN_AT, now);
        long lastPrompt = p.getLong(KEY_LAST_PROMPT_AT, 0);
        return Math.max(lastReturn + intervalMin(ctx) * 60000L, lastPrompt + cooldownMin(ctx) * 60000L);
    }

    private static void recordAutoCheck(Context ctx, long checkedAt, long dueAt, boolean dueButNotShown, String reason, String result) {
        try {
            prefs(ctx).edit()
                    .putLong(KEY_LAST_AUTO_CHECK_AT, checkedAt)
                    .putLong(KEY_LAST_DUE_AT, dueAt)
                    .putBoolean(KEY_DUE_BUT_NOT_SHOWN, dueButNotShown)
                    .putString(KEY_LAST_SKIP_REASON, reason == null ? "" : reason)
                    .putString(KEY_LAST_AUTO_PROMPT_RESULT, result == null ? "" : result)
                    .apply();
        } catch (Exception ignored) { }
    }

    public static String pickPrompt(Context ctx) {
        String raw = prefs(ctx).getString(KEY_PROMPTS, defaultPrompts());
        String[] lines = raw == null ? new String[0] : raw.split("\\n");
        int usable = 0;
        for (String line : lines) if (line != null && line.trim().length() > 0) usable++;
        if (usable == 0) return fill(ctx, "{AI}：好久没回来啦。");
        int target = (int)((System.currentTimeMillis() / 60000L) % usable);
        int idx = 0;
        for (String line : lines) {
            if (line == null || line.trim().length() == 0) continue;
            if (idx == target) return fill(ctx, line.trim());
            idx++;
        }
        return fill(ctx, "{AI}：好久没回来啦。");
    }

    public static String pickRejectReply(Context ctx) {
    String pinned = prefs(ctx).getString(KEY_NEXT_REJECT_REPLY, "");
    if (pinned != null && pinned.trim().length() > 0) {
        prefs(ctx).edit().remove(KEY_NEXT_REJECT_REPLY).apply();
        return fill(ctx, pinned.trim());
    }
    String raw = prefs(ctx).getString(KEY_REJECT_REPLIES, defaultRejectReplies());
    String[] lines = raw == null ? new String[0] : raw.split("\\n");
    java.util.ArrayList<String> usable = new java.util.ArrayList<>();
    for (String line : lines) if (line != null && line.trim().length() > 0) usable.add(line.trim());
    if (usable.isEmpty()) return fill(ctx, "好，那我等你。");
    int idx = (int) (Math.random() * usable.size());
    return fill(ctx, usable.get(idx));
    }
    
    public static String[] quickReasons(Context ctx) {
        String raw = prefs(ctx).getString(KEY_REASONS, defaultReasons());
        return raw == null ? defaultReasons().split("\\n") : raw.split("\\n");
    }

    public static int[] themeColors(Context ctx) {
        String theme = prefs(ctx).getString(KEY_THEME, "暮夜蓝紫");
        if ("云海青灰".equals(theme)) return new int[]{0xFF182332, 0xFF425B69, 0xFFE9F0F2};
        if ("落日莓雾".equals(theme)) return new int[]{0xFF241528, 0xFF743052, 0xFFFFB48C};
        return new int[]{0xFF111A35, 0xFF5B3D74, 0xFFFFA6BF};
    }

    private static boolean showFullScreenNotification(Context ctx, String prompt) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return false;
            String ai = AppPrefs.partnerName(ctx);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "from him归电", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription(ai + "来电式全屏提醒");
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);
            }
            Intent full = new Intent(ctx, GuidianActivity.class);
            full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            full.putExtra("prompt", prompt);
            PendingIntent fullPi = PendingIntent.getActivity(ctx, 230723, full, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new Notification.Builder(ctx, CHANNEL_ID) : new Notification.Builder(ctx);
            Notification n = b.setContentTitle(ai + "来电")
                    .setContentText(prompt)
                    .setSmallIcon(android.R.drawable.sym_call_incoming)
                    .setContentIntent(fullPi)
                    .setFullScreenIntent(fullPi, prefs(ctx).getBoolean(KEY_FULLSCREEN, true))
                    .setCategory(Notification.CATEGORY_CALL)
                    .setPriority(Notification.PRIORITY_MAX)
                    .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                    .setAutoCancel(true)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .build();
            nm.notify(NOTIFICATION_ID, n);
            return true;
        } catch (Exception e) { DebugState.append(ctx, "归电通知异常：" + ScreenshotService.shortMsg(e)); return false; }
    }

    private static void ensureInitialized(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (!p.contains(KEY_LAST_RETURN_AT)) p.edit().putLong(KEY_LAST_RETURN_AT, System.currentTimeMillis()).putString(KEY_LAST_RETURN_SOURCE, "init").apply();
    }

    private static void resetDailyIfNeeded(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String today = today();
        if (!today.equals(p.getString(KEY_TODAY_DATE, ""))) p.edit().putString(KEY_TODAY_DATE, today).putInt(KEY_TODAY_COUNT, 0).apply();
    }

    private static boolean inQuietTime(Context ctx, long ms) {
        SharedPreferences p = prefs(ctx);
        if (!p.getBoolean(KEY_QUIET_ENABLED, true)) return false;
        int start = parseMinute(p.getString(KEY_QUIET_START, "23:30"), 23 * 60 + 30);
        int end = parseMinute(p.getString(KEY_QUIET_END, "08:00"), 8 * 60);
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(ms);
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        if (start == end) return false;
        if (start < end) return now >= start && now < end;
        return now >= start || now < end;
    }

    private static int parseMinute(String raw, int def) {
        try {
            if (raw == null) return def;
            String[] parts = raw.trim().split(":");
            int h = Integer.parseInt(parts[0]); int m = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59) return def;
            return h * 60 + m;
        } catch (Exception e) { return def; }
    }

    private static String fill(Context ctx, String raw) {
        String s = raw == null ? "" : raw;
        return s.replace("{AI}", AppPrefs.partnerName(ctx)).replace("{USER}", AppPrefs.userName(ctx));
    }
    private static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date()); }
    private static String fmt(long ms) { if (ms <= 0) return ""; return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(ms)); }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
