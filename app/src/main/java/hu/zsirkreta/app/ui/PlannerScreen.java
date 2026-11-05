package hu.zsirkreta.app.ui;

import android.app.DatePickerDialog;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.R;
import hu.zsirkreta.app.data.Models.Plan;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.util.Dt;

/** Saját házik és dolgozatok felírása – csak a készüléken, a KRÉTA-ba nem kerül be. */
public final class PlannerScreen extends Screen {
    public PlannerScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Tervező"; }

    private static String newId() { return Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1296), 36); }

    @Override public void render(LinearLayout root) {
        root.addView(Ui.muted(c(), "Saját házik és dolgozatok – csak nálad, a KRÉTA-ba nem kerül be."), Ui.lp(Ui.MATCH, Ui.WRAP));
        List<String> subs = St.allSubjects();
        root.addView(section("Házi feladat hozzáadása", "Feladat", "pl. 45. oldal 3–5. feladat", "Hozzáadás", true, St.prefs.myHomework, subs), Ui.lp(Ui.MATCH, Ui.WRAP));
        root.addView(section("Dolgozat felírása", "Témakör", "pl. Másodfokú egyenletek", "Felírás", false, St.prefs.myTests, subs), Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    private LinearLayout section(String title, String bodyLabel, String bodyHint, String addLabel, boolean hw, List<Plan> store, List<String> subs) {
        LinearLayout card = Ui.card(c(), title, null);
        if (subs.isEmpty()) {
            card.addView(Ui.empty(c(), "Még nincs betöltött tantárgy – frissíts a Kezdőlapon."), Ui.lp(Ui.MATCH, Ui.WRAP));
        } else {
            final int[] sel = {0};
            final LocalDate[] date = {Dt.today()};
            List<String> names = new ArrayList<>();
            for (String s : subs) names.add(St.subjName(s));
            card.addView(Ui.field(c(), "Tantárgy", Ui.select(c(), names, 0, i -> sel[0] = i)), Ui.lp(Ui.MATCH, Ui.WRAP));

            LinearLayout dateBox = Ui.hbox(c(), 8);
            dateBox.setBackground(Ui.ripple(Ui.inputBg(), Math.max(10, Theme.radius - 4)));
            dateBox.setPadding(Ui.dp(14), Ui.dp(11), Ui.dp(10), Ui.dp(11));
            dateBox.setMinimumHeight(Ui.dp(46));
            TextView dt = Ui.body(c(), Dt.fmtFull(date[0]));
            dateBox.addView(dt, Ui.grow());
            dateBox.addView(Ui.icon(c(), R.drawable.ic_calendar, Theme.muted, 20));
            dateBox.setOnClickListener(v -> new DatePickerDialog(act, (p, y, m, d) -> {
                date[0] = LocalDate.of(y, m + 1, d);
                dt.setText(Dt.fmtFull(date[0]));
            }, date[0].getYear(), date[0].getMonthValue() - 1, date[0].getDayOfMonth()).show());
            card.addView(Ui.field(c(), hw ? "Határidő" : "Dátum", dateBox), Ui.lp(Ui.MATCH, Ui.WRAP));

            EditText text = hw ? Ui.textArea(c(), bodyHint, "", 2) : Ui.input(c(), bodyHint, "");
            card.addView(Ui.field(c(), bodyLabel, text), Ui.lp(Ui.MATCH, Ui.WRAP));
            card.addView(Ui.button(c(), addLabel, Ui.B_PRIMARY, () -> {
                String t = text.getText().toString().trim();
                if (t.isEmpty()) { act.toast(hw ? "Írd be a feladatot" : "Írd be a témakört"); return; }
                store.add(new Plan(newId(), subs.get(sel[0]), Dt.iso(date[0]), t.length() > 500 ? t.substring(0, 500) : t, false));
                St.persist();
                act.rerender();
            }), Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        card.addView(Ui.divider(c()));
        List<Plan> sorted = new ArrayList<>(store);
        sorted.sort(Comparator.comparing(p -> p.date));
        if (sorted.isEmpty()) card.addView(Ui.small(c(), hw ? "Még nincs saját házi" : "Még nincs felírt dolgozat"), Ui.lp(Ui.MATCH, Ui.WRAP));
        LinearLayout list = Ui.vbox(c(), 8);
        LocalDate today = Dt.today();
        for (Plan p : sorted) {
            LocalDate d = Dt.parseDate(p.date);
            LinearLayout it = Ui.item(c());
            it.addView(Ui.dot(c(), Theme.subjColor(p.subject)));
            TextView body = Ui.small(c(), p.body);
            body.setMaxLines(3);
            body.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout col = Rows.twoLine(c(), Ui.bold(c(), St.subjName(p.subject)), body);
            if (d != null) col.addView(Ui.small(c(), Dt.fmtFull(d)), Ui.lp(Ui.MATCH, Ui.WRAP));
            it.addView(col, Ui.grow());
            if (d != null) {
                int kind = d.isBefore(today) ? Ui.C_BAD : Dt.days(today, d) < 2 ? Ui.C_WARN : Ui.C_NORMAL;
                it.addView(Ui.chip(c(), Dt.relDay(d), kind));
            }
            it.addView(Ui.iconButton(c(), R.drawable.ic_delete, Theme.g[1], () -> {
                store.removeIf(x -> x.id.equals(p.id));
                St.persist();
                act.rerender();
            }));
            it.setGravity(Gravity.CENTER_VERTICAL);
            list.addView(it, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        card.addView(list, Ui.lp(Ui.MATCH, Ui.WRAP));
        return card;
    }
}
