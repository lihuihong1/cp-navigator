package com.ojhelper;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** 比赛页：每次进入都重新抓取，结果不落盘。 */
final class PageContests {

    private final MainActivity act;
    private final Store store;

    final LinearLayout view;
    private final TextView subtitle;
    private final LinearLayout body;
    private final TextView btnRefresh;

    private boolean busy;
    private int seq;

    PageContests(MainActivity a, Store s) {
        act = a;
        store = s;

        view = Ui.col(act);
        view.setBackgroundColor(Ui.BG);

        LinearLayout head = Ui.col(act);
        head.setBackgroundColor(Ui.CARD);
        head.setPadding(Ui.dp(act, 16), Ui.dp(act, 14), Ui.dp(act, 14), Ui.dp(act, 10));

        LinearLayout titleRow = Ui.row(act);
        TextView title = Ui.tv(act, "近期比赛", 21f, Ui.TEXT, true);
        titleRow.addView(title, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        btnRefresh = Ui.tv(act, "刷新", 13f, Ui.ACCENT, true);
        btnRefresh.setBackground(Ui.stroke(0x00000000, Ui.ACCENT, 8, 1, act));
        btnRefresh.setPadding(Ui.dp(act, 12), Ui.dp(act, 5), Ui.dp(act, 12), Ui.dp(act, 5));
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        titleRow.addView(btnRefresh);
        head.addView(titleRow);

        subtitle = Ui.tv(act, M.windowText() + " · 牛客 / 力扣 / CF / 洛谷", 12f, Ui.SUB, false);
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
        refresh();
    }

    private void refresh() {
        if (busy) return;
        busy = true;
        final int my = ++seq;
        btnRefresh.setText("查询中");
        btnRefresh.setTextColor(Ui.SUB);
        subtitle.setText("正在查询 4 个源…");

        body.removeAllViews();
        for (String oj : M.CONTEST_OJS) body.addView(skeleton(oj));

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<ContestFetch.Result> rs = ContestFetch.fetchAll();
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (my != seq) return;
                        busy = false;
                        btnRefresh.setText("刷新");
                        btnRefresh.setTextColor(Ui.ACCENT);
                        render(rs);
                    }
                });
            }
        }, "fetch-contests").start();
    }

    private View skeleton(String oj) {
        LinearLayout card = Ui.card(act, 0);
        LinearLayout.LayoutParams lp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(act, 10);
        card.setLayoutParams(lp);

        LinearLayout head = Ui.row(act);
        head.setPadding(Ui.dp(act, 14), Ui.dp(act, 11), Ui.dp(act, 14), Ui.dp(act, 11));
        head.addView(Ui.badge(act, M.ojShort(oj), M.ojColor(oj)));
        TextView name = Ui.tv(act, "  正在查询…", 12f, Ui.SUB, false);
        head.addView(name, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(head);
        return card;
    }

    private void render(List<ContestFetch.Result> rs) {
        body.removeAllViews();
        long now = System.currentTimeMillis();
        int total = 0;
        for (ContestFetch.Result r : rs) total += r.list.size();

        SimpleDateFormat hm = M.fmt("HH:mm:ss");
        subtitle.setText("共 " + total + " 场 · " + M.windowText() + " · "
                + hm.format(new Date()) + " 更新");

        if (total == 0) {
            LinearLayout empty = Ui.card(act, 18);
            LinearLayout.LayoutParams elp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            elp.bottomMargin = Ui.dp(act, 10);
            empty.setLayoutParams(elp);
            TextView t = Ui.tv(act, "未来 7 天还没有已公布的比赛", 14f, Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            empty.addView(t);
            TextView s = Ui.tv(act, "比赛通常提前 1~2 周公布，可以过几天再来看看", 12f, Ui.SUB, false);
            s.setGravity(Gravity.CENTER);
            s.setPadding(0, Ui.dp(act, 4), 0, 0);
            empty.addView(s);
            body.addView(empty);
        }

        for (ContestFetch.Result r : rs) {
            body.addView(section(r, now));
        }

        TextView note = Ui.tv(act, "数据实时抓取自各站公开页面；站点改版可能导致某一路失效，"
                + "此时可按「刷新」重试。", 11f, Ui.SUB, false);
        note.setPadding(Ui.dp(act, 4), Ui.dp(act, 6), Ui.dp(act, 4), Ui.dp(act, 16));
        body.addView(note);
    }

    private View section(final ContestFetch.Result r, long now) {
        LinearLayout card = Ui.card(act, 0);
        LinearLayout.LayoutParams lp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(act, 10);
        card.setLayoutParams(lp);

        LinearLayout head = Ui.row(act);
        head.setPadding(Ui.dp(act, 14), Ui.dp(act, 11), Ui.dp(act, 14), Ui.dp(act, 9));
        head.addView(Ui.badge(act, M.ojShort(r.oj), M.ojColor(r.oj)));
        TextView name = Ui.tv(act, "  " + M.ojName(r.oj), 13f, Ui.TEXT, true);
        head.addView(name, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView cnt = Ui.tv(act, r.err != null ? "失败" : (r.list.size() + " 场"), 12f,
                r.err != null ? Ui.RED : Ui.SUB, false);
        head.addView(cnt);
        card.addView(head);
        card.addView(Ui.divider(act));

        if (r.err != null) {
            TextView e = Ui.tv(act, r.err.userText(), 12f, Ui.RED, false);
            e.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 13));
            card.addView(e);
            return card;
        }

        if (r.list.isEmpty()) {
            TextView e = Ui.tv(act, "7 天内没有已公布的比赛", 12f, Ui.SUB, false);
            e.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 13));
            card.addView(e);
            return card;
        }

        for (int i = 0; i < r.list.size(); i++) {
            View row = ContestRow.build(act, store, r.list.get(i), null);
            LinearLayout wrap = Ui.col(act);
            wrap.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 10), 0);
            wrap.addView(row);
            card.addView(wrap);
            if (i < r.list.size() - 1) {
                View d = Ui.divider(act);
                LinearLayout dw = Ui.col(act);
                dw.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 14), 0);
                dw.addView(d);
                card.addView(dw);
            }
        }
        View bottom = new View(act);
        bottom.setLayoutParams(Ui.lp(1, Ui.dp(act, 6)));
        card.addView(bottom);
        return card;
    }
}