package com.ojhelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** HTTP 访问与错误归类。 */
final class Net {

    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    /** 手动跳转上限：洛谷会 302 回自己一次，正常 1 跳就够。 */
    private static final int MAX_HOPS = 6;

    private static boolean inited;

    /** 自己维护的极简 cookie 罐（host -> "a=1; b=2"）。 */
    private static final Map<String, String> JAR = new HashMap<String, String>();

    static synchronized void init() {
        if (inited) return;
        inited = true;
        // 故意不装 CookieHandler：Android 的 HttpURLConnection 在存在 CookieHandler 时
        // 会用自己的 jar 覆盖我们手写的 Cookie 头，洛谷这类靠 cookie 放行的站点就会 302 死循环。
        // 所以 cookie 完全由下面的 JAR 自己维护。
    }

    static String get(String url, String referer) throws M.OjError {
        return get(url, referer, null);
    }

    /**
     * @param charset 为空时按响应头/UTF-8 解码；HDU 等站点需显式传 GB18030。
     */
    static String get(String url, String referer, String charset) throws M.OjError {
        return request(url, referer, null, null, charset);
    }

    static String postJson(String url, String body, String referer) throws M.OjError {
        return request(url, referer, body, "application/json", null);
    }

    /** 一次请求的结果：要么是正文，要么是下一跳地址。 */
    private static final class Hop {
        String text;
        String location;
    }

    private static String request(String url, String referer, String body,
                                  String contentType, String charset) throws M.OjError {
        init();
        String cur = url;
        String ref = referer;
        String payload = body;

        for (int hop = 0; hop <= MAX_HOPS; hop++) {
            Hop h = once(cur, ref, payload, contentType, charset);
            if (h.location == null) return h.text;
            ref = cur;
            cur = resolve(cur, h.location);
            payload = null;   // 跳转后一律按 GET 重发，不再带请求体
        }
        throw new M.OjError(M.Err.BROKEN, "重定向次数过多（" + MAX_HOPS + " 跳）");
    }

    private static Hop once(String url, String referer, String body, String contentType,
                            String charset) throws M.OjError {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod(body == null ? "GET" : "POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            // 关键：自己处理跳转，否则平台实现会走到 "Too many follow-up requests: 21"
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "*/*");
            c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            c.setRequestProperty("Accept-Encoding", "gzip");
            String cookie = cookieFor(url);
            if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
            if (referer != null) c.setRequestProperty("Referer", referer);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", contentType);
                c.getOutputStream().write(body.getBytes("UTF-8"));
            }

            int code = c.getResponseCode();
            storeCookies(url, c.getHeaderFields());

            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                if (loc == null) throw new M.OjError(M.Err.BROKEN, "HTTP " + code + " 没有跳转地址");
                Hop h = new Hop();
                h.location = loc;
                return h;
            }
            if (code == 404) throw new M.OjError(M.Err.NOT_FOUND, "页面不存在");
            if (code == 429 || code == 403) throw new M.OjError(M.Err.RATE_LIMITED, "HTTP " + code);
            if (code >= 400) throw new M.OjError(M.Err.BROKEN, "HTTP " + code);

            InputStream in = c.getInputStream();
            String enc = c.getContentEncoding();
            if (enc != null && enc.toLowerCase(Locale.ROOT).contains("gzip")) {
                in = new GZIPInputStream(in);
            }
            byte[] data = readAll(in);
            in.close();

            String cset = charset;
            if (cset == null) {
                String ct = c.getContentType();
                if (ct != null) {
                    int i = ct.toLowerCase(Locale.ROOT).indexOf("charset=");
                    if (i >= 0) {
                        cset = ct.substring(i + 8).trim();
                        int semi = cset.indexOf(';');
                        if (semi > 0) cset = cset.substring(0, semi);
                        cset = cset.replace("\"", "").trim();
                    }
                }
            }
            if (cset == null || cset.isEmpty()) cset = "UTF-8";
            String text = new String(data, cset);
            String head = text.length() > 4000 ? text.substring(0, 4000) : text;
            String low = head.toLowerCase(Locale.ROOT);
            if (low.contains("just a moment") || low.contains("cf-browser-verification")
                    || low.contains("security check") || low.contains("访问验证")
                    || low.contains("请完成安全验证")) {
                throw new M.OjError(M.Err.RATE_LIMITED, "被站点风控拦截");
            }
            Hop h = new Hop();
            h.text = text;
            return h;
        } catch (M.OjError e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw new M.OjError(M.Err.NETWORK, "连接超时");
        } catch (UnknownHostException e) {
            throw new M.OjError(M.Err.NETWORK, "域名解析失败");
        } catch (javax.net.ssl.SSLException e) {
            throw new M.OjError(M.Err.NETWORK, "TLS 握手失败");
        } catch (IOException e) {
            String m = e.getMessage();
            if (m == null) m = e.getClass().getSimpleName();
            if (m.contains("ECONNREFUSED") || m.contains("ETIMEDOUT") || m.contains("reset")) {
                throw new M.OjError(M.Err.NETWORK, "连接被拒绝或中断");
            }
            throw new M.OjError(M.Err.NETWORK, m.length() > 60 ? m.substring(0, 60) : m);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String resolve(String base, String loc) throws M.OjError {
        try {
            return new URL(new URL(base), loc).toString();
        } catch (Exception e) {
            throw new M.OjError(M.Err.BROKEN, "跳转地址无法解析");
        }
    }

    private static String hostOf(String url) {
        try {
            String h = new URL(url).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    private static String cookieFor(String url) {
        synchronized (JAR) {
            return JAR.get(hostOf(url));
        }
    }

    /** 收集 Set-Cookie，只留 name=value（忽略 path/expires 等属性）。 */
    private static void storeCookies(String url, Map<String, List<String>> headers) {
        if (headers == null) return;
        String host = hostOf(url);
        if (host.isEmpty()) return;
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.equalsIgnoreCase("Set-Cookie")) continue;
            List<String> vs = e.getValue();
            if (vs == null) continue;
            for (String v : vs) {
                if (v == null) continue;
                int semi = v.indexOf(';');
                String pair = (semi > 0 ? v.substring(0, semi) : v).trim();
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                String name = pair.substring(0, eq).trim();
                String val = pair.substring(eq + 1).trim();
                if (name.isEmpty()) continue;
                if (val.isEmpty() || val.equalsIgnoreCase("deleted")) dropCookie(host, name);
                else putCookie(host, name, val);
            }
        }
    }

    private static void putCookie(String host, String name, String val) {
        synchronized (JAR) {
            String cur = JAR.get(host);
            StringBuilder sb = new StringBuilder();
            boolean replaced = false;
            if (cur != null && !cur.isEmpty()) {
                for (String part : cur.split(";")) {
                    String p = part.trim();
                    if (p.isEmpty()) continue;
                    int eq = p.indexOf('=');
                    String n = eq > 0 ? p.substring(0, eq).trim() : p;
                    if (n.equals(name)) {
                        sb.append(name).append('=').append(val);
                        replaced = true;
                    } else {
                        sb.append(p);
                    }
                    sb.append("; ");
                }
            }
            if (!replaced) sb.append(name).append('=').append(val).append("; ");
            int len = sb.length();
            JAR.put(host, len > 2 ? sb.substring(0, len - 2) : sb.toString());
        }
    }

    private static void dropCookie(String host, String name) {
        synchronized (JAR) {
            String cur = JAR.get(host);
            if (cur == null) return;
            StringBuilder sb = new StringBuilder();
            for (String part : cur.split(";")) {
                String p = part.trim();
                if (p.isEmpty()) continue;
                int eq = p.indexOf('=');
                String n = eq > 0 ? p.substring(0, eq).trim() : p;
                if (n.equals(name)) continue;
                sb.append(p).append("; ");
            }
            int len = sb.length();
            JAR.put(host, len > 2 ? sb.substring(0, len - 2) : "");
        }
    }

    /**
     * 裸 TLS 请求：请求头、跳转、cookie 全由自己控制，用于绕开平台实现对 Cookie 头的改写。
     * 只支持 GET，够洛谷这类站点用。
     */
    static String getRaw(String url, String referer) throws M.OjError {
        init();
        try {
            return raw(url, referer, 0);
        } catch (M.OjError e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw new M.OjError(M.Err.NETWORK, "连接超时");
        } catch (UnknownHostException e) {
            throw new M.OjError(M.Err.NETWORK, "域名解析失败");
        } catch (Exception e) {
            String m = e.getMessage();
            if (m == null) m = e.getClass().getSimpleName();
            throw new M.OjError(M.Err.NETWORK, m.length() > 60 ? m.substring(0, 60) : m);
        }
    }

    private static String raw(String url, String referer, int depth) throws Exception {
        if (depth > MAX_HOPS) {
            throw new M.OjError(M.Err.BROKEN, "重定向次数过多（" + MAX_HOPS + " 跳）");
        }
        URL u = new URL(url);
        boolean tls = "https".equalsIgnoreCase(u.getProtocol());
        String host = u.getHost();
        int port = u.getPort() > 0 ? u.getPort() : (tls ? 443 : 80);

        java.net.Socket sock;
        if (tls) {
            javax.net.ssl.SSLSocketFactory f =
                    (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
            javax.net.ssl.SSLSocket ss = (javax.net.ssl.SSLSocket) f.createSocket(host, port);
            ss.setSoTimeout(20000);                 // 握手也要有超时，否则弱网会一直转圈
            try {
                javax.net.ssl.SSLParameters p = ss.getSSLParameters();
                p.setServerNames(java.util.Collections.singletonList(
                        (javax.net.ssl.SNIServerName) new javax.net.ssl.SNIHostName(host)));
                p.setEndpointIdentificationAlgorithm("HTTPS");
                ss.setSSLParameters(p);
            } catch (Throwable ignored) {
                // 个别 ROM 没有 SNI 实现，退化成普通握手
            }
            ss.startHandshake();
            sock = ss;
        } else {
            sock = new java.net.Socket(host, port);
        }
        sock.setSoTimeout(20000);

        String path = u.getPath();
        if (path == null || path.isEmpty()) path = "/";
        if (u.getQuery() != null) path = path + "?" + u.getQuery();

        StringBuilder req = new StringBuilder(320);
        req.append("GET ").append(path).append(" HTTP/1.1\r\n")
                .append("Host: ").append(host).append("\r\n")
                .append("User-Agent: ").append(UA).append("\r\n")
                .append("Accept: text/html,application/json;q=0.9,*/*;q=0.8\r\n")
                .append("Accept-Language: zh-CN,zh;q=0.9,en;q=0.8\r\n")
                .append("Accept-Encoding: identity\r\n")
                .append("Connection: close\r\n");
        String ck = cookieFor(url);
        if (ck != null && !ck.isEmpty()) req.append("Cookie: ").append(ck).append("\r\n");
        if (referer != null) req.append("Referer: ").append(referer).append("\r\n");
        req.append("\r\n");

        byte[] all;
        try {
            java.io.OutputStream os = sock.getOutputStream();
            os.write(req.toString().getBytes("UTF-8"));
            os.flush();
            all = readAll(sock.getInputStream());
        } finally {
            try {
                sock.close();
            } catch (Exception ignored) {
            }
        }

        int split = -1;
        for (int i = 0; i + 3 < all.length; i++) {
            if (all[i] == '\r' && all[i + 1] == '\n' && all[i + 2] == '\r' && all[i + 3] == '\n') {
                split = i;
                break;
            }
        }
        if (split < 0) throw new M.OjError(M.Err.BROKEN, "响应格式异常");

        String head = new String(all, 0, split, "ISO-8859-1");
        byte[] body = new byte[all.length - split - 4];
        System.arraycopy(all, split + 4, body, 0, body.length);

        String[] lines = head.split("\r\n");
        int code = 0;
        if (lines.length > 0) {
            String[] p = lines[0].split(" ");
            if (p.length > 1) {
                try {
                    code = Integer.parseInt(p[1].trim());
                } catch (Exception ignored) {
                }
            }
        }

        String location = null;
        boolean chunked = false;
        List<String> setCookies = new ArrayList<String>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) continue;
            String k = lines[i].substring(0, colon).trim();
            String v = lines[i].substring(colon + 1).trim();
            if (k.equalsIgnoreCase("Set-Cookie")) {
                setCookies.add(v);
            } else if (k.equalsIgnoreCase("Location")) {
                location = v;
            } else if (k.equalsIgnoreCase("Transfer-Encoding")
                    && v.toLowerCase(Locale.ROOT).contains("chunked")) {
                chunked = true;
            }
        }
        if (!setCookies.isEmpty()) {
            Map<String, List<String>> m = new HashMap<String, List<String>>();
            m.put("Set-Cookie", setCookies);
            storeCookies(url, m);
        }

        if (code >= 300 && code < 400) {
            if (location == null) throw new M.OjError(M.Err.BROKEN, "HTTP " + code + " 没有跳转地址");
            return raw(resolve(url, location), url, depth + 1);
        }
        if (code == 404) throw new M.OjError(M.Err.NOT_FOUND, "页面不存在");
        if (code == 429 || code == 403) throw new M.OjError(M.Err.RATE_LIMITED, "HTTP " + code);
        if (code >= 400) throw new M.OjError(M.Err.BROKEN, "HTTP " + code);

        if (chunked) body = dechunk(body);
        return new String(body, "UTF-8");
    }

    /** 解 HTTP/1.1 的 chunked 正文。 */
    private static byte[] dechunk(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        int i = 0;
        while (i < in.length) {
            int eol = -1;
            for (int j = i; j + 1 < in.length; j++) {
                if (in[j] == '\r' && in[j + 1] == '\n') {
                    eol = j;
                    break;
                }
            }
            if (eol < 0) break;
            String sz = new String(in, i, eol - i, java.nio.charset.StandardCharsets.ISO_8859_1).trim();
            int semi = sz.indexOf(';');
            if (semi > 0) sz = sz.substring(0, semi).trim();
            int n;
            try {
                n = Integer.parseInt(sz, 16);
            } catch (Exception e) {
                break;
            }
            i = eol + 2;
            if (n == 0) break;
            if (i + n > in.length) n = in.length - i;
            out.write(in, i, n);
            i += n + 2;
        }
        return out.toByteArray();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    /** 常见 HTML 实体反转义（标题里 &amp; &quot; &#39; 很常见）。 */
    static String unescapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&nbsp;", " ");
    }

    /** 去掉 HTML 标签并压缩空白。 */
    static String stripTags(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    static int toInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}