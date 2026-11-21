package com.ojhelper;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 应用内提示条（替代系统 toast）。
 *
 * 系统 toast 的图标由 SystemUI 取应用图标绘制，部分 ROM 会退成默认的机器人占位图，
 * 且 API 30+ 已不允许 setView 自绘 toast。这里改成往当前 Activity 的 content 里贴一个
 * 黑色圆角浮层：左边固定用应用自己的图标，样式完全可控，全应用一致。
 */
final class Tip {

    private static final Handler H = new Handler(Looper.getMainLooper());
    private static View cur;
    private static Runnable pending;

    static void show(Context c, String msg) {
        if (!(c instanceof Activity) || msg == null || msg.length() == 0) {
            fallback(c, msg);
            return;
        }
        final Activity a = (Activity) c;
        if (a.isFinishing()) {
            fallback(c, msg);
            return;
        }
        ViewGroup root;
        try {
            root = (ViewGroup) a.findViewById(android.R.id.content);
        } catch (Throwable t) {
            root = null;
        }
        if (root == null) {
            fallback(c, msg);
            return;
        }
        hide();

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0202124);
        bg.setCornerRadius(Ui.dp(a, 22));
        box.setBackground(bg);
        int ph = Ui.dp(a, 14);
        int pv = Ui.dp(a, 10);
        box.setPadding(ph, pv, ph, pv);
        if (Build.VERSION.SDK_INT >= 21) box.setElevation(Ui.dp(a, 6));

        ImageView icon = new ImageView(a);
        int id = a.getResources().getIdentifier("ic_launcher", "mipmap", a.getPackageName());
        if (id != 0) {
            icon.setImageResource(id);
            LinearLayout.LayoutParams ip =
                    new LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22));
            ip.rightMargin = Ui.dp(a, 9);
            box.addView(icon, ip);
        }

        TextView tv = Ui.tv(a, msg, 13.5f, 0xFFFFFFFF, false);
        tv.setMaxLines(3);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setMaxWidth(Ui.dp(a, 250));
        box.addView(tv);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = Ui.dp(a, 96);
        box.setAlpha(0f);
        try {
            root.addView(box, lp);
        } catch (Throwable t) {
            fallback(c, msg);
            return;
        }
        cur = box;
        box.animate().alpha(1f).setDuration(140).start();

        pending = new Runnable() {
            @Override
            public void run() {
                fadeOut();
            }
        };
        H.postDelayed(pending, 1900);
    }

    private static void fadeOut() {
        final View v = cur;
        if (v == null) return;
        v.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
            @Override
            public void run() {
                detach(v);
            }
        }).start();
    }

    private static void detach(View v) {
        try {
            ViewGroup p = (ViewGroup) v.getParent();
            if (p != null) p.removeView(v);
        } catch (Throwable ignored) {
        }
        if (cur == v) cur = null;
    }

    private static void hide() {
        if (pending != null) {
            H.removeCallbacks(pending);
            pending = null;
        }
        if (cur != null) {
            View v = cur;
            cur = null;
            detach(v);
        }
    }

    private static void fallback(Context c, String msg) {
        try {
            android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}