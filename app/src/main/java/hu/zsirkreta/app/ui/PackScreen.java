package hu.zsirkreta.app.ui;

import android.content.Intent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.data.St;

/** Táska: mit kell vinni tantárgyanként (és minden órához). A „holnapi táska” teljes képernyős nézetet a PackActivity adja. */
public final class PackScreen extends Screen {
    public PackScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Táska"; }

    private static List<String> parse(String s) {
        List<String> out = new ArrayList<>();
        for (String x : s.split(",")) { String t = x.trim(); if (!t.isEmpty()) out.add(t.length() > 60 ? t.substring(0, 60) : t); }
        return out;
    }

    /** Hozzáadó sor: szövegmező + „+” gomb; vesszővel több elem is felvehető. */
    private LinearLayout addRow(String hint, java.util.function.Consumer<List<String>> onAdd) {
        LinearLayout row = Ui.hbox(c(), 8);
        EditText in = Ui.input(c(), hint, "");
        in.setImeOptions(EditorInfo.IME_ACTION_DONE);
        Runnable go = () -> {
            List<String> v = parse(in.getText().toString());
            if (!v.isEmpty()) onAdd.accept(v);
        };
        in.setOnEditorActionListener((v, a, e) -> { go.run(); return true; });
        row.addView(in, Ui.lp(0, Ui.WRAP, 1f));
        row.addView(Ui.button(c(), "+", Ui.B_DEFAULT, go), Ui.lp(Ui.dp(52), Ui.WRAP));
        return row;
    }

    private Ui.Flow chips(List<String> items, java.util.function.IntConsumer onRemove, String emptyText) {
        Ui.Flow f = Ui.flow(c(), 6);
        if (items.isEmpty()) f.addView(Ui.small(c(), emptyText));
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            android.widget.TextView chip = Ui.chip(c(), items.get(i) + "  ✕");
            chip.setOnClickListener(v -> onRemove.accept(idx));
            f.addView(chip);
        }
        return f;
    }

    @Override public void render(LinearLayout root) {
        root.addView(Ui.muted(c(), "Add meg tantárgyanként, mit kell vinni – a holnapi órákhoz kiírja."), Ui.lp(Ui.MATCH, Ui.WRAP));
        root.addView(Ui.button(c(), "⛶  Holnapi táska teljes képernyőn", Ui.B_PRIMARY, () -> {
            St.flushNow();
            act.startActivity(new Intent(act, PackActivity.class));
        }), Ui.lp(Ui.MATCH, Ui.WRAP));

        // minden órához
        LinearLayout common = Ui.card(c(), "🎒 Minden órához", null);
        common.addView(Ui.small(c(), "minden tantárgynál megjelenik"), Ui.lp(Ui.MATCH, Ui.WRAP));
        common.addView(chips(St.prefs.packAll, i -> { St.prefs.packAll.remove(i); St.persist(); act.rerender(); }, "Még nincs (pl. tolltartó, füzet, tízórai)"), Ui.lp(Ui.MATCH, Ui.WRAP));
        common.addView(addRow("pl. tolltartó, toll…", v -> { St.prefs.packAll.addAll(v); St.persist(); act.rerender(); }), Ui.lp(Ui.MATCH, Ui.WRAP));
        root.addView(common, Ui.lp(Ui.MATCH, Ui.WRAP));

        List<String> subs = St.allSubjects();
        if (subs.isEmpty()) {
            LinearLayout card = Ui.card(c());
            card.addView(Ui.empty(c(), "Még nincs betöltött tantárgy – frissíts a Kezdőlapon."));
            root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        for (String s : subs) {
            LinearLayout card = Ui.card(c());
            LinearLayout head = Ui.hbox(c(), 10);
            head.addView(Ui.dot(c(), Theme.subjColor(s)));
            head.addView(Ui.h3(c(), St.subjName(s)), Ui.grow());
            card.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));
            List<String> own = St.prefs.packItems.computeIfAbsent(s, k -> new ArrayList<>());
            card.addView(chips(own, i -> {
                own.remove(i);
                if (own.isEmpty()) St.prefs.packItems.remove(s);
                St.persist();
                act.rerender();
            }, "Még nincs felszerelés"), Ui.lp(Ui.MATCH, Ui.WRAP));
            if (own.isEmpty()) St.prefs.packItems.remove(s);
            card.addView(addRow("pl. füzet, könyv…", v -> {
                St.prefs.packItems.computeIfAbsent(s, k -> new ArrayList<>()).addAll(v);
                St.persist();
                act.rerender();
            }), Ui.lp(Ui.MATCH, Ui.WRAP));
            root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
    }
}
