package com.ojhelper;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 比赛页：进入页面不自动查询，必须点右上角「查询」；结果只留在内存，不落盘。
 */
final class PageContests {

    private final MainActivity act;
    private final Store store;

    final LinearLayout view;
    private final TextView subtitle;
    private final LinearLayout body;
    private final TextView btnRefresh;

    private boolean busy;
    private int seq;

    /** 上次查询结果（仅内存），null 表示本进程还没查过。 */
    private List<ContestFetch.Result> last;
    private long lastAt;

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

        btnRefresh = Ui.tv(act, "查询", 13f, Ui.ACCENT, true);
        btnRefresh.setBackground(Ui.stroke(0x00000000, Ui.ACCENT, 8, 1, act));
        btnRefresh.setPadding(Ui.dp(act, 14), Ui.dp(act, 5), Ui.dp(act, 14), Ui.dp(act, 5));
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

    /** 进入页面：不联网。有上次结果就重新显示（带时间戳），否则显示引导。 */
    void onShow() {
        if (busy || last == null) {
            if (!busy) intro();
            return;
        }
        render(last, lastAt);
    }

    private void intro() {
        body.removeAllViews();
        subtitle.setText(M.windowText() + " · 牛客 / 力扣 / CF / 洛谷");

        LinearLayout card = Ui.card(act, 20);
        LinearLayout.LayoutParams lp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(act, 10);
        card.setLayoutParams(lp);

        TextView t = Ui.tv(act, "点右上角「查询」", 15f, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        card.addView(t);

        TextView s = Ui.tv(act, "会实时访问牛客、力扣、Codeforces、洛谷的公开页面，\n"
                + "列出 " + M.windowText() + " 之间未开始和正在进行的比赛。", 12f, Ui.SUB, false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, Ui.dp(act, 6), 0, 0);
        s.setLineSpacing(Ui.dp(act, 3), 1f);
        card.addView(s);

        TextView n = Ui.tv(act, "查询结果不落盘，退出应用即清空；收藏和成绩另存本地。", 11f, Ui.SUB, false);
        n.setGravity(Gravity.CENTER);
        n.setPadding(0, Ui.dp(act, 10), 0, 0);
        card.addView(n);

        body.addView(card);
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
                        btnRefresh.setText("查询");
                        btnRefresh.setTextColor(Ui.ACCENT);
                        last = rs;
                        lastAt = System.currentTimeMillis();
                        render(rs, lastAt);
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

    private void render(List<ContestFetch.Result> rs, long at) {
        body.removeAllViews();
        long now = System.currentTimeMillis();
        int total = 0;
        for (ContestFetch.Result r : rs) total += r.list.size();

        SimpleDateFormat hm = M.fmt("HH:mm:ss");
        subtitle.setText("共 " + total + " 场 · " + M.windowText() + " · "
                + hm.format(new Date(at)) + " 查询");

        // 结果放久了给个提醒：比赛可能已经开始了
        long ageMin = (now - at) / 60000L;
        if (ageMin >= 15) {
            LinearLayout tip = Ui.card(act, 10);
            LinearLayout.LayoutParams tlp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            tlp.bottomMargin = Ui.dp(act, 10);
            tip.setLayoutParams(tlp);
            String when = ageMin < 60 ? (ageMin + " 分钟") : (ageMin / 60 + " 小时");
            TextView tv = Ui.tv(act, "这是 " + hm.format(new Date(at)) + " 的快照，已过 " + when
                    + "，可能已经过期，点「查询」更新。", 11.5f, 0xFFB45309, false);
            tv.setLineSpacing(Ui.dp(act, 2), 1f);
            tip.addView(tv);
            body.addView(tip);
        }

        if (total == 0) {
            LinearLayout empty = Ui.card(act, 18);
            LinearLayout.LayoutParams elp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            elp.bottomMargin = Ui.dp(act, 10);
            empty.setLayoutParams(elp);
            TextView t = Ui.tv(act, "未来 7 天还没有已公布的比赛", 14f, Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            empty.addView(t);
            TextView s = Ui.tv(act, "比赛通常提前 1~2 周公布，可以过几天再查一次", 12f, Ui.SUB, false);
            s.setGravity(Gravity.CENTER);
            s.setPadding(0, Ui.dp(act, 4), 0, 0);
            empty.addView(s);
            body.addView(empty);
        }

        for (ContestFetch.Result r : rs) {
            body.addView(section(r, now));
        }

        TextView note = Ui.tv(act, "数据实时抓取自各站公开页面；站点改版可能导致某一路失效，"
                + "此时可按「查询」重试。", 11f, Ui.SUB, false);
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