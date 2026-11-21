package com.ojhelper;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 单 Activity + 三个页签。 */
public class MainActivity extends Activity {

    private Store store;
    private FrameLayout content;
    private final TextView[] tabs = new TextView[4];
    private final View[] pages = new View[4];
    private PageContests pc;
    private PageFavs pf;
    private PageMe pm;
    private PageBoards pb;
    private int cur = -1;

    private static final String[] TAB_NAME = {"比赛", "收藏", "榜单", "我的"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Net.init();
        store = new Store(this);

        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Ui.CARD);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);

        content = new FrameLayout(this);
        root.addView(content, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(Ui.divider(this));
        root.addView(buildTabs());

        setContentView(root);
        switchTo(0, true);
    }

    private View buildTabs() {
        LinearLayout bar = Ui.row(this);
        bar.setBackgroundColor(Ui.CARD);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView t = Ui.tv(this, TAB_NAME[i], 13f, Ui.SUB, false);
            t.setGravity(android.view.Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 13), 0, Ui.dp(this, 13));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchTo(idx, false);
                }
            });
            tabs[i] = t;
            bar.addView(t, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        return bar;
    }

    private void switchTo(int i, boolean force) {
        if (i == cur && !force) return;
        cur = i;

        if (pages[i] == null) {
            if (i == 0) {
                if (pc == null) pc = new PageContests(this, store);
                pages[i] = pc.view;
            } else if (i == 1) {
                if (pf == null) pf = new PageFavs(this, store);
                pages[i] = pf.view;
            } else if (i == 2) {
                if (pb == null) pb = new PageBoards(this);
                pages[i] = pb.view;
            } else {
                if (pm == null) pm = new PageMe(this, store);
                pages[i] = pm.view;
            }
        }

        content.removeAllViews();
        content.addView(pages[i], new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        for (int k = 0; k < 4; k++) {
            boolean on = k == i;
            tabs[k].setTextColor(on ? Ui.ACCENT : Ui.SUB);
            tabs[k].setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
        }
        refreshFavBadge();

        if (i == 0) pc.onShow();
        else if (i == 1) pf.onShow();
        else if (i == 2) pb.onShow();
        else pm.onShow();
    }

    void refreshFavBadge() {
        if (tabs[1] == null || store == null) return;
        // 导航栏只显示「收藏」，不显示条数（收藏数在页面头部可见）
        tabs[1].setText(TAB_NAME[1]);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && cur != 0) {
            switchTo(0, false);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (store != null) store.close();
    }
}