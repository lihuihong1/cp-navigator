package com.ojhelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 榜单页的数据源。
 *
 * <p>RankLand（rl.algoux.cn）有公开的 JSON API，可以直接抓列表；XCPCIO
 * （board.xcpcio.com）是纯静态 SPA，没有统一的"赛事列表"接口，只能在应用内用
 * WebView 打开站点本身。
 */
final class BoardFetch {

    static final String RANKLAND = "https://rl.algoux.cn";
    static final String XCPCIO = "https://board.xcpcio.com/";
    static final String ICCPC = "https://rank.iccpc.cn/";
    static final String CPCFINDER = "https://cpcfinder.com/";

    private BoardFetch() {
    }

    /** 一条榜单。 */
    static final class Board {
        final String uk;
        final String name;
        final long startAt;
        final int views;

        Board(String uk, String name, long startAt, int views) {
            this.uk = uk;
            this.name = name;
            this.startAt = startAt;
            this.views = views;
        }

        /** 榜单在 RankLand 上的地址。 */
        String url() {
            return RANKLAND + "/ranklist/" + uk;
        }
    }

    /** 取 RankLand 最近的一批榜单（按开始时间倒序）。 */
    static List<Board> rankland() throws M.OjError {
        String body = Net.get(RANKLAND + "/api/v2/public/contests?page=1&pageSize=30",
                RANKLAND + "/search");
        List<Board> out = new ArrayList<Board>();
        try {
            JSONObject root = new JSONObject(body);
            JSONObject data = root.optJSONObject("data");
            JSONArray arr = data == null ? null : data.optJSONArray("contests");
            if (arr == null) throw new M.OjError(M.Err.BROKEN, "返回里没有 contests");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String uk = o.optString("uk", "");
                if (uk.isEmpty()) continue;
                String name = title(o);
                if (name.isEmpty()) name = uk;
                out.add(new Board(uk, name, time(o.optString("startAt", "")),
                        o.optInt("viewCount", 0)));
            }
        } catch (M.OjError e) {
            throw e;
        } catch (Exception e) {
            throw new M.OjError(M.Err.BROKEN, "解析失败：" + e.getMessage());
        }
        if (out.isEmpty()) throw new M.OjError(M.Err.BROKEN, "没有取到榜单");
        Collections.sort(out, new Comparator<Board>() {
            @Override
            public int compare(Board a, Board b) {
                return Long.compare(b.startAt, a.startAt);
            }
        });
        return out;
    }

    /** title 可能是 {"zh-CN":…}、{"fallback":…} 或纯字符串。 */
    private static String title(JSONObject o) {
        Object t = o.opt("title");
        if (t instanceof String) return (String) t;
        if (t instanceof JSONObject) {
            JSONObject j = (JSONObject) t;
            String s = j.optString("zh-CN", "");
            if (!s.isEmpty()) return s;
            s = j.optString("fallback", "");
            if (!s.isEmpty()) return s;
            s = j.optString("en", "");
            if (!s.isEmpty()) return s;
        }
        return o.optString("name", "");
    }

    private static long time(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        String[] pats = {"yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"};
        for (String p : pats) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                Date d = f.parse(iso);
                if (d != null) return d.getTime();
            } catch (Exception ignore) {
            }
        }
        return 0;
    }
}