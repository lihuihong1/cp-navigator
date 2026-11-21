package com.ojhelper;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 一行比赛卡片，比赛页与收藏页共用。 */
final class ContestRow {

    /** 未收藏的星星：原来用卡片描边色，几乎看不见，改成中灰。 */
    private static final int STAR_OFF = 0xFF9AA1AC;

    interface OnFavChanged {
        void changed();
    }

    static View build(MainActivity act, Store store, M.Contest ct, OnFavChanged cb) {
        return build(act, store, ct, cb, false, false);
    }

    static View build(final MainActivity act, final Store store, final M.Contest ct,
                      final OnFavChanged cb, boolean dateMode) {
        return build(act, store, ct, cb, dateMode, dateMode);
    }

    /**
     * @param dateMode 按日期分组时省略日期（分组标题已经给出），只显示 19:00-21:00。
     * @param showLogo 行首是否画站点 logo（按平台视图里分组标题已写了 OJ 名，就不必重复）。
     */
    static View build(final MainActivity act, final Store store, final M.Contest ct,
                      final OnFavChanged cb, boolean dateMode, boolean showLogo) {
        LinearLayout row = Ui.row(act);
        row.setPadding(0, Ui.dp(act, 11), 0, Ui.dp(act, 11));

        // 左：站点 logo。按平台视图里分组标题已经写了 OJ 名，行内不再重复；
        // 按日期视图里同一个卡里混着各家，logo 就是"这家是谁"的唯一标识。
        if (showLogo) {
            ImageView logo = Ui.logo(act, ct.oj, 32);
            if (logo != null) {
                LinearLayout.LayoutParams lp =
                        (LinearLayout.LayoutParams) logo.getLayoutParams();
                lp.rightMargin = Ui.dp(act, 10);
                lp.gravity = android.view.Gravity.CENTER_VERTICAL;
                logo.setLayoutParams(lp);
                row.addView(logo);
            } else {
                TextView b = Ui.badge(act, M.ojShort(ct.oj), M.ojColor(ct.oj));
                LinearLayout.LayoutParams lp =
                        (LinearLayout.LayoutParams) b.getLayoutParams();
                if (lp == null) lp = Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = Ui.dp(act, 8);
                lp.gravity = android.view.Gravity.CENTER_VERTICAL;
                b.setLayoutParams(lp);
                row.addView(b);
            }
        }

        LinearLayout left = Ui.col(act);
        row.addView(left, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = Ui.tv(act, ct.title, 14.5f, Ui.TEXT, false);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        left.addView(title);

        String span = M.hm(ct.start) + "-" + M.hm(ct.end) + " · " + M.duration(ct.end - ct.start);
        TextView sub = Ui.tv(act, dateMode ? span : (M.dayLabel(ct.start) + " " + span),
                12f, Ui.SUB, false);
        sub.setPadding(0, Ui.dp(act, 3), 0, 0);
        left.addView(sub);

        long nw = System.currentTimeMillis();
        if (ct.running(nw)) {
            TextView run = Ui.tv(act, "进行中", 11f, Ui.GREEN, true);
            run.setPadding(0, Ui.dp(act, 3), 0, 0);
            left.addView(run);
        } else if (ct.finished(nw)) {
            TextView over = Ui.tv(act, "已结束", 11f, Ui.SUB, false);
            over.setPadding(0, Ui.dp(act, 3), 0, 0);
            left.addView(over);
        }

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmOpen(act, ct);
            }
        });

        final TextView star = Ui.tv(act, store.isFav(ct.key()) ? "★" : "☆", 20f,
                store.isFav(ct.key()) ? 0xFFF59E0B : STAR_OFF, false);
        star.setPadding(Ui.dp(act, 8), Ui.dp(act, 2), Ui.dp(act, 4), Ui.dp(act, 2));
        star.setGravity(android.view.Gravity.CENTER_VERTICAL);
        star.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean was = store.isFav(ct.key());
                if (was) {
                    store.removeFav(ct.key());
                    star.setText("☆");
                    star.setTextColor(STAR_OFF);
                } else {
                    store.addFav(ct);
                    star.setText("★");
                    star.setTextColor(0xFFF59E0B);
                    Ui.toast(act, "已收藏");
                }
                if (cb != null) cb.changed();
            }
        });
        row.addView(star);

        // 铃铛：开赛提醒（已设置时变蓝）
        final ImageView bell = new ImageView(act);
        final int on = R.drawable.ic_bell_on;
        final int off = R.drawable.ic_bell_off;
        bell.setImageResource(store.remindLead(ct.key()) >= 0 ? on : off);
        track(ct.key(), bell);
        bell.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bell.setContentDescription("开赛提醒");
        LinearLayout.LayoutParams bp = Ui.lp(Ui.dp(act, 22), Ui.dp(act, 22));
        bp.leftMargin = Ui.dp(act, 6);
        bp.gravity = android.view.Gravity.CENTER_VERTICAL;
        bell.setLayoutParams(bp);
        final Runnable syncBell = new Runnable() {
            @Override
            public void run() {
                bell.setImageResource(store.remindLead(ct.key()) >= 0 ? on : off);
            }
        };
        bell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Reminder.pick(act, store, ct, syncBell);
            }
        });
        row.addView(bell);

        final TextView cal = Ui.tv(act, "日历", 12f, Ui.ACCENT, true);
        cal.setBackground(Ui.stroke(0x00000000, Ui.ACCENT, 7, 1, act));
        cal.setPadding(Ui.dp(act, 8), Ui.dp(act, 4), Ui.dp(act, 8), Ui.dp(act, 4));
        cal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Cal.add(act, ct);
            }
        });
        LinearLayout.LayoutParams cp = Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = Ui.dp(act, 4);
        cp.gravity = android.view.Gravity.CENTER_VERTICAL;
        row.addView(cal, cp);

        return row;
    }

    // ---------------- 铃铛状态同步 ----------------
    // 提醒响过之后库里那条会被删掉，但屏幕上已经画好的铃铛不会自己变。
    // 这里登记每一行的铃铛，回到前台时统一按库里的状态刷一遍（不重建列表、不丢滚动位置）。

    private static final java.util.List<Object[]> BELLS = new java.util.ArrayList<Object[]>();

    private static void track(String key, ImageView bell) {
        // 顺手清掉已经被回收的旧条目，避免越攒越多
        for (int i = BELLS.size() - 1; i >= 0; i--) {
            if (((java.lang.ref.WeakReference<?>) BELLS.get(i)[1]).get() == null) BELLS.remove(i);
        }
        BELLS.add(new Object[]{key, new java.lang.ref.WeakReference<ImageView>(bell)});
    }

    static void syncBells(Store store) {
        for (int i = 0; i < BELLS.size(); i++) {
            Object[] o = BELLS.get(i);
            ImageView v = (ImageView) ((java.lang.ref.WeakReference<?>) o[1]).get();
            if (v == null) continue;
            boolean on = store.remindLead((String) o[0]) >= 0;
            v.setImageResource(on ? R.drawable.ic_bell_on : R.drawable.ic_bell_off);
        }
    }

    /** 点比赛先确认，避免误触跳走。 */
    private static void confirmOpen(final MainActivity act, final M.Contest ct) {
        new AlertDialog.Builder(act)
                .setTitle("访问比赛页面？")
                .setMessage(ct.title + "\n\n" + ct.url)
                .setNegativeButton("取消", null)
                .setPositiveButton("访问", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        try {
                            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(ct.url));
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            act.startActivity(i);
                        } catch (Exception e) {
                            Ui.toast(act, "无法打开链接");
                        }
                    }
                })
                .show();
    }
}