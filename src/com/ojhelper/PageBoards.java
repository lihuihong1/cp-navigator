package com.ojhelper;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 榜单页：只有两个榜单站点入口（XCPCIO / RankLand），点开用应用内 WebView 看。 */
final class PageBoards {

    private final MainActivity act;
    final LinearLayout view;

    PageBoards(MainActivity a) {
        act = a;

        view = Ui.col(act);
        view.setBackgroundColor(Ui.BG);

        LinearLayout head = Ui.row(act);
        head.setBackgroundColor(Ui.CARD);
        head.setPadding(Ui.dp(act, 16), Ui.dp(act, 14), Ui.dp(act, 16), Ui.dp(act, 12));
        head.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = Ui.col(act);
        texts.addView(Ui.tv(act, "榜单", 21f, Ui.TEXT, true));
        TextView subtitle = Ui.tv(act, "ICPC / CCPC 等赛事榜单 · 应用内打开", 12f, Ui.SUB, false);
        subtitle.setPadding(0, Ui.dp(act, 4), 0, 0);
        texts.addView(subtitle);
        head.addView(texts, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        view.addView(head);
        view.addView(Ui.divider(act));

        ScrollView sv = new ScrollView(act);
        sv.setFillViewport(true);
        LinearLayout body = Ui.col(act);
        int p = Ui.dp(act, 12);
        body.setPadding(p, p, p, p);
        sv.addView(body, new android.widget.FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        view.addView(sv, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        body.addView(card("XCPCIO 榜单", "ICPC / CCPC 实时榜与历史榜",
                BoardFetch.XCPCIO, "XCPCIO 榜单"));

        LinearLayout gap = Ui.col(act);
        gap.setPadding(0, Ui.dp(act, 10), 0, 0);
        body.addView(gap);

        body.addView(card("RankLand", "400+ 赛事榜单合集",
                BoardFetch.RANKLAND, "RankLand"));

        TextView tip = Ui.tv(act, "XCPCIO 单场榜单较重，慢时可点右上角「浏览器」。",
                11.5f, Ui.SUB, false);
        tip.setPadding(Ui.dp(act, 4), Ui.dp(act, 10), Ui.dp(act, 4), 0);
        body.addView(tip);
    }

    void onShow() {
    }

    private View card(String name, String desc, final String url, final String title) {
        LinearLayout card = Ui.card(act, 0);
        card.setLayoutParams(Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout row = Ui.row(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(act, 14), Ui.dp(act, 13), Ui.dp(act, 14), Ui.dp(act, 13));

        LinearLayout texts = Ui.col(act);
        texts.addView(Ui.tv(act, name, 15f, Ui.TEXT, true));
        TextView d = Ui.tv(act, desc, 12f, Ui.SUB, false);
        d.setPadding(0, Ui.dp(act, 3), 0, 0);
        texts.addView(d);
        row.addView(texts, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(Ui.tv(act, "›", 20f, Ui.SUB, false));

        row.setBackgroundColor(Ui.CARD);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BoardView.open(act, title, url);
            }
        });
        card.addView(row);
        return card;
    }
}