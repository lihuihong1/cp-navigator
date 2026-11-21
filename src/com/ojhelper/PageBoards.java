package com.ojhelper;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

/** 榜单页：两个榜单站点入口 + RankLand 最近的榜单列表（点开应用内看）。 */
final class PageBoards {

    private final MainActivity act;
    final LinearLayout view;
    private final LinearLayout body;
    private final TextView subtitle;
    private int seq = 0;

    PageBoards(MainActivity a) {
        act = a;

        view = Ui.col(act);
        view.setBackgroundColor(Ui.BG);

        LinearLayout head = Ui.row(act);
        head.setBackgroundColor(Ui.CARD);
        head.setPadding(Ui.dp(act, 16), Ui.dp(act, 14), Ui.dp(act, 16), Ui.dp(act, 10));
        head.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = Ui.col(act);
        texts.addView(Ui.tv(act, "榜单", 21f, Ui.TEXT, true));
        subtitle = Ui.tv(act, "ICPC / CCPC 等赛事榜单 · 应用内打开", 12f, Ui.SUB, false);
        subtitle.setPadding(0, Ui.dp(act, 4), 0, 0);
        texts.addView(subtitle);
        head.addView(texts, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView refresh = Ui.button(act, "刷新", Ui.ACCENT, false);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        });
        head.addView(refresh);

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

        renderSites();
        load();
    }

    void onShow() {
    }

    // ---------------- 站点入口 ----------------

    private void renderSites() {
        LinearLayout card = Ui.card(act, 0);
        card.setLayoutParams(Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        card.addView(nativeIcp());
        card.addView(pad());
        card.addView(Ui.divider(act));
        card.addView(site("XCPCIO 榜单", "board.xcpcio.com · ICPC / CCPC 实时榜与历史榜",
                BoardFetch.XCPCIO, "XCPCIO 榜单"));
        card.addView(pad());
        card.addView(Ui.divider(act));
        card.addView(site("RankLand", "rl.algoux.cn · 400+ 赛事榜单合集",
                BoardFetch.RANKLAND, "RankLand"));

        body.addView(card);

        TextView tip = Ui.tv(act,
                "XCPCIO 的单场榜单是桌面级页面：进入时要多拉 0.5 MB 脚本（原始 1.4 MB，含导出用的 ExcelJS）"
                        + "和近 1 MB 榜单数据，手机首次打开常常要十几秒。等不及可以在页面里点右上角「浏览器」，"
                        + "改用系统浏览器打开。RankLand 的榜单轻得多。",
                11.5f, Ui.SUB, false);
        tip.setPadding(Ui.dp(act, 4), Ui.dp(act, 8), Ui.dp(act, 4), 0);
        body.addView(tip);
    }

    /** 原生 ICPC 榜单入口：自己拉 JSON、自己算排名，不用打开网页。 */
    private View nativeIcp() {
        LinearLayout row = Ui.col(act);
        row.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12));
        row.setBackgroundColor(0xFFEFF6FF);

        LinearLayout line = Ui.row(act);
        line.addView(Ui.tv(act, "ICPC 原生榜单", 15f, Ui.ACCENT, true));
        TextView mark = Ui.tv(act, " 推荐", 10.5f, 0xFFFFFFFF, true);
        mark.setBackgroundColor(Ui.ACCENT);
        mark.setPadding(Ui.dp(act, 5), Ui.dp(act, 1), Ui.dp(act, 5), Ui.dp(act, 1));
        mark.setLayoutParams(Ui.margins(Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT), act, 6, 1, 0, 0));
        line.addView(mark);
        row.addView(line);

        TextView d = Ui.tv(act, "直连 XCPCIO 数据 · 区域赛 / CCPC / 省赛 482 场 · 原生表格，秒开",
                12f, Ui.SUB, false);
        d.setPadding(0, Ui.dp(act, 3), 0, 0);
        row.addView(d);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.startActivity(new android.content.Intent(act, IcpList.class));
            }
        });
        return row;
    }

    private View pad() {
        LinearLayout w = Ui.col(act);
        w.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 14), 0);
        return w;
    }

    private View site(String name, String desc, final String url, final String title) {
        LinearLayout row = Ui.col(act);
        row.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12));
        row.addView(Ui.tv(act, name, 15f, Ui.TEXT, true));
        TextView d = Ui.tv(act, desc, 12f, Ui.SUB, false);
        d.setPadding(0, Ui.dp(act, 3), 0, 0);
        row.addView(d);
        row.setBackgroundColor(Ui.CARD);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BoardView.open(act, title, url);
            }
        });
        return row;
    }

    // ---------------- RankLand 列表 ----------------

    private void load() {
        final int my = ++seq;
        setState("正在获取 RankLand 榜单…", Ui.SUB);
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<BoardFetch.Board> list = null;
                M.OjError err = null;
                try {
                    list = BoardFetch.rankland();
                } catch (M.OjError e) {
                    err = e;
                }
                run_(my, list, err);
            }
        }, "boards").start();
    }

    private void run_(final int my, final List<BoardFetch.Board> list, final M.OjError err) {
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (my != seq) return;
                if (err != null) {
                    renderError(err);
                } else {
                    renderList(list);
                }
            }
        });
    }

    private TextView state;

    /** 结果区（列表/错误共用一个容器）。 */
    private LinearLayout listBox;

    private void setState(String text, int color) {
        listBox = Ui.col(act);
        listBox.setPadding(0, Ui.dp(act, 8), 0, 0);
        LinearLayout card = Ui.card(act, 16);
        card.setLayoutParams(Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        state = Ui.tv(act, text, 13f, color, false);
        state.setGravity(Gravity.CENTER);
        card.addView(state);
        listBox.addView(card);
        replace(listBox);
    }

    private void renderError(M.OjError e) {
        listBox = Ui.col(act);
        listBox.setPadding(0, Ui.dp(act, 8), 0, 0);
        LinearLayout card = Ui.card(act, 16);
        card.setLayoutParams(Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView t = Ui.tv(act, "榜单列表获取失败", 14f, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        card.addView(t);
        TextView s = Ui.tv(act, e.userText(), 12f, Ui.RED, false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, Ui.dp(act, 4), 0, 0);
        card.addView(s);
        TextView retry = Ui.button(act, "重试", Ui.ACCENT, false);
        LinearLayout.LayoutParams rp = Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = Ui.dp(act, 10);
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(retry, rp);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        });
        listBox.addView(card);
        replace(listBox);
    }

    private void renderList(List<BoardFetch.Board> list) {
        listBox = Ui.col(act);
        LinearLayout titleRow = Ui.row(act);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(Ui.tv(act, "RankLand 最近榜单", 14f, Ui.TEXT, true),
                Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(Ui.tv(act, list.size() + " 个", 12f, Ui.SUB, false));
        LinearLayout.LayoutParams tp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = Ui.dp(act, 12);
        tp.bottomMargin = Ui.dp(act, 8);
        listBox.addView(titleRow, tp);

        LinearLayout card = Ui.card(act, 0);
        card.setLayoutParams(Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < list.size(); i++) {
            final BoardFetch.Board b = list.get(i);
            LinearLayout row = Ui.col(act);
            row.setPadding(Ui.dp(act, 14), Ui.dp(act, 11), Ui.dp(act, 14), Ui.dp(act, 11));
            TextView name = Ui.tv(act, b.name, 14f, Ui.TEXT, false);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name);

            StringBuilder sub = new StringBuilder();
            if (b.startAt > 0) {
                sub.append(M.fmt("yyyy-MM-dd").format(new Date(b.startAt)))
                        .append(" ").append(M.weekOf(b.startAt));
            } else {
                sub.append("时间未知");
            }
            if (b.views > 0) sub.append(" · 浏览 ").append(b.views);
            TextView meta = Ui.tv(act, sub.toString(), 12f, Ui.SUB, false);
            meta.setPadding(0, Ui.dp(act, 3), 0, 0);
            row.addView(meta);

            row.setBackgroundColor(Ui.CARD);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    BoardView.open(act, b.name, b.url());
                }
            });
            card.addView(row);
            if (i < list.size() - 1) {
                LinearLayout dw = Ui.col(act);
                dw.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 14), 0);
                dw.addView(Ui.divider(act));
                card.addView(dw);
            }
        }
        listBox.addView(card);
        replace(listBox);
    }

    /** 用新块替换上一次的结果块（保留站点入口卡）。 */
    private View current;

    private void replace(View v) {
        if (current != null) body.removeView(current);
        current = v;
        body.addView(v);
    }
}