package com.ojhelper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** 应用内打开榜单站点（纯 framework WebView，不需要任何权限）。 */
public class BoardView extends Activity {

    static final String EXTRA_URL = "url";
    static final String EXTRA_TITLE = "title";

    private WebView web;
    private ProgressBar bar;
    private TextView banner;
    private String title = "";
    private final Handler main = new Handler(Looper.getMainLooper());

    /** 慢加载提示：XCPCIO 这类 SPA 子页要拉 0.5～1.4 MB 脚本 + 近 1 MB 数据，手机上来得慢。 */
    private final Runnable slow = new Runnable() {
        @Override
        public void run() {
            showBanner("页面较大，仍在加载…  点右上角「浏览器」可改用系统浏览器打开", Ui.SUB);
        }
    };

    static void open(Activity from, String title, String url) {
        Intent i = new Intent(from, BoardView.class);
        i.putExtra(EXTRA_TITLE, title);
        i.putExtra(EXTRA_URL, url);
        from.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (TextUtils.isEmpty(title)) title = "榜单";

        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Ui.CARD);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.CARD);

        // 顶栏：返回 / 标题 / 用浏览器打开
        LinearLayout head = Ui.row(this);
        head.setBackgroundColor(Ui.CARD);
        head.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = Ui.tv(this, "‹ 返回", 14f, Ui.ACCENT, false);
        back.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goBack();
            }
        });
        head.addView(back);

        TextView name = Ui.tv(this, title, 15f, Ui.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(name, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView browser = Ui.tv(this, "浏览器", 13f, Ui.SUB, false);
        browser.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        browser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (web == null) return;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(web.getUrl())));
                } catch (Exception e) {
                    Ui.toast(BoardView.this, "没有可用的浏览器");
                }
            }
        });
        head.addView(browser);

        root.addView(head);
        root.addView(Ui.divider(this));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setVisibility(View.GONE);
        root.addView(bar, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 2)));

        banner = Ui.tv(this, "", 12f, Ui.SUB, false);
        banner.setVisibility(View.GONE);
        banner.setBackgroundColor(0xFFFFF7E0);
        banner.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        root.addView(banner, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        if (Build.VERSION.SDK_INT >= 21) {
            // 榜单页会引用 http 的队徽/图表，混用内容直接放行，免得整页卡在阻塞状态
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        s.setUserAgentString(s.getUserAgentString() + " OjHelper/1.0");
        web.setBackgroundColor(Color.WHITE);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String u) {
                return false; // 站内跳转都在应用里看
            }

            @Override
            public void onPageStarted(WebView v, String u, android.graphics.Bitmap f) {
                main.removeCallbacks(slow);
                main.postDelayed(slow, 7000L);
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                main.removeCallbacks(slow);
                hideBanner();
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                main.removeCallbacks(slow);
                // 只有主文档失败才算错误，子资源失败（图片、统计脚本）忽略
                if (req != null && req.isForMainFrame()) {
                    showBanner("页面加载失败（" + err.getDescription() + "）  可用右上角「浏览器」打开", Ui.RED);
                }
            }

            @Override
            public void onReceivedError(WebView v, int code, String desc, String failingUrl) {
                main.removeCallbacks(slow);
                showBanner("页面加载失败（" + desc + "）  可用右上角「浏览器」打开", Ui.RED);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                if (bar == null) return;
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                if (m != null) Log.d("ojweb", m.message() + " @" + m.lineNumber());
                return true;
            }
        });

        FrameLayout holder = new FrameLayout(this);
        holder.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(holder, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        if (!TextUtils.isEmpty(url)) web.loadUrl(url);
    }

    private void showBanner(String msg, int color) {
        if (banner == null) return;
        banner.setText(msg);
        banner.setTextColor(color);
        banner.setVisibility(View.VISIBLE);
    }

    private void hideBanner() {
        if (banner != null) banner.setVisibility(View.GONE);
    }

    private void goBack() {
        if (web != null && web.canGoBack()) web.goBack();
        else finish();
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(slow);
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}