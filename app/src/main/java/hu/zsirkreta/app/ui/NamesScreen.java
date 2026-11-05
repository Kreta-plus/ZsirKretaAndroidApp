package hu.zsirkreta.app.ui;

import android.text.TextUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.data.St;

public final class NamesScreen extends Screen {
    public NamesScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Nevek"; }

    @Override public void render(LinearLayout root) {
        root.addView(Ui.muted(c(), "Állítsd be, minek hívjanak, és hogy hívod a tanárokat és tantárgyakat. Csak nálad jelenik meg így."), Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout me = Ui.card(c(), "Rólad", null);
        EditText dn = Ui.input(c(), St.studentName(), St.prefs.displayName);
        EditText nn = Ui.input(c(), "", St.prefs.nickname);
        EditText em = Ui.input(c(), "", St.prefs.emoji);
        dn.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
        nn.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        em.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
        TextView preview = Ui.small(c(), "");
        Runnable upd = () -> preview.setText("Előnézet: „Szia, " + St.shownName() + "! " + St.prefs.emoji + "”");
        Ui.onText(dn, s -> { St.prefs.displayName = s.trim(); St.persist(); upd.run(); });
        Ui.onText(nn, s -> { St.prefs.nickname = s.trim(); St.persist(); upd.run(); });
        Ui.onText(em, s -> { St.prefs.emoji = s.trim().isEmpty() ? "🎓" : s.trim(); St.persist(); upd.run(); });
        upd.run();
        me.addView(Ui.field(c(), "Megjelenített név", dn), Ui.lp(Ui.MATCH, Ui.WRAP));
        me.addView(Ui.field(c(), "Becenév (ezen köszöntünk)", nn), Ui.lp(Ui.MATCH, Ui.WRAP));
        me.addView(Ui.field(c(), "Emoji", em), Ui.lp(Ui.MATCH, Ui.WRAP));
        me.addView(preview, Ui.lp(Ui.MATCH, Ui.WRAP));
        root.addView(me, Ui.lp(Ui.MATCH, Ui.WRAP));

        List<String> teach = St.allTeachers(), subj = St.allSubjects();
        root.addView(group("Tanárok", teach, St.prefs.teacherNames), Ui.lp(Ui.MATCH, Ui.WRAP));
        root.addView(group("Tantárgyak", subj, St.prefs.subjectNames), Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    private LinearLayout group(String title, List<String> items, Map<String, String> map) {
        LinearLayout card = Ui.card(c(), title, String.valueOf(items.size()));
        if (items.isEmpty()) {
            card.addView(Ui.empty(c(), "Még nincs betöltött adat"));
            return card;
        }
        for (String n : items) {
            LinearLayout row = Ui.hbox(c(), 10);
            TextView name = Ui.small(c(), n);
            name.setTextColor(Theme.muted);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(name, Ui.lp(0, Ui.WRAP, 0.9f));
            EditText in = Ui.input(c(), "saját név", map.get(n));
            in.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
            Ui.onText(in, s -> {
                String v = s.trim();
                if (v.isEmpty()) map.remove(n); else map.put(n, v);
                St.persist();
            });
            row.addView(in, Ui.lp(0, Ui.WRAP, 1.1f));
            card.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        return card;
    }
}
