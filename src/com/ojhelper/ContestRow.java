package com.ojhelper;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 一行比赛卡片，比赛页与收藏页共用。 */
final class ContestRow {

    interface OnFavChanged {
        void changed();
    }

    static View build(final MainActivity act, final Store store, final M.Contest ct,
                      final OnFavChanged cb) {
        LinearLayout row = Ui.row(act);
        row.setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 10));

        LinearLayout left = Ui.col(act);
        row.addView(left, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = Ui.tv(act, ct.title, 14.5f, Ui.TEXT, false);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        left.addView(title);

        long now = System.currentTimeMillis();
        String time = M.stampShort(ct.start) + " · " + M.duration(ct.end - ct.start);
        TextView sub = Ui.tv(act, time, 12f, Ui.SUB, false);
        sub.setPadding(0, Ui.dp(act, 3), 0, 0);
        left.addView(sub);

        if (ct.running(now)) {
            TextView run = Ui.tv(act, "进行中", 11f, Ui.GREEN, true);
            run.setPadding(0, Ui.dp(act, 3), 0, 0);
            left.addView(run);
        }

        // 整块可点：打开比赛页面
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(ct.url));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    act.startActivity(i);
                } catch (Exception e) {
                    Ui.toast(act, "无法打开链接");
                }
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
}