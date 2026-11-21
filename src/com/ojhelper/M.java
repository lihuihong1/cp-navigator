package com.ojhelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** 数据模型与常量。 */
final class M {

    static final String VERSION = "1.1.0";
    static final int VERSION_CODE = 2;
    /** 由 build.sh 生成 BuildStamp.java 注入，见 BuildStamp.T。 */
    static final String BUILD_TIME = BuildStamp.T;

    static final TimeZone BJ = TimeZone.getTimeZone("Asia/Shanghai");
    static final long DAY = 24L * 3600 * 1000;

    static final String CF = "cf";
    static final String ATC = "atcoder";
    static final String LC = "lc";
    static final String LG = "luogu";
    static final String NC = "nowcoder";
    static final String VJ = "vjudge";
    static final String HDU = "hdu";

    /** 比赛可查询的 4 个源。 */
    static final String[] CONTEST_OJS = {NC, LC, CF, LG};

    /** 统计页的 7 个 OJ（顺序即展示顺序）。 */
    static final String[] STAT_OJS = {CF, ATC, LC, LG, NC, VJ, HDU};

    static String ojName(String oj) {
        if (CF.equals(oj)) return "Codeforces";
        if (ATC.equals(oj)) return "AtCoder";
        if (LC.equals(oj)) return "力扣";
        if (LG.equals(oj)) return "洛谷";
        if (NC.equals(oj)) return "牛客";
        if (VJ.equals(oj)) return "VJudge";
        if (HDU.equals(oj)) return "HDU";
        return oj;
    }

    static String ojShort(String oj) {
        if (CF.equals(oj)) return "CF";
        if (ATC.equals(oj)) return "AtCoder";
        if (LC.equals(oj)) return "力扣";
        if (LG.equals(oj)) return "洛谷";
        if (NC.equals(oj)) return "牛客";
        if (VJ.equals(oj)) return "VJudge";
        if (HDU.equals(oj)) return "HDU";
        return oj;
    }

    static int ojColor(String oj) {
        if (CF.equals(oj)) return 0xFF1F8ACB;
        if (ATC.equals(oj)) return 0xFF3A3A46;
        if (LC.equals(oj)) return 0xFFF89F1B;
        if (LG.equals(oj)) return 0xFF00A6A6;
        if (NC.equals(oj)) return 0xFF00B96B;
        if (VJ.equals(oj)) return 0xFFE2574C;
        if (HDU.equals(oj)) return 0xFF8E44AD;
        return 0xFF666666;
    }

    static String idHint(String oj) {
        if (CF.equals(oj)) return "handle，如 tourist";
        if (ATC.equals(oj)) return "handle，如 tourist";
        if (LC.equals(oj)) return "用户名，如 shang-shan-zao-xing";
        if (LG.equals(oj)) return "用户名，如 lihuihong123";
        if (NC.equals(oj)) return "数字 id，如 448522731";
        if (VJ.equals(oj)) return "用户名";
        if (HDU.equals(oj)) return "数字 id";
        return "用户名";
    }

    /** 比赛。时间均为 epoch 毫秒。 */
    static final class Contest {
        String oj;
        String sid;
        String title;
        long start;
        long end;
        String url;

        String key() { return oj + ":" + sid; }

        /** 是否已结束。 */
        boolean finished(long now) { return end <= now; }
        boolean running(long now) { return start <= now && end > now; }
    }

    /** 某个 OJ 的统计结果；null 表示该 OJ 不提供 / 未取到。 */
    static final class Stat {
        String oj;
        Integer solved;
        Double rating;
        String extra;

        static Stat of(String oj) {
            Stat s = new Stat();
            s.oj = oj;
            return s;
        }
    }

    /** 失败类别 -> 用户可读文案。 */
    enum Err {
        EMPTY("信息不全"),
        NOT_FOUND("用户名有误"),
        RATE_LIMITED("查询过于频繁"),
        NETWORK("网络异常"),
        BROKEN("接口失效");

        final String text;
        Err(String t) { this.text = t; }
    }

    /** 查询失败。message 用于日志/详情，kind 用于展示。 */
    static final class OjError extends Exception {
        final Err kind;
        final String detail;

        OjError(Err kind, String detail) {
            super(kind.text + (detail == null || detail.isEmpty() ? "" : "：" + detail));
            this.kind = kind;
            this.detail = detail == null ? "" : detail;
        }

        String userText() {
            String t = kind.text;
            if (detail != null && !detail.isEmpty()) t += " · " + detail;
            return t;
        }
    }

    // ---------- 时间 ----------

    static SimpleDateFormat fmt(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.CHINA);
        f.setTimeZone(BJ);
        return f;
    }

    private static final String[] WEEK = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    /** 2026-11-22 周日 19:00 */
    static String stamp(long ms) {
        Calendar c = Calendar.getInstance(BJ);
        c.setTimeInMillis(ms);
        return fmt("MM-dd").format(c.getTime()) + " " + WEEK[c.get(Calendar.DAY_OF_WEEK) - 1]
                + " " + fmt("HH:mm").format(c.getTime());
    }

    /** 今天 19:00 / 明天 19:00 / 11-22 周日 19:00 */
    static String stampShort(long ms) {
        Calendar a = Calendar.getInstance(BJ);
        Calendar b = Calendar.getInstance(BJ);
        b.setTimeInMillis(ms);
        if (sameDay(a, b)) return "今天 " + fmt("HH:mm").format(b.getTime());
        a.add(Calendar.DAY_OF_YEAR, 1);
        if (sameDay(a, b)) return "明天 " + fmt("HH:mm").format(b.getTime());
        return stamp(ms);
    }

    static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /** 今天 00:00 的毫秒。 */
    static long startOfToday() {
        Calendar c = Calendar.getInstance(BJ);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 查询窗口：现在 ~ 第 7 天结束（含今天共 7 个自然日）。 */
    static long windowEnd() { return startOfToday() + 7 * DAY; }

    static String windowText() {
        long s = startOfToday();
        long e = windowEnd() - 1;
        return fmt("MM-dd").format(s) + " ~ " + fmt("MM-dd").format(e);
    }

    static String duration(long ms) {
        long min = Math.max(0, ms / 60000);
        if (min < 60) return min + " 分钟";
        long h = min / 60, m = min % 60;
        return m == 0 ? h + " 小时" : h + " 小时 " + m + " 分";
    }

    /** "2小时" / "90分钟" / "1.5小时" -> 毫秒；无法解析返回 -1。 */
    static long parseDuration(String s) {
        if (s == null) return -1;
        s = s.trim();
        try {
            if (s.endsWith("小时")) return (long) (Double.parseDouble(s.substring(0, s.length() - 2)) * 3600_000);
            if (s.endsWith("分钟")) return Long.parseLong(s.substring(0, s.length() - 2)) * 60_000;
            if (s.endsWith("h")) return (long) (Double.parseDouble(s.substring(0, s.length() - 1)) * 3600_000);
        } catch (Exception ignored) {
        }
        return -1;
    }

    static List<M.Contest> sortByStart(List<M.Contest> in) {
        List<M.Contest> out = new ArrayList<M.Contest>(in);
        java.util.Collections.sort(out, new java.util.Comparator<M.Contest>() {
            @Override
            public int compare(M.Contest a, M.Contest b) {
                if (a.start != b.start) return a.start < b.start ? -1 : 1;
                return a.oj.compareTo(b.oj);
            }
        });
        return out;
    }
}