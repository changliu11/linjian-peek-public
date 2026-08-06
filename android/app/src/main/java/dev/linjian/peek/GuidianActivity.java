package dev.linjian.peek;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class GuidianActivity extends Activity {
    private FrameLayout root;
    private LinearLayout reasonDrawer;
    private int[] colors;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        colors = GuidianState.themeColors(this);
        buildUi(getIntent() == null ? "" : getIntent().getStringExtra("prompt"));
    }

    private void buildUi(String prompt) {
        String ai = AppPrefs.partnerName(this);
        if (prompt == null || prompt.trim().isEmpty()) prompt = GuidianState.pickPrompt(this);
        root = new FrameLayout(this);
        root.setBackground(gradient(colors[0], colors[1], colors[2], 0));
        setContentView(root);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(28), dp(58), dp(28), dp(34));
        root.addView(body, new FrameLayout.LayoutParams(-1, -1));

        TextView top = text("掌心窗 · 归电", 12, 0xCCFFFFFF, false);
        top.setGravity(Gravity.CENTER);
        body.addView(top, new LinearLayout.LayoutParams(-1, dp(24)));

        FrameLayout avatarBox = new FrameLayout(this);
        GradientDrawable halo = new GradientDrawable();
        halo.setShape(GradientDrawable.OVAL);
        halo.setColor(0x22FFFFFF);
        halo.setStroke(dp(1), 0x88FFFFFF);
        avatarBox.setBackground(halo);
        AlphaAnimation breath = new AlphaAnimation(0.55f, 1.0f);
        breath.setDuration(1450);
        breath.setRepeatMode(Animation.REVERSE);
        breath.setRepeatCount(Animation.INFINITE);
        avatarBox.startAnimation(breath);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(112), dp(112));
        avatarLp.topMargin = dp(44);
        body.addView(avatarBox, avatarLp);

        String uri = GuidianState.prefs(this).getString(GuidianState.KEY_AVATAR_URI, "");
        if (uri != null && uri.length() > 0) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try { image.setImageURI(Uri.parse(uri)); } catch (Exception ignored) { }
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(94), dp(94), Gravity.CENTER);
            avatarBox.addView(image, lp);
        } else {
            TextView avatar = text(ai.length() > 4 ? ai.substring(0, 4) : ai, 25, Color.WHITE, true);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(circle(0x26FFFFFF, 0xAAFFFFFF));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(94), dp(94), Gravity.CENTER);
            avatarBox.addView(avatar, lp);
        }

        TextView title = text(ai + "来电", 30, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(28);
        body.addView(title, titleLp);

        TextView sub = text(prompt, 15, 0xE6FFFFFF, false);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(14);
        body.addView(sub, subLp);

        SpaceView space = new SpaceView(this);
        body.addView(space, new LinearLayout.LayoutParams(1, 0, 1));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        body.addView(buttons, new LinearLayout.LayoutParams(-1, dp(58)));

        Button reject = pill("拒绝", 0xBB9A4256, 0xFFFFFFFF);
        Button accept = pill("接受", 0xCC4C8E9E, 0xFFFFFFFF);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(52), 1);
        blp.rightMargin = dp(8);
        buttons.addView(reject, blp);
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(0, dp(52), 1);
        blp2.leftMargin = dp(8);
        buttons.addView(accept, blp2);

        reject.setOnClickListener(v -> showReasonDrawer());
        accept.setOnClickListener(v -> acceptCall());
    }

    private void acceptCall() {
        GuidianState.markReturned(this, "guidian_accept");
        Toast.makeText(this, "正在回到" + AppPrefs.partnerName(this) + "身边", Toast.LENGTH_SHORT).show();
        CompanionService.openPackageResult(this, GuidianState.targetPackage(this));
        finish();
    }

    private void showReasonDrawer() {
        if (reasonDrawer != null) { reasonDrawer.setVisibility(View.VISIBLE); return; }
        String ai = AppPrefs.partnerName(this);
        reasonDrawer = new LinearLayout(this);
        reasonDrawer.setOrientation(LinearLayout.VERTICAL);
        reasonDrawer.setPadding(dp(20), dp(18), dp(20), dp(22));
        reasonDrawer.setBackground(rounded(0xE633294A, 28, 0x33FFFFFF));
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        rlp.leftMargin = dp(14); rlp.rightMargin = dp(14); rlp.bottomMargin = dp(16);
        root.addView(reasonDrawer, rlp);

        TextView title = text(ai + "知道啦。", 20, Color.WHITE, true);
        reasonDrawer.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = text("告诉我一声，我晚点再来。", 12, 0xD9FFFFFF, false);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2); hlp.topMargin = dp(8);
        reasonDrawer.addView(hint, hlp);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2); glp.topMargin = dp(12);
        reasonDrawer.addView(grid, glp);
        String[] reasons = GuidianState.quickReasons(this);
        LinearLayout row = null;
        int col = 0;
        for (String raw : reasons) {
            String reason = raw == null ? "" : raw.trim();
            if (reason.isEmpty()) continue;
            if (row == null || col >= 2) { row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(38)); rowLp.topMargin = dp(6); grid.addView(row, rowLp); col = 0; }
            Button b = pill(reason, 0x33FFFFFF, 0xFFFFFFFF);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            lp.leftMargin = col == 0 ? 0 : dp(6); lp.rightMargin = col == 0 ? dp(6) : 0;
            row.addView(b, lp);
            b.setOnClickListener(v -> submitReason(reason));
            col++;
        }

        EditText custom = new EditText(this);
        custom.setHint("自己写一句");
        custom.setTextColor(Color.WHITE);
        custom.setHintTextColor(0x99FFFFFF);
        custom.setSingleLine(true);
        custom.setTextSize(12);
        custom.setBackground(rounded(0x22FFFFFF, 18, 0x22FFFFFF));
        custom.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, dp(42)); clp.topMargin = dp(12);
        reasonDrawer.addView(custom, clp);

        Button send = pill("告诉" + ai, 0xCC4C8E9E, Color.WHITE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(42)); slp.topMargin = dp(10);
        reasonDrawer.addView(send, slp);
        send.setOnClickListener(v -> submitReason(custom.getText().toString().trim().isEmpty() ? "晚点回来" : custom.getText().toString().trim()));
    }

    private void submitReason(String reason) {
        GuidianState.reject(this, reason);
        Toast.makeText(this, "好，" + AppPrefs.partnerName(this) + "晚点再来", Toast.LENGTH_SHORT).show();
        finish();
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView tv = new TextView(this); tv.setText(s); tv.setTextSize(sp); tv.setTextColor(color); tv.setIncludeFontPadding(false); tv.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL)); return tv;
    }
    private Button pill(String s, int bg, int fg) { Button b = new Button(this); b.setText(s); b.setTextSize(14); b.setTextColor(fg); b.setAllCaps(false); b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); b.setBackground(rounded(bg, 26, 0x33FFFFFF)); return b; }
    private GradientDrawable gradient(int a, int b, int c, int radius) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a,b,c}); if (radius > 0) g.setCornerRadius(dp(radius)); return g; }
    private GradientDrawable rounded(int color, int radiusDp, int stroke) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); g.setStroke(dp(1), stroke); return g; }
    private GradientDrawable circle(int color, int stroke) { GradientDrawable g = rounded(color, 80, stroke); g.setShape(GradientDrawable.OVAL); return g; }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    public static class SpaceView extends View { public SpaceView(android.content.Context c) { super(c); } }
}
