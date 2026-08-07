package dev.linjian.peek;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 悬浮横幅提醒：所有 send_notification / 主动提醒统一走这里。 */
public class ReminderActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String title = getIntent().getStringExtra("title");
        String message = getIntent().getStringExtra("message");
        if (title == null || title.trim().isEmpty()) title = "from him提醒";
        if (message == null || message.trim().isEmpty()) message = AppPrefs.userName(this) + "，看一眼这里。";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(22);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xFFFFF8FB);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(24);
        titleView.setTextColor(0xFF2F403B);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(null, 1);
        root.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        TextView msgView = new TextView(this);
        msgView.setText(message);
        msgView.setTextSize(17);
        msgView.setTextColor(0xFF4F625C);
        msgView.setGravity(Gravity.CENTER);
        msgView.setLineSpacing(dp(4), 1.0f);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.setMargins(0, dp(16), 0, dp(18));
        root.addView(msgView, mp);

        Button home = new Button(this);
        home.setText(AppPrefs.returnButtonText(this));
        home.setTextSize(16);
        home.setOnClickListener(v -> { CompanionService.openPackageResult(this, AppPrefs.homeTargetPackage(this)); finish(); });
        root.addView(home, new LinearLayout.LayoutParams(-1, dp(48)));

        Button later = new Button(this);
        later.setText("等会儿");
        later.setTextSize(15);
        later.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.setMargins(0, dp(10), 0, 0);
        root.addView(later, lp);

        setContentView(root);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
