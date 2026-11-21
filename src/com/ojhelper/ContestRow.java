package com.ojhelper;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 一行比赛卡片，比赛页与收藏页共用。 */
final class ContestRow {

    interface OnFavChanged {
        void changed();
    }

    static View build(MainActivity act, Store store, M.Contest ct, OnFavChanged cb) {
        return build(act, store, ct, cb, false);
    }

    /**
     * @param dateMode 按日期分组时省略日期（分组标题已经给出），只显示 19:00-21:00。
     */
    static View build(final MainActivity act, final Store store, final M.Contest ct,
                      final OnFavChanged cb, boolean dateMode) {
        LinearLayout row = Ui.row(act);
        row.setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 10));

        LinearLayout left = Ui.col(act);
        row.addView(left, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (dateMode) {
            // 平台OJ - 比赛名称
            LinearLayout t = Ui.row(act);
            t.addView(Ui.badge(act, M.ojShort(ct.oj), M.ojColor(ct.oj)));
            TextView title = Ui.tv(act, ct.title, 14.5f, Ui.TEXT, false);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tp = Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tp.leftMargin = Ui.dp(act, 6);
            t.addView(title, tp);
            left.addView(t);
        } else {
            TextView title = Ui.tv(act, ct.title, 14.5f, Ui.TEXT, false);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            left.addView(title);
        }

        String span = M.hm(ct.start) + "-" + M.hm(ct.end) + " · " + M.duration(ct.end - ct.start);
        TextView sub = Ui.tv(act, dateMode ? span : (M.dayLabel(ct.start) + " " + span),
                12f, Ui.SUB, false);
        sub.setPadding(0, Ui.dp(act, 3), 0, 0);
        left.addView(sub);

        if (ct.running(System.currentTimeMillis())) {
            TextView run = Ui.tv(act, "进行中", 11f, Ui.GREEN, true);
            run.setPadding(0, Ui.dp(act, 3), 0, 0);
            left.addView(run);
        }

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmOpen(act, ct);
            }
        });

        final TextView star = Ui.tv(act, store.isFav(ct.key()) ? "★" : "☆", 21f,
                store.isFav(ct.key()) ? 0xFFF59E0B : Ui.LINE, false);
        star.setPadding(Ui.dp(act, 10), Ui.dp(act, 2), Ui.dp(act, 6), Ui.dp(act, 2));
        star.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean was = store.isFav(ct.key());
                if (was) {
                    store.removeFav(ct.key());
                    star.setText("☆");
                    star.setTextColor(Ui.LINE);
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
        row.addView(cal, cp);

        return row;
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