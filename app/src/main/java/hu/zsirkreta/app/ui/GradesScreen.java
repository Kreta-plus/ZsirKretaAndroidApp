package hu.zsirkreta.app.ui;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.R;
import hu.zsirkreta.app.data.Models.Grade;
import hu.zsirkreta.app.data.Models.SubjectAvg;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.util.Dt;
import hu.zsirkreta.app.util.Fmt;

public final class GradesScreen extends Screen {
    /** Kipróbálós (még nem létező) jegy – nem mentődik. */
    private static final class Sim {
        final long id = System.nanoTime();
        final String subject; final int value; final int weight;
        Sim(String s, int v, int w) { subject = s; value = v; weight = w; }
        Grade asGrade() {
            Grade g = new Grade();
            g.value = value; g.weight = weight / 100.0; g.subject = subject; g.date = java.time.LocalDateTime.now();
            g.topic = ""; g.teacher = ""; g.mode = ""; g.kind = ""; g.type = ""; g.text = ""; g.id = "sim" + id;
            return g;
        }
    }

    private static String term = "all", q = "";
    private static final Set<String> open = new HashSet<>();
    private static final List<Sim> sim = new ArrayList<>();
    private static boolean calcOpen = false;

    private LinearLayout body;
    private TextView summary;

    public GradesScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Jegyek"; }

    private static boolean inTerm(Grade g) {
        if (term.equals("all")) return true;
        int m = g.date.getMonthValue() - 1;
        boolean first = m >= 8 || m == 0;   // szept–jan
        return term.equals("1") == first;
    }

    private static List<Grade> simOf(String subject) {
        return sim.stream().filter(x -> x.subject.equals(subject)).map(Sim::asGrade).collect(Collectors.toList());
    }

    /** Hány darab 5-ös / 4-es kell a célátlaghoz. */
    private static final class Need {
        boolean ok; long five = -1, four = -1;
    }

    private static Need needed(List<Grade> list, double target) {
        boolean w = St.prefs.weighted;
        double W = 0, S = 0;
        for (Grade g : list) { double wt = w ? g.weight : 1; W += wt; S += g.value * wt; }
        Need n = new Need();
        if (W > 0 && S / W >= target) { n.ok = true; return n; }
        if (5 > target) n.five = (long) Math.ceil((target * W - S) / (5 - target));
        if (4 > target) n.four = (long) Math.ceil((target * W - S) / (4 - target));
        return n;
    }

    private TextView diff(Double a, Double b) {
        if (a == null || b == null) return Ui.small(c(), "");
        double d = b - a;
        int col = d >= 0.005 ? Theme.g[5] : d <= -0.005 ? Theme.g[1] : Theme.muted;
        return Ui.text(c(), (d >= 0 ? "▲ " : "▼ ") + Fmt.f2(Math.abs(d)), 13, col, true);
    }

    @Override public void render(LinearLayout root) {
        summary = Ui.muted(c(), "");
        root.addView(summary, Ui.lp(Ui.MATCH, Ui.WRAP));

        EditText search = Ui.input(c(), "Keresés tantárgy vagy téma szerint…", q);
        Ui.onText(search, s -> { q = s.toLowerCase(Locale.ROOT); renderBody(); });
        root.addView(search, Ui.lp(Ui.MATCH, Ui.WRAP));
        root.addView(Ui.seg(c(), new String[]{"all", "1", "2"}, new String[]{"Egész év", "1. félév", "2. félév"}, term, k -> { term = k; renderBody(); }), Ui.lp(Ui.MATCH, Ui.WRAP));

        body = Ui.vbox(c(), Theme.gap);
        root.addView(body, Ui.lp(Ui.MATCH, Ui.WRAP));
        renderBody();
    }

    private void renderBody() {
        body.removeAllViews();
        String ql = q.trim();
        List<SubjectAvg> subs = new ArrayList<>();
        for (SubjectAvg s : St.bySubject()) {
            List<Grade> l = s.list.stream().filter(GradesScreen::inTerm)
                    .filter(g -> ql.isEmpty() || (g.topic + g.subject + St.subjName(g.subject)).toLowerCase(Locale.ROOT).contains(ql))
                    .collect(Collectors.toList());
            if (!l.isEmpty()) subs.add(new SubjectAvg(s.subject, l, St.avgOf(l)));
        }
        List<Grade> all = new ArrayList<>();
        for (SubjectAvg s : subs) all.addAll(s.list);
        summary.setText(all.size() + " jegy · átlag " + Fmt.f2(St.avgOf(all)));

        body.addView(calculator(subs, all), Ui.lp(Ui.MATCH, Ui.WRAP));

        List<Grade> terms = St.termGrades();
        if (!terms.isEmpty()) {
            LinearLayout card = Ui.card(c(), "Félévi és év végi jegyek", null);
            LinearLayout list = Ui.vbox(c(), 8);
            for (Grade g : terms) {
                LinearLayout it = Ui.item(c());
                TextView b = Ui.gradeBadge(c(), g.value == null ? "·" : String.valueOf(g.value), g.value == null ? null : Double.valueOf(g.value), 36, 16);
                b.setBackground(Ui.shape(Theme.gradeColor(g.value == null ? null : Double.valueOf(g.value)), 10, Theme.accent, 2));
                it.addView(b);
                it.addView(Rows.twoLine(c(), Ui.bold(c(), St.subjName(g.subject)), Ui.small(c(), g.mode.isEmpty() ? g.type : g.mode)), Ui.grow());
                list.addView(it, Ui.lp(Ui.MATCH, Ui.WRAP));
            }
            card.addView(list, Ui.lp(Ui.MATCH, Ui.WRAP));
            body.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        if (subs.isEmpty()) {
            LinearLayout card = Ui.card(c());
            card.addView(Ui.empty(c(), "Nincs a szűrésnek megfelelő jegy"));
            body.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        for (SubjectAvg s : subs) body.addView(subjectCard(s, subs), Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    // ---------- jegyszámoló ----------
    private View calculator(List<SubjectAvg> subs, List<Grade> all) {
        LinearLayout card = Ui.card(c());
        LinearLayout head = Ui.hbox(c(), 8);
        head.setMinimumHeight(Ui.dp(32));
        LinearLayout t = Ui.vbox(c(), 2);
        t.addView(Ui.h3(c(), "🧮 Jegyszámoló"), Ui.lp(Ui.WRAP, Ui.WRAP));
        t.addView(Ui.small(c(), "mi lenne az átlag, ha…"), Ui.lp(Ui.WRAP, Ui.WRAP));
        head.addView(t, Ui.grow());
        ImageView chev = Ui.icon(c(), R.drawable.ic_expand_more, Theme.muted, 24);
        chev.setRotation(calcOpen ? 180 : 0);
        head.addView(chev);
        head.setOnClickListener(v -> { calcOpen = !calcOpen; renderBody(); });
        card.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));
        if (!calcOpen) return card;

        card.addView(simForm(null, subs), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (sim.isEmpty()) {
            card.addView(Ui.small(c(), "Válassz tantárgyat, jegyet és súlyozást (%), majd koppints a Kipróbálra. Tantárgyanként is kipróbálhatsz jegyet lent."), Ui.lp(Ui.MATCH, Ui.WRAP));
            return card;
        }
        card.addView(simChips(sim, true), Ui.lp(Ui.MATCH, Ui.WRAP));

        List<Grade> extra = new ArrayList<>(all);
        for (Sim x : sim) if (subs.stream().anyMatch(s -> s.subject.equals(x.subject))) extra.add(x.asGrade());
        Double totNow = St.avgOf(all), totNext = St.avgOf(extra);
        List<Double> nows = new ArrayList<>(), nexts = new ArrayList<>();
        for (SubjectAvg s : subs) {
            List<Grade> l = new ArrayList<>(s.list);
            l.addAll(simOf(s.subject));
            if (s.avg != null) nows.add(s.avg);
            Double n = St.avgOf(l);
            if (n != null) nexts.add(n);
        }
        Double mNow = nows.isEmpty() ? null : nows.stream().mapToDouble(Double::doubleValue).average().getAsDouble();
        Double mNext = nexts.isEmpty() ? null : nexts.stream().mapToDouble(Double::doubleValue).average().getAsDouble();
        card.addView(summaryItem("Összes jegy átlaga", Fmt.f2(totNow) + " → " + Fmt.f2(totNext), diff(totNow, totNext)), Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(summaryItem("Tantárgyi átlagok átlaga", Fmt.f2(mNow) + " → " + Fmt.f2(mNext), diff(mNow, mNext)), Ui.lp(Ui.MATCH, Ui.WRAP));
        for (SubjectAvg s : subs) {
            if (sim.stream().noneMatch(x -> x.subject.equals(s.subject))) continue;
            List<Grade> l = new ArrayList<>(s.list);
            l.addAll(simOf(s.subject));
            Double nx = St.avgOf(l);
            LinearLayout it = Ui.item(c());
            it.addView(Ui.bold(c(), St.subjName(s.subject)), Ui.grow());
            it.addView(Ui.body(c(), Fmt.f2(s.avg) + " → "));
            it.addView(Ui.bold(c(), Fmt.f2(nx)));
            it.addView(diff(s.avg, nx));
            card.addView(it, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        card.addView(Ui.smallButton(c(), "Összes kipróbált jegy törlése", Ui.B_DANGER, () -> { sim.clear(); renderBody(); }), Ui.lp(Ui.WRAP, Ui.WRAP));
        return card;
    }

    private View summaryItem(String label, String value, View delta) {
        LinearLayout it = Ui.item(c());
        it.addView(Rows.twoLine(c(), Ui.small(c(), label), Ui.bold(c(), value)), Ui.grow());
        it.addView(delta);
        return it;
    }

    private View simChips(List<Sim> list, boolean withSubject) {
        Ui.Flow f = Ui.flow(c(), 6);
        for (Sim x : list) {
            TextView chip = Ui.chip(c(), (withSubject ? St.subjName(x.subject) + " · " : "") + x.value + " · " + x.weight + "%  ✕");
            chip.setOnClickListener(v -> { sim.removeIf(y -> y.id == x.id); renderBody(); });
            f.addView(chip);
        }
        return f;
    }

    /** subject == null: tantárgyválasztóval (globális), különben az adott tárgyhoz. */
    private View simForm(String subject, List<SubjectAvg> subs) {
        LinearLayout f = Ui.vbox(c(), 8);
        final String[] subj = {subject != null ? subject : (subs.isEmpty() ? null : subs.get(0).subject)};
        if (subject == null) {
            List<String> names = subs.stream().map(s -> St.subjName(s.subject)).collect(Collectors.toList());
            f.addView(Ui.select(c(), names, 0, i -> subj[0] = subs.get(i).subject), Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        final int[] val = {5};
        LinearLayout row = Ui.hbox(c(), 8);
        row.addView(Ui.select(c(), Arrays.asList("5-ös", "4-es", "3-as", "2-es", "1-es"), 0, i -> val[0] = 5 - i), Ui.lp(0, Ui.WRAP, 1f));
        EditText w = Ui.numberInput(c(), "Súly %", "100");
        row.addView(w, Ui.lp(0, Ui.WRAP, 1f));
        row.addView(Ui.button(c(), "+ Kipróbál", Ui.B_PRIMARY, () -> {
            Double wt = Fmt.parse(w.getText().toString());
            if (subj[0] == null) { act.toast("Nincs tantárgy"); return; }
            if (wt == null || wt < 10 || wt > 500) { act.toast("A súlyozás 10 és 500% közötti legyen"); return; }
            sim.add(new Sim(subj[0], val[0], (int) Math.round(wt)));
            if (subject == null) calcOpen = true; else open.add(subject);
            renderBody();
        }));
        f.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        return f;
    }

    // ---------- tantárgy kártya ----------
    private View subjectCard(SubjectAvg s, List<SubjectAvg> subs) {
        double target0 = St.prefs.targetAvg;
        Double goal = St.prefs.subjectGoals.get(s.subject);
        double target = goal != null ? goal : target0;
        boolean isOpen = open.contains(s.subject);
        LinearLayout card = Ui.card(c());

        double trendUp = 0;
        if (s.list.size() >= 4) {
            Double a = St.avgOf(s.list.subList(0, 3)), b = St.avgOf(s.list.subList(3, s.list.size()));
            if (a != null && b != null) trendUp = a - b;
        }

        LinearLayout head = Ui.hbox(c(), 12);
        head.addView(Ui.gradeBadge(c(), Fmt.f2(s.avg), s.avg, 56, 16));
        LinearLayout mid = Ui.vbox(c(), 3);
        TextView name = Ui.h3(c(), St.subjName(s.subject));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(name, Ui.lp(Ui.MATCH, Ui.WRAP));
        LinearLayout meta = Ui.hbox(c(), 6);
        meta.addView(Ui.small(c(), s.list.size() + " jegy"));
        if (trendUp != 0) meta.addView(Ui.text(c(), trendUp > 0 ? "▲ javuló" : "▼ romló", 12, trendUp > 0 ? Theme.g[5] : Theme.g[1], true));
        mid.addView(meta, Ui.lp(Ui.MATCH, Ui.WRAP));
        head.addView(mid, Ui.grow());
        ImageView chev = Ui.icon(c(), R.drawable.ic_expand_more, Theme.muted, 24);
        chev.setRotation(isOpen ? 180 : 0);
        head.addView(chev);
        card.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));

        Ui.Flow chips = Ui.flow(c(), 6);
        for (int i = 0; i < Math.min(8, s.list.size()); i++) chips.addView(Ui.gradeChip(c(), s.list.get(i)));
        card.addView(chips, Ui.lp(Ui.MATCH, Ui.WRAP));

        head.setOnClickListener(v -> { if (!open.remove(s.subject)) open.add(s.subject); renderBody(); });
        if (!isOpen) return card;

        card.addView(Ui.divider(c()));
        // cél + mennyi kell hozzá
        Need n = needed(s.list, target);
        LinearLayout goalRow = Ui.hbox(c(), 10);
        goalRow.addView(Ui.muted(c(), "Cél ennél a tárgynál:"), Ui.grow());
        EditText g = Ui.numberInput(c(), "", Fmt.num(target));
        g.setGravity(Gravity.CENTER);
        g.setOnFocusChangeListener((v, focus) -> {
            if (focus) return;
            Double val = Fmt.parse(g.getText().toString());
            St.prefs.subjectGoals.put(s.subject, val == null ? target0 : Math.min(5, Math.max(1, val)));
            St.persist();
            v.post(this::renderBody);
        });
        g.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        g.setOnEditorActionListener((v, a, e) -> { v.clearFocus(); return false; });
        goalRow.addView(g, Ui.lp(Ui.dp(84), Ui.WRAP));
        card.addView(goalRow, Ui.lp(Ui.MATCH, Ui.WRAP));
        Ui.Flow need = Ui.flow(c(), 6);
        if (n.ok) need.addView(Ui.chip(c(), "✓ a cél teljesítve", Ui.C_OK));
        else {
            if (n.five >= 0) need.addView(Ui.chip(c(), n.five + " db 5-ös kell", Ui.C_WARN));
            if (n.four >= 0) need.addView(Ui.chip(c(), n.four + " db 4-es kell", Ui.C_WARN));
        }
        card.addView(need, Ui.lp(Ui.MATCH, Ui.WRAP));

        card.addView(Ui.small(c(), "Jegy kipróbálása ennél a tárgynál:"), Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(simForm(s.subject, subs), Ui.lp(Ui.MATCH, Ui.WRAP));
        List<Sim> mine = sim.stream().filter(x -> x.subject.equals(s.subject)).collect(Collectors.toList());
        if (!mine.isEmpty()) {
            card.addView(simChips(mine, false), Ui.lp(Ui.MATCH, Ui.WRAP));
            List<Grade> l = new ArrayList<>(s.list);
            l.addAll(simOf(s.subject));
            Double nx = St.avgOf(l);
            LinearLayout it = Ui.item(c());
            it.addView(Ui.muted(c(), "Új átlag ennél a tárgynál"), Ui.grow());
            it.addView(Ui.bold(c(), Fmt.f2(s.avg) + " → " + Fmt.f2(nx)));
            it.addView(diff(s.avg, nx));
            card.addView(it, Ui.lp(Ui.MATCH, Ui.WRAP));
            card.addView(Ui.smallButton(c(), "Kipróbált jegyek törlése", Ui.B_DANGER, () -> { sim.removeIf(x -> x.subject.equals(s.subject)); renderBody(); }), Ui.lp(Ui.WRAP, Ui.WRAP));
        }

        LinearLayout list = Ui.vbox(c(), 8);
        for (Grade gr : s.list) {
            LinearLayout it = Ui.item(c());
            it.addView(Ui.gradeChip(c(), gr));
            TextView sub = Ui.small(c(), gr.mode + " · " + St.teacherName(gr.teacher));
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            TextView title = Ui.bold(c(), gr.topic.isEmpty() ? (gr.mode.isEmpty() ? "Érdemjegy" : gr.mode) : gr.topic);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            it.addView(Rows.twoLine(c(), title, sub), Ui.grow());
            it.addView(Ui.small(c(), Dt.fmtFull(gr.date)));
            list.addView(it, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        card.addView(list, Ui.lp(Ui.MATCH, Ui.WRAP));
        return card;
    }
}
