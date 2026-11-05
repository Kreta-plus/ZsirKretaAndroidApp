package hu.zsirkreta.app.ui;

import android.widget.LinearLayout;

import java.util.List;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.data.Models.Note;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.util.Dt;
import hu.zsirkreta.app.util.Fmt;

public final class NotesScreen extends Screen {
    public NotesScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Feljegyzések"; }

    @Override public void render(LinearLayout root) {
        List<Note> list = St.notes();
        root.addView(Ui.muted(c(), list.size() + " bejegyzés a tanároktól"), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (list.isEmpty()) {
            LinearLayout card = Ui.card(c());
            card.addView(Ui.empty(c(), "Nincs feljegyzés"));
            root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        for (Note n : list) {
            LinearLayout card = Ui.card(c(), n.title.isEmpty() ? "Feljegyzés" : n.title, n.date == null ? "" : Dt.fmtFull(n.date));
            card.addView(Ui.body(c(), Fmt.stripHtml(n.text).trim()), Ui.lp(Ui.MATCH, Ui.WRAP));
            card.addView(Ui.small(c(), St.teacherName(n.teacher) + (n.type.isEmpty() ? "" : " · " + n.type)), Ui.lp(Ui.MATCH, Ui.WRAP));
            root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
    }
}
