package hu.zsirkreta.app.ui;

import android.text.TextUtils;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.data.Models.Homework;
import hu.zsirkreta.app.data.Models.TestItem;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.util.Dt;
import hu.zsirkreta.app.util.Fmt;

public final class HomeworkScreen extends Screen {
    private static String tab = "open";

    public HomeworkScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Házi"; }

    @Override public void render(LinearLayout root) {
        LocalDate today = Dt.today();
        List<Homework> all = St.homework();
        List<Homework> shown = all.stream().filter(h -> {
            boolean notPast = !h.due.toLocalDate().isBefore(today);
            switch (tab) {
                case "late": return !h.done && !notPast;
                case "done": return h.done;
                default: return !h.done && notPast;
            }
        }).collect(Collectors.toList());
        List<TestItem> ts = St.tests().stream().filter(t -> !t.date.toLocalDate().isBefore(today)).collect(Collectors.toList());
        long openN = all.stream().filter(h -> !h.done && !h.due.toLocalDate().isBefore(today)).count();

        root.addView(Ui.muted(c(), openN + " nyitott házi · " + ts.size() + " számonkérés"), Ui.lp(Ui.MATCH, Ui.WRAP));

        if (!ts.isEmpty()) {
            LinearLayout card = Ui.card(c(), "Közelgő számonkérések", String.valueOf(ts.size()));
            LinearLayout list = Ui.vbox(c(), 8);
            for (TestItem t : ts) list.addView(Rows.test(c(), t), Ui.lp(Ui.MATCH, Ui.WRAP));
            card.addView(list, Ui.lp(Ui.MATCH, Ui.WRAP));
            root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        root.addView(Ui.seg(c(), new String[]{"open", "late", "done"}, new String[]{"Nyitott", "Lejárt", "Kész"}, tab, k -> { tab = k; act.rerender(); }), Ui.lp(Ui.MATCH, Ui.WRAP));

        if (shown.isEmpty()) {
            LinearLayout card = Ui.card(c());
            card.addView(Ui.empty(c(), "Itt nincs semmi 🎉"));
            root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        for (Homework h : shown) root.addView(card(h), Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    private LinearLayout card(Homework h) {
        LinearLayout card = Ui.card(c());
        card.setAlpha(h.done ? 0.7f : 1f);
        LinearLayout head = Ui.hbox(c(), 10);
        CheckBox chk = Ui.check(c(), h.done, on -> { St.setHomeworkDone(h, on); act.rerender(); });
        head.addView(chk, Ui.lp(Ui.WRAP, Ui.WRAP));
        head.addView(Ui.dot(c(), Theme.subjColor(h.subject)));
        TextView name = Ui.h3(c(), St.subjName(h.subject));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(name, Ui.grow());
        head.addView(h.done ? Ui.chip(c(), "kész", Ui.C_OK) : Rows.dueChip(c(), h.due));
        card.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));
        TextView text = Ui.body(c(), Fmt.stripHtml(h.text).trim());
        card.addView(text, Ui.lp(Ui.MATCH, Ui.WRAP));
        String meta = h.own ? "saját · " : St.teacherName(h.teacher) + " · feladva: " + Dt.fmtFull(h.given) + " · ";
        card.addView(Ui.small(c(), meta + "határidő: " + Dt.fmtFull(h.due)), Ui.lp(Ui.MATCH, Ui.WRAP));
        return card;
    }
}
