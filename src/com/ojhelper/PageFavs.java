package com.ojhelper;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** 收藏页：只读本地数据库，不发网络请求。 */
final class PageFavs {

    private final MainActivity act;
    private final Store store;
    final LinearLayout view;
    private final LinearLayout body;
    private final TextView subtitle;

    PageFavs(MainActivity a, Store s) {
        act = a;
        store = s;

        view = Ui.col(act);
        view.setBackgroundColor(Ui.BG);

        LinearLayout head = Ui.col(act);
        head.setBackgroundColor(Ui.CARD);
        head.setPadding(Ui.dp(act, 16), Ui.dp(act, 14), Ui.dp(act, 16), Ui.dp(act, 10));
        head.addView(Ui.tv(act, "收藏", 21f, Ui.TEXT, true));
        subtitle = Ui.tv(act, "", 12f, Ui.SUB, false);
        subtitle.setPadding(0, Ui.dp(act, 4), 0, 0);
        head.addView(subtitle);
        view.addView(head);
        view.addView(Ui.divider(act));

        ScrollView sv = new ScrollView(act);
        sv.setFillViewport(true);
        body = Ui.col(act);
        int p = Ui.dp(act, 12);
        body.setPadding(p, p, p, p);
        sv.addView(body, new android.widget.FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        view.addView(sv, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    void onShow() {
        reload();
    }

    private void reload() {
        body.removeAllViews();
        final List<M.Contest> list = store.favorites();
        subtitle.setText("共 " + list.size() + " 场 · 只保存在本机");

        if (list.isEmpty()) {
            LinearLayout empty = Ui.card(act, 18);
            empty.setLayoutParams(Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView t = Ui.tv(act, "还没有收藏的比赛", 14f, Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            empty.addView(t);
            TextView s = Ui.tv(act, "在比赛列表点右侧的 ☆ 即可收藏", 12f, Ui.SUB, false);
            s.setGravity(Gravity.CENTER);
            s.setPadding(0, Ui.dp(act, 4), 0, 0);
            empty.addView(s);
            body.addView(empty);
            return;
        }

        LinearLayout card = Ui.card(act, 0);
        card.setLayoutParams(Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < list.size(); i++) {
            final M.Contest c = list.get(i);
            LinearLayout wrap = Ui.col(act);
            wrap.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 10), 0);
            wrap.addView(ContestRow.build(act, store, c, new ContestRow.OnFavChanged() {
                @Override
                public void changed() {
                    reload();
                }
            }, false, true));
            card.addView(wrap);
            if (i < list.size() - 1) {
                LinearLayout dw = Ui.col(act);
                dw.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 14), 0);
                dw.addView(Ui.divider(act));
                card.addView(dw);
            }
        }
        View bottom = new View(act);
        bottom.setLayoutParams(Ui.lp(1, Ui.dp(act, 6)));
        card.addView(bottom);
        body.addView(card);
    }
}