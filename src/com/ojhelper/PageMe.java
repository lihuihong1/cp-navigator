package com.ojhelper;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** 我的页：各 OJ 手填用户名，查解题数与排位分；数据缓存在本地。 */
final class PageMe {

    private final MainActivity act;
    private final Store store;
    final LinearLayout view;
    private final LinearLayout body;
    private final List<Row> rows = new ArrayList<Row>();
    private boolean allBusy;

    private static final class Row {
        String oj;
        EditText input;
        TextView value;
        TextView meta;
        TextView btn;
    }

    PageMe(MainActivity a, Store s) {
        act = a;
        store = s;

        view = Ui.col(act);
        view.setBackgroundColor(Ui.BG);

        LinearLayout head = Ui.col(act);
        head.setBackgroundColor(Ui.CARD);
        head.setPadding(Ui.dp(act, 16), Ui.dp(act, 14), Ui.dp(act, 16), Ui.dp(act, 10));

        LinearLayout titleRow = Ui.row(act);
        titleRow.addView(Ui.tv(act, "我的", 21f, Ui.TEXT, true),
                Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView stats = Ui.tv(act, "题解统计", 13f, Ui.ACCENT, true);
        stats.setBackground(Ui.stroke(0x00000000, Ui.ACCENT, 8, 1, act));
        stats.setPadding(Ui.dp(act, 12), Ui.dp(act, 5), Ui.dp(act, 12), Ui.dp(act, 5));
        stats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStats();
            }
        });
        titleRow.addView(stats, Ui.margins(Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT), act, 0, 0, 8, 0));

        TextView all = Ui.tv(act, "全部查询", 13f, Ui.ACCENT, true);
        all.setBackground(Ui.stroke(0x00000000, Ui.ACCENT, 8, 1, act));
        all.setPadding(Ui.dp(act, 12), Ui.dp(act, 5), Ui.dp(act, 12), Ui.dp(act, 5));
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                queryAll();
            }
        });
        titleRow.addView(all);
        head.addView(titleRow);

        TextView sub = Ui.tv(act, "只需填各站公开的用户名 / id，不需要登录", 12f, Ui.SUB, false);
        sub.setPadding(0, Ui.dp(act, 4), 0, 0);
        head.addView(sub);
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

        buildRows();
        buildAbout();
    }

    void onShow() {
        // 每次进入只重画数值，不改输入框内容
        for (Row r : rows) paint(r, store.loadAcc(r.oj));
    }

    // ---------------- 构建 ----------------

    private void buildRows() {
        for (final String oj : M.STAT_OJS) {
            final Row row = new Row();
            row.oj = oj;

            LinearLayout card = Ui.card(act, 0);
            LinearLayout.LayoutParams lp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(act, 10);
            card.setLayoutParams(lp);

            LinearLayout top = Ui.row(act);
            top.setPadding(Ui.dp(act, 14), Ui.dp(act, 11), Ui.dp(act, 12), Ui.dp(act, 2));
            android.widget.ImageView mark = Ui.logo(act, oj, 20);
            LinearLayout.LayoutParams mp = Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            mp.rightMargin = Ui.dp(act, 8);
            mp.gravity = android.view.Gravity.CENTER_VERTICAL;
            top.addView(mark != null ? (View) mark
                    : Ui.badge(act, M.ojShort(oj), M.ojColor(oj)), mp);
            TextView name = Ui.tv(act, M.ojName(oj), 13f, Ui.TEXT, true);
            top.addView(name, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            row.btn = Ui.tv(act, "查询", 12f, Ui.ACCENT, true);
            row.btn.setBackground(Ui.stroke(0x00000000, Ui.ACCENT, 7, 1, act));
            row.btn.setPadding(Ui.dp(act, 10), Ui.dp(act, 4), Ui.dp(act, 10), Ui.dp(act, 4));
            row.btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    query(row);
                }
            });
            top.addView(row.btn);
            card.addView(top);

            LinearLayout mid = Ui.col(act);
            mid.setPadding(Ui.dp(act, 14), Ui.dp(act, 6), Ui.dp(act, 14), Ui.dp(act, 2));
            row.input = new EditText(act);
            row.input.setTextSize(13f);
            row.input.setSingleLine(true);
            row.input.setHint(M.idHint(oj));
            row.input.setHintTextColor(0xFFA5ABB5);
            row.input.setTextColor(Ui.TEXT);
            row.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            row.input.setBackground(Ui.round(Ui.BG, 8, act));
            row.input.setPadding(Ui.dp(act, 10), Ui.dp(act, 7), Ui.dp(act, 10), Ui.dp(act, 7));
            String saved = store.loadAcc(oj).handle;
            if (saved != null && !saved.isEmpty()) row.input.setText(saved);
            mid.addView(row.input, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            card.addView(mid);

            LinearLayout bottom = Ui.col(act);
            bottom.setPadding(Ui.dp(act, 14), Ui.dp(act, 6), Ui.dp(act, 14), Ui.dp(act, 10));
            row.value = Ui.tv(act, "", 13f, Ui.TEXT, false);
            bottom.addView(row.value);
            row.meta = Ui.tv(act, "", 11f, Ui.SUB, false);
            row.meta.setPadding(0, Ui.dp(act, 2), 0, 0);
            bottom.addView(row.meta);
            card.addView(bottom);

            body.addView(card);
            rows.add(row);
            paint(row, store.loadAcc(oj));
        }
    }

    private void buildAbout() {
        LinearLayout card = Ui.card(act, 14);
        card.setLayoutParams(Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        card.addView(Ui.tv(act, "关于", 13f, Ui.TEXT, true));
        TextView v = Ui.tv(act, "版本 " + M.VERSION + " (" + M.VERSION_CODE + ")\n编译于 "
                + M.BUILD_TIME, 12f, Ui.SUB, false);
        v.setPadding(0, Ui.dp(act, 5), 0, 0);
        card.addView(v);

        TextView note = Ui.tv(act, "所有数据（收藏、解题数、rating）只存在本机应用私有目录，"
                + "不参与云备份，卸载即清空，不支持迁移。", 11f, Ui.SUB, false);
        note.setPadding(0, Ui.dp(act, 8), 0, 0);
        note.setLineSpacing(Ui.dp(act, 2), 1f);
        card.addView(note);

        TextView clear = Ui.button(act, "清空本地数据", Ui.RED, false);
        LinearLayout.LayoutParams clp = Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(act, 12);
        clear.setLayoutParams(clp);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClear();
            }
        });
        card.addView(clear);
        body.addView(card);
    }

    // ---------------- 查询 ----------------

    private void query(final Row row) {
        final String h = row.input.getText().toString().trim();
        if (h.isEmpty()) {
            row.input.setError("请先填写");
            Ui.toast(act, "信息不全");
            return;
        }
        row.btn.setText("查询中");
        row.btn.setTextColor(Ui.SUB);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final Store.Acc acc = store.loadAcc(row.oj);
                acc.handle = h;
                try {
                    M.Stat st = StatsFetch.fetch(row.oj, h);
                    acc.solved = st.solved;
                    acc.rating = st.rating;
                    acc.extra = st.extra == null ? "" : st.extra;
                    acc.updated = System.currentTimeMillis();
                    acc.lastError = "";
                } catch (M.OjError e) {
                    acc.lastError = e.userText();
                } catch (Exception e) {
                    acc.lastError = "接口失效 · " + e.getClass().getSimpleName();
                }
                store.saveAcc(acc);
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        row.btn.setText("查询");
                        row.btn.setTextColor(Ui.ACCENT);
                        paint(row, acc);
                    }
                });
            }
        }, "stat-" + row.oj).start();
    }

    private void queryAll() {
        if (allBusy) return;
        final List<Row> todo = new ArrayList<Row>();
        for (Row r : rows) {
            if (!r.input.getText().toString().trim().isEmpty()) todo.add(r);
        }
        if (todo.isEmpty()) {
            Ui.toast(act, "还没有填写任何用户名");
            return;
        }
        allBusy = true;
        Ui.toast(act, "正在依次查询 " + todo.size() + " 个站点…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (Row r : todo) {
                    final String h = r.input.getText().toString().trim();
                    final Store.Acc acc = store.loadAcc(r.oj);
                    acc.handle = h;
                    try {
                        M.Stat st = StatsFetch.fetch(r.oj, h);
                        acc.solved = st.solved;
                        acc.rating = st.rating;
                        acc.extra = st.extra == null ? "" : st.extra;
                        acc.updated = System.currentTimeMillis();
                        acc.lastError = "";
                    } catch (M.OjError e) {
                        acc.lastError = e.userText();
                    } catch (Exception e) {
                        acc.lastError = "接口失效 · " + e.getClass().getSimpleName();
                    }
                    store.saveAcc(acc);
                    act.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            paint(r, acc);
                        }
                    });
                }
                allBusy = false;
            }
        }, "stat-all").start();
    }

    // ---------------- 渲染 ----------------

    private void paint(Row row, Store.Acc a) {
        String solved = a.solved == null ? "—" : String.valueOf(a.solved.intValue());
        String rating = a.rating == null ? "—" : trim(a.rating.doubleValue());
        row.value.setText("解题 " + solved + "　·　rating " + rating);
        row.value.setTextColor(a.lastError.isEmpty() ? Ui.TEXT : 0xFF9CA3AF);

        StringBuilder meta = new StringBuilder();
        if (!a.lastError.isEmpty()) {
            meta.append(a.lastError);
        } else if (a.updated > 0) {
            meta.append("更新于 ").append(M.fmt("MM-dd HH:mm").format(new Date(a.updated)));
        }
        if (!a.extra.isEmpty()) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(a.extra);
        }
        row.meta.setText(meta.toString());
        row.meta.setTextColor(a.lastError.isEmpty() ? Ui.SUB : Ui.RED);
    }

    private static String trim(double d) {
        if (d == Math.floor(d)) return String.valueOf((long) d);
        return String.valueOf(Math.round(d * 10) / 10.0);
    }

    private void confirmClear() {
        new android.app.AlertDialog.Builder(act)
                .setTitle("清空本地数据？")
                .setMessage("收藏和已保存的解题数 / rating 都会被删除，无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Reminder.cancelAll(act, store);
                        store.clearAll();
                        for (Row r : rows) {
                            r.input.setText("");
                            paint(r, store.loadAcc(r.oj));
                        }
                        Ui.toast(act, "已清空");
                        act.refreshFavBadge();
                    }
                }).show();
    }

    // ---------------- 题解统计（饼图） ----------------

    /** 按本地缓存的解题数出饼图；解题数为 0 / 没查过的 OJ 不参与统计。 */
    private void showStats() {
        List<Integer> cols = new ArrayList<Integer>();
        List<String> names = new ArrayList<String>();
        List<Integer> vals = new ArrayList<Integer>();
        int total = 0;
        for (String oj : M.STAT_OJS) {
            Store.Acc a = store.loadAcc(oj);
            if (a == null || a.solved == null || a.solved.intValue() <= 0) continue;
            cols.add(Integer.valueOf(M.ojColor(oj)));
            names.add(M.ojShort(oj));
            vals.add(a.solved);
            total += a.solved.intValue();
        }

        if (vals.isEmpty()) {
            new AlertDialog.Builder(act)
                    .setTitle("题解统计")
                    .setMessage("暂无数据：先在上面查一次各站的解题数，再回来看统计。")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }

        int n = vals.size();
        int[] colors = new int[n];
        String[] labels = new String[n];
        int[] values = new int[n];
        for (int i = 0; i < n; i++) {
            colors[i] = cols.get(i).intValue();
            labels[i] = names.get(i);
            values[i] = vals.get(i).intValue();
        }

        LinearLayout box = Ui.col(act);
        int p = Ui.dp(act, 16);
        box.setPadding(p, Ui.dp(act, 4), p, 0);
        box.addView(Ui.tv(act, "解题统计", 17f, Ui.TEXT, true));

        TextView sum = Ui.tv(act, "总计：" + total + " 题", 15f, 0xFF16A34A, true);
        sum.setGravity(Gravity.CENTER);
        sum.setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 2));
        box.addView(sum);

        box.addView(new PieChart(act, colors, labels, values),
                Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(act, 236)));
        box.addView(legend(colors, labels, values, total));

        new AlertDialog.Builder(act).setView(box).setPositiveButton("关闭", null).show();
    }

    /** 图例：两列，色点 + 名称 + 题数 + 占比。太小的扇区在饼里不写标签，靠这里认。 */
    private LinearLayout legend(int[] colors, String[] labels, int[] values, int total) {
        LinearLayout wrap = Ui.col(act);
        wrap.setPadding(0, Ui.dp(act, 12), 0, 0);
        LinearLayout row = null;
        for (int i = 0; i < labels.length; i++) {
            if (i % 2 == 0) {
                row = Ui.row(act);
                wrap.addView(row, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout cell = Ui.row(act);
            // 每个格子里的「色点 + 文字」整体居中，图例看起来才是水平居中的
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, Ui.dp(act, 3), 0, Ui.dp(act, 3));
            View dot = new View(act);
            dot.setBackground(Ui.round(colors[i], 5, act));
            cell.addView(dot, Ui.margins(Ui.lp(Ui.dp(act, 10), Ui.dp(act, 10)), act, 0, 0, 6, 0));
            String pct = String.format(java.util.Locale.CHINA, "%.1f%%", 100.0 * values[i] / total);
            TextView t = Ui.tv(act, labels[i] + " " + values[i] + " · " + pct, 12f, Ui.SUB, false);
            cell.addView(t);
            row.addView(cell, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        return wrap;
    }
}
