package com.ojhelper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 原生榜单：赛事列表（数据来自 board-data.xcpcio.com 的 contest_list.json）。
 * 点一场进 {@link IcpBoard}。
 */
public class IcpList extends Activity {

    static final String EXTRA_PATH = "path";
    static final String EXTRA_TITLE = "title";

    private final List<Object> flat = new ArrayList<Object>();   // String 标题 / BoardData.Item 比赛
    private final List<Object> shown = new ArrayList<Object>();
    private BaseAdapter adapter;
    private TextView state;
    private ListView list;
    private EditText search;
    private List<BoardData.Series> series;

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
        back.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        head.addView(back);
        LinearLayout texts = Ui.col(this);
        texts.addView(Ui.tv(this, "ICPC 榜单（原生）", 16f, Ui.TEXT, true));
        TextView sub = Ui.tv(this, "XCPCIO 数据直连 · 不用打开网页", 11f, Ui.SUB, false);
        sub.setPadding(0, Ui.dp(this, 2), 0, 0);
        texts.addView(sub);
        head.addView(texts, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(head);
        root.addView(Ui.divider(this));

        LinearLayout box = Ui.row(this);
        box.setBackgroundColor(Ui.CARD);
        box.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        search = new EditText(this);
        search.setHint("搜索比赛，例如 沈阳 / 2025");
        search.setTextSize(13f);
        search.setSingleLine(true);
        search.setBackgroundColor(0xFFF3F4F6);
        search.setPadding(Ui.dp(this, 10), Ui.dp(this, 7), Ui.dp(this, 10), Ui.dp(this, 7));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b2, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter(s.toString());
            }
        });
        box.addView(search, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(box);
        root.addView(Ui.divider(this));

        state = Ui.tv(this, "正在获取赛事列表…", 13f, Ui.SUB, false);
        state.setGravity(Gravity.CENTER);
        state.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 12));
        root.addView(state, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setBackgroundColor(Ui.BG);
        list.setAdapter(adapter = new Adapter());
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Object o = shown.get(pos);
                if (!(o instanceof BoardData.Item)) return;
                BoardData.Item it = (BoardData.Item) o;
                IcpBoard.open(IcpList.this, it.path, it.name);
            }
        });
        root.addView(list, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        load();
    }

    private void load() {
        state.setText("正在获取赛事列表…");
        state.setTextColor(Ui.SUB);
        state.setVisibility(View.VISIBLE);
        state.setOnClickListener(null);
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<BoardData.Series> r = null;
                M.OjError err = null;
                try {
                    r = BoardData.index();
                } catch (M.OjError e) {
                    err = e;
                }
                final List<BoardData.Series> fr = r;
                final M.OjError fe = err;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (fe != null) {
                            state.setText("赛事列表获取失败：" + fe.userText() + "\n点这里重试");
                            state.setTextColor(Ui.RED);
                            state.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    load();
                                }
                            });
                            return;
                        }
                        series = fr;
                        build();
                        state.setVisibility(View.GONE);
                    }
                });
            }
        }, "icpIndex").start();
    }

    private void build() {
        flat.clear();
        for (int i = 0; i < series.size(); i++) {
            BoardData.Series s = series.get(i);
            flat.add(s.label);
            for (int j = 0; j < s.years.size(); j++) {
                BoardData.Year y = s.years.get(j);
                flat.add(y.label);
                flat.addAll(y.items);
            }
        }
        applyFilter(search.getText().toString());
    }

    private void applyFilter(String q) {
        shown.clear();
        String key = q == null ? "" : q.trim().toLowerCase();
        if (key.isEmpty()) {
            shown.addAll(flat);
        } else {
            for (int i = 0; i < flat.size(); i++) {
                Object o = flat.get(i);
                if (o instanceof BoardData.Item) {
                    BoardData.Item it = (BoardData.Item) o;
                    String t = (it.name + " " + M.fmt("yyyy-MM-dd").format(new Date(it.start)))
                            .toLowerCase();
                    if (t.contains(key)) shown.add(it);
                }
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (shown.isEmpty() && state != null && series != null) {
            state.setText("没有匹配的比赛");
            state.setTextColor(Ui.SUB);
            state.setVisibility(View.VISIBLE);
        } else if (state != null && series != null) {
            state.setVisibility(View.GONE);
        }
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int i) {
            return shown.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public int getItemViewType(int i) {
            Object o = shown.get(i);
            if (o instanceof BoardData.Item) return 0;
            String s = String.valueOf(o);
            return s.startsWith("ICPC") || s.startsWith("CCPC") || s.startsWith("省赛")
                    || s.startsWith("训练营") ? 1 : 2;
        }

        @Override
        public View getView(int i, View cv, ViewGroup parent) {
            Object o = shown.get(i);
            int type = getItemViewType(i);
            if (type == 1) {  // 赛系标题
                LinearLayout head = Ui.col(IcpList.this);
                head.setPadding(Ui.dp(IcpList.this, 16), Ui.dp(IcpList.this, 16),
                        Ui.dp(IcpList.this, 16), Ui.dp(IcpList.this, 6));
                head.addView(Ui.tv(IcpList.this, String.valueOf(o), 15f, Ui.TEXT, true));
                return head;
            }
            if (type == 2) {  // 年份
                LinearLayout y = Ui.col(IcpList.this);
                y.setPadding(Ui.dp(IcpList.this, 16), Ui.dp(IcpList.this, 8),
                        Ui.dp(IcpList.this, 16), Ui.dp(IcpList.this, 4));
                y.addView(Ui.tv(IcpList.this, String.valueOf(o), 12.5f, Ui.SUB, true));
                return y;
            }
            BoardData.Item it = (BoardData.Item) o;
            LinearLayout row = Ui.col(IcpList.this);
            row.setBackgroundColor(Ui.CARD);
            int px = Ui.dp(IcpList.this, 16), py = Ui.dp(IcpList.this, 10);
            row.setPadding(px, py, px, py);

            TextView name = Ui.tv(IcpList.this, it.name, 13.5f, Ui.TEXT, false);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(name);

            StringBuilder sb = new StringBuilder();
            if (it.start > 0) {
                sb.append(M.fmt("yyyy-MM-dd").format(new Date(it.start)))
                        .append(" ").append(M.weekOf(it.start));
            } else {
                sb.append("时间未知");
            }
            TextView meta = Ui.tv(IcpList.this, sb.toString(), 11.5f, Ui.SUB, false);
            meta.setPadding(0, Ui.dp(IcpList.this, 3), 0, 0);
            row.addView(meta);

            View line = new View(IcpList.this);
            line.setBackgroundColor(Ui.LINE);
            row.addView(line, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(IcpList.this, 0.7f))));
            return row;
        }
    }
}