package hu.zsirkreta.app.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import hu.zsirkreta.app.R;
import hu.zsirkreta.app.data.Models.Homework;
import hu.zsirkreta.app.data.Models.Lesson;
import hu.zsirkreta.app.data.Models.Raw;
import hu.zsirkreta.app.data.Norm;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.util.Async;
import hu.zsirkreta.app.util.Dt;
import hu.zsirkreta.app.util.Fmt;

/**
 * Holnapi táska teljes képernyőn: a következő tanítási nap óráihoz kiírja, mit kell bepakolni
 * (4 elrendezés), a házikkal együtt. A betűméret úgy áll be, hogy minden elférjen görgetés nélkül.
 */
public class PackActivity extends AppCompatActivity {
    /** A megjelenítendő nap és óráik. */
    private static final class Plan {
        LocalDate day;
        List<Lesson> lessons = new ArrayList<>();
        boolean fake;
        String note = "Holnapi órák";
    }

    private static final String[][] LAYOUTS = {{"list", "Lista"}, {"grid", "Rács"}, {"strip", "Idősáv"}, {"check", "Csak lista"}};

    private LinearLayout page;
    private FrameLayout bodyFrame;
    private TextView titleView, noteView;
    private Plan plan;
    private final Set<String> checked = new HashSet<>();
    private boolean fitted = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        boolean sysDark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        Theme.apply(St.prefs, sysDark);
        getWindow().getDecorView().setBackgroundColor(Theme.bg);
        WindowInsetsControllerCompat ic = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ic.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        ic.hide(WindowInsetsCompat.Type.systemBars());

        page = Ui.vbox(this, 0);
        page.setBackgroundColor(Theme.bg);
        ViewCompat.setOnApplyWindowInsetsListener(page, (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left + Ui.dp(14), sb.top + Ui.dp(10), sb.right + Ui.dp(14), sb.bottom + Ui.dp(10));
            return WindowInsetsCompat.CONSUMED;
        });

        LinearLayout top = Ui.hbox(this, 10);
        LinearLayout titles = Ui.vbox(this, 2);
        titleView = Ui.text(this, "🎒 …", 24, Theme.text, true);
        noteView = Ui.small(this, "Betöltés…");
        titles.addView(titleView, Ui.lp(Ui.WRAP, Ui.WRAP));
        titles.addView(noteView, Ui.lp(Ui.WRAP, Ui.WRAP));
        top.addView(titles, Ui.grow());
        top.addView(Ui.iconButton(this, R.drawable.ic_close, Theme.text, this::finish));
        page.addView(top, Ui.lp(Ui.MATCH, Ui.WRAP));

        String[] keys = new String[LAYOUTS.length], labels = new String[LAYOUTS.length];
        for (int i = 0; i < LAYOUTS.length; i++) { keys[i] = LAYOUTS[i][0]; labels[i] = LAYOUTS[i][1]; }
        page.addView(Ui.seg(this, keys, labels, St.prefs.packLayout, k -> { St.prefs.packLayout = k; St.persist(); if (plan != null) refit(); }),
                Ui.margin(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 10, 0, 10));

        bodyFrame = new FrameLayout(this);
        page.addView(bodyFrame, Ui.lp(Ui.MATCH, 0, 1f));
        setContentView(page);

        bodyFrame.addView(Ui.muted(this, "Betöltés…"), new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        Async.run(this::nextDay, p -> { plan = p; paint(); }, e -> {
            bodyFrame.removeAllViews();
            bodyFrame.addView(Ui.body(this, e.getMessage() == null ? "Hiba a betöltéskor" : e.getMessage()), new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        });
    }

    // ---------- melyik nap órái ----------
    private List<Lesson> lessonsOn(LocalDate d) throws Exception {
        LocalDate mon = Dt.mondayOf(d);
        String key = Dt.iso(mon);
        Raw r = St.weekCache.get(key);
        if (r == null) {
            r = St.fetchWeek(mon, false);
            St.weekCache.put(key, r);
        }
        return St.fillTestLessons(Norm.lessons(r.data), mon).stream().filter(l -> Dt.sameDay(l.start, d) && !l.off).collect(Collectors.toList());
    }

    private Plan nextDay() throws Exception {
        LocalDate tomorrow = Dt.today().plusDays(1);
        Plan p = new Plan();
        List<Lesson> first = lessonsOn(tomorrow);
        if (!first.isEmpty()) { p.day = tomorrow; p.lessons = first; return p; }
        if (St.prefs.devMode && St.prefs.devFillLessons) { p.day = tomorrow; p.lessons = randomLessons(tomorrow); p.fake = true; return p; }
        // nincs óra holnap (pl. hétvége): a következő tanítási nap
        for (int i = 2; i <= 14; i++) {
            LocalDate d = tomorrow.plusDays(i - 1);
            List<Lesson> ls = lessonsOn(d);
            if (!ls.isEmpty()) { p.day = d; p.lessons = ls; p.note = "Holnap nincs óra, a következő tanítási nap:"; return p; }
        }
        p.day = tomorrow;
        p.note = "Holnap nincs óra.";
        return p;
    }

    private List<Lesson> randomLessons(LocalDate day) {
        List<String> pool = St.allSubjects();
        List<String> withGear = pool.stream().filter(s -> !ownGear(s).isEmpty()).collect(Collectors.toList());
        List<String> src = new ArrayList<>(withGear.size() >= 3 ? withGear : pool);
        Random rnd = new Random();
        int n = Math.min(src.size(), 5 + rnd.nextInt(3));
        List<Lesson> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Lesson l = new Lesson();
            l.subject = src.remove(rnd.nextInt(src.size()));
            l.id = "fake" + i;
            l.start = day.atTime(8, 0).plusMinutes(i * 55L);
            l.end = l.start.plusMinutes(45);
            l.nr = i + 1; l.room = ""; l.teacher = ""; l.sub = ""; l.name = l.subject; l.topic = "";
            out.add(l);
        }
        return out;
    }

    // ---------- felszerelés ----------
    private List<String> commonGear() { return St.prefs.packAll; }
    private List<String> ownGear(String subject) { List<String> l = St.prefs.packItems.get(subject); return l == null ? new ArrayList<>() : l; }

    private boolean isCommon(String x) {
        for (String c : commonGear()) if (c.equalsIgnoreCase(x)) return true;
        return false;
    }

    private List<String> gearOf(String subject) {
        List<String> out = new ArrayList<>(commonGear());
        for (String x : ownGear(subject)) if (!isCommon(x)) out.add(x);
        return out;
    }

    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }

    private List<String> hwFor(String subject, LocalDate day) {
        List<String> out = new ArrayList<>();
        for (Homework h : St.homework()) {
            if (h.done || !Dt.sameDay(h.due, day) || !norm(h.subject).equals(norm(subject))) continue;
            String t = Fmt.stripHtml(h.text).replaceAll("\\s+", " ").trim();
            if (!t.isEmpty()) out.add(t + (h.own ? "  (saját)" : ""));
        }
        return out;
    }

    private Map<String, Object[]> summary(List<Lesson> lessons) {
        Map<String, Object[]> all = new LinkedHashMap<>();   // kulcs → {név, darab}
        for (String g : commonGear()) all.put(g.toLowerCase(Locale.ROOT), new Object[]{g, 1});
        for (Lesson l : lessons) {
            for (String g : ownGear(l.subject)) {
                String k = g.toLowerCase(Locale.ROOT);
                if (all.containsKey(k) && !isCommon(g)) all.get(k)[1] = (Integer) all.get(k)[1] + 1;
                else if (!all.containsKey(k)) all.put(k, new Object[]{g, 1});
            }
        }
        return all;
    }

    // ---------- megjelenítés ----------
    private void paint() {
        titleView.setText("🎒 " + Dt.DAYS[Dt.wd(plan.day)]);
        noteView.setText(plan.note + (plan.fake ? " · véletlen órák (developer)" : ""));
        fitted = false;
        build(1.0f);
        // a legnagyobb betűméret keresése: minden elférjen görgetés nélkül
        bodyFrame.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (bodyFrame.getHeight() == 0 || fitted) return;
                bodyFrame.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                fitted = true;
                fit();
            }
        });
    }

    private void fit() {
        int avail = bodyFrame.getHeight(), width = bodyFrame.getWidth();
        float[] scales = {1.5f, 1.35f, 1.2f, 1.1f, 1.0f, 0.92f, 0.85f, 0.78f, 0.7f, 0.62f};
        float chosen = scales[scales.length - 1];
        float max = maxScale();
        for (float s : scales) {
            if (s > max) continue;
            View v = content(s);
            v.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            if (v.getMeasuredHeight() <= avail) { chosen = s; break; }
        }
        build(chosen);
    }

    private float maxScale() {
        switch (St.prefs.packLayout) {
            case "grid": return 1.0f;
            case "strip": return 1.2f;
            default: return 1.5f;
        }
    }

    private void build(float scale) {
        bodyFrame.removeAllViews();
        View v = content(scale);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(v, new ScrollView.LayoutParams(Ui.MATCH, Ui.WRAP));
        bodyFrame.addView(sv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
    }

    /** A teljes tartalom (elrendezésváltó + a kiválasztott elrendezés) adott betűmérettel. */
    private View content(float scale) {
        float saved = Theme.fs;
        Theme.fs = St.prefs.fontScale > 0 ? (float) St.prefs.fontScale * scale : scale;
        try {
            LinearLayout col = Ui.vbox(this, 12);
            switch (St.prefs.packLayout) {
                case "grid": col.addView(grid(), Ui.lp(Ui.MATCH, Ui.WRAP)); col.addView(summaryBox(), Ui.lp(Ui.MATCH, Ui.WRAP)); break;
                case "strip": col.addView(strip(scale), Ui.lp(Ui.MATCH, Ui.WRAP)); break;
                case "check": col.addView(checkLayout(), Ui.lp(Ui.MATCH, Ui.WRAP)); break;
                default: col.addView(list(), Ui.lp(Ui.MATCH, Ui.WRAP)); col.addView(summaryBox(), Ui.lp(Ui.MATCH, Ui.WRAP));
            }
            return col;
        } finally {
            Theme.fs = saved;
        }
    }

    private void refit() { fitted = false; paint(); }

    private TextView chk(String key, String label) {
        TextView t = Ui.text(this, (checked.contains(key) ? "☑ " : "☐ ") + label, 15, checked.contains(key) ? Theme.muted : Theme.text, true);
        t.setBackground(Ui.shape(Theme.surface2, 20, checked.contains(key) ? Theme.g[5] : Theme.border, 1));
        t.setPadding(Ui.dp(12), Ui.dp(7), Ui.dp(12), Ui.dp(7));
        if (checked.contains(key)) t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        t.setOnClickListener(v -> {
            if (!checked.remove(key)) checked.add(key);
            boolean on = checked.contains(key);
            t.setText((on ? "☑ " : "☐ ") + label);
            t.setTextColor(on ? Theme.muted : Theme.text);
            t.setBackground(Ui.shape(Theme.surface2, 20, on ? Theme.g[5] : Theme.border, 1));
            t.setPaintFlags(on ? t.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG : t.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        });
        return t;
    }

    private View gearChips(Lesson l) {
        List<String> g = gearOf(l.subject);
        Ui.Flow f = Ui.flow(this, 6);
        if (g.isEmpty()) f.addView(Ui.small(this, "nincs felszerelés megadva"));
        for (String x : g) f.addView(chk(l.id + "|" + x, x));
        return f;
    }

    /** stacked: az idő a név alatt (keskeny, kétoszlopos kártyákban, hogy a szavak ne törjenek). */
    private View head(Lesson l, boolean withTime, boolean stacked) {
        String time = Dt.fmtTime(l.start) + (l.room == null || l.room.isEmpty() ? "" : " · " + l.room);
        LinearLayout h = Ui.hbox(this, 8);
        h.addView(Ui.dot(this, Theme.subjColor(l.subject)));
        String name = (l.nr != null ? l.nr + ". " : "") + St.subjName(l.subject);
        if (stacked && withTime) {
            LinearLayout col = Ui.vbox(this, 2);
            col.addView(Ui.text(this, name, 17, Theme.text, true), Ui.lp(Ui.MATCH, Ui.WRAP));
            col.addView(Ui.small(this, time), Ui.lp(Ui.MATCH, Ui.WRAP));
            h.addView(col, Ui.grow());
            return h;
        }
        h.addView(Ui.text(this, name, 17, Theme.text, true), Ui.grow());
        if (withTime) h.addView(Ui.small(this, time));
        return h;
    }

    private View hwBox(Lesson l) {
        List<String> hw = hwFor(l.subject, plan.day);
        if (hw.isEmpty()) return null;
        LinearLayout box = Ui.vbox(this, 4);
        box.setBackground(Ui.shape(Theme.withAlpha(Theme.g[3], 0.14f), 10));
        box.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8));
        for (String x : hw) box.addView(Ui.text(this, "📝 " + x, 14, Theme.text, false), Ui.lp(Ui.MATCH, Ui.WRAP));
        return box;
    }

    private View lessonCard(Lesson l, boolean stacked) {
        LinearLayout card = Ui.card(this);
        card.addView(head(l, true, stacked), Ui.lp(Ui.MATCH, Ui.WRAP));
        View hw = hwBox(l);
        if (hw != null) card.addView(hw, Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(gearChips(l), Ui.lp(Ui.MATCH, Ui.WRAP));
        return card;
    }

    private View summaryBox() {
        LinearLayout card = Ui.card(this);
        card.setBackground(Ui.shape(Theme.accentSoft, Theme.radius, Theme.accent, 1));
        card.addView(Ui.h3(this, "Összesen be kell pakolni"), Ui.lp(Ui.MATCH, Ui.WRAP));
        Map<String, Object[]> sum = summary(plan.lessons);
        Ui.Flow f = Ui.flow(this, 6);
        if (sum.isEmpty()) f.addView(Ui.small(this, "Nincs felszerelés megadva"));
        for (Object[] g : sum.values()) f.addView(chk("sum|" + g[0], g[0] + ((Integer) g[1] > 1 ? " ×" + g[1] : "")));
        card.addView(f, Ui.lp(Ui.MATCH, Ui.WRAP));
        return card;
    }

    private View list() {
        LinearLayout col = Ui.vbox(this, 10);
        if (plan.lessons.isEmpty()) col.addView(Ui.empty(this, "Nincs óra"));
        for (Lesson l : plan.lessons) col.addView(lessonCard(l, false), Ui.lp(Ui.MATCH, Ui.WRAP));
        return col;
    }

    private View grid() {
        LinearLayout col = Ui.vbox(this, 10);
        if (plan.lessons.isEmpty()) col.addView(Ui.empty(this, "Nincs óra"));
        for (int i = 0; i < plan.lessons.size(); i += 2) {
            LinearLayout row = Ui.hbox(this, 10);
            row.setBaselineAligned(false);
            row.setGravity(Gravity.TOP);
            for (int j = i; j < Math.min(i + 2, plan.lessons.size()); j++) row.addView(lessonCard(plan.lessons.get(j), true), Ui.lp(0, Ui.WRAP, 1f));
            if (row.getChildCount() == 1) row.addView(new View(this), Ui.lp(0, 1, 1f));
            col.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        return col;
    }

    private View strip(float scale) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.hbox(this, 10);
        row.setGravity(Gravity.TOP);
        if (plan.lessons.isEmpty()) row.addView(Ui.empty(this, "Nincs óra"));
        for (Lesson l : plan.lessons) {
            LinearLayout colm = Ui.card(this);
            colm.addView(Ui.text(this, Dt.fmtTime(l.start), 13, Theme.muted, true), Ui.lp(Ui.WRAP, Ui.WRAP));
            colm.addView(head(l, false, false), Ui.lp(Ui.MATCH, Ui.WRAP));
            View hw = hwBox(l);
            if (hw != null) colm.addView(hw, Ui.lp(Ui.MATCH, Ui.WRAP));
            List<String> g = gearOf(l.subject);
            if (g.isEmpty()) colm.addView(Ui.small(this, "–"));
            for (String x : g) colm.addView(chk(l.id + "|" + x, x), Ui.lp(Ui.WRAP, Ui.WRAP));
            row.addView(colm, Ui.lp(Ui.dp(190 * Math.max(1f, scale)), Ui.WRAP));
        }
        hs.addView(row, new HorizontalScrollView.LayoutParams(Ui.WRAP, Ui.WRAP));
        return hs;
    }

    private View checkLayout() {
        LinearLayout col = Ui.vbox(this, 10);
        col.addView(summaryBox(), Ui.lp(Ui.MATCH, Ui.WRAP));
        List<Lesson> withHw = plan.lessons.stream().filter(l -> !hwFor(l.subject, plan.day).isEmpty()).collect(Collectors.toList());
        if (!withHw.isEmpty()) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.h3(this, "Házik"), Ui.lp(Ui.MATCH, Ui.WRAP));
            for (Lesson l : withHw) {
                card.addView(Ui.bold(this, St.subjName(l.subject)), Ui.lp(Ui.MATCH, Ui.WRAP));
                card.addView(hwBox(l), Ui.lp(Ui.MATCH, Ui.WRAP));
            }
            col.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        return col;
    }
}
