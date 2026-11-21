package com.ojhelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 4 个比赛源的抓取与统一过滤。 */
final class ContestFetch {

    static final class Result {
        final String oj;
        List<M.Contest> list = new ArrayList<M.Contest>();
        M.OjError err;

        Result(String oj) { this.oj = oj; }
    }

    private static final SimpleDateFormat YMD_HMS = M.fmt("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat YMD_HM = M.fmt("yyyy-MM-dd HH:mm");

    static boolean inWindow(long start, long end, long now) {
        // 今天已经结束的比赛也保留（用户要求"严格查今天"），只是不能加日历；
        // 昨天及更早结束的全部丢掉。
        return start < M.windowEnd() && end > M.startOfToday();
    }

    /** 并行拉取全部源；阻塞，调用方自行放到子线程。 */
    static List<Result> fetchAll() {
        return fetchAll(java.util.Arrays.asList(M.ALL_CONTEST));
    }

    /** 只拉取指定源，返回顺序与传入一致。 */
    static List<Result> fetchAll(java.util.Collection<String> sources) {
        final List<String> src = new ArrayList<String>(sources);
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(4, src.size())));
        List<Future<Result>> fs = new ArrayList<Future<Result>>();
        final long now = System.currentTimeMillis();
        for (final String oj : src) {
            fs.add(pool.submit(new Callable<Result>() {
                @Override
                public Result call() {
                    return one(oj, now);
                }
            }));
        }
        List<Result> out = new ArrayList<Result>();
        for (int i = 0; i < fs.size(); i++) {
            try {
                out.add(fs.get(i).get());
            } catch (Exception e) {
                Result r = new Result(src.get(i));
                r.err = new M.OjError(M.Err.BROKEN, "任务异常");
                out.add(r);
            }
        }
        pool.shutdown();
        return out;
    }

    private static Result one(String oj, long now) {
        Result r = new Result(oj);
        try {
            if (M.CF.equals(oj)) cf(r, now);
            else if (M.LC.equals(oj)) lc(r, now);
            else if (M.LG.equals(oj)) luogu(r, now);
            else if (M.NC.equals(oj)) nowcoder(r, now);
            else if (M.LQ.equals(oj)) lanqiao(r, now);
            else if (M.ATC.equals(oj)) atc(r, now);
        } catch (M.OjError e) {
            r.err = e;
            r.list.clear();
        } catch (Exception e) {
            r.err = new M.OjError(M.Err.BROKEN, e.getClass().getSimpleName());
            r.list.clear();
        }
        return r;
    }

    // ---------------- Codeforces ----------------

    private static void cf(Result r, long now) throws Exception {
        String body = Net.get("https://codeforces.com/api/contest.list?gym=false",
                "https://codeforces.com/contests");
        if (!body.contains("\"status\"") || !body.contains("\"OK\"")) {
            throw new M.OjError(M.Err.BROKEN, "返回非 JSON");
        }
        org.json.JSONObject root = new org.json.JSONObject(body);
        org.json.JSONArray arr = root.optJSONArray("result");
        if (arr == null) throw new M.OjError(M.Err.BROKEN, "缺少 result");
        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String phase = o.optString("phase", "");
            if (!"BEFORE".equals(phase) && !"CODING".equals(phase)) continue;
            long st = o.optLong("startTimeSeconds", 0) * 1000L;
            if (st <= 0) continue;
            long dur = o.optLong("durationSeconds", 0) * 1000L;
            if (!inWindow(st, st + dur, now)) continue;
            M.Contest c = new M.Contest();
            c.oj = M.CF;
            c.sid = String.valueOf(o.optInt("id"));
            c.title = o.optString("name", "");
            c.start = st;
            c.end = st + dur;
            c.url = "https://codeforces.com/contest/" + c.sid;
            r.list.add(c);
        }
    }

    // ---------------- 力扣（GraphQL 只问未来几场，响应几百字节） ----------------

    private static final String LC_UPCOMING =
            "{\"query\":\"{ contestUpcomingContests { title startTime duration titleSlug } }\"}";

    private static void lc(Result r, long now) throws Exception {
        try {
            lcGraphql(r, now);
            return;
        } catch (Exception e) {
            // GraphQL 挂了才退回旧接口（约 9MB，慢），正常情况下走不到这里
        }
        lcRest(r, now);
    }

    private static void lcGraphql(Result r, long now) throws Exception {
        String body = Net.postJson("https://leetcode.cn/graphql", LC_UPCOMING,
                "https://leetcode.cn/contest/");
        java.util.List<String> objs = objectsAt(body, body.indexOf("contestUpcomingContests"));
        if (objs.isEmpty()) throw new M.OjError(M.Err.BROKEN, "返回格式变化");
        for (String o : objs) {
            String title = Json.str(o, "title");
            long st = Json.num(o, "startTime") * 1000L;
            long dur = Json.num(o, "duration") * 1000L;
            if (st <= 0 || title.isEmpty()) continue;
            if (dur <= 0) dur = 5400_000L;
            if (!inWindow(st, st + dur, now)) continue;
            String slug = Json.str(o, "titleSlug");
            M.Contest c = new M.Contest();
            c.oj = M.LC;
            c.sid = slug.isEmpty() ? String.valueOf(st) : slug;
            c.title = title;
            c.start = st;
            c.end = st + dur;
            c.url = "https://leetcode.cn/contest/" + c.sid + "/";
            r.list.add(c);
        }
    }

    /** 旧接口：9MB，按开始时间倒序排列，扫到很久以前的比赛就停。 */
    private static void lcRest(Result r, long now) throws Exception {
        String body = Net.get("https://leetcode.cn/contest/api/list/", "https://leetcode.cn/contest/");
        if (!body.contains("\"contests\"")) throw new M.OjError(M.Err.BROKEN, "返回格式变化");
        int i = body.indexOf("\"contests\":[");
        if (i < 0) throw new M.OjError(M.Err.BROKEN, "未找到 contests");
        i += "\"contests\":[".length();

        int depth = 0, objStart = -1, oldRun = 0;
        boolean inStr = false, esc = false;
        for (; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (inStr) {
                if (esc) esc = false;
                else if (ch == '\\') esc = true;
                else if (ch == '"') inStr = false;
                continue;
            }
            if (ch == '"') { inStr = true; continue; }
            if (ch == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    String obj = body.substring(objStart, i + 1);
                    long st = Json.num(obj, "start_time");
                    if (st > 0) {
                        st *= 1000L;
                        long dur = Json.num(obj, "duration");
                        if (dur <= 0) dur = 5400;
                        dur *= 1000L;
                        if (inWindow(st, st + dur, now)) {
                            oldRun = 0;
                            M.Contest c = new M.Contest();
                            c.oj = M.LC;
                            c.sid = String.valueOf(Json.num(obj, "id"));
                            c.title = Json.str(obj, "title");
                            String slug = Json.str(obj, "title_slug");
                            c.start = st;
                            c.end = st + dur;
                            c.url = "https://leetcode.cn/contest/" + (slug.isEmpty() ? c.sid : slug) + "/";
                            r.list.add(c);
                        } else if (st < now - 2 * M.DAY) {
                            // 列表按时间倒序，连续出现很久以前的比赛即可停止扫描
                            if (++oldRun > 30) break;
                        }
                    }
                    objStart = -1;
                }
            } else if (ch == ']' && depth == 0) {
                break;
            }
        }
        if (r.list.isEmpty() && !body.contains("\"id\"")) {
            throw new M.OjError(M.Err.BROKEN, "未解析到比赛");
        }
    }

    // ---------------- AtCoder（官网 SSR HTML，含 Upcoming Contests） ----------------
    // 不用 kenkoooo 镜像：它的 contests.json 只到当天，没有已公布的未来比赛。

    private static final Pattern ATC_ROW = Pattern.compile(
            "<tr>\\s*<td[^>]*>\\s*<a href='[^>]*'>\\s*<time class='fixtime fixtime-full'>([^<]+)</time>"
                    + ".*?<a href=\"/contests/([^\"]+)\">([^<]+)</a>"
                    + ".*?<td class=\"text-center\">(\\d{1,3}):(\\d{2})</td>",
            Pattern.DOTALL);

    /** 时间形如 2026-11-21 21:00:00+0900，本身带时区，不需要再换算。 */
    private static long atcTime(String raw) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ",
                    java.util.Locale.US);
            f.setLenient(false);
            java.util.Date d = f.parse(raw.trim());
            return d == null ? -1 : d.getTime();
        } catch (Exception e) {
            return -1;
        }
    }

    private static void atc(Result r, long now) throws Exception {
        String body = Net.get("https://atcoder.jp/contests/", "https://atcoder.jp/");
        if (body == null || body.isEmpty()) throw new M.OjError(M.Err.BROKEN, "空响应");
        Matcher m = ATC_ROW.matcher(body);
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        int rows = 0;
        while (m.find()) {
            rows++;
            String sid = m.group(2).trim();
            long st = atcTime(m.group(1));
            int durMin = Integer.parseInt(m.group(4)) * 60 + Integer.parseInt(m.group(5));
            if (sid.isEmpty() || st <= 0 || durMin <= 0) continue;
            if (!seen.add(sid)) continue;          // 同一场比赛在多个表里重复出现
            long end = st + durMin * 60000L;
            if (!inWindow(st, end, now)) continue; // 表里也有已结束的比赛，靠时间过滤
            M.Contest c = new M.Contest();
            c.oj = M.ATC;
            c.sid = sid;
            String t = Net.unescapeHtml(m.group(3)).trim();
            c.title = t.isEmpty() ? sid : t;
            c.start = st;
            c.end = end;
            c.url = "https://atcoder.jp/contests/" + sid;
            r.list.add(c);
        }
        if (rows == 0) throw new M.OjError(M.Err.BROKEN, "页面结构变化");
    }

    // ---------------- 洛谷（服务端渲染 HTML） ----------------

    private static final Pattern LG_ITEM = Pattern.compile(
            "<li>\\s*<h3><a href=\"/contest/(\\d+)\">(.*?)</a></h3>\\s*<p><small>(.*?)</small></p>",
            Pattern.DOTALL);

    private static void luogu(Result r, long now) throws Exception {
        // 同时带 x-lentille-request 与 x-requested-with，洛谷才直接回 JSON：
        // 省掉「302 回自己 → 拿 C3VK cookie → 重放」那一套，也绕开 Android 改写 Cookie 头的老问题。
        String api = "https://www.luogu.com.cn/contest/list?_contentOnly=1";
        String body = null;
        try {
            body = Net.getWith(api, "https://www.luogu.com.cn/contest/list",
                    new String[]{"x-lentille-request", "content-only",
                            "x-requested-with", "XMLHttpRequest"});
        } catch (Exception ignored) {
            // 退回 HTML
        }
        if (body != null && body.trim().startsWith("{")) {
            java.util.List<String> objs = objectsAt(body, body.indexOf("\"result\""));
            for (String o : objs) {
                String name = Json.str(o, "name");
                long st = Json.num(o, "startTime") * 1000L;
                long en = Json.num(o, "endTime") * 1000L;
                if (st <= 0 || name.isEmpty()) continue;
                if (en <= st) en = st + 2 * 3600_000L;
                if (!inWindow(st, en, now)) continue;
                M.Contest c = new M.Contest();
                c.oj = M.LG;
                c.sid = String.valueOf(Json.num(o, "id"));
                c.title = name;
                c.start = st;
                c.end = en;
                c.url = "https://www.luogu.com.cn/contest/" + c.sid;
                r.list.add(c);
            }
            return;
        }
        luoguHtml(r, now);
    }

    /** 兜底：拿不到 JSON 就解析服务端渲染的 HTML（要先过 cookie 那一关，见 Net.getRaw）。 */
    private static void luoguHtml(Result r, long now) throws Exception {
        String LG = "https://www.luogu.com.cn/contest/list";
        String body;
        try {
            body = Net.getRaw(LG, LG);
        } catch (Exception e) {
            body = Net.get(LG, LG);
        }
        Matcher m = LG_ITEM.matcher(body);
        while (m.find()) {
            String span = m.group(3);
            int sep = span.indexOf('~');
            if (sep < 0) continue;
            long st = parse(YMD_HMS, span.substring(0, sep).trim());
            long en = parse(YMD_HMS, span.substring(sep + 1).trim());
            if (st <= 0) continue;
            if (en <= st) en = st + 2 * 3600_000L;
            if (!inWindow(st, en, now)) continue;
            M.Contest c = new M.Contest();
            c.oj = M.LG;
            c.sid = m.group(1);
            c.title = Net.unescapeHtml(m.group(2)).trim();
            c.start = st;
            c.end = en;
            c.url = "https://www.luogu.com.cn/contest/" + c.sid;
            r.list.add(c);
        }
        if (r.list.isEmpty() && !body.contains("/contest/")) {
            throw new M.OjError(M.Err.BROKEN, "页面结构变化");
        }
    }

    // ---------------- 牛客（服务端渲染 HTML） ----------------

    private static final Pattern NC_TIME = Pattern.compile(
            "比赛时间：\\s*(\\d{4}-\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{2})\\s*至\\s*(\\d{4}-\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{2})");
    private static final Pattern NC_LINK = Pattern.compile(
            "<a href=\"/acm/contest/(\\d+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);

    private static void nowcoder(Result r, long now) throws Exception {
        String body = Net.get("https://ac.nowcoder.com/acm/contest/vip-index",
                "https://ac.nowcoder.com/acm/contest/vip-index");
        String[] blocks = body.split("<div class=\"platform-item-main\">");
        for (int i = 1; i < blocks.length; i++) {
            String b = blocks[i];
            Matcher mt = NC_TIME.matcher(b);
            if (!mt.find()) continue;
            long st = parse(YMD_HM, mt.group(1).replaceAll("\\s+", " ").trim());
            long en = parse(YMD_HM, mt.group(2).replaceAll("\\s+", " ").trim());
            if (st <= 0 || en <= st) continue;
            if (!inWindow(st, en, now)) continue;
            Matcher ml = NC_LINK.matcher(b);
            if (!ml.find()) continue;
            M.Contest c = new M.Contest();
            c.oj = M.NC;
            c.sid = ml.group(1);
            c.title = Net.unescapeHtml(Net.stripTags(ml.group(2))).trim();
            c.start = st;
            c.end = en;
            c.url = "https://ac.nowcoder.com/acm/contest/" + c.sid;
            r.list.add(c);
        }
        if (r.list.isEmpty() && !body.contains("platform-item")) {
            throw new M.OjError(M.Err.BROKEN, "页面结构变化");
        }
    }

    // ---------------- 蓝桥云课（官网 JSON；game_type_code=2 只取算法赛） ----------------

    private static final String LQ_API = "https://www.lanqiao.cn/api/v2/contests/"
            + "?sort=opentime&paginate=0&status=not_finished&game_type_code=2";

    private static void lanqiao(Result r, long now) throws Exception {
        String body = Net.get(LQ_API, "https://www.lanqiao.cn/contests/");
        if (!body.trim().startsWith("[")) throw new M.OjError(M.Err.BROKEN, "返回格式变化");
        java.util.List<String> objs = objectsAt(body, 0);
        for (String o : objs) {
            String name = Json.str(o, "name");
            long st = isoTime(Json.str(o, "open_at"));
            long en = isoTime(Json.str(o, "end_at"));
            if (st <= 0 || name.isEmpty()) continue;
            if (en <= st) en = st + 2 * 3600_000L;
            if (!inWindow(st, en, now)) continue;
            String href = Json.str(o, "html_url");
            M.Contest c = new M.Contest();
            c.oj = M.LQ;
            c.sid = String.valueOf(Json.num(o, "id"));
            c.title = name;
            c.start = st;
            c.end = en;
            c.url = href.startsWith("http") ? href : "https://www.lanqiao.cn" + href;
            r.list.add(c);
        }
    }

    /** 蓝桥的时间形如 2026-11-21T19:00:00+08:00。 */
    private static long isoTime(String s) {
        if (s == null || s.isEmpty()) return -1;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            f.setLenient(false);
            java.util.Date d = f.parse(s.trim());
            return d == null ? -1 : d.getTime();
        } catch (Exception e) {
            return -1;
        }
    }

    // ---------------- 小工具 ----------------

    /**
     * 从 from 之后第一个 '[' 起，把数组第一层的 {@code {...}} 逐个切出来。
     * 跳过字符串内的括号（标题里常有括号字符），嵌套数组/对象靠深度计平衡。
     */
    static java.util.List<String> objectsAt(String body, int from) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (body == null || from < 0) return out;
        int i = body.indexOf('[', from);
        if (i < 0) return out;
        int arrDepth = 0;
        int depth = 0;
        int objStart = -1;
        boolean inStr = false;
        boolean esc = false;
        for (; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (inStr) {
                if (esc) esc = false;
                else if (ch == '\\') esc = true;
                else if (ch == '"') inStr = false;
                continue;
            }
            if (ch == '"') { inStr = true; continue; }
            if (ch == '[') arrDepth++;
            else if (ch == ']') {
                arrDepth--;
                if (arrDepth == 0) break;
            } else if (ch == '{') {
                if (depth == 0 && arrDepth == 1) objStart = i;
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    out.add(body.substring(objStart, i + 1));
                    objStart = -1;
                }
            }
        }
        return out;
    }

    static long parse(SimpleDateFormat f, String s) {
        try {
            synchronized (f) {
                java.util.Date d = f.parse(s);
                return d == null ? -1 : d.getTime();
            }
        } catch (Exception e) {
            return -1;
        }
    }

    /** 轻量 JSON 取值：只针对比赛对象，避免构造整棵 9MB 的树。 */
    static final class Json {
        static String str(String obj, String key) {
            String pat = "\"" + key + "\":\"";
            int i = obj.indexOf(pat);
            if (i < 0) return "";
            i += pat.length();
            StringBuilder sb = new StringBuilder();
            boolean esc = false;
            for (; i < obj.length(); i++) {
                char ch = obj.charAt(i);
                if (esc) {
                    esc = false;
                    switch (ch) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'u':
                            if (i + 4 < obj.length()) {
                                try {
                                    sb.append((char) Integer.parseInt(obj.substring(i + 1, i + 5), 16));
                                    i += 4;
                                } catch (Exception ignored) {
                                }
                            }
                            break;
                        default: sb.append(ch);
                    }
                    continue;
                }
                if (ch == '\\') { esc = true; continue; }
                if (ch == '"') break;
                sb.append(ch);
            }
            return sb.toString();
        }

        static long num(String obj, String key) {
            String pat = "\"" + key + "\":";
            int i = obj.indexOf(pat);
            if (i < 0) return -1;
            i += pat.length();
            int j = i;
            if (j < obj.length() && (obj.charAt(j) == '-' || Character.isDigit(obj.charAt(j)))) {
                while (j < obj.length() && (Character.isDigit(obj.charAt(j)) || obj.charAt(j) == '-')) j++;
                try {
                    return Long.parseLong(obj.substring(i, j));
                } catch (Exception e) {
                    return -1;
                }
            }
            return -1;
        }
    }
}