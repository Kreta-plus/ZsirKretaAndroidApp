package hu.zsirkreta.app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.widget.CompoundButtonCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.function.Consumer;

import hu.zsirkreta.app.R;
import hu.zsirkreta.app.data.Models.Grade;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.util.Async;
import hu.zsirkreta.app.util.Dt;

/** A UI építőkockái. Minden szín és méret a futásidejű Theme-ből jön, így a téma váltása újrarajzolással érvényesül. */
public final class Ui {
    private Ui() {}

    public static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    public static final int B_DEFAULT = 0, B_PRIMARY = 1, B_DANGER = 2, B_GHOST = 3;
    public static final int C_NORMAL = 0, C_OK = 1, C_WARN = 2, C_BAD = 3, C_ACCENT = 4;

    private static float density = 3f;
    public static void init(Context c) { density = c.getResources().getDisplayMetrics().density; }
    public static int dp(float v) { return Math.round(v * density); }

    // ---------- színek, formák ----------
    public static GradientDrawable shape(int fill, float rDp) { return shape(fill, rDp, 0, 0); }

    public static GradientDrawable shape(int fill, float rDp, int stroke, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(rDp));
        if (strokeDp > 0) d.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return d;
    }

    public static Drawable ripple(Drawable content, float rDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(rDp));
        return new RippleDrawable(ColorStateList.valueOf(Theme.accentSoft), content, mask);
    }

    public static GradientDrawable gap(float dpSize, boolean vertical) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.TRANSPARENT);
        d.setSize(vertical ? 1 : dp(dpSize), vertical ? dp(dpSize) : 1);
        return d;
    }

    // ---------- szöveg ----------
    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * Theme.fs);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.12f);
        return t;
    }
    public static TextView h1(Context c, CharSequence s) { return text(c, s, 26, Theme.text, true); }
    public static TextView h2(Context c, CharSequence s) { return text(c, s, 20, Theme.text, true); }
    public static TextView h3(Context c, CharSequence s) { return text(c, s, 16, Theme.text, true); }
    public static TextView body(Context c, CharSequence s) { return text(c, s, 14.5f, Theme.text, false); }
    public static TextView bold(Context c, CharSequence s) { return text(c, s, 14.5f, Theme.text, true); }
    public static TextView muted(Context c, CharSequence s) { return text(c, s, 13.5f, Theme.muted, false); }
    public static TextView small(Context c, CharSequence s) { return text(c, s, 12, Theme.muted, false); }

    // ---------- elrendezés ----------
    public static LinearLayout vbox(Context c, float gapDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        if (gapDp > 0) { l.setDividerDrawable(gap(gapDp, true)); l.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE); }
        return l;
    }

    public static LinearLayout hbox(Context c, float gapDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        if (gapDp > 0) { l.setDividerDrawable(gap(gapDp, false)); l.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE); }
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    public static LinearLayout.LayoutParams lp(int w, int h, float weight) { return new LinearLayout.LayoutParams(w, h, weight); }

    /** Vízszintes sorban a „kitöltő” elem. */
    public static LinearLayout.LayoutParams grow() { return new LinearLayout.LayoutParams(0, WRAP, 1f); }

    public static LinearLayout.LayoutParams margin(LinearLayout.LayoutParams p, float l, float t, float r, float b) {
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    public static <T extends View> T add(ViewGroup parent, T child) { parent.addView(child); return child; }
    public static <T extends View> T add(ViewGroup parent, T child, ViewGroup.LayoutParams lp) { parent.addView(child, lp); return child; }
    public static <T extends View> T addGrow(LinearLayout parent, T child) { parent.addView(child, grow()); return child; }

    public static View spacer(Context c, float dpH) {
        View v = new View(c);
        v.setLayoutParams(lp(1, dp(dpH)));
        return v;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Theme.border);
        v.setLayoutParams(lp(MATCH, Math.max(1, dp(1))));
        return v;
    }

    public static View dot(Context c, int color) {
        View v = new View(c);
        v.setBackground(shape(color, 5));
        v.setLayoutParams(lp(dp(10), dp(10)));
        return v;
    }

    // ---------- háttér ----------
    /** Az oldal háttere a Beállítások → Háttér szerint (a webes html[data-bg] stílusok). */
    public static Drawable background() {
        switch (Theme.bgStyle) {
            case "glow": {
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{Theme.mix(Theme.bg, Theme.accent, 0.17f), Theme.mix(Theme.bg, Theme.accent, 0.05f), Theme.bg});
                return g;
            }
            case "dots": return new PatternBg(false);
            case "grid": return new PatternBg(true);
            default: {
                GradientDrawable g = new GradientDrawable();
                g.setColor(Theme.bg);
                return g;
            }
        }
    }

    /** Pöttyös (22 dp-s rács, halvány szövegszín) vagy kockás füzet (24 dp, halvány kiemelő szín) háttér. */
    private static final class PatternBg extends Drawable {
        private final boolean grid;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        PatternBg(boolean grid) { this.grid = grid; }
        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            c.drawColor(Theme.bg);
            if (grid) {
                p.setColor(Theme.withAlpha(Theme.accent, 0.11f));
                p.setStrokeWidth(Math.max(1, dp(1)));
                float step = dp(24);
                for (float x = b.left; x < b.right; x += step) c.drawLine(x, b.top, x, b.bottom, p);
                for (float y = b.top; y < b.bottom; y += step) c.drawLine(b.left, y, b.right, y, p);
            } else {
                p.setColor(Theme.withAlpha(Theme.text, 0.14f));
                float step = dp(22), r = dp(1.2f);
                for (float y = b.top + step / 2; y < b.bottom; y += step)
                    for (float x = b.left + step / 2; x < b.right; x += step) c.drawCircle(x, y, r, p);
            }
        }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(android.graphics.ColorFilter cf) {}
        @SuppressWarnings("deprecation") @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
    }

    // ---------- kártyák ----------
    public static GradientDrawable cardBg() {
        return shape(Theme.withAlpha(Theme.surface, Theme.cardAlpha), Theme.radius, Theme.cardBorder, Theme.borderW);
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = vbox(c, Theme.gap);
        l.setBackground(cardBg());
        int p = dp(Theme.pad);
        l.setPadding(p, p, p, p);
        return l;
    }

    /** Kártya címmel (és opcionálisan egy kis jobb oldali felirattal). */
    public static LinearLayout card(Context c, String title, CharSequence small) {
        LinearLayout l = card(c);
        LinearLayout head = hbox(c, 8);
        addGrow(head, h3(c, title));
        if (small != null && small.length() > 0) head.addView(small(c, small));
        l.addView(head, lp(MATCH, WRAP));
        return l;
    }

    /** Listaelem (.item): halvány hátterű sor. */
    public static LinearLayout item(Context c) {
        LinearLayout l = hbox(c, 12);
        l.setBackground(shape(Theme.surface2, Math.max(8, Theme.radius - 4)));
        int ph = dp(12), pv = dp(10);
        l.setPadding(ph, pv, ph, pv);
        return l;
    }

    public static void clickable(View v, Runnable r) {
        v.setOnClickListener(x -> r.run());
    }

    public static void rippleOn(View v, float rDp) {
        Drawable bg = v.getBackground();
        v.setBackground(ripple(bg, rDp));
    }

    public static View empty(Context c, String t) {
        TextView v = muted(c, t);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(8), dp(28), dp(8), dp(28));
        v.setLayoutParams(lp(MATCH, WRAP));
        return v;
    }

    // ---------- jegyek ----------
    public static TextView gradeBadge(Context c, String label, Double colorValue, float minDp, float sp) {
        TextView t = text(c, label, sp, Theme.onGrade(colorValue), true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(shape(Theme.gradeColor(colorValue), 10));
        t.setMinWidth(dp(minDp));
        t.setMinHeight(dp(minDp));
        t.setPadding(dp(6), 0, dp(6), 0);
        return t;
    }

    public static TextView gradeChip(Context c, Grade g) {
        String v = g.value == null ? "·" : String.valueOf(g.value);
        CharSequence label = v;
        if (g.weight != 1) {
            String w = String.valueOf(Math.round(g.weight * 100));
            SpannableString sp = new SpannableString(v + w);
            sp.setSpan(new SuperscriptSpan(), v.length(), sp.length(), 0);
            sp.setSpan(new RelativeSizeSpan(0.55f), v.length(), sp.length(), 0);
            label = sp;
        }
        TextView t = gradeBadge(c, "", g.value == null ? null : Double.valueOf(g.value), 34, 15);
        t.setText(label);
        return t;
    }

    // ---------- címkék ----------
    public static TextView chip(Context c, CharSequence s, int kind) {
        int col = kind == C_OK ? Theme.g[5] : kind == C_WARN ? Theme.g[3] : kind == C_BAD ? Theme.g[1] : kind == C_ACCENT ? Theme.accent : Theme.muted;
        TextView t = text(c, s, 12, col, true);
        t.setBackground(shape(Theme.surface2, 20, Theme.border, 1));
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        return t;
    }

    public static TextView chip(Context c, CharSequence s) { return chip(c, s, C_NORMAL); }

    // ---------- gombok ----------
    public static TextView button(Context c, String label, int kind, Runnable onClick) {
        TextView b = text(c, label, 14, kind == B_PRIMARY ? Theme.onAccent : kind == B_DANGER ? Theme.g[1] : Theme.text, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(44));
        b.setPadding(dp(16), dp(10), dp(16), dp(10));
        Drawable bg;
        if (kind == B_PRIMARY) {
            GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Theme.accent, Theme.accentStrong});
            gd.setCornerRadius(dp(Math.max(10, Theme.radius - 4)));
            bg = gd;
        } else if (kind == B_GHOST) {
            bg = shape(Color.TRANSPARENT, Math.max(10, Theme.radius - 4));
        } else {
            bg = shape(Theme.surface2, Math.max(10, Theme.radius - 4), Theme.border, 1);
        }
        b.setBackground(ripple(bg, Math.max(10, Theme.radius - 4)));
        b.setClickable(true);
        if (onClick != null) b.setOnClickListener(v -> onClick.run());
        return b;
    }

    public static TextView smallButton(Context c, String label, int kind, Runnable onClick) {
        TextView b = button(c, label, kind, onClick);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13 * Theme.fs);
        b.setMinHeight(dp(36));
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        return b;
    }

    public static ImageView icon(Context c, @DrawableRes int res, int color, float sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setColorFilter(color);
        v.setLayoutParams(lp(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    public static FrameLayout iconButton(Context c, @DrawableRes int res, int color, Runnable onClick) {
        FrameLayout f = new FrameLayout(c);
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setColorFilter(color);
        f.addView(i, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        f.setBackground(ripple(shape(Color.TRANSPARENT, 22), 22));
        f.setLayoutParams(lp(dp(44), dp(44)));
        f.setClickable(true);
        f.setOnClickListener(v -> onClick.run());
        return f;
    }

    // ---------- beviteli mezők ----------
    public static GradientDrawable inputBg() { return shape(Theme.surface2, Math.max(10, Theme.radius - 4), Theme.border, 1); }

    public static EditText input(Context c, String hint, String value) {
        EditText e = new EditText(c);
        e.setText(value == null ? "" : value);
        e.setHint(hint);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15 * Theme.fs);
        e.setTextColor(Theme.text);
        e.setHintTextColor(Theme.withAlpha(Theme.muted, 0.7f));
        e.setBackground(inputBg());
        e.setPadding(dp(14), dp(11), dp(14), dp(11));
        e.setMinHeight(dp(46));
        e.setSingleLine(true);
        e.setHighlightColor(Theme.accentSoft);
        e.setLayoutParams(lp(MATCH, WRAP));
        return e;
    }

    public static EditText numberInput(Context c, String hint, String value) {
        EditText e = input(c, hint, value);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    public static EditText textArea(Context c, String hint, String value, int minLines) {
        EditText e = input(c, hint, value);
        e.setSingleLine(false);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setMinLines(minLines);
        e.setGravity(Gravity.TOP | Gravity.START);
        return e;
    }

    public static void onText(EditText e, Consumer<String> cb) {
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { cb.accept(s.toString()); }
        });
    }

    /** Címkézett mező (web: label.f). */
    public static LinearLayout field(Context c, String label, View input) {
        LinearLayout l = vbox(c, 6);
        TextView t = text(c, label, 13, Theme.muted, true);
        l.addView(t, lp(MATCH, WRAP));
        l.addView(input, input.getLayoutParams() instanceof LinearLayout.LayoutParams ? input.getLayoutParams() : lp(MATCH, WRAP));
        return l;
    }

    // ---------- választók ----------
    /** Szegmentált váltó (web: .seg). */
    public static final class Seg extends LinearLayout {
        private final String[] keys;
        private final TextView[] btns;
        private String sel;

        Seg(Context c, String[] keys, String[] labels, String selected, Consumer<String> cb) {
            super(c);
            this.keys = keys; this.sel = selected;
            setOrientation(HORIZONTAL);
            setBackground(shape(Theme.surface2, Math.max(10, Theme.radius - 4), Theme.border, 1));
            setPadding(dp(3), dp(3), dp(3), dp(3));
            btns = new TextView[keys.length];
            for (int i = 0; i < keys.length; i++) {
                final String k = keys[i];
                TextView b = text(c, labels[i], 13.5f, Theme.muted, true);
                b.setGravity(Gravity.CENTER);
                b.setPadding(dp(8), dp(9), dp(8), dp(9));
                b.setSingleLine(true);
                b.setEllipsize(android.text.TextUtils.TruncateAt.END);
                b.setOnClickListener(v -> { select(k); cb.accept(k); });
                btns[i] = b;
                addView(b, new LayoutParams(0, WRAP, 1f));
            }
            paint();
        }

        public void select(String k) { sel = k; paint(); }

        private void paint() {
            float r = Math.max(8, Theme.radius - 7);
            for (int i = 0; i < keys.length; i++) {
                boolean on = keys[i].equals(sel);
                btns[i].setTextColor(on ? Theme.onAccent : Theme.muted);
                btns[i].setBackground(on ? shape(Theme.accent, r) : null);
            }
        }
    }

    public static Seg seg(Context c, String[] keys, String[] labels, String selected, Consumer<String> cb) {
        Seg s = new Seg(c, keys, labels, selected, cb);
        s.setLayoutParams(lp(MATCH, WRAP));
        return s;
    }

    public static MaterialSwitch toggle(Context c, boolean checked, Consumer<Boolean> cb) {
        MaterialSwitch sw = new MaterialSwitch(c);
        int[][] st = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(st, new int[]{Theme.onAccent, Theme.muted}));
        sw.setTrackTintList(new ColorStateList(st, new int[]{Theme.accent, Theme.surface2}));
        sw.setTrackDecorationTintList(new ColorStateList(st, new int[]{Theme.accent, Theme.border}));
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, on) -> cb.accept(on));
        return sw;
    }

    public static LinearLayout switchRow(Context c, String label, boolean checked, Consumer<Boolean> cb) {
        LinearLayout row = hbox(c, 12);
        TextView t = body(c, label);
        row.addView(t, grow());
        MaterialSwitch sw = toggle(c, checked, cb);
        row.addView(sw, lp(WRAP, WRAP));
        row.setOnClickListener(v -> sw.toggle());
        row.setMinimumHeight(dp(44));
        return row;
    }

    public static CheckBox check(Context c, boolean checked, Consumer<Boolean> cb) {
        AppCompatCheckBox cbx = new AppCompatCheckBox(c);
        cbx.setMinWidth(dp(36));
        cbx.setMinHeight(dp(36));
        cbx.setPadding(0, 0, 0, 0);
        CompoundButtonCompat.setButtonTintList(cbx, new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Theme.accent, Theme.muted}));
        cbx.setChecked(checked);
        cbx.setOnCheckedChangeListener((b, on) -> cb.accept(on));
        return cbx;
    }

    /** Legördülő mező: koppintásra lapot nyit a lehetőségekkel. */
    public static LinearLayout select(Context c, List<String> labels, int sel, Consumer<Integer> cb) {
        LinearLayout box = hbox(c, 8);
        box.setBackground(ripple(inputBg(), Math.max(10, Theme.radius - 4)));
        box.setPadding(dp(14), dp(11), dp(10), dp(11));
        box.setMinimumHeight(dp(46));
        TextView t = body(c, sel >= 0 && sel < labels.size() ? labels.get(sel) : "–");
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        box.addView(t, grow());
        box.addView(icon(c, R.drawable.ic_expand_more, Theme.muted, 20));
        final int[] cur = {sel};
        box.setOnClickListener(v -> {
            Sheet s = sheet(c, null);
            for (int i = 0; i < labels.size(); i++) {
                final int idx = i;
                LinearLayout row = hbox(c, 10);
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                boolean on = i == cur[0];
                row.setBackground(ripple(shape(on ? Theme.accentSoft : Color.TRANSPARENT, 12), 12));
                TextView lab = on ? bold(c, labels.get(i)) : body(c, labels.get(i));
                row.addView(lab, grow());
                if (on) row.addView(icon(c, R.drawable.ic_check, Theme.accent, 20));
                row.setOnClickListener(x -> { cur[0] = idx; t.setText(labels.get(idx)); s.dismiss(); cb.accept(idx); });
                s.body.addView(row, lp(MATCH, WRAP));
            }
            s.show();
        });
        return box;
    }

    public static LinearLayout labeledSwitchSub(Context c, String label, String sub, boolean checked, Consumer<Boolean> cb) {
        LinearLayout row = hbox(c, 12);
        LinearLayout col = vbox(c, 2);
        col.addView(body(c, label), lp(MATCH, WRAP));
        col.addView(small(c, sub), lp(MATCH, WRAP));
        row.addView(col, grow());
        MaterialSwitch sw = toggle(c, checked, cb);
        row.addView(sw, lp(WRAP, WRAP));
        row.setOnClickListener(v -> sw.toggle());
        return row;
    }

    // ---------- lap (bottom sheet) ----------
    public static final class Sheet {
        public final BottomSheetDialog dlg;
        public final LinearLayout body;
        Sheet(BottomSheetDialog d, LinearLayout b) { dlg = d; body = b; }
        public void show() { dlg.show(); }
        public void dismiss() { dlg.dismiss(); }
    }

    public static Sheet sheet(Context c, String title) {
        BottomSheetDialog d = new BottomSheetDialog(c);
        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(false);
        LinearLayout body = vbox(c, Theme.gap);
        body.setPadding(dp(18), dp(8), dp(18), dp(24));
        if (title != null) {
            TextView h = h3(c, title);
            h.setPadding(0, dp(8), 0, dp(4));
            body.addView(h, lp(MATCH, WRAP));
        }
        sv.addView(body, new ScrollView.LayoutParams(MATCH, WRAP));
        d.setContentView(sv);
        d.setOnShowListener(x -> {
            View sheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                float r = Math.max(16, Theme.radius + 8);
                GradientDrawable g = new GradientDrawable();
                g.setColor(Theme.surface);
                g.setCornerRadii(new float[]{dp(r), dp(r), dp(r), dp(r), 0, 0, 0, 0});
                sheet.setBackground(g);
            }
            BottomSheetBehavior<?> b = d.getBehavior();
            b.setSkipCollapsed(true);
            b.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        return new Sheet(d, body);
    }

    public static void confirm(Context c, String title, String msg, String okLabel, boolean danger, Runnable onOk) {
        Sheet s = sheet(c, title);
        if (msg != null && !msg.isEmpty()) s.body.addView(muted(c, msg), lp(MATCH, WRAP));
        LinearLayout row = hbox(c, 10);
        row.addView(button(c, "Mégsem", B_DEFAULT, s::dismiss), grow());
        row.addView(button(c, okLabel, danger ? B_DANGER : B_PRIMARY, () -> { s.dismiss(); onOk.run(); }), grow());
        s.body.addView(row, lp(MATCH, WRAP));
        s.show();
    }

    // ---------- értesítés ----------
    public static void toast(Context c, String msg) {
        if (!(c instanceof Activity)) return;
        View root = ((Activity) c).findViewById(android.R.id.content);
        if (root == null) return;
        Snackbar sb = Snackbar.make(root, msg, Snackbar.LENGTH_SHORT);
        sb.setBackgroundTint(Theme.text);   // mint a webes .toast: szövegszínű alap, háttérszínű szöveg
        sb.setTextColor(Theme.bg);
        sb.show();
    }

    // ---------- diagram-kiegészítők ----------
    public static View bar(Context c, float frac, int color) {
        FrameLayout track = new FrameLayout(c);
        track.setBackground(shape(Theme.surface2, 4));
        View fill = new View(c);
        fill.setBackground(shape(color, 4));
        track.addView(fill, new FrameLayout.LayoutParams(0, MATCH));
        track.setLayoutParams(lp(MATCH, dp(8)));
        final float f = Math.max(0.02f, Math.min(1f, frac));
        track.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
            int w = Math.round((r - l) * f);
            ViewGroup.LayoutParams p = fill.getLayoutParams();
            if (p.width != w) { p.width = w; fill.setLayoutParams(p); }
        });
        return track;
    }

    /** KPI csempe a kezdőlapon / statisztikában. */
    public static LinearLayout kpi(Context c, String value, String label, String note, int noteColor) { return kpi(c, value, label, note, noteColor, 0); }

    /** Szövegkiemelő színű KPI csempe sötét szöveggel (mint a webes kezdőlap négy összesítője); fill = 0: sima kártya. */
    public static LinearLayout kpi(Context c, String value, String label, String note, int noteColor, int fill) {
        if (fill != 0) {
            LinearLayout l = vbox(c, 0);
            l.setBackground(shape(fill, Theme.radius));
            int p = dp(Theme.pad);
            l.setPadding(p, p, p, p);
            l.addView(text(c, value, 30 * Theme.kpiScale, Theme.INK, true), lp(WRAP, WRAP));
            l.addView(text(c, label, 14, Theme.INK, true), margin(lp(WRAP, WRAP), 0, 8, 0, 0));
            if (note != null && !note.isEmpty()) l.addView(text(c, note, 12, Theme.withAlpha(Theme.INK, 0.8f), false), margin(lp(WRAP, WRAP), 0, 2, 0, 0));
            return l;
        }
        LinearLayout l = new KpiTile(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(Theme.pad);
        l.setPadding(p, p, p, p);
        l.addView(text(c, value, 28 * Theme.kpiScale, Theme.text, true), lp(WRAP, WRAP));
        l.addView(small(c, label), margin(lp(WRAP, WRAP), 0, 4, 0, 0));
        if (note != null && !note.isEmpty()) {
            TextView n = text(c, note, 12, noteColor, true);
            l.addView(n, margin(lp(WRAP, WRAP), 0, 8, 0, 0));
        }
        return l;
    }

    /** Kártya halvány, kiemelő színű köríves dísszel a jobb felső sarokban (web: .kpi::after). */
    static final class KpiTile extends LinearLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        KpiTile(Context c) {
            super(c);
            setBackground(cardBg());
            setWillNotDraw(false);
            setClipToOutline(true);
            setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        }
        @Override protected void onDraw(Canvas canvas) {
            paint.setColor(Theme.accentSoft);
            canvas.drawCircle(getWidth() - dp(2), dp(2), dp(48), paint);
            super.onDraw(canvas);
        }
    }

    // ---------- folyó (tördelődő) elrendezés ----------
    public static class Flow extends ViewGroup {
        private final int gapPx;
        private int[] xs = new int[0], ys = new int[0];

        public Flow(Context c, float gapDp) { super(c); gapPx = dp(gapDp); }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int maxW = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
            if (MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED) maxW = Integer.MAX_VALUE / 4;
            int n = getChildCount();
            if (xs.length < n) { xs = new int[n]; ys = new int[n]; }
            int x = 0, y = 0, rowH = 0, used = 0;
            for (int i = 0; i < n; i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                LayoutParams clp = ch.getLayoutParams();
                int cws = clp != null && clp.width >= 0 ? MeasureSpec.makeMeasureSpec(clp.width, MeasureSpec.EXACTLY) : MeasureSpec.makeMeasureSpec(Math.max(0, maxW), MeasureSpec.AT_MOST);
                int chs = clp != null && clp.height >= 0 ? MeasureSpec.makeMeasureSpec(clp.height, MeasureSpec.EXACTLY) : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                ch.measure(cws, chs);
                int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
                if (x > 0 && x + cw > maxW) { x = 0; y += rowH + gapPx; rowH = 0; }
                xs[i] = x; ys[i] = y;
                x += cw + gapPx;
                rowH = Math.max(rowH, chh);
                used = Math.max(used, x - gapPx);
            }
            int w = MeasureSpec.getMode(wSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(wSpec) : used + getPaddingLeft() + getPaddingRight();
            setMeasuredDimension(w, y + rowH + getPaddingTop() + getPaddingBottom());
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                int cx = getPaddingLeft() + xs[i], cy = getPaddingTop() + ys[i];
                ch.layout(cx, cy, cx + ch.getMeasuredWidth(), cy + ch.getMeasuredHeight());
            }
        }
    }

    public static Flow flow(Context c, float gapDp) {
        Flow f = new Flow(c, gapDp);
        f.setLayoutParams(lp(MATCH, WRAP));
        return f;
    }

    /** Tableten ne nyúljon végig a tartalom: legfeljebb ennyire széles, középre igazítva. */
    public static class MaxWidthLayout extends LinearLayout {
        private final int maxPx;
        public MaxWidthLayout(Context c, float maxDp) { super(c); maxPx = dp(maxDp); }
        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            if (w > maxPx && MeasureSpec.getMode(wSpec) != MeasureSpec.UNSPECIFIED) wSpec = MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.EXACTLY);
            super.onMeasure(wSpec, hSpec);
        }
    }

    // ---------- a vízszintes sávdiagram egy sora ----------
    public static LinearLayout barRow(Context c, String label, String valueText, float frac, int color) {
        LinearLayout l = vbox(c, 5);
        LinearLayout top = hbox(c, 8);
        TextView lab = body(c, label);
        lab.setSingleLine(true);
        lab.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(lab, grow());
        top.addView(bold(c, valueText), lp(WRAP, WRAP));
        l.addView(top, lp(MATCH, WRAP));
        l.addView(bar(c, frac, color));
        return l;
    }

    /** A lépésrácsra igazított érték (a Material csúszka egyébként kivételt dob, vagy közbülső értéket ad). */
    static float snap(float min, float max, float step, float v) {
        float x = Math.max(min, Math.min(max, v));
        x = min + Math.round((x - min) / step) * step;
        return Math.max(min, Math.min(max, Float.parseFloat(String.format(java.util.Locale.US, "%.4f", x))));
    }

    /** Csúszka: live az éppen húzott értékkel, done az elengedéskor (és billentyűs állításkor). */
    public static com.google.android.material.slider.Slider slider(Context c, float min, float max, float step, float value, Consumer<Float> live, Consumer<Float> done) {
        com.google.android.material.slider.Slider s = new com.google.android.material.slider.Slider(c);
        s.setValueFrom(min);
        s.setValueTo(max);
        s.setStepSize(step);
        // A Material csúszka kivételt dob, ha az érték nem a lépésköz pontos többszöröse (pl. színválasztó HSV-értékei,
        // importált beállítások): ezért a lépésrácsra igazítjuk.
        final float snapped = snap(min, max, step, value);
        s.setValue(snapped);
        s.setTrackActiveTintList(ColorStateList.valueOf(Theme.accent));
        s.setTrackInactiveTintList(ColorStateList.valueOf(Theme.border));
        s.setThumbTintList(ColorStateList.valueOf(Theme.accent));
        s.setHaloTintList(ColorStateList.valueOf(Theme.accentSoft));
        s.setTickVisible(false);
        // A „kész” jelzés egyetlen úton megy: elengedéskor azonnal, billentyűs/kisegítő állításnál késleltetve.
        // (Korábban a húzás elején is jelzett, így a hívó többször, a csúszka érintéskezelése közben épített újra.)
        final boolean[] touching = {false};
        final float[] last = {snapped};
        final Runnable fire = () -> { if (done != null) done.accept(last[0]); };
        s.addOnChangeListener((sl, raw, fromUser) -> {
            float val = snap(min, max, step, raw);
            last[0] = val;
            if (live != null) live.accept(val);
            if (fromUser && !touching[0] && done != null) {
                Async.handler().removeCallbacks(fire);
                Async.handler().postDelayed(fire, 400);
            }
        });
        s.addOnSliderTouchListener(new com.google.android.material.slider.Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(com.google.android.material.slider.Slider sl) {
                touching[0] = true;
                Async.handler().removeCallbacks(fire);
            }
            @Override public void onStopTrackingTouch(com.google.android.material.slider.Slider sl) {
                touching[0] = false;
                Async.handler().removeCallbacks(fire);
                last[0] = snap(min, max, step, sl.getValue());
                if (done != null) Async.handler().post(fire);   // az érintéskezelés után, nem közben
            }
        });
        s.setLayoutParams(lp(MATCH, WRAP));
        return s;
    }

    public static String gradeLine(Grade g) {
        return St.subjName(g.subject) + " · " + (g.topic.isEmpty() ? g.mode : g.topic) + " · " + Dt.fmtDate(g.date);
    }
}
