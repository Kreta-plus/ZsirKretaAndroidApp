package hu.zsirkreta.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.data.Funny;
import hu.zsirkreta.app.data.Models.Absence;
import hu.zsirkreta.app.data.Models.Grade;
import hu.zsirkreta.app.data.Models.Homework;
import hu.zsirkreta.app.data.Models.Lesson;
import hu.zsirkreta.app.data.Models.Raw;
import hu.zsirkreta.app.data.Models.SubjectAvg;
import hu.zsirkreta.app.data.Models.TestItem;
import hu.zsirkreta.app.data.Norm;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.util.Async;
import hu.zsirkreta.app.util.Dt;
import hu.zsirkreta.app.util.Fmt;

public final class DashboardScreen extends Screen {
    private static final Map<String, String> QUIPS = new HashMap<>();
    // a webes --hl-* szövegkiemelő színek (a kezdőlap négy összesítője)
    private static final int HL_YELLOW = 0xFFFFE14D, HL_SKY = 0xFF5CC8FF, HL_PINK = 0xFFFF5FA2, HL_MINT = 0xFF45E3A0;   // naponta/óraszerkezetenként egy szöveg, hogy újrarajzoláskor ne változzon

    public DashboardScreen(MainActivity a) { super(a); }

    @Override public String title() { return "ZsírKréta"; }

    /** Kumulált átlag hónapról hónapra (a diagram és a statisztika is ezt használja). */
    static List<Charts.Pt> monthlyTrend(List<Grade> list) {
        List<Grade> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> a.date.compareTo(b.date));
        List<Charts.Pt> pts = new ArrayList<>();
        List<Grade> acc = new ArrayList<>();
        Integer cur = null;
        for (Grade g : sorted) {
            int key = g.date.getYear() * 12 + g.date.getMonthValue() - 1;
            if (cur != null && key != cur) pts.add(new Charts.Pt(Dt.MONTHS[cur % 12], St.avgOf(acc)));
            cur = key;
            acc.add(g);
        }
        if (!acc.isEmpty()) pts.add(new Charts.Pt(Dt.MONTHS[cur % 12], St.avgOf(acc)));
        return pts;
    }

    @Override public void render(LinearLayout root) {
        List<Grade> gs = St.numericGrades();
        List<Lesson> ls = St.lessons();
        List<Homework> hw = St.homework();
        List<TestItem> ts = St.tests();
        List<Absence> ab = St.absences();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        java.util.Set<String> widgets = new java.util.HashSet<>(St.prefs.widgets);
        Double avg = St.avgOf(gs);
        double target = St.prefs.targetAvg;

        List<Grade> recent = gs.subList(0, Math.min(8, gs.size()));
        List<Grade> last30 = new ArrayList<>(), prev = new ArrayList<>();
        for (Grade g : gs) { if (ChronoUnit.DAYS.between(g.date, now) < 30) last30.add(g); else prev.add(g); }
        Double avgPrev = St.avgOf(prev);
        Double delta = avg != null && avgPrev != null && !last30.isEmpty() ? avg - avgPrev : null;
        List<Lesson> todayL = ls.stream().filter(l -> Dt.sameDay(l.start, today)).collect(Collectors.toList());
        Lesson nextL = null;
        for (Lesson l : ls) if (l.end.isAfter(now) && !l.off) { nextL = l; break; }
        List<Homework> hwOpen = hw.stream().filter(h -> !h.done && !h.due.toLocalDate().isBefore(today)).collect(Collectors.toList());
        List<TestItem> testsNext = ts.stream().filter(t -> !t.date.toLocalDate().isBefore(today)).limit(5).collect(Collectors.toList());
        long unexc = ab.stream().filter(a -> !a.ok && !a.na).count();
        int hour = now.getHour();
        String hello = hour < 5 ? "Még ébren?" : hour < 11 ? "Jó reggelt" : hour < 18 ? "Szia" : "Jó estét";

        UpdateCard.addTo(root, act);   // csak akkor látszik, ha van új verzió
        NotifyCard.addSetupTo(root, act);   // csak ha az értesítésekhez még engedély kell
        root.addView(hero(hello, now, nextL), Ui.lp(Ui.MATCH, Ui.WRAP));

        // vicces mondat a holnapi órák alapján
        if (St.prefs.funnyQuips) {
            LinearLayout quip = Ui.vbox(c(), 0);
            root.addView(quip, Ui.lp(Ui.MATCH, Ui.WRAP));
            showQuip(quip, today);
        }

        if (widgets.contains("avg")) {
            LinearLayout grid = Ui.vbox(c(), Theme.gap);
            LinearLayout r1 = Ui.hbox(c(), Theme.gap), r2 = Ui.hbox(c(), Theme.gap);
            String note1 = delta == null ? "cél: " + Fmt.f2(target) : (delta >= 0 ? "▲ " : "▼ ") + Fmt.f2(Math.abs(delta)) + " 30 napja";
            int col1 = delta == null ? Theme.muted : delta >= 0 ? Theme.g[5] : Theme.g[1];
            r1.addView(Ui.kpi(c(), Fmt.f2(avg), "Összátlag", note1, col1, HL_YELLOW), Ui.lp(0, Ui.MATCH, 1f));
            r1.addView(Ui.kpi(c(), String.valueOf(gs.size()), "Érdemjegy", last30.size() + " az elmúlt 30 napban", Theme.muted, HL_SKY), Ui.lp(0, Ui.MATCH, 1f));
            r2.addView(Ui.kpi(c(), String.valueOf(ab.size()), "Mulasztás", unexc > 0 ? unexc + " igazolatlan" : "minden igazolt", unexc > 0 ? Theme.g[1] : Theme.g[5], HL_PINK), Ui.lp(0, Ui.MATCH, 1f));
            r2.addView(Ui.kpi(c(), String.valueOf(hwOpen.size()), "Nyitott házi", testsNext.size() + " közelgő számonkérés", Theme.muted, HL_MINT), Ui.lp(0, Ui.MATCH, 1f));
            grid.addView(r1, Ui.lp(Ui.MATCH, Ui.WRAP));
            grid.addView(r2, Ui.lp(Ui.MATCH, Ui.WRAP));
            root.addView(grid, Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        if (widgets.contains("next")) {
            LinearLayout card = Ui.card(c(), "Mai órák", todayL.isEmpty() ? "" : todayL.size() + " óra");
            if (todayL.isEmpty()) card.addView(Ui.empty(c(), "Ma nincs óra az órarendben 🎉"));
            else {
                LinearLayout list = Ui.vbox(c(), 8);
                for (Lesson l : todayL) list.addView(Rows.lesson(c(), l, l.end.isBefore(now), nextL != null && nextL.id.equals(l.id)), Ui.lp(Ui.MATCH, Ui.WRAP));
                card.addView(list, Ui.lp(Ui.MATCH, Ui.WRAP));
            }
            root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        if (widgets.contains("grades")) {
            LinearLayout card = Ui.card(c(), "Friss jegyek", null);
            addLink((LinearLayout) card.getChildAt(0), "Mind", "grades");
            if (recent.isEmpty()) card.addView(Ui.empty(c(), "Még nincs jegy"));
            else {
                LinearLayout list = Ui.vbox(c(), 8);
                for (Grade g : recent) list.addView(Rows.grade(c(), g), Ui.lp(Ui.MATCH, Ui.WRAP));
                card.addView(list, Ui.lp(Ui.MATCH, Ui.WRAP));
            }
            root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        if (widgets.contains("homework")) {
            LinearLayout card = Ui.card(c(), "Házi feladatok", null);
            addLink((LinearLayout) card.getChildAt(0), "Mind", "homework");
            if (hwOpen.isEmpty()) card.addView(Ui.empty(c(), "Nincs függő házi 🙌"));
            else {
                LinearLayout list = Ui.vbox(c(), 8);
                for (Homework h : hwOpen.subList(0, Math.min(6, hwOpen.size()))) list.addView(Rows.homeworkBrief(c(), h), Ui.lp(Ui.MATCH, Ui.WRAP));
                card.addView(list, Ui.lp(Ui.MATCH, Ui.WRAP));
            }
            root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        if (widgets.contains("tests")) {
            LinearLayout card = Ui.card(c(), "Számonkérések", null);
            if (testsNext.isEmpty()) card.addView(Ui.empty(c(), "Nincs bejelentett számonkérés"));
            else {
                LinearLayout list = Ui.vbox(c(), 8);
                for (TestItem t : testsNext) list.addView(Rows.test(c(), t), Ui.lp(Ui.MATCH, Ui.WRAP));
                card.addView(list, Ui.lp(Ui.MATCH, Ui.WRAP));
            }
            root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        if (widgets.contains("trend")) {
            LinearLayout card = Ui.card(c(), "Átlag alakulása", "cél: " + Fmt.f2(target));
            card.addView(Charts.line(c(), monthlyTrend(gs), target), Ui.lp(Ui.MATCH, Ui.WRAP));
            root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        if (widgets.contains("subjects")) {
            List<SubjectAvg> subs = St.bySubject().stream().filter(s -> s.avg != null).sorted((a, b) -> Double.compare(b.avg, a.avg)).collect(Collectors.toList());
            LinearLayout card = Ui.card(c(), "Tantárgyi átlagok", null);
            List<Charts.Item> items = new ArrayList<>();
            for (SubjectAvg s : subs) items.add(new Charts.Item(St.subjName(s.subject), s.avg, Theme.gradeColor(s.avg)));
            card.addView(Charts.hbars(c(), items, 5.0, Fmt::f2, null), Ui.lp(Ui.MATCH, Ui.WRAP));
            root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        if (widgets.contains("absences")) {
            Map<String, Integer> by = new LinkedHashMap<>();
            for (Absence a : ab) by.merge(a.subject, 1, Integer::sum);
            List<Charts.Item> top = by.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(5)
                    .map(e -> new Charts.Item(St.subjName(e.getKey()), e.getValue())).collect(Collectors.toList());
            LinearLayout card = Ui.card(c(), "Hiányzások tantárgyanként", null);
            if (top.isEmpty()) card.addView(Ui.empty(c(), "Nincs hiányzás 👏"));
            else card.addView(Charts.hbars(c(), top, null, v -> String.valueOf(Math.round(v)), null), Ui.lp(Ui.MATCH, Ui.WRAP));
            root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        if (widgets.isEmpty()) {
            LinearLayout card = Ui.card(c());
            card.addView(Ui.empty(c(), "Minden widget ki van kapcsolva. Beállítások → Jegyek és átlag."));
            root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
    }

    private void addLink(LinearLayout head, String label, String target) {
        TextView t = Ui.text(c(), label, 13, Theme.accent, true);
        t.setPadding(Ui.dp(8), Ui.dp(4), 0, Ui.dp(4));
        t.setOnClickListener(v -> act.show(target));
        head.addView(t, Ui.lp(Ui.WRAP, Ui.WRAP));
    }

    // ---------- hős blokk ----------
    private View hero(String hello, LocalDateTime now, Lesson nextL) {
        HeroCard card = new HeroCard(c());
        card.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(Math.max(Theme.pad, 18));
        card.setPadding(p, p, p, p);
        LinearLayout top = Ui.hbox(c(), 10);
        LinearLayout greet = Ui.vbox(c(), 4);
        greet.addView(Ui.text(c(), hello + ",", 15, Theme.withAlpha(Theme.onAccent, 0.85f), false), Ui.lp(Ui.WRAP, Ui.WRAP));
        greet.addView(Ui.text(c(), St.shownName() + "! " + St.prefs.emoji, 26, Theme.onAccent, true), Ui.lp(Ui.WRAP, Ui.WRAP));
        top.addView(greet, Ui.grow());
        TextView story = Ui.text(c(), "📸 Story", 13, Theme.onAccent, true);
        story.setBackground(Ui.ripple(Ui.shape(Theme.withAlpha(Theme.onAccent, 0.2f), 20), 20));
        story.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8));
        story.setOnClickListener(v -> StoryDialog.open(act));
        top.addView(story, Ui.lp(Ui.WRAP, Ui.WRAP));
        top.setGravity(Gravity.TOP);
        card.addView(top, Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(Ui.text(c(), Dt.longDay(now.toLocalDate()), 14, Theme.withAlpha(Theme.onAccent, 0.85f), false), Ui.margin(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 6, 0, 0));
        if (nextL != null) {
            TextView pill = Ui.text(c(), "Következő óra: " + St.subjName(nextL.subject) + " · " + Dt.fmtTime(nextL.start), 13, Theme.onAccent, true);
            pill.setBackground(Ui.shape(Theme.withAlpha(Theme.onAccent, 0.18f), 20));
            pill.setPadding(Ui.dp(12), Ui.dp(7), Ui.dp(12), Ui.dp(7));
            pill.setSingleLine(true);
            pill.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(pill, Ui.margin(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 14, 0, 0));
        }
        return card;
    }

    /** Színátmenetes kártya dekoratív körökkel. */
    static final class HeroCard extends LinearLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        HeroCard(android.content.Context c) {
            super(c);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Theme.mix(Theme.accent, 0xFF000000, 0.22f), Theme.accent, Theme.accentStrong});
            g.setCornerRadius(Ui.dp(Theme.radius + 6));
            setBackground(g);
            setWillNotDraw(false);
            setClipToOutline(true);
            setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        }
        @Override protected void onDraw(Canvas cv) {
            paint.setColor(Theme.withAlpha(Theme.onAccent, 0.10f));
            cv.drawCircle(getWidth() - Ui.dp(10), Ui.dp(10), Ui.dp(90), paint);
            cv.drawCircle(Ui.dp(20), getHeight() + Ui.dp(10), Ui.dp(70), paint);
            super.onDraw(cv);
        }
    }

    // ---------- holnapi órák alapján: vicces mondat ----------
    private void showQuip(LinearLayout holder, LocalDate today) {
        LocalDate day = today.plusDays(1);
        LocalDate mon = Dt.mondayOf(day);
        Raw cached = St.weekCache.get(Dt.iso(mon));
        if (cached != null) { fillQuip(holder, day, mon, cached); return; }
        Async.run(() -> St.fetchWeek(mon, false), r -> {
            St.weekCache.put(Dt.iso(mon), r);
            if (holder.isAttachedToWindow()) fillQuip(holder, day, mon, r);
        }, e -> { });
    }

    private void fillQuip(LinearLayout holder, LocalDate day, LocalDate mon, Raw r) {
        List<Lesson> ls = St.fillTestLessons(Norm.lessons(r.data), mon).stream().filter(l -> Dt.sameDay(l.start, day)).collect(Collectors.toList());
        String key = Dt.iso(day) + ls.stream().map(l -> l.id).collect(Collectors.joining());
        List<TestItem> tests = St.tests().stream().filter(t -> Dt.sameDay(t.date, day)).collect(Collectors.toList());
        List<Homework> hw = St.homework().stream().filter(h -> !h.done && Dt.sameDay(h.due, day)).collect(Collectors.toList());
        String text = QUIPS.computeIfAbsent(key, k -> Funny.tomorrowQuip(day, ls, tests, hw, St::subjName));
        holder.removeAllViews();
        LinearLayout card = Ui.card(c());
        LinearLayout row = Ui.hbox(c(), 12);
        row.setGravity(Gravity.TOP);
        row.addView(Ui.text(c(), "💬", 22, Theme.text, false));
        LinearLayout col = Ui.vbox(c(), 4);
        col.addView(Ui.small(c(), "Holnapi órák alapján"), Ui.lp(Ui.MATCH, Ui.WRAP));
        col.addView(Ui.text(c(), text, 15, Theme.text, true), Ui.lp(Ui.MATCH, Ui.WRAP));
        row.addView(col, Ui.grow());
        card.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        holder.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
    }
}
