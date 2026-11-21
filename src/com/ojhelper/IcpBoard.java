package com.ojhelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Date;

/** 原生 ICPC 榜单：自己算排名，直接渲染，不加载那 1.37 MB 的网页脚本。 */
public class IcpBoard extends Activity {

    private static final String EXTRA_PATH = "path";
    private static final String EXTRA_TITLE = "title";

    private LinearLayout content;
    private TextView titleView, subView;

    static void open(Activity from, String path, String title) {
        Intent i = new Intent(from, IcpBoard.class);
        i.putExtra(EXTRA_PATH, path);
        i.putExtra(EXTRA_TITLE, title);
        from.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Ui.CARD);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout head = Ui.row(this);
        head.setBackgroundColor(Ui.CARD);
        head.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        TextView back = Ui.tv(this, "‹ 返回", 14f, Ui.ACCENT, false);
        back.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        head.addView(back);

        LinearLayout texts = Ui.col(this);
        titleView = Ui.tv(this, getIntent().getStringExtra(EXTRA_TITLE), 14f, Ui.TEXT, true);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView);
        subView = Ui.tv(this, "正在读取榜单数据…", 11f, Ui.SUB, false);
        subView.setPadding(0, Ui.dp(this, 2), 0, 0);
        texts.addView(subView);
        head.addView(texts, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(head);
        root.addView(Ui.divider(this));

        content = Ui.col(this);
        root.addView(content, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        load();
    }

    private void load() {
        final String path = getIntent().getStringExtra(EXTRA_PATH);
        TextView wait = Ui.tv(this, "正在获取 config / team / run …\n（三四个 JSON，几秒钟）",
                12.5f, Ui.SUB, false);
        wait.setGravity(Gravity.CENTER);
        wait.setPadding(0, Ui.dp(this, 40), 0, 0);
        content.removeAllViews();
        content.addView(wait);

        new Thread(new Runnable() {
            @Override
            public void run() {
                BoardData.Board r = null;
                M.OjError err = null;
                try {
                    r = BoardData.load(path);
                } catch (M.OjError e) {
                    err = e;
                }
                final BoardData.Board fr = r;
                final M.OjError fe = err;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (fe != null) {
                            showError(fe);
                        } else {
                            render(fr);
                        }
                    }
                });
            }
        }, "icpBoard").start();
    }

    private void showError(M.OjError e) {
        content.removeAllViews();
        LinearLayout card = Ui.card(this, 16);
        card.setLayoutParams(Ui.margins(Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT), this, 12, 12, 12, 0));
        TextView t = Ui.tv(this, "榜单加载失败", 14f, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        card.addView(t);
        TextView s = Ui.tv(this, e.userText(), 12f, Ui.RED, false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, Ui.dp(this, 4), 0, 0);
        card.addView(s);
        TextView retry = Ui.button(this, "重试", Ui.ACCENT, false);
        LinearLayout.LayoutParams rp = Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = Ui.dp(this, 10);
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(retry, rp);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        });
        content.addView(card);
        subView.setText("失败");
    }

    private void render(BoardData.Board b) {
        cur = b;
        titleView.setText(b.title.length() > 0 ? b.title : titleView.getText());
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(b.teams).append(" 队 · ").append(b.probs.size()).append(" 题 · 罚时 ")
                .append(b.penaltyMin).append(" 分钟/次");
        if (b.start > 0) sb.append(" · ").append(M.fmt("yyyy-MM-dd HH:mm").format(new Date(b.start)));
        if (b.frozen) sb.append(" · 封榜中");
        subView.setText(sb.toString());

        content.removeAllViews();
        if (b.rows.isEmpty()) {
            TextView t = Ui.tv(this, "这场没有队伍数据", 13f, Ui.SUB, false);
            t.setGravity(Gravity.CENTER);
            content.addView(t);
            return;
        }

        final int wRank = 44, wTeam = 196, wSolved = 38, wPen = 46, wProb = 40;
        int tableW = wRank + wTeam + wSolved + wPen + wProb * b.probs.size();

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setFillViewport(false);
        hsv.setHorizontalScrollBarEnabled(true);

        LinearLayout table = Ui.col(this);
        table.setBackgroundColor(Ui.CARD);

        LinearLayout headRow = Ui.row(this);
        headRow.setBackgroundColor(0xFFF1F5F9);
        headRow.addView(cell("名次", wRank, 11f, Ui.SUB, true));
        headRow.addView(cell("队伍 / 学校", wTeam, 11f, Ui.SUB, true));
        headRow.addView(cell("题数", wSolved, 11f, Ui.SUB, true));
        headRow.addView(cell("罚时", wPen, 11f, Ui.SUB, true));
        for (int i = 0; i < b.probs.size(); i++) {
            TextView p = cell(b.probs.get(i).label, wProb, 11.5f, Ui.TEXT, true);
            p.setBackgroundColor(b.probs.get(i).bg);
            p.setTextColor(b.probs.get(i).fg);
            headRow.addView(p);
        }
        table.addView(headRow, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 34)));

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setAdapter(new RowAdapter(b, wRank, wTeam, wSolved, wPen, wProb));
        table.addView(list, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        hsv.addView(table, new HorizontalScrollView.LayoutParams(Ui.dp(this, tableW),
                ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(hsv, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private TextView cell(String text, int wDp, float size, int color, boolean bold) {
        TextView t = Ui.tv(this, text, size, color, bold);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setLayoutParams(Ui.lp(Ui.dp(this, wDp), LinearLayout.LayoutParams.MATCH_PARENT));
        return t;
    }

    private final class RowAdapter extends BaseAdapter {
        private final BoardData.Board b;
        private final int wRank, wTeam, wSolved, wPen, wProb;

        RowAdapter(BoardData.Board b, int a, int t, int s, int p, int pr) {
            this.b = b;
            wRank = a;
            wTeam = t;
            wSolved = s;
            wPen = p;
            wProb = pr;
        }

        @Override
        public int getCount() {
            return b.rows.size();
        }

        @Override
        public Object getItem(int i) {
            return b.rows.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            Holder h;
            if (cv == null) {
                LinearLayout row = Ui.row(IcpBoard.this);
                row.setPadding(0, Ui.dp(IcpBoard.this, 4), 0, Ui.dp(IcpBoard.this, 4));
                h = new Holder();
                h.rank = cell("", wRank, 12f, Ui.SUB, true);
                row.addView(h.rank);

                LinearLayout team = Ui.col(IcpBoard.this);
                team.setPadding(Ui.dp(IcpBoard.this, 6), 0, Ui.dp(IcpBoard.this, 4), 0);
                team.setLayoutParams(Ui.lp(Ui.dp(IcpBoard.this, wTeam), LinearLayout.LayoutParams.MATCH_PARENT));
                team.setGravity(Gravity.CENTER_VERTICAL);
                h.name = Ui.tv(IcpBoard.this, "", 12.5f, Ui.TEXT, true);
                h.name.setSingleLine(true);
                h.name.setEllipsize(TextUtils.TruncateAt.END);
                team.addView(h.name);
                h.org = Ui.tv(IcpBoard.this, "", 10f, Ui.SUB, false);
                h.org.setSingleLine(true);
                h.org.setEllipsize(TextUtils.TruncateAt.END);
                team.addView(h.org);
                row.addView(team);

                h.solved = cell("", wSolved, 13f, Ui.TEXT, true);
                row.addView(h.solved);
                h.pen = cell("", wPen, 12f, Ui.SUB, false);
                row.addView(h.pen);

                h.cells = new TextView[b.probs.size()];
                for (int i = 0; i < h.cells.length; i++) {
                    TextView c = Ui.tv(IcpBoard.this, "", 9.5f, Ui.TEXT, false);
                    c.setGravity(Gravity.CENTER);
                    c.setMaxLines(2);
                    c.setLayoutParams(Ui.lp(Ui.dp(IcpBoard.this, wProb), LinearLayout.LayoutParams.MATCH_PARENT));
                    h.cells[i] = c;
                    row.addView(c);
                }
                row.setTag(h);
                cv = row;
            } else {
                h = (Holder) cv.getTag();
            }

            final BoardData.Row r = b.rows.get(pos);
            h.rank.setText(String.valueOf(r.rank));
            h.rank.setTextColor(r.rank <= 3 ? Ui.ACCENT : Ui.SUB);
            h.name.setText(r.team);
            h.org.setText(r.org);
            h.solved.setText(String.valueOf(r.solved));
            h.pen.setText(String.valueOf(r.penalty));
            for (int i = 0; i < h.cells.length; i++) {
                BoardData.Cell c = r.cells[i];
                TextView tv = h.cells[i];
                if (c.state == 1) {
                    tv.setText(c.attempts + "\n" + c.timeMin);
                    tv.setTextColor(b.probs.get(i).fg);
                    tv.setBackgroundColor(b.probs.get(i).bg);
                    tv.setTypeface(Typeface.DEFAULT_BOLD);
                } else if (c.state == 2) {
                    tv.setText("-" + c.attempts);
                    tv.setTextColor(0xFFDC2626);
                    tv.setBackgroundColor(0x00000000);
                    tv.setTypeface(Typeface.DEFAULT);
                } else if (c.state == 3) {
                    tv.setText("?");
                    tv.setTextColor(Ui.SUB);
                    tv.setBackgroundColor(0xFFE5E7EB);
                    tv.setTypeface(Typeface.DEFAULT_BOLD);
                } else {
                    tv.setText("");
                    tv.setBackgroundColor(0x00000000);
                }
            }
            // 交替底色
            cv.setBackgroundColor(pos % 2 == 0 ? 0xFFFFFFFF : 0xFFFAFBFC);
            cv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    detail(r);
                }
            });
            return cv;
        }
    }

    private static final class Holder {
        TextView rank, name, org, solved, pen;
        TextView[] cells;
    }

    private void detail(BoardData.Row r) {
        StringBuilder sb = new StringBuilder();
        sb.append("第 ").append(r.rank).append(" 名 · 通过 ").append(r.solved)
                .append(" 题 · 罚时 ").append(r.penalty).append(" 分钟\n");
        if (r.org.length() > 0) sb.append(r.org).append("\n");
        if (r.detail.length() > 0) sb.append(r.detail).append("\n");
        sb.append("\n");
        for (int i = 0; i < r.cells.length; i++) {
            BoardData.Cell c = r.cells[i];
            sb.append(viewProbs(i)).append("  ");
            if (c.state == 1) {
                sb.append("通过 ").append(c.attempts).append(" 次 / ").append(c.timeMin).append(" 分钟\n");
            } else if (c.state == 2) {
                sb.append("未通过 ").append(c.attempts).append(" 次\n");
            } else if (c.state == 3) {
                sb.append("封榜中\n");
            } else {
                sb.append("—\n");
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(r.team)
                .setMessage(sb.toString())
                .setPositiveButton("关闭", null)
                .show();
    }

    private BoardData.Board cur;

    private String viewProbs(int i) {
        return cur != null && i < cur.probs.size() ? cur.probs.get(i).label : String.valueOf(i);
    }
}