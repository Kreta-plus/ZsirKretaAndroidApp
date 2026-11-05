package hu.zsirkreta.app.ui;

import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.data.Models.Absence;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.util.Dt;

public final class AbsencesScreen extends Screen {
    private static String flt = "all";

    public AbsencesScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Hiányzás"; }

    @Override public void render(LinearLayout root) {
        List<Absence> all = St.absences();
        List<Absence> list = all.stream().filter(a -> {
            switch (flt) {
                case "bad": return !a.ok && !a.na;
                case "ok": return a.ok;
                case "late": return a.late > 0;
                default: return true;
            }
        }).collect(Collectors.toList());
        long days = all.stream().map(a -> a.date.toLocalDate()).distinct().count();
        long bad = all.stream().filter(a -> !a.ok && !a.na).count();
        root.addView(Ui.muted(c(), all.size() + " óra · " + days + " nap · " + bad + " igazolatlan"), Ui.lp(Ui.MATCH, Ui.WRAP));
        root.addView(Ui.seg(c(), new String[]{"all", "bad", "ok", "late"}, new String[]{"Mind", "Igazolatlan", "Igazolt", "Késés"}, flt, k -> { flt = k; act.rerender(); }), Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout card = Ui.card(c());
        if (list.isEmpty()) card.addView(Ui.empty(c(), "Nincs találat"));
        else {
            LinearLayout items = Ui.vbox(c(), 8);
            for (Absence a : list) {
                LinearLayout it = Ui.item(c());
                it.addView(Ui.chip(c(), a.ok ? "igazolt" : a.na ? "nem igazolandó" : "igazolatlan", a.ok ? Ui.C_OK : a.na ? Ui.C_NORMAL : Ui.C_BAD));
                String sub = a.kind.replace('_', ' ') + (a.late > 0 ? " · " + a.late + " perc késés" : "") + (a.type.isEmpty() ? "" : " · " + a.type)
                        + " · " + St.teacherName(a.teacher);
                TextView s = Ui.small(c(), sub);
                s.setMaxLines(2);
                it.addView(Rows.twoLine(c(), Ui.bold(c(), St.subjName(a.subject) + (a.nr != null ? " · " + a.nr + ". óra" : "")), s), Ui.grow());
                it.addView(Ui.small(c(), Dt.fmtFull(a.date)));
                items.addView(it, Ui.lp(Ui.MATCH, Ui.WRAP));
            }
            card.addView(items, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));

        Map<String, Integer> by = new LinkedHashMap<>();
        for (Absence a : all) by.merge(a.subject, 1, Integer::sum);
        List<Charts.Item> top = by.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue())
                .map(e -> new Charts.Item(St.subjName(e.getKey()), e.getValue())).collect(Collectors.toList());
        LinearLayout bars = Ui.card(c(), "Tantárgyanként", null);
        bars.addView(Charts.hbars(c(), top, null, v -> String.valueOf(Math.round(v)), null), Ui.lp(Ui.MATCH, Ui.WRAP));
        root.addView(bars, Ui.lp(Ui.MATCH, Ui.WRAP));
    }
}
