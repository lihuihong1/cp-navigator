package com.ojhelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 7 个 OJ 的解题数 / 排位分抓取。全部为匿名可访问的公开数据。 */
final class StatsFetch {

    static M.Stat fetch(String oj, String handle) throws M.OjError {
        if (handle == null || handle.trim().isEmpty()) throw new M.OjError(M.Err.EMPTY, "");
        String h = handle.trim();
        if (M.CF.equals(oj)) return cf(h);
        if (M.ATC.equals(oj)) return atcoder(h);
        if (M.LC.equals(oj)) return leetcode(h);
        if (M.LG.equals(oj)) return luogu(h);
        if (M.NC.equals(oj)) return nowcoder(h);
        if (M.VJ.equals(oj)) return vjudge(h);
        if (M.HDU.equals(oj)) return hdu(h);
        throw new M.OjError(M.Err.BROKEN, "未知 OJ");
    }

    static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /** 把一段说明拼到 extra 后面（extra 为空时直接用它）。 */
    static String append(String cur, String more) {
        if (cur == null || cur.isEmpty()) return more;
        return cur + " · " + more;
    }

    /** 10.07 -> "10.1"，10.0 -> "10"。 */
    static String trimPct(double d) {
        double r = Math.round(d * 10) / 10.0;
        if (r == Math.floor(r)) return String.valueOf((long) r);
        return String.valueOf(r);
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    // ---------------- ojhunt：第三方聚合，一次请求直出解题数 ----------------

    /**
     * 从 ojhunt 取解题数。成功返回 >=0；任何异常 / error:true / 没有 solved 都返回 -1，
     * 由调用方回退到各自的原始抓取逻辑 —— 这个服务挂了不能影响整体查询。
     */
    private static int ojhunt(String oj, String h) {
        try {
            String body = Net.get("https://ojhunt.com/api/crawlers/" + oj + "/" + enc(h),
                    "https://ojhunt.com/");
            JSONObject o = new JSONObject(body);
            if (o.optBoolean("error", false)) return -1;
            JSONObject data = o.optJSONObject("data");
            if (data == null || !data.has("solved")) return -1;
            int n = data.optInt("solved", -1);
            return n < 0 ? -1 : n;
        } catch (Exception e) {
            return -1;
        }
    }

    // ---------------- Codeforces ----------------

    private static M.Stat cf(String h) throws M.OjError {
        M.Stat s = M.Stat.of(M.CF);
        String info = Net.get("https://codeforces.com/api/user.info?handles=" + enc(h),
                "https://codeforces.com/profile/" + enc(h));
        JSONObject root;
        try {
            root = new JSONObject(info);
        } catch (Exception e) {
            throw new M.OjError(M.Err.BROKEN, "返回非 JSON");
        }
        if (!"OK".equals(root.optString("status"))) {
            String cmt = root.optString("comment", "");
            if (cmt.contains("not found")) throw new M.OjError(M.Err.NOT_FOUND, "handle 不存在");
            if (cmt.contains("limit")) throw new M.OjError(M.Err.RATE_LIMITED, "CF 限制 2 秒 1 次");
            throw new M.OjError(M.Err.BROKEN, cmt);
        }
        JSONArray ra = root.optJSONArray("result");
        if (ra == null || ra.length() == 0) throw new M.OjError(M.Err.NOT_FOUND, "handle 不存在");
        JSONObject u = ra.optJSONObject(0);
        if (u.has("rating") && !u.isNull("rating")) s.rating = Double.valueOf(u.optDouble("rating"));
        StringBuilder ex = new StringBuilder();
        if (u.has("maxRating") && !u.isNull("maxRating")) ex.append("最高 ").append(u.optInt("maxRating"));
        String rank = u.optString("rank", "");
        if (!rank.isEmpty()) {
            if (ex.length() > 0) ex.append(" · ");
            ex.append(rank);
        }
        s.extra = ex.toString();

        // 解题数：优先 ojhunt（一次请求，几百毫秒）；拿不到再回退到拉全量提交流程。
        // 原流程对活跃号要请求 2–5 页、页间 sleep 2.1 秒，是「我的」页最慢的一项。
        int fast = ojhunt("codeforces", h);
        if (fast >= 0) {
            s.solved = Integer.valueOf(fast);
            return s;
        }

        // 回退：拉取全部提交后按题目去重。CF 限流 1 次 / 2 秒。
        sleep(2100);
        Set<String> solved = new HashSet<String>();
        int from = 1;
        final int COUNT = 10000;
        for (int page = 0; page < 5; page++) {
            String body = Net.get("https://codeforces.com/api/user.status?handle=" + enc(h)
                    + "&from=" + from + "&count=" + COUNT, "https://codeforces.com/submissions/" + enc(h));
            JSONObject r2;
            try {
                r2 = new JSONObject(body);
            } catch (Exception e) {
                throw new M.OjError(M.Err.BROKEN, "提交列表解析失败");
            }
            if (!"OK".equals(r2.optString("status"))) {
                String cmt = r2.optString("comment", "");
                if (cmt.contains("limit")) throw new M.OjError(M.Err.RATE_LIMITED, "CF 限制 2 秒 1 次");
                break;
            }
            JSONArray arr = r2.optJSONArray("result");
            if (arr == null || arr.length() == 0) break;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject sub = arr.optJSONObject(i);
                if (sub == null) continue;
                if (!"OK".equals(sub.optString("verdict"))) continue;
                JSONObject p = sub.optJSONObject("problem");
                if (p == null) continue;
                String key = p.optString("contestId", p.optString("problemsetName", ""))
                        + p.optString("index", p.optString("name", ""));
                solved.add(key);
            }
            if (arr.length() < COUNT) break;
            from += COUNT;
            sleep(2100);
        }
        s.solved = Integer.valueOf(solved.size());
        return s;
    }

    // ---------------- AtCoder ----------------

    private static M.Stat atcoder(String h) throws M.OjError {
        M.Stat s = M.Stat.of(M.ATC);
        String hist = Net.get("https://atcoder.jp/users/" + enc(h) + "/history/json",
                "https://atcoder.jp/users/" + enc(h));
        JSONArray arr = null;
        try {
            arr = new JSONArray(hist);
        } catch (Exception e) {
            throw new M.OjError(M.Err.BROKEN, "rating 历史解析失败");
        }
        if (arr.length() == 0) {
            // 空数组也可能是"存在但没打过 rated 比赛"，用主页确认
            String page = Net.get("https://atcoder.jp/users/" + enc(h), "https://atcoder.jp/");
            if (page.contains("404") && page.contains("Not Found")) {
                throw new M.OjError(M.Err.NOT_FOUND, "handle 不存在");
            }
        } else {
            JSONObject last = arr.optJSONObject(arr.length() - 1);
            if (last != null && last.has("NewRating") && !last.isNull("NewRating")) {
                s.rating = Double.valueOf(last.optDouble("NewRating"));
            }
        }
        // 解题数走 AtCoder Problems 的公开统计（第三方，但长期稳定）
        try {
            String ac = Net.get("https://kenkoooo.com/atcoder/atcoder-api/v3/user/ac_rank?user=" + enc(h),
                    "https://kenkoooo.com/");
            JSONObject o = new JSONObject(ac);
            if (o.has("count") && !o.isNull("count")) s.solved = Integer.valueOf(o.optInt("count"));
            if (o.has("rank") && !o.isNull("rank")) s.extra = "AC 排名 " + o.optInt("rank");
        } catch (M.OjError e) {
            if (e.kind == M.Err.NOT_FOUND && arr.length() == 0) throw e;
            s.extra = "解题数暂不可用";
        } catch (Exception ignored) {
            s.extra = "解题数暂不可用";
        }
        return s;
    }

    // ---------------- 力扣 ----------------

    private static M.Stat leetcode(String h) throws M.OjError {
        M.Stat s = M.Stat.of(M.LC);
        String q = "{\"query\":\"query u($u:String!){ userProfileUserQuestionProgress(userSlug:$u)"
                + "{ numAcceptedQuestions { difficulty count } }"
                + " userProfilePublicProfile(userSlug:$u){ siteRanking } }\","
                + "\"variables\":{\"u\":\"" + h.replace("\\", "").replace("\"", "") + "\"} }";
        String body = Net.postJson("https://leetcode.cn/graphql/", q,
                "https://leetcode.cn/u/" + enc(h) + "/");
        JSONObject root;
        try {
            root = new JSONObject(body);
        } catch (Exception e) {
            throw new M.OjError(M.Err.BROKEN, "返回非 JSON");
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            if (body.contains("not found") || body.contains("does not exist"))
                throw new M.OjError(M.Err.NOT_FOUND, "用户名不存在");
            throw new M.OjError(M.Err.BROKEN, root.optString("errors", "无 data"));
        }
        JSONObject prof = data.optJSONObject("userProfilePublicProfile");
        JSONObject prog = data.optJSONObject("userProfileUserQuestionProgress");
        if (prof == null && prog == null) throw new M.OjError(M.Err.NOT_FOUND, "用户名不存在");
        if (prog != null) {
            JSONArray nums = prog.optJSONArray("numAcceptedQuestions");
            int total = 0;
            if (nums != null) {
                for (int i = 0; i < nums.length(); i++) {
                    JSONObject o = nums.optJSONObject(i);
                    if (o != null) total += o.optInt("count");
                }
            }
            s.solved = Integer.valueOf(total);
        }
        if (prof != null) {
            int sr = prof.optInt("siteRanking", 0);
            if (sr > 0) s.extra = "站点排名 " + sr;
        }
        // 竞赛分要走 noj-go 端点：主站 /graphql 的 UserContestRankingNode 上没有 rating 字段
        String nq = "{\"query\":\"query userContestRankingInfo($userSlug:String!){"
                + " userContestRanking(userSlug:$userSlug){ rating globalRanking localRanking topPercentage }"
                + " }\",\"variables\":{\"userSlug\":\"" + h.replace("\\", "").replace("\"", "") + "\"},"
                + "\"operationName\":\"userContestRankingInfo\"}";
        String ratingErr = null;
        try {
            String nb = Net.postJson("https://leetcode.cn/graphql/noj-go/", nq,
                    "https://leetcode.cn/u/" + enc(h) + "/");
            JSONObject nr = new JSONObject(nb).optJSONObject("data");
            JSONObject uc = nr == null ? null : nr.optJSONObject("userContestRanking");
            if (uc != null) {
                double rt = uc.optDouble("rating", Double.NaN);
                if (!Double.isNaN(rt) && rt > 0) s.rating = Double.valueOf(rt);
                int gr = uc.optInt("globalRanking", 0);
                if (gr > 0) s.extra = append(s.extra, "竞赛排名 " + gr);
                double tp = uc.optDouble("topPercentage", 0);
                if (tp > 0 && tp <= 100) s.extra = append(s.extra, "前 " + trimPct(tp) + "%");
            } else {
                s.extra = append(s.extra, "未参加过竞赛");
            }
        } catch (Exception e) {
            ratingErr = "竞赛分获取失败";
        }
        if (ratingErr != null) s.extra = append(s.extra, ratingErr);
        return s;
    }

    // ---------------- 洛谷 ----------------

    private static M.Stat luogu(String h) throws M.OjError {
        M.Stat s = M.Stat.of(M.LG);
        String uid = null;
        if (h.matches("\\d+")) {
            uid = h;
        } else {
            String body = Net.get("https://www.luogu.com.cn/api/user/search?keyword=" + enc(h),
                    "https://www.luogu.com.cn/");
            try {
                JSONObject root = new JSONObject(body);
                JSONArray users = root.optJSONArray("users");
                if (users != null) {
                    for (int i = 0; i < users.length(); i++) {
                        JSONObject u = users.optJSONObject(i);
                        if (u != null && h.equalsIgnoreCase(u.optString("name"))) {
                            uid = String.valueOf(u.optInt("uid"));
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                throw new M.OjError(M.Err.BROKEN, "搜索接口解析失败");
            }
            if (uid == null) throw new M.OjError(M.Err.NOT_FOUND, "用户名不存在");
        }
        String body = Net.get("https://www.luogu.com.cn/api/user/info/" + enc(uid),
                "https://www.luogu.com.cn/user/" + enc(uid));
        JSONObject root;
        try {
            root = new JSONObject(body);
        } catch (Exception e) {
            if (body.contains("用户未找到")) throw new M.OjError(M.Err.NOT_FOUND, "用户不存在");
            throw new M.OjError(M.Err.BROKEN, "返回非 JSON");
        }
        JSONObject u = root.optJSONObject("user");
        if (u == null) {
            if (body.contains("用户未找到")) throw new M.OjError(M.Err.NOT_FOUND, "用户不存在");
            throw new M.OjError(M.Err.BROKEN, "缺少 user 字段");
        }
        if (u.has("passedProblemCount") && !u.isNull("passedProblemCount"))
            s.solved = Integer.valueOf(u.optInt("passedProblemCount"));
        if (u.has("elo") && !u.isNull("elo")) s.rating = Double.valueOf(u.optDouble("elo"));
        int rk = u.optInt("ranking", 0);
        if (rk > 0) s.extra = "排名 " + rk;
        return s;
    }

    // ---------------- 牛客 ----------------

    private static final Pattern NC_RATING = Pattern.compile("class=\"state-num rate-score4\">(\\d+)<");

    private static M.Stat nowcoder(String h) throws M.OjError {
        M.Stat s = M.Stat.of(M.NC);
        String body = Net.get("https://ac.nowcoder.com/acm/contest/profile/" + enc(h),
                "https://ac.nowcoder.com/acm/contest/profile/" + enc(h));
        if (body.contains("用户不存在") || body.contains("该用户不存在")) {
            throw new M.OjError(M.Err.NOT_FOUND, "id 不存在");
        }
        Matcher m = NC_RATING.matcher(body);
        if (m.find()) s.rating = Double.valueOf(Net.toInt(m.group(1), 0));
        int i = body.indexOf("Rating排名");
        if (i > 0) {
            String pre = body.substring(Math.max(0, i - 300), i);
            // 结构：<a href=".../rating-index">5540</a> </div> <span>Rating排名</span>
            Matcher rm = Pattern.compile(">(\\d+)</a>\\s*</div>\\s*<span>\\s*$", Pattern.DOTALL)
                    .matcher(pre);
            if (rm.find()) s.extra = "Rating 排名 " + rm.group(1);
        }
        // 牛客个人主页不公开解题数（页面里没有 AC 统计），只能借 ojhunt 补；
        // 拿不到就留空，不影响 rating 的展示。
        int fast = ojhunt("nowcoder", h);
        if (fast >= 0) s.solved = Integer.valueOf(fast);
        return s;
    }

    // ---------------- VJudge ----------------

    private static M.Stat vjudge(String h) throws M.OjError {
        M.Stat s = M.Stat.of(M.VJ);
        String body = Net.get("https://vjudge.net/user/" + enc(h), "https://vjudge.net/");
        // 解题数不在 HTML 表格里（probRecords 是空壳，行由前端 JS 填充），
        // 但站点把统计数字内嵌成了 <script type="application/json" id="profile-header-data">
        // 形如 {"username":"…","counts":{"ac7d":0,"acAll":97,"ac30d":0,"attAll":102},"ranks":{"all":67701}}
        String json = null;
        int a = body.indexOf("id=\"profile-header-data\"");
        if (a >= 0) {
            int b = body.indexOf('>', a);
            int c = b < 0 ? -1 : body.indexOf("</script>", b);
            if (b > 0 && c > b) json = body.substring(b + 1, c).trim();
        }
        if (json == null || json.isEmpty()) {
            // 页面还在（说明站点没改版到认不出用户），但没有那段 JSON —— 多半是改版
            if (body.contains("profile-stat-strip") || body.contains("probRecords")) {
                throw new M.OjError(M.Err.BROKEN, "VJudge 页面结构已变，读不到统计数据");
            }
            throw new M.OjError(M.Err.NOT_FOUND, "用户名不存在");
        }
        try {
            JSONObject o = new JSONObject(json);
            JSONObject counts = o.optJSONObject("counts");
            if (counts == null || !counts.has("acAll")) {
                throw new M.OjError(M.Err.NOT_FOUND, "用户名不存在");
            }
            s.solved = counts.optInt("acAll", 0);
            JSONObject ranks = o.optJSONObject("ranks");
            if (ranks != null && ranks.has("all")) {
                // VJudge 没有 rating，只在 extra 里报个总排名
                s.extra = "排名 " + ranks.optInt("all");
            }
        } catch (M.OjError e) {
            throw e;
        } catch (Exception e) {
            throw new M.OjError(M.Err.BROKEN, "无法解析 VJudge 统计");
        }
        return s;
    }

    // ---------------- HDU ----------------

    private static M.Stat hdu(String h) throws M.OjError {
        M.Stat s = M.Stat.of(M.HDU);
        String body = Net.get("https://acm.hdu.edu.cn/userstatus.php?user=" + enc(h),
                "https://acm.hdu.edu.cn/", "GB18030");
        if (body.contains("No such user")) throw new M.OjError(M.Err.NOT_FOUND, "id 不存在");
        Matcher ms = Pattern.compile("<td>Problems Solved</td>\\s*<td[^>]*>(\\d+)</td>").matcher(body);
        if (ms.find()) s.solved = Integer.valueOf(Net.toInt(ms.group(1), 0));
        Matcher mr = Pattern.compile("<td>Rank</td>\\s*<td[^>]*>(\\d+)</td>").matcher(body);
        if (mr.find()) s.extra = "排名 " + mr.group(1);
        if (s.solved == null) {
            // 页面结构变了 / 老账号显示不同：再试一次 ojhunt，仍拿不到才报接口失效
            int fast = ojhunt("hdu", h);
            if (fast >= 0) s.solved = Integer.valueOf(fast);
            else throw new M.OjError(M.Err.BROKEN, "页面结构变化");
        }
        return s;
    }
}