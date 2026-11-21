package com.ojhelper;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 比赛页：进入页面不自动查询，必须点右上角「查询」；结果只留在内存，不落盘。
 * 支持「按平台」/「按日期」两种显示方式，查询哪些平台可配置。
 */
final class PageContests {

    private final MainActivity act;
    private final Store store;
    private final Prefs prefs;

    final LinearLayout view;
    private final TextView dateValue;
    private final TextView srcValue;
    private final TextView countLine;
    private final LinearLayout body;
    private final TextView btnQuery;
    private final TextView tabOj;
    private final TextView tabDate;
    private final TextView btnSrc;

    private boolean busy;
    private int seq;

    /** 折叠状态："oj:<oj>" / "day:<dayStart>"，只在内存里，切页回来还在。 */
    private final Set<String> collapsed = new HashSet<String>();

    /** 上次查询结果（仅内存），null 表示本进程还没查过。 */
    private List<ContestFetch.Result> last;
    private long lastAt;

    PageContests(MainActivity a, Store s) {
        act = a;
        store = s;
        prefs = new Prefs(a);

        view = Ui.col(act);
        view.setBackgroundColor(Ui.BG);

        LinearLayout head = Ui.col(act);
        head.setBackgroundColor(Ui.CARD);
        head.setPadding(Ui.dp(act, 16), Ui.dp(act, 14), Ui.dp(act, 14), Ui.dp(act, 10));

        LinearLayout titleRow = Ui.row(act);
        titleRow.addView(Ui.tv(act, "近期比赛", 21f, Ui.TEXT, true),
                Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        btnQuery = Ui.tv(act, "查询", 13f, Ui.ACCENT, true);
        btnQuery.setBackground(Ui.stroke(0x00000000, Ui.ACCENT, 8, 1, act));
        btnQuery.setPadding(Ui.dp(act, 14), Ui.dp(act, 5), Ui.dp(act, 14), Ui.dp(act, 5));
        btnQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        titleRow.addView(btnQuery);
        head.addView(titleRow);

        // 第二行：显示方式 + 平台选择
        LinearLayout modeRow = Ui.row(act);
        LinearLayout.LayoutParams mrp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        mrp.topMargin = Ui.dp(act, 10);
        modeRow.setLayoutParams(mrp);

        tabOj = modeTab("按平台");
        tabOj.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(false);
            }
        });
        modeRow.addView(tabOj);

        tabDate = modeTab("按日期");
        LinearLayout.LayoutParams tdp = Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tdp.leftMargin = Ui.dp(act, 8);
        tabDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(true);
            }
        });
        modeRow.addView(tabDate, tdp);

        modeRow.addView(new View(act), Ui.lp(0, 1, 1f));

        btnSrc = Ui.tv(act, "", 12f, Ui.SUB, false);
        btnSrc.setBackground(Ui.stroke(0x00000000, Ui.LINE, 8, 1, act));
        btnSrc.setPadding(Ui.dp(act, 10), Ui.dp(act, 5), Ui.dp(act, 10), Ui.dp(act, 5));
        btnSrc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickSources();
            }
        });
        modeRow.addView(btnSrc);
        head.addView(modeRow);

        // 第三块：日期、平台分两行显示；查询后再补一行「共 N 场 · HH:mm:ss 查询」
        LinearLayout info = Ui.col(act);
        LinearLayout.LayoutParams infoLp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = Ui.dp(act, 8);
        info.setLayoutParams(infoLp);

        dateValue = Ui.tv(act, M.windowText(), 11.5f, Ui.withAlpha(Ui.TEXT, 184), false);
        info.addView(infoRow("日期", dateValue));

        srcValue = Ui.tv(act, srcNames(), 11.5f, Ui.withAlpha(Ui.TEXT, 184), false);
        LinearLayout.LayoutParams srcLp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        srcLp.topMargin = Ui.dp(act, 3);
        info.addView(infoRow("平台", srcValue), srcLp);

        countLine = Ui.tv(act, "", 11.5f, Ui.SUB, false);
        countLine.setVisibility(View.GONE);
        countLine.setPadding(0, Ui.dp(act, 3), 0, 0);
        info.addView(countLine);

        head.addView(info);
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

        styleTabs();
        updateSrcText();
    }

    private LinearLayout infoRow(String label, TextView value) {
        LinearLayout r = Ui.row(act);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpl = Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpl.rightMargin = Ui.dp(act, 6);
        r.addView(Ui.tv(act, label, 11.5f, Ui.SUB, false), lpl);
        r.addView(value, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return r;
    }

    private TextView modeTab(String text) {
        TextView t = Ui.tv(act, text, 12.5f, Ui.SUB, false);
        t.setPadding(Ui.dp(act, 12), Ui.dp(act, 5), Ui.dp(act, 12), Ui.dp(act, 5));
        t.setClickable(true);
        return t;
    }

    /** 进入页面：不联网。有上次结果就重新显示（带时间戳），否则显示引导。 */
    void onShow() {
        if (busy) return;
        if (last == null) {
            intro();
        } else {
            render(last, lastAt);
        }
    }

    private void setMode(boolean date) {
        if (prefs.dateMode() == date) return;
        prefs.setDateMode(date);
        styleTabs();
        if (busy) return;
        if (last == null) intro();
        else render(last, lastAt);
    }

    private void styleTabs() {
        boolean date = prefs.dateMode();
        style(tabOj, !date);
        style(tabDate, date);
    }

    private void style(TextView t, boolean on) {
        if (on) {
            t.setBackground(Ui.round(Ui.withAlpha(Ui.ACCENT, 32), 8, act));
            t.setTextColor(Ui.ACCENT);
        } else {
            t.setBackground(Ui.round(0x00000000, 8, act));
            t.setTextColor(Ui.SUB);
        }
    }

    private void updateSrcText() {
        Set<String> en = prefs.enabled();
        btnSrc.setText("平台 " + en.size() + "/" + M.ALL_CONTEST.length);
        if (srcValue != null) srcValue.setText(srcNames());
    }

    /** 当前启用的源，保持固定展示顺序。 */
    private List<String> sources() {
        Set<String> en = prefs.enabled();
        List<String> out = new ArrayList<String>();
        for (String oj : M.ALL_CONTEST) if (en.contains(oj)) out.add(oj);
        return out;
    }

    private String srcNames() {
        List<String> s = sources();
        StringBuilder sb = new StringBuilder();
        for (String oj : s) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(M.ojShort(oj));
        }
        return sb.toString();
    }

    private void intro() {
        body.removeAllViews();
        dateValue.setText(M.windowText());
        srcValue.setText(srcNames());
        countLine.setVisibility(View.GONE);

        LinearLayout card = Ui.card(act, 20);
        LinearLayout.LayoutParams lp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(act, 10);
        card.setLayoutParams(lp);

        TextView t = Ui.tv(act, "暂无比赛信息", 15f, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        card.addView(t);

        TextView s = Ui.tv(act, "请点击右上角「查询」获取比赛信息。", 12f, Ui.SUB, false);
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

    // ---------------- 查询 ----------------

    private void refresh() {
        if (busy) return;
        final List<String> src = sources();
        if (src.isEmpty()) {
            Ui.toast(act, "至少选择一个平台");
            return;
        }

        busy = true;
        final int my = ++seq;
        btnQuery.setText("查询中");
        btnQuery.setTextColor(Ui.SUB);
        countLine.setText("正在查询 " + src.size() + " 个源…");
        countLine.setVisibility(View.VISIBLE);

        body.removeAllViews();
        for (String oj : src) body.addView(skeleton(oj));

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<ContestFetch.Result> rs = ContestFetch.fetchAll(src);
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (my != seq) return;
                        busy = false;
                        btnQuery.setText("查询");
                        btnQuery.setTextColor(Ui.ACCENT);
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
        head.addView(siteMark(oj, 18), markLp());
        head.addView(Ui.tv(act, "正在查询…", 12f, Ui.SUB, false),
                Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(head);
        return card;
    }

    /** 站点标识：有 logo 用 logo，没有就退回文字徽标。 */
    private View siteMark(String oj, float sizeDp) {
        android.widget.ImageView iv = Ui.logo(act, oj, sizeDp);
        return iv != null ? iv : Ui.badge(act, M.ojShort(oj), M.ojColor(oj));
    }

    private LinearLayout.LayoutParams markLp() {
        LinearLayout.LayoutParams lp = Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(act, 8);
        lp.gravity = android.view.Gravity.CENTER_VERTICAL;
        return lp;
    }

    private void render(List<ContestFetch.Result> rs, long at) {
        if (prefs.dateMode()) {
            renderByDate(rs, at);
        } else {
            renderByOj(rs, at);
        }
    }

    // ---------------- 按平台 ----------------

    private void renderByOj(List<ContestFetch.Result> rs, long at) {
        body.removeAllViews();
        long now = System.currentTimeMillis();
        int total = 0;
        int bad = 0;
        for (ContestFetch.Result r : rs) {
            total += r.list.size();
            if (r.err != null) bad++;
        }

        String stamp = new SimpleDateFormat("HH:mm:ss").format(new Date(at));
        dateValue.setText(M.windowText());
        srcValue.setText(srcNames());
        countLine.setText("共 " + total + " 场" + (bad > 0 ? "（" + bad + " 个源失败）" : "")
                + " · " + stamp + " 查询");
        countLine.setVisibility(View.VISIBLE);
        body.addView(staleTip(at, now));

        if (total == 0) body.addView(emptyCard("未来 7 天还没有已公布的比赛"));

        for (ContestFetch.Result r : rs) body.addView(section(r, now));

        body.addView(note());
    }

    private View section(final ContestFetch.Result r, long now) {
        final String key = "oj:" + r.oj;
        final boolean open = !collapsed.contains(key);
        LinearLayout card = Ui.card(act, 0);
        LinearLayout.LayoutParams lp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(act, 10);
        card.setLayoutParams(lp);

        LinearLayout head = Ui.row(act);
        head.setPadding(Ui.dp(act, 14), Ui.dp(act, 11), Ui.dp(act, 14), Ui.dp(act, 9));
        head.addView(siteMark(r.oj, 22), markLp());
        head.addView(Ui.tv(act, M.ojName(r.oj), 13f, Ui.TEXT, true),
                Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.tv(act, r.err != null ? "失败" : (r.list.size() + " 场"), 12f,
                r.err != null ? Ui.RED : Ui.SUB, false));
        addChevron(head, open);
        head.setClickable(true);
        head.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle(key);
            }
        });
        card.addView(head, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(Ui.divider(act));

        if (!open) return card;

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

        // 组内按开始时间升序：越早越靠前（同一天也是早的在前）
        List<M.Contest> sorted = M.sortByStart(r.list);
        for (int i = 0; i < sorted.size(); i++) {
            LinearLayout wrap = Ui.col(act);
            wrap.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 10), 0);
            wrap.addView(ContestRow.build(act, store, sorted.get(i), favCb(), false));
            card.addView(wrap);
            if (i < sorted.size() - 1) card.addView(insetDivider());
        }
        card.addView(spacer(6));
        return card;
    }

    // ---------------- 按日期 ----------------

    private void renderByDate(List<ContestFetch.Result> rs, long at) {
        body.removeAllViews();
        long now = System.currentTimeMillis();

        List<M.Contest> all = new ArrayList<M.Contest>();
        List<ContestFetch.Result> bad = new ArrayList<ContestFetch.Result>();
        for (ContestFetch.Result r : rs) {
            all.addAll(r.list);
            if (r.err != null) bad.add(r);
        }

        String stamp = new SimpleDateFormat("HH:mm:ss").format(new Date(at));
        dateValue.setText(M.windowText());
        srcValue.setText(srcNames());
        countLine.setText("共 " + all.size() + " 场" + (bad.isEmpty() ? "" : "（" + bad.size()
                + " 个源失败）") + " · " + stamp + " 查询");
        countLine.setVisibility(View.VISIBLE);
        body.addView(staleTip(at, now));

        long today = M.startOfToday();
        for (int d = 0; d < 7; d++) {
            long ds = today + d * M.DAY;
            long de = ds + M.DAY;
            List<M.Contest> day = new ArrayList<M.Contest>();
            for (M.Contest c : all) {
                // 按"开始时间"归日：跨零点的比赛只算在开始那天；
                // 唯一例外是昨天开始、此刻还没结束的，归到今天开头（否则它会从日期视图里消失）
                boolean carry = (d == 0 && c.start < ds && c.end > ds);
                if ((c.start >= ds && c.start < de) || carry) day.add(c);
            }
            body.addView(dayCard(ds, M.sortByStart(day), now));
        }

        if (!bad.isEmpty()) body.addView(badCard(bad));
        body.addView(note());
    }

    private View dayCard(long dayStart, List<M.Contest> list, long now) {
        final String key = "day:" + dayStart;
        final boolean open = !collapsed.contains(key);
        LinearLayout card = Ui.card(act, 0);
        LinearLayout.LayoutParams lp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(act, 10);
        card.setLayoutParams(lp);

        LinearLayout head = Ui.row(act);
        head.setPadding(Ui.dp(act, 14), Ui.dp(act, 11), Ui.dp(act, 14), Ui.dp(act, 9));
        TextView title = Ui.tv(act, M.dayFull(dayStart), 14f, Ui.TEXT, true);
        head.addView(title, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        boolean isToday = M.dayDiff(dayStart) == 0;
        head.addView(Ui.tv(act, list.size() + " 场", 12f,
                list.isEmpty() ? Ui.SUB : (isToday ? Ui.ACCENT : Ui.SUB), false));
        addChevron(head, open);
        head.setClickable(true);
        head.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle(key);
            }
        });
        card.addView(head, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(Ui.divider(act));

        if (!open) return card;

        if (list.isEmpty()) {
            TextView e = Ui.tv(act, "没有比赛", 12f, Ui.SUB, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 13));
            card.addView(e);
            return card;
        }

        for (int i = 0; i < list.size(); i++) {
            LinearLayout wrap = Ui.col(act);
            wrap.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 10), 0);
            wrap.addView(ContestRow.build(act, store, list.get(i), favCb(), true));
            card.addView(wrap);
            if (i < list.size() - 1) card.addView(insetDivider());
        }
        card.addView(spacer(6));
        return card;
    }

    private View badCard(List<ContestFetch.Result> bad) {
        LinearLayout card = Ui.card(act, 14);
        LinearLayout.LayoutParams lp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(act, 10);
        card.setLayoutParams(lp);

        card.addView(Ui.tv(act, "以下平台没查到", 12f, Ui.RED, true));
        for (ContestFetch.Result r : bad) {
            TextView t = Ui.tv(act, M.ojName(r.oj) + " · " + (r.err == null ? "失败" : r.err.userText()),
                    11.5f, Ui.SUB, false);
            t.setPadding(0, Ui.dp(act, 5), 0, 0);
            card.addView(t);
        }
        return card;
    }

    // ---------------- 公共小件 ----------------

    /** 点分组标题：收起 / 展开这一组，用内存里的上次结果重绘，不联网。 */
    private void toggle(String key) {
        if (!collapsed.remove(key)) collapsed.add(key);
        if (last != null) render(last, lastAt);
    }

    private void addChevron(LinearLayout head, boolean open) {
        TextView c = Ui.tv(act, open ? "▾" : "▸", 12f, Ui.SUB, false);
        c.setPadding(Ui.dp(act, 8), 0, 0, 0);
        head.addView(c);
    }

    private ContestRow.OnFavChanged favCb() {
        return new ContestRow.OnFavChanged() {
            @Override
            public void changed() {
                act.refreshFavBadge();
            }
        };
    }

    /** 结果放久了给个提醒：比赛可能已经开始了。 */
    private View staleTip(long at, long now) {
        LinearLayout box = Ui.col(act);
        long ageMin = (now - at) / 60000L;
        if (ageMin < 15) {
            box.setVisibility(View.GONE);
            return box;
        }
        LinearLayout tip = Ui.card(act, 10);
        LinearLayout.LayoutParams tlp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = Ui.dp(act, 10);
        tip.setLayoutParams(tlp);
        String when = ageMin < 60 ? (ageMin + " 分钟") : (ageMin / 60 + " 小时");
        String hm = new SimpleDateFormat("HH:mm:ss").format(new Date(at));
        TextView tv = Ui.tv(act, "这是 " + hm + " 的快照，已过 " + when
                + "，可能已经过期，点「查询」更新。", 11.5f, 0xFFB45309, false);
        tv.setLineSpacing(Ui.dp(act, 2), 1f);
        tip.addView(tv);
        box.addView(tip);
        return box;
    }

    private View emptyCard(String text) {
        LinearLayout empty = Ui.card(act, 18);
        LinearLayout.LayoutParams elp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        elp.bottomMargin = Ui.dp(act, 10);
        empty.setLayoutParams(elp);
        TextView t = Ui.tv(act, text, 14f, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        empty.addView(t);
        TextView s = Ui.tv(act, "比赛通常提前 1~2 周公布，可以过几天再查一次", 12f, Ui.SUB, false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, Ui.dp(act, 4), 0, 0);
        empty.addView(s);
        return empty;
    }

    private View note() {
        TextView note = Ui.tv(act, "数据实时抓取自各站公开页面；站点改版可能导致某一路失效，"
                + "此时可按「查询」重试。", 11f, Ui.SUB, false);
        note.setPadding(Ui.dp(act, 4), Ui.dp(act, 6), Ui.dp(act, 4), Ui.dp(act, 16));
        return note;
    }

    private View insetDivider() {
        LinearLayout dw = Ui.col(act);
        dw.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 14), 0);
        dw.addView(Ui.divider(act));
        return dw;
    }

    private View spacer(int dp) {
        View v = new View(act);
        v.setLayoutParams(Ui.lp(1, Ui.dp(act, dp)));
        return v;
    }

    // ---------------- 平台多选 ----------------

    private void pickSources() {
        final String[] ojs = M.ALL_CONTEST;
        final String[] names = new String[ojs.length];
        final boolean[] checked = new boolean[ojs.length];
        Set<String> en = prefs.enabled();
        for (int i = 0; i < ojs.length; i++) {
            names[i] = M.ojName(ojs[i]);
            checked[i] = en.contains(ojs[i]);
        }

        new AlertDialog.Builder(act)
                .setTitle("查询哪些平台")
                .setMultiChoiceItems(names, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("全选", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        for (int i = 0; i < checked.length; i++) checked[i] = true;
                        applySources(ojs, checked);
                    }
                })
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        applySources(ojs, checked);
                    }
                })
                .show();
    }

    private void applySources(String[] ojs, boolean[] checked) {
        Set<String> set = new HashSet<String>();
        for (int i = 0; i < ojs.length; i++) if (checked[i]) set.add(ojs[i]);
        if (set.isEmpty()) {
            Ui.toast(act, "至少选择一个平台");
            return;
        }
        prefs.setEnabled(set);
        updateSrcText();
        if (!busy) {
            // 源变了，旧结果不再代表当前配置，回到引导页
            last = null;
            intro();
        }
    }
}