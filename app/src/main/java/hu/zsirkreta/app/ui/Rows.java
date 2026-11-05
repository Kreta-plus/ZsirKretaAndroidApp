package hu.zsirkreta.app.ui;

import android.content.Context;
import android.graphics.Paint;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import hu.zsirkreta.app.data.Models.Grade;
import hu.zsirkreta.app.data.Models.Homework;
import hu.zsirkreta.app.data.Models.Lesson;
import hu.zsirkreta.app.data.Models.TestItem;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.util.Dt;
import hu.zsirkreta.app.util.Fmt;

/** Több képernyőn is használt listasorok. */
final class Rows {
    private Rows() {}

    static LinearLayout twoLine(Context c, TextView title, TextView sub) {
        LinearLayout col = Ui.vbox(c, 3);
        col.addView(title, Ui.lp(Ui.MATCH, Ui.WRAP));
        if (sub != null) col.addView(sub, Ui.lp(Ui.MATCH, Ui.WRAP));
        return col;
    }

    static LinearLayout grade(Context c, Grade g) {
        LinearLayout it = Ui.item(c);
        it.addView(Ui.gradeChip(c, g));
        String topic = g.topic.isEmpty() ? g.mode : g.topic;
        TextView sub = Ui.small(c, (topic.isEmpty() ? "—" : topic) + " · " + St.teacherName(g.teacher));
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        it.addView(twoLine(c, Ui.bold(c, St.subjName(g.subject)), sub), Ui.grow());
        it.addView(Ui.small(c, Dt.fmtDate(g.date)));
        return it;
    }

    /** Határidő-címke: lejárt = piros, ma/holnap = sárga. */
    static TextView dueChip(Context c, java.time.LocalDateTime due) {
        LocalDate today = Dt.today();
        long hours = ChronoUnit.HOURS.between(today.atStartOfDay(), due);
        int kind = due.toLocalDate().isBefore(today) ? Ui.C_BAD : hours < 36 ? Ui.C_WARN : Ui.C_NORMAL;
        return Ui.chip(c, Dt.relDay(due), kind);
    }

    static LinearLayout homeworkBrief(Context c, Homework h) {
        LinearLayout it = Ui.item(c);
        it.addView(Ui.dot(c, Theme.subjColor(h.subject)));
        TextView sub = Ui.small(c, Fmt.stripHtml(h.text).replaceAll("\\s+", " ").trim());
        sub.setMaxLines(2);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        it.addView(twoLine(c, Ui.bold(c, St.subjName(h.subject)), sub), Ui.grow());
        it.addView(dueChip(c, h.due));
        return it;
    }

    static LinearLayout test(Context c, TestItem t) {
        LinearLayout it = Ui.item(c);
        it.addView(Ui.dot(c, Theme.subjColor(t.subject)));
        String kind = t.kind.isEmpty() ? "számonkérés" : t.kind;
        TextView sub = Ui.small(c, kind + (t.topic.isEmpty() ? "" : " · " + t.topic));
        sub.setMaxLines(2);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        it.addView(twoLine(c, Ui.bold(c, St.subjName(t.subject)), sub), Ui.grow());
        it.addView(Ui.chip(c, Dt.relDay(t.date), Ui.C_WARN));
        return it;
    }

    /** Egy óra az órarendből / a mai órák listájából. */
    static LinearLayout lesson(Context c, Lesson l, boolean past, boolean next) {
        LinearLayout it = Ui.item(c);
        if (next) it.setBackground(Ui.shape(Theme.surface2, Math.max(8, Theme.radius - 4), Theme.accent, 1.5f));
        it.setAlpha(past ? 0.5f : 1f);
        it.addView(Ui.dot(c, Theme.subjColor(l.subject)));
        TextView name = Ui.bold(c, St.subjName(l.subject));
        if (l.off) { name.setPaintFlags(name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG); name.setTextColor(Theme.muted); }
        String who = St.teacherName(l.sub.isEmpty() ? l.teacher : l.sub) + (l.sub.isEmpty() ? "" : " (helyettes)");
        TextView sub = Ui.small(c, Dt.fmtTime(l.start) + "–" + Dt.fmtTime(l.end) + " · " + (l.room.isEmpty() ? "—" : l.room) + " · " + who);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        it.addView(twoLine(c, name, sub), Ui.grow());
        if (l.test) it.addView(Ui.chip(c, "dolgozat", Ui.C_WARN));
        return it;
    }
}
