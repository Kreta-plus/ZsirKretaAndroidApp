package hu.zsirkreta.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import hu.zsirkreta.app.util.Fmt;

/** Kis diagramkönyvtár külső függőség nélkül (a webes charts.js megfelelője), belépő animációval. */
public final class Charts {
    private Charts() {}

    private static final int[] PALETTE = {0xFF6C5CE7, 0xFF2ECC8F, 0xFFF4C542, 0xFFEF5B5B, 0xFF38BDF8, 0xFFF39A3D, 0xFFD946EF, 0xFF8BD450};
    public static int palette(int i) { return PALETTE[i % PALETTE.length]; }

    public static final class Pt {
        public final String label; public final double value;
        public Pt(String label, double value) { this.label = label; this.value = value; }
    }

    public static final class Item {
        public final String label; public final double value; public final Integer color;
        public Item(String label, double value, Integer color) { this.label = label; this.value = value; this.color = color; }
        public Item(String label, double value) { this(label, value, null); }
    }

    /** Közös ős: bejövő animáció (0 → 1). */
    private abstract static class Anim extends View {
        float progress = 0f;
        private ValueAnimator anim;
        /** Kívánt méret px-ben; a szélesség 0 = a rendelkezésre álló teljes szélesség. */
        int prefW = 0, prefH = 0;
        Anim(Context c) { super(c); }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w = prefW > 0 ? resolveSize(prefW, wSpec) : MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED ? Ui.dp(300) : MeasureSpec.getSize(wSpec);
            setMeasuredDimension(w, resolveSize(prefH, hSpec));
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(700);
            anim.setInterpolator(new DecelerateInterpolator(1.6f));
            anim.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); });
            anim.start();
        }

        @Override protected void onDetachedFromWindow() {
            if (anim != null) anim.cancel();
            super.onDetachedFromWindow();
        }

        float sp(float v) { return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v * Theme.fs, getResources().getDisplayMetrics()); }
    }

    // ---------- vonaldiagram ----------
    private static final class LineView extends Anim {
        private final List<Pt> pts;
        private final Double goal;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        LineView(Context c, List<Pt> pts, Double goal) {
            super(c);
            this.pts = pts; this.goal = goal;
            prefH = Ui.dp(200);
        }

        @Override protected void onDraw(Canvas cv) {
            int n = pts.size();
            float W = getWidth(), H = getHeight();
            float L = Ui.dp(30), R = Ui.dp(10), T = Ui.dp(14), B = Ui.dp(26);
            double lo = Double.MAX_VALUE;
            for (Pt q : pts) lo = Math.min(lo, q.value);
            lo = Math.min(lo, goal != null ? goal : 5);
            double min = Math.max(1, Math.floor(lo * 2) / 2 - 0.5), max = 5;
            float cw = W - L - R, ch = H - T - B;

            p.setTextSize(sp(11));
            p.setTypeface(Typeface.DEFAULT);
            p.setStrokeWidth(Ui.dp(1));
            for (int v = (int) Math.ceil(min); v <= max; v++) {
                float y = (float) (T + (max - v) * ch / (max - min));
                p.setColor(Theme.border); p.setStyle(Paint.Style.STROKE);
                cv.drawLine(L, y, W - R, y, p);
                p.setColor(Theme.muted); p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.RIGHT);
                cv.drawText(String.valueOf(v), L - Ui.dp(8), y + Ui.dp(4), p);
            }
            if (goal != null) {
                float y = (float) (T + (max - goal) * ch / (max - min));
                p.setColor(Theme.g[3]); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Ui.dp(1.4f));
                p.setPathEffect(new DashPathEffect(new float[]{Ui.dp(5), Ui.dp(5)}, 0));
                cv.drawLine(L, y, W - R, y, p);
                p.setPathEffect(null);
                p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.RIGHT);
                cv.drawText("cél " + Fmt.num(goal), W - R, y - Ui.dp(5), p);
            }
            cv.save();
            cv.clipRect(0, 0, W * progress + 1, H);
            Path line = new Path(), area = new Path();
            for (int i = 0; i < n; i++) {
                float x = L + i * cw / (n - 1);
                float y = (float) (T + (max - pts.get(i).value) * ch / (max - min));
                if (i == 0) { line.moveTo(x, y); area.moveTo(x, y); } else { line.lineTo(x, y); area.lineTo(x, y); }
            }
            area.lineTo(L + cw, H - B);
            area.lineTo(L, H - B);
            area.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, T, 0, H - B, Theme.withAlpha(Theme.accent, 0.35f), Theme.withAlpha(Theme.accent, 0f), Shader.TileMode.CLAMP));
            cv.drawPath(area, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setColor(Theme.accent); p.setStrokeWidth(Ui.dp(2.6f));
            p.setStrokeJoin(Paint.Join.ROUND); p.setStrokeCap(Paint.Cap.ROUND);
            cv.drawPath(line, p);
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < n; i++) {
                float x = L + i * cw / (n - 1);
                float y = (float) (T + (max - pts.get(i).value) * ch / (max - min));
                p.setColor(Theme.surface);
                cv.drawCircle(x, y, Ui.dp(5), p);
                p.setColor(Theme.accent);
                cv.drawCircle(x, y, Ui.dp(3.5f), p);
            }
            cv.restore();
            int every = (int) Math.ceil(n / 7.0);
            p.setColor(Theme.muted); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(sp(11));
            for (int i = 0; i < n; i++) {
                if (i % every == 0 || i == n - 1) cv.drawText(pts.get(i).label, L + i * cw / (n - 1), H - Ui.dp(6), p);
            }
        }
    }

    public static View line(Context c, List<Pt> points, Double goal) {
        if (points.size() < 2) return Ui.empty(c, "Még kevés adat a diagramhoz");
        return new LineView(c, points, goal);
    }

    // ---------- oszlopdiagram ----------
    private static final class BarsView extends Anim {
        private final List<Item> items;
        private final Integer color;
        private final Function<Double, String> fmt;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final double max;

        BarsView(Context c, List<Item> items, Integer color, Function<Double, String> fmt) {
            super(c);
            this.items = items; this.color = color; this.fmt = fmt;
            double m = 1;
            for (Item i : items) m = Math.max(m, i.value);
            max = m;
            prefH = Ui.dp(190);
        }

        @Override protected void onDraw(Canvas cv) {
            int n = items.size();
            float W = getWidth(), H = getHeight(), pad = Ui.dp(6);
            float step = (W - pad * 2) / n;
            float bw = Math.min(Ui.dp(40), step - Ui.dp(8));
            float labelH = Ui.dp(22), topH = Ui.dp(22);
            RectF r = new RectF();
            for (int i = 0; i < n; i++) {
                Item it = items.get(i);
                float x = pad + i * step + (step - bw) / 2;
                float full = (float) ((H - labelH - topH) * it.value / max);
                float bh = full * progress;
                r.set(x, H - labelH - bh, x + bw, H - labelH);
                p.setStyle(Paint.Style.FILL);
                p.setColor(it.color != null ? it.color : color != null ? color : palette(i));
                float rad = Math.min(Ui.dp(7), bw / 2);
                cv.drawRoundRect(r, rad, rad, p);
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(Theme.text); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(sp(12));
                cv.drawText(fmt.apply(it.value), x + bw / 2, H - labelH - bh - Ui.dp(6), p);
                p.setColor(Theme.muted); p.setTypeface(Typeface.DEFAULT); p.setTextSize(sp(11));
                cv.drawText(it.label, x + bw / 2, H - Ui.dp(5), p);
            }
        }
    }

    public static View bars(Context c, List<Item> items, Integer color, Function<Double, String> fmt) {
        if (items.isEmpty()) return Ui.empty(c, "Nincs adat");
        return new BarsView(c, items, color, fmt);
    }

    public static View bars(Context c, List<Item> items) { return bars(c, items, null, v -> String.valueOf(Math.round(v))); }

    /** Vízszintes sávok (címke, érték, sáv egymás alatt). */
    public static View hbars(Context c, List<Item> items, Double max, Function<Double, String> fmt, Integer color) {
        if (items.isEmpty()) return Ui.empty(c, "Nincs adat");
        double m = 1;
        if (max != null) m = max; else for (Item i : items) m = Math.max(m, i.value);
        LinearLayout l = Ui.vbox(c, 12);
        for (Item i : items) {
            int col = i.color != null ? i.color : color != null ? color : Theme.accent;
            l.addView(Ui.barRow(c, i.label, fmt.apply(i.value), (float) (i.value / m), col));
        }
        return l;
    }

    // ---------- fánkdiagram ----------
    private static final class DonutView extends Anim {
        private final List<Item> items;
        private final String center;
        private final double total;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        DonutView(Context c, List<Item> items, String center) {
            super(c);
            this.items = items; this.center = center;
            double t = 0;
            for (Item i : items) t += i.value;
            total = t;
            prefW = prefH = Ui.dp(140);
        }

        @Override protected void onDraw(Canvas cv) {
            float W = getWidth(), H = getHeight();
            float stroke = W * 20f / 140f, r = W * 52f / 140f;
            RectF oval = new RectF(W / 2 - r, H / 2 - r, W / 2 + r, H / 2 + r);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setStrokeCap(Paint.Cap.BUTT);
            float start = -90f;
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                if (it.value <= 0) continue;
                float sweep = (float) (it.value / total * 360f) * progress;
                p.setColor(it.color != null ? it.color : palette(i));
                cv.drawArc(oval, start, Math.max(0f, sweep - 0.8f), false, p);
                start += (float) (it.value / total * 360f) * progress;
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Theme.text); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(sp(22));
            cv.drawText(center, W / 2, H / 2 + Ui.dp(2), p);
            p.setColor(Theme.muted); p.setTypeface(Typeface.DEFAULT); p.setTextSize(sp(11));
            cv.drawText("összesen", W / 2, H / 2 + Ui.dp(18), p);
        }
    }

    public static View donut(Context c, List<Item> items, String center) {
        double total = 0;
        for (Item i : items) total += i.value;
        if (total <= 0) return Ui.empty(c, "Nincs adat");
        LinearLayout row = Ui.hbox(c, 18);
        row.addView(new DonutView(c, items, center));
        LinearLayout legend = Ui.vbox(c, 8);
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            LinearLayout l = Ui.hbox(c, 8);
            l.addView(Ui.dot(c, it.color != null ? it.color : palette(i)));
            TextView t = Ui.body(c, it.label);
            l.addView(t, Ui.lp(Ui.WRAP, Ui.WRAP));
            l.addView(Ui.bold(c, String.valueOf(Math.round(it.value))));
            legend.addView(l);
        }
        row.addView(legend, Ui.lp(0, Ui.WRAP, 1f));
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }
}
