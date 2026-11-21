package com.ojhelper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * 极简饼图。不引第三方图表库：直接按「圆心 + 扇形」画，
 * 标签写在扇区内部（只有够大的扇区才写，太小的交给下面的图例）。
 */
final class PieChart extends View {

    private final int[] colors;
    private final String[] labels;
    private final int[] values;
    private final int total;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    PieChart(Context c, int[] colors, String[] labels, int[] values) {
        super(c);
        this.colors = colors;
        this.labels = labels;
        this.values = values;
        int s = 0;
        for (int v : values) s += v;
        this.total = s;

        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Ui.dp(c, 1.5f));
        edge.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        if (w <= 0) w = Ui.dp(getContext(), 240);
        int h = Math.min(w, Ui.dp(getContext(), 236));
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (total <= 0) return;
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f - Ui.dp(getContext(), 3);
        box.set(cx - r, cy - r, cx + r, cy + r);

        float start = -90f;
        for (int i = 0; i < values.length; i++) {
            float sweep = 360f * values[i] / total;
            fill.setColor(colors[i]);
            cv.drawArc(box, start, sweep, true, fill);
            cv.drawArc(box, start, sweep, true, edge);

            // 扇区够大才写标签，小的（< 9%）留给下方图例，避免文字压在一起
            if (sweep >= 32f) {
                double mid = Math.toRadians(start + sweep / 2f);
                float lx = (float) (cx + Math.cos(mid) * r * 0.62);
                float ly = (float) (cy + Math.sin(mid) * r * 0.62);
                text.setTextSize(Math.max(Ui.dp(getContext(), 11), Math.min(r * 0.135f, Ui.dp(getContext(), 16))));
                text.setColor(luminance(colors[i]) > 0.62 ? 0xFF24272B : 0xFFFFFFFF);
                cv.drawText(labels[i], lx, ly + text.getTextSize() * 0.34f, text);
            }
            start += sweep;
        }
    }

    /** 0..1 的感知亮度，用来决定标签该用黑字还是白字。 */
    private static float luminance(int c) {
        return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
    }
}