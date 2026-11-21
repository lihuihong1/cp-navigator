package com.ojhelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/** HTTP 访问与错误归类。 */
final class Net {

    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static boolean inited;

    static synchronized void init() {
        if (inited) return;
        inited = true;
        // 洛谷需要接受 C3VK cookie 才能通过 302 自跳转
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
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

    private static String request(String url, String referer, String body,
                                  String contentType, String charset) throws M.OjError {
        init();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod(body == null ? "GET" : "POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "*/*");
            c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            c.setRequestProperty("Accept-Encoding", "gzip");
            if (referer != null) c.setRequestProperty("Referer", referer);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", contentType);
                c.getOutputStream().write(body.getBytes("UTF-8"));
            }

            int code = c.getResponseCode();
            if (code == 404) throw new M.OjError(M.Err.NOT_FOUND, "页面不存在");
            if (code == 429 || code == 403) throw new M.OjError(M.Err.RATE_LIMITED, "HTTP " + code);
            if (code >= 500) throw new M.OjError(M.Err.BROKEN, "HTTP " + code);
            if (code >= 400) throw new M.OjError(M.Err.BROKEN, "HTTP " + code);

            InputStream in = c.getInputStream();
            String enc = c.getContentEncoding();
            if (enc != null && enc.toLowerCase(Locale.ROOT).contains("gzip")) {
                in = new GZIPInputStream(in);
            }
            byte[] data = readAll(in);
            in.close();

            String cs = charset;
            if (cs == null) {
                String ct = c.getContentType();
                if (ct != null) {
                    int i = ct.toLowerCase(Locale.ROOT).indexOf("charset=");
                    if (i >= 0) {
                        cs = ct.substring(i + 8).trim();
                        int semi = cs.indexOf(';');
                        if (semi > 0) cs = cs.substring(0, semi);
                        cs = cs.replace("\"", "").trim();
                    }
                }
            }
            if (cs == null || cs.isEmpty()) cs = "UTF-8";
            String text = new String(data, cs);
            String head = text.length() > 4000 ? text.substring(0, 4000) : text;
            String low = head.toLowerCase(Locale.ROOT);
            if (low.contains("just a moment") || low.contains("cf-browser-verification")
                    || low.contains("security check") || low.contains("访问验证")
                    || low.contains("请完成安全验证")) {
                throw new M.OjError(M.Err.RATE_LIMITED, "被站点风控拦截");
            }
            return text;
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