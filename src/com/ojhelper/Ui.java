package com.ojhelper;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 轻量 UI 工具：不用 AndroidX，全部用系统控件 + 代码布局。 */
final class Ui {

    static final int BG = 0xFFF3F4F6;
    static final int CARD = 0xFFFFFFFF;
    static final int TEXT = 0xFF15181C;
    static final int SUB = 0xFF6B7280;
    static final int LINE = 0xFFE5E7EB;
    static final int ACCENT = 0xFF2563EB;
    static final int RED = 0xFFDC2626;
    static final int GREEN = 0xFF16A34A;

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    static int sp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                c.getResources().getDisplayMetrics());
    }

    static TextView tv(Context c, String text, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static GradientDrawable stroke(int fill, int strokeColor, float radiusDp, int strokeWidthDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, strokeWidthDp), strokeColor);
        return d;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f)));
        v.setLayoutParams(p);
        return v;
    }

    /** 白色圆角卡片容器。 */
    static LinearLayout card(Context c, int padDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(CARD, 14, c));
        int p = dp(c, padDp);
        l.setPadding(p, p, p, p);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, Context c,
                                             float l, float t, float r, float b) {
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    /** 小圆角标签（OJ 名）。 */
    static TextView badge(Context c, String text, int color) {
        TextView t = tv(c, text, 11f, 0xFFFFFFFF, true);
        GradientDrawable d = round(color, 5, c);
        t.setBackground(d);
        t.setPadding(dp(c, 6), dp(c, 2), dp(c, 6), dp(c, 2));
        return t;
    }

    static TextView button(Context c, String text, int color, boolean filled) {
        TextView t = tv(c, text, 13f, filled ? 0xFFFFFFFF : color, true);
        t.setGravity(android.view.Gravity.CENTER);
        if (filled) {
            t.setBackground(round(color, 9, c));
        } else {
            t.setBackground(stroke(0x00000000, color, 9, 1, c));
        }
        t.setPadding(dp(c, 10), dp(c, 7), dp(c, 10), dp(c, 7));
        t.setClickable(true);
        return t;
    }

    static void toast(Context c, String msg) {
        android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** 页面顶部标题条。 */
    static LinearLayout header(Context c, String title, String subtitle) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setBackgroundColor(CARD);
        int p = dp(c, 16);
        h.setPadding(p, dp(c, 14), p, dp(c, 12));

        TextView t = tv(c, title, 21f, TEXT, true);
        h.addView(t);

        if (subtitle != null) {
            TextView s = tv(c, subtitle, 12f, SUB, false);
            s.setPadding(0, dp(c, 3), 0, 0);
            h.addView(s);
        }
        h.addView(divider(c));
        return h;
    }
}