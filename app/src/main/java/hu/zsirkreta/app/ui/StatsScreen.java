package hu.zsirkreta.app.ui;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.data.Models.Absence;
import hu.zsirkreta.app.data.Models.Grade;
import hu.zsirkreta.app.data.Models.Lesson;
import hu.zsirkreta.app.data.Models.SubjectAvg;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.util.Dt;
import hu.zsirkreta.app.util.Fmt;

public final class StatsScreen extends Screen {
    public StatsScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Statisztika"; }

    private LinearLayout card(LinearLayout root, String title, String small) {
        LinearLayout card = Ui.card(c(), title, small);
        root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        return card;
    }

    private static List<Charts.Item> lastMonths(Map<Integer, Integer> counts) {
        List<Integer> keys = new ArrayList<>(new TreeMap<>(counts).keySet());
        keys = keys.subList(Math.max(0, keys.size() - 10), keys.size());
        List<Charts.Item> out = new ArrayList<>();
        for (int k : keys) out.add(new Charts.Item(Dt.MONTHS[k % 12], counts.get(k)));
        return out;
    }

    @Override public void render(LinearLayout root) {
        List<Grade> gs = St.numericGrades();
        List<Absence> ab = St.absences();
        if (gs.isEmpty()) {
            LinearLayout c = Ui.card(c());
            c.addView(Ui.empty(c(), "Még nincs elég adat."));
            root.addView(c, Ui.lp(Ui.MATCH, Ui.WRAP));
            return;
        }
        List<Integer> vals = gs.stream().map(g -> g.value).collect(Collectors.toList());
        Double avg = St.avgOf(gs);
        double plain = vals.stream().mapToInt(Integer::intValue).average().orElse(0);
        int[] dist = new int[6];
        for (int v : vals) dist[v]++;
        int modeV = 5;
        for (int v = 5; v >= 1; v--) if (dist[v] > dist[modeV]) modeV = v;
        List<SubjectAvg> subs = St.bySubject().stream().filter(s -> s.avg != null).sorted((a, b) -> Double.compare(b.avg, a.avg)).collect(Collectors.toList());
        SubjectAvg best = subs.isEmpty() ? null : subs.get(0), worst = subs.isEmpty() ? null : subs.get(subs.size() - 1);

        // tanárok szerinti átlagok
        Map<String, List<Grade>> tm = new LinkedHashMap<>();
        for (Grade g : gs) if (!g.teacher.isEmpty()) tm.computeIfAbsent(g.teacher, k -> new ArrayList<>()).add(g);
        List<Charts.Item> teachers = tm.entrySet().stream().filter(e -> e.getValue().size() >= 2)
                .map(e -> new Charts.Item(St.teacherName(e.getKey()), St.avgOf(e.getValue())))
                .sorted((a, b) -> Double.compare(b.value, a.value)).limit(8).collect(Collectors.toList());

        // jegyek hónaponként, hét napjai szerint
        Map<Integer, Integer> mc = new TreeMap<>();
        int[] wd = new int[5];
        for (Grade g : gs) {
            mc.merge(g.date.getYear() * 12 + g.date.getMonthValue() - 1, 1, Integer::sum);
            int d = Dt.wd(g.date);
            if (d < 5) wd[d]++;
        }
        // hiányzások
        long okN = ab.stream().filter(a -> a.ok).count(), naN = ab.stream().filter(a -> a.na).count();
        long badN = ab.size() - okN - naN;
        Map<Integer, Integer> abM = new TreeMap<>();
        for (Absence a : ab) abM.merge(a.date.getYear() * 12 + a.date.getMonthValue() - 1, 1, Integer::sum);

        // leghosszabb 4–5-ös sorozat (időrendben)
        int streak = 0, cur = 0;
        for (int i = gs.size() - 1; i >= 0; i--) { cur = gs.get(i).value >= 4 ? cur + 1 : 0; streak = Math.max(streak, cur); }

        int[] perDay = new int[5];
        for (Lesson l : St.lessons()) if (!l.off) { int d = Dt.wd(l.start); if (d < 5) perDay[d]++; }

        // fejléc + story
        LinearLayout head = Ui.hbox(c(), 10);
        head.addView(Ui.muted(c(), gs.size() + " jegy alapján"), Ui.grow());
        head.addView(Ui.button(c(), "📸 Story kép", Ui.B_PRIMARY, () -> StoryDialog.open(act)));
        root.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout r1 = Ui.hbox(c(), Theme.gap), r2 = Ui.hbox(c(), Theme.gap);
        long fives = dist[5];
        r1.addView(Ui.kpi(c(), Fmt.f2(avg), St.prefs.weighted ? "Súlyozott átlag" : "Egyszerű átlag", "egyszerű: " + Fmt.f2(plain), Theme.muted), Ui.lp(0, Ui.MATCH, 1f));
        r1.addView(Ui.kpi(c(), Fmt.f1(Fmt.median(vals)), "Medián", "leggyakoribb: " + modeV + "-es (" + dist[modeV] + " db)", Theme.muted), Ui.lp(0, Ui.MATCH, 1f));
        r2.addView(Ui.kpi(c(), String.valueOf(streak), "Leghosszabb 4–5-ös sorozat", "egymás után", Theme.muted), Ui.lp(0, Ui.MATCH, 1f));
        r2.addView(Ui.kpi(c(), String.valueOf(fives), "Ötösök", Math.round(fives * 100f / gs.size()) + "%-a az összes jegynek", Theme.g[5]), Ui.lp(0, Ui.MATCH, 1f));
        root.addView(r1, Ui.lp(Ui.MATCH, Ui.WRAP));
        root.addView(r2, Ui.lp(Ui.MATCH, Ui.WRAP));

        List<Charts.Item> distItems = new ArrayList<>();
        for (int v = 5; v >= 1; v--) distItems.add(new Charts.Item(v + "-es", dist[v], Theme.g[v]));
        card(root, "Jegyek eloszlása", null).addView(Charts.donut(c(), distItems, String.valueOf(gs.size())), Ui.lp(Ui.MATCH, Ui.WRAP));
        card(root, "Átlag alakulása", null).addView(Charts.line(c(), DashboardScreen.monthlyTrend(gs), St.prefs.targetAvg), Ui.lp(Ui.MATCH, Ui.WRAP));

        List<Charts.Item> subItems = new ArrayList<>();
        for (SubjectAvg s : subs) subItems.add(new Charts.Item(St.subjName(s.subject), s.avg, Theme.gradeColor(s.avg)));
        card(root, "Tantárgyak sorrendje", null).addView(Charts.hbars(c(), subItems, 5.0, Fmt::f2, null), Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout tc = card(root, "Tanárok szerinti átlag", "legalább 2 jegy");
        tc.addView(teachers.isEmpty() ? Ui.empty(c(), "Nincs elég adat") : Charts.hbars(c(), teachers, 5.0, Fmt::f2, null), Ui.lp(Ui.MATCH, Ui.WRAP));

        card(root, "Jegyek havonta", null).addView(Charts.bars(c(), lastMonths(mc)), Ui.lp(Ui.MATCH, Ui.WRAP));
        List<Charts.Item> wdItems = new ArrayList<>();
        for (int i = 0; i < 5; i++) wdItems.add(new Charts.Item(Dt.DAYS[i].substring(0, 3), wd[i]));
        card(root, "Jegyek a hét napjain", null).addView(Charts.bars(c(), wdItems), Ui.lp(Ui.MATCH, Ui.WRAP));

        List<Charts.Item> abItems = new ArrayList<>();
        abItems.add(new Charts.Item("Igazolt", okN, Theme.g[5]));
        abItems.add(new Charts.Item("Igazolatlan", badN, Theme.g[1]));
        abItems.add(new Charts.Item("Nem igazolandó", naN, Theme.muted));
        card(root, "Mulasztások igazolása", null).addView(Charts.donut(c(), abItems, String.valueOf(ab.size())), Ui.lp(Ui.MATCH, Ui.WRAP));
        List<Charts.Item> am = lastMonths(abM);
        LinearLayout amc = card(root, "Mulasztások havonta", null);
        amc.addView(am.isEmpty() ? Ui.empty(c(), "Nincs mulasztás") : Charts.bars(c(), am, Theme.g[2], v -> String.valueOf(Math.round(v))), Ui.lp(Ui.MATCH, Ui.WRAP));

        List<Charts.Item> pdItems = new ArrayList<>();
        for (int i = 0; i < 5; i++) pdItems.add(new Charts.Item(Dt.DAYS[i].substring(0, 3), perDay[i]));
        card(root, "Órák a hét napjain", "a betöltött hét").addView(Charts.bars(c(), pdItems), Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout fun = card(root, "Érdekességek", null);
        LinearLayout list = Ui.vbox(c(), 8);
        if (best != null) list.addView(fact("★", best.avg, "Legjobb tárgy", St.subjName(best.subject) + " · " + Fmt.f2(best.avg)), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (worst != null) list.addView(fact("!", worst.avg, "Legnagyobb fejlődési lehetőség", St.subjName(worst.subject) + " · " + Fmt.f2(worst.avg)), Ui.lp(Ui.MATCH, Ui.WRAP));
        list.addView(fact("Σ", null, "Súlyozott/egyszerű különbség", Fmt.f2(avg - plain)), Ui.lp(Ui.MATCH, Ui.WRAP));
        fun.addView(list, Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    private LinearLayout fact(String icon, Double v, String title, String sub) {
        LinearLayout it = Ui.item(c());
        TextView b = Ui.gradeBadge(c(), icon, v, 36, 16);
        b.setGravity(Gravity.CENTER);
        it.addView(b);
        it.addView(Rows.twoLine(c(), Ui.bold(c(), title), Ui.small(c(), sub)), Ui.grow());
        return it;
    }
}
