package com.ojhelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * XCPCIO 榜单数据（board-data.xcpcio.com）的原生读取 + ICPC 排名计算。
 *
 * <p>站点本身是桌面级 SPA，单场榜单要额外解析 1.37 MB 的 Board 脚本；但它的数据是
 * 纯 JSON，可以绕过页面直接算：
 * <pre>
 *   {HOST}index/contest_list.json                     全部赛事索引
 *   {HOST}{path}/config.json                          题目、罚时、封榜
 *   {HOST}{path}/team.json                            队伍（含学校 id）
 *   {HOST}{path}/run.json                             提交
 *   {HOST}{path}/organizations.json                   学校名
 * </pre>
 * 排名规则与 ICPC 一致：先比通过题数，再比罚时（通过用时 + 通过前错误次数 × 20 分钟），
 * 编译错误不计罚时（与 XCPCIO 页面一致，已用沈阳站 35 行页面数据核对过）。
 */
final class BoardData {

    static final String HOST = "https://board-data.xcpcio.com/data/";
    static final String SITE = "https://board.xcpcio.com/";

    private BoardData() {
    }

    // ---------------------------------------------------------------- 索引

    /** 列表里的一场比赛。 */
    static final class Item {
        final String path, name;
        final long start, end;

        Item(String path, String name, long start, long end) {
            this.path = path;
            this.name = name;
            this.start = start;
            this.end = end;
        }
    }

    static final class Year {
        final String label;
        final List<Item> items = new ArrayList<Item>();
        long latest;

        Year(String label) {
            this.label = label;
        }
    }

    static final class Series {
        final String key, label;
        final List<Year> years = new ArrayList<Year>();

        Series(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    private static final String[] SERIES_KEY = {"icpc", "ccpc", "provincial-contest", "camp"};
    private static final String[] SERIES_LABEL = {"ICPC 区域赛", "CCPC", "省赛 / 邀请赛", "训练营"};

    /** 拉取并解析全部赛事索引（约 340 KB）。 */
    static List<Series> index() throws M.OjError {
        String body = Net.get(HOST + "index/contest_list.json", SITE);
        List<Series> out = new ArrayList<Series>();
        try {
            JSONObject root = new JSONObject(body);
            for (int s = 0; s < SERIES_KEY.length; s++) {
                JSONObject sv = root.optJSONObject(SERIES_KEY[s]);
                if (sv == null) continue;
                Series series = new Series(SERIES_KEY[s], SERIES_LABEL[s]);
                Iterator<?> yk = sv.keys();
                while (yk.hasNext()) {
                    String yearKey = String.valueOf(yk.next());
                    JSONObject yv = sv.optJSONObject(yearKey);
                    if (yv == null) continue;
                    Year year = new Year(yearKey);
                    Iterator<?> ck = yv.keys();
                    while (ck.hasNext()) {
                        String cKey = String.valueOf(ck.next());
                        JSONObject cv = yv.optJSONObject(cKey);
                        if (cv == null) continue;
                        String link = cv.optString("board_link", "");
                        String path = link.startsWith("/") ? link.substring(1) : link;
                        if (path.isEmpty()) continue;
                        JSONObject cfg = cv.optJSONObject("config");
                        String name = "";
                        if (cfg != null) {
                            name = nameOf(cfg.optJSONObject("contest_name"));
                            if (name.isEmpty()) name = cfg.optString("contest_name", "");
                        }
                        if (name.isEmpty()) name = cKey;
                        long start = cfg == null ? 0 : ms(cfg.optLong("start_time", 0));
                        long end = cfg == null ? 0 : ms(cfg.optLong("end_time", 0));
                        year.items.add(new Item(path, name, start, end));
                        if (start > year.latest) year.latest = start;
                    }
                    if (!year.items.isEmpty()) series.years.add(year);
                }
                Collections.sort(series.years, new Comparator<Year>() {
                    @Override
                    public int compare(Year a, Year b) {
                        return Long.compare(b.latest, a.latest);
                    }
                });
                for (int i = 0; i < series.years.size(); i++) {
                    final Year y = series.years.get(i);
                    Collections.sort(y.items, new Comparator<Item>() {
                        @Override
                        public int compare(Item a, Item b) {
                            return Long.compare(b.start, a.start);
                        }
                    });
                }
                if (!series.years.isEmpty()) out.add(series);
            }
        } catch (Exception e) {
            throw new M.OjError(M.Err.BROKEN, "榜单索引解析失败：" + e.getMessage());
        }
        if (out.isEmpty()) throw new M.OjError(M.Err.BROKEN, "榜单索引为空");
        return out;
    }

    /** 秒级时间戳统一成毫秒（索引里是秒，单场 config 里是毫秒）。 */
    private static long ms(long v) {
        return v > 0 && v < 100000000000L ? v * 1000 : v;
    }

    /** 有的文件是 [..]，有的是 {"list":[..]}。 */
    private static JSONArray listOf(String body) throws Exception {
        String t = body == null ? "" : body.trim();
        if (t.startsWith("[")) return new JSONArray(t);
        JSONObject o = new JSONObject(t);
        JSONArray a = o.optJSONArray("list");
        if (a == null) a = o.optJSONArray("data");
        return a == null ? new JSONArray() : a;
    }

    private static String nameOf(JSONObject cn) {
        if (cn == null) return "";
        JSONObject texts = cn.optJSONObject("texts");
        if (texts != null) {
            String zh = texts.optString("zh-CN", "");
            if (!zh.isEmpty()) return zh;
            String fb = cn.optString("fallback_lang", "");
            if (!fb.isEmpty()) {
                String v = texts.optString(fb, "");
                if (!v.isEmpty()) return v;
            }
            String en = texts.optString("en", "");
            if (!en.isEmpty()) return en;
        }
        return cn.optString("fallback", "");
    }

    // ---------------------------------------------------------------- 榜单

    static final class Prob {
        final String label;
        final int bg, fg;

        Prob(String label, int bg, int fg) {
            this.label = label;
            this.bg = bg;
            this.fg = fg;
        }
    }

    /** 一格：该队在这道题上的战绩。state 0 未提交 / 1 通过 / 2 有错未过 / 3 封榜中。 */
    static final class Cell {
        int state, attempts, timeMin;
    }

    static final class Row {
        int rank, solved, penalty;
        long lastAc;
        String team = "", org = "", detail = "";
        Cell[] cells;
    }

    static final class Board {
        String title = "";
        long start, end;
        int penaltyMin = 20;
        boolean frozen;
        long frozenAt;
        int teams;
        final List<Prob> probs = new ArrayList<Prob>();
        final List<Row> rows = new ArrayList<Row>();
    }

    private static final int ACC = 0, WRONG = 1;

    /** 拉取一场比赛的四个文件并算好排名。 */
    static Board load(String path) throws M.OjError {
        final String base = HOST + path + "/";
        final String ref = SITE + path;
        final String[] files = {"config.json", "team.json", "run.json", "organizations.json"};
        final String[] bodies = new String[files.length];
        final M.OjError[] err = new M.OjError[1];
        Thread[] ts = new Thread[files.length];
        for (int i = 0; i < files.length; i++) {
            final int idx = i;
            ts[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        bodies[idx] = Net.get(base + files[idx], ref);
                    } catch (M.OjError e) {
                        synchronized (err) {
                            if (err[0] == null) err[0] = e;
                        }
                    }
                }
            }, "board-" + files[i]);
            ts[i].start();
        }
        for (int i = 0; i < ts.length; i++) {
            try {
                ts[i].join();
            } catch (InterruptedException ignore) {
            }
        }
        if (err[0] != null && bodies[0] == null) throw err[0];
        if (bodies[0] == null) throw new M.OjError(M.Err.NETWORK, "榜单数据没取到");

        try {
            Board b = new Board();
            JSONObject cfg = new JSONObject(bodies[0]);
            b.title = nameOf(cfg.optJSONObject("contest_name"));
            b.start = ms(cfg.optLong("start_time", 0));
            b.end = ms(cfg.optLong("end_time", 0));
            int penaltySec = cfg.optInt("penalty", 1200);
            b.penaltyMin = Math.max(1, penaltySec / 60);
            int frozenSec = cfg.optInt("frozen_time", 0);
            long now = System.currentTimeMillis();
            b.frozen = frozenSec > 0 && b.end > now;
            b.frozenAt = b.end - frozenSec * 1000L;

            JSONArray probs = cfg.optJSONArray("problems");
            Map<String, Integer> probIndex = new HashMap<String, Integer>();
            if (probs != null) {
                for (int i = 0; i < probs.length(); i++) {
                    JSONObject p = probs.optJSONObject(i);
                    if (p == null) continue;
                    String id = p.optString("id", String.valueOf(i));
                    String label = p.optString("label", id);
                    JSONObject bc = p.optJSONObject("balloon_color");
                    int bg = 0xFF64748B, fg = 0xFFFFFFFF;
                    if (bc != null) {
                        bg = color(bc.optString("background_color", ""), bg);
                        fg = color(bc.optString("color", ""), fg);
                    }
                    probIndex.put(id, b.probs.size());
                    b.probs.add(new Prob(label, bg, fg));
                }
            }

            // 学校：id → "53 绍兴理工学院"
            Map<String, String> orgs = new HashMap<String, String>();
            if (bodies[3] != null) {
                try {
                    JSONArray arr = listOf(bodies[3]);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        String name = o.optString("name", "");
                        String icpc = o.optString("icpc_id", "");
                        if (!name.isEmpty()) orgs.put(o.optString("id", ""),
                                icpc.isEmpty() ? name : icpc + " " + name);
                    }
                } catch (Exception ignore) {
                }
            }

            // 队伍
            Map<String, Row> byId = new HashMap<String, Row>();
            Map<String, String> teamName = new HashMap<String, String>();
            JSONArray teams = listOf(bodies[1]);
            for (int i = 0; i < teams.length(); i++) {
                JSONObject t = teams.optJSONObject(i);
                if (t == null) continue;
                Row r = new Row();
                r.team = t.optString("name", "");
                r.org = orgs.get(t.optString("organization_id", ""));
                if (r.org == null) r.org = "";
                r.cells = new Cell[b.probs.size()];
                for (int k = 0; k < r.cells.length; k++) r.cells[k] = new Cell();
                r.detail = detailOf(t);
                String id = t.optString("id", "");
                byId.put(id, r);
                teamName.put(id, r.team);
            }
            b.teams = byId.size();

            // 提交：team → 按时间排序
            Map<String, List<int[]>> runs = new HashMap<String, List<int[]>>();
            JSONArray all = listOf(bodies[2]);
            for (int i = 0; i < all.length(); i++) {
                JSONObject o = all.optJSONObject(i);
                if (o == null) continue;
                String tid = o.optString("team_id", "");
                if (!byId.containsKey(tid)) continue;
                Integer pi = probIndex.get(o.optString("problem_id", ""));
                if (pi == null) continue;
                String status = o.optString("status", "");
                int code = ACC;
                if (!"ACCEPTED".equals(status)) code = WRONG;
                long tms = o.optLong("timestamp", 0);
                List<int[]> list = runs.get(tid);
                if (list == null) {
                    list = new ArrayList<int[]>();
                    runs.put(tid, list);
                }
                // {题目下标, 时间毫秒(截断到 int 够用), ACC/WRONG, 是否编译错误}
                list.add(new int[]{pi, (int) Math.min(Integer.MAX_VALUE, tms), code,
                        "COMPILATION_ERROR".equals(status) ? 1 : 0});
            }

            for (Map.Entry<String, Row> e : byId.entrySet()) {
                Row r = e.getValue();
                List<int[]> list = runs.get(e.getKey());
                int solved = 0, penalty = 0;
                long lastAc = 0;
                if (list != null) {
                    Collections.sort(list, new Comparator<int[]>() {
                        @Override
                        public int compare(int[] a, int[] b) {
                            return Integer.compare(a[1], b[1]);
                        }
                    });
                    int[] wrong = new int[r.cells.length];
                    boolean[] done = new boolean[r.cells.length];
                    for (int i = 0; i < list.size(); i++) {
                        int[] run = list.get(i);
                        int p = run[0];
                        if (done[p]) continue;
                        if (b.frozen && run[1] > b.frozenAt) {
                            if (run[2] == ACC) r.cells[p].state = 3;
                            continue;
                        }
                        if (run[2] == ACC) {
                            done[p] = true;
                            solved++;
                            penalty += run[1] / 60000 + wrong[p] * b.penaltyMin;
                            lastAc = Math.max(lastAc, run[1]);
                            Cell c = r.cells[p];
                            c.state = 1;
                            c.attempts = wrong[p] + 1;
                            c.timeMin = run[1] / 60000;
                        } else if (run[3] == 0) {
                            wrong[p]++;
                            Cell c = r.cells[p];
                            if (c.state == 0) {
                                c.state = 2;
                                c.attempts = wrong[p];
                                c.timeMin = run[1] / 60000;
                            } else if (c.state == 2) {
                                c.attempts = wrong[p];
                                c.timeMin = run[1] / 60000;
                            }
                        }
                    }
                }
                r.solved = solved;
                r.penalty = penalty;
                r.lastAc = lastAc;
                b.rows.add(r);
            }

            Collections.sort(b.rows, new Comparator<Row>() {
                @Override
                public int compare(Row a, Row b2) {
                    if (a.solved != b2.solved) return b2.solved - a.solved;
                    if (a.penalty != b2.penalty) return a.penalty - b2.penalty;
                    if (a.lastAc != b2.lastAc) return a.lastAc < b2.lastAc ? -1 : 1;
                    return a.team.compareTo(b2.team);
                }
            });
            // XCPCIO 的名次是逐行递增的（并列不共享名次），这里保持一致
            for (int i = 0; i < b.rows.size(); i++) {
                b.rows.get(i).rank = i + 1;
            }
            return b;
        } catch (Exception e) {
            if (e instanceof M.OjError) throw (M.OjError) e;
            throw new M.OjError(M.Err.BROKEN, "榜单解析失败：" + e.getMessage());
        }
    }

    private static String detailOf(JSONObject t) {
        StringBuilder sb = new StringBuilder();
        JSONArray members = t.optJSONArray("members");
        if (members != null) {
            for (int i = 0; i < members.length(); i++) {
                JSONObject m = members.optJSONObject(i);
                if (m == null) continue;
                String n = nameOf(m.optJSONObject("name"));
                if (n.isEmpty()) n = m.optString("name", "");
                if (n.isEmpty()) continue;
                if (sb.length() > 0) sb.append("、");
                sb.append(n);
            }
        }
        JSONArray coaches = t.optJSONArray("coaches");
        if (coaches != null && coaches.length() > 0) {
            JSONObject c = coaches.optJSONObject(0);
            if (c != null) {
                String n = nameOf(c.optJSONObject("name"));
                if (n.isEmpty()) n = c.optString("name", "");
                if (!n.isEmpty()) sb.append(sb.length() > 0 ? "　教练 " : "教练 ").append(n);
            }
        }
        return sb.toString();
    }

    private static int color(String hex, int def) {
        if (hex == null) return def;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6) return def;
        try {
            return 0xFF000000 | Integer.parseInt(h, 16);
        } catch (Exception e) {
            return def;
        }
    }
}