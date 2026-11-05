package hu.zsirkreta.app.ui;

import android.app.TimePickerDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.R;
import hu.zsirkreta.app.bg.Alarms;
import hu.zsirkreta.app.bg.LessonReminder;
import hu.zsirkreta.app.bg.Notifs;
import hu.zsirkreta.app.bg.Watcher;
import hu.zsirkreta.app.util.Async;
import hu.zsirkreta.app.util.Dt;

/**
 * Értesítések: a kezdőlapi „Kapcsold be” kártya, a Beállítások értesítés-része (háttér-értesítések és az óra előtti
 * emlékeztető) és a fejlesztői gyakoriság-választó.
 */
final class NotifyCard {
    private NotifyCard() {}

    private static String two(int n) { return n < 10 ? "0" + n : String.valueOf(n); }

    static String intervalLabel(int m) { return m < 60 ? m + " perc" : m % 60 == 0 ? (m / 60) + " óra" : (m / 60) + " óra " + (m % 60) + " perc"; }

    private static boolean ready(MainActivity act) { return Notifs.permitted(act) && Notifs.unrestricted(act) && Notifs.exactAllowed(act); }

    // ---------- kezdőlap ----------
    /** Csak akkor látszik, ha az értesítések be vannak kapcsolva, de valamelyik engedély hiányzik (és nem nyomott rá a „Most nem”-re). */
    static void addSetupTo(LinearLayout root, MainActivity act) {
        if (!Notifs.enabled(act) || ready(act) || Notifs.setupDismissed(act)) return;
        LinearLayout card = Ui.card(act);
        card.setBackground(Ui.shape(Theme.withAlpha(Theme.surface, Math.max(0.85f, Theme.cardAlpha)), Theme.radius, Theme.accent, 2));
        LinearLayout head = Ui.hbox(act, 12);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badge = new FrameLayout(act);
        badge.setBackground(Ui.shape(Theme.accent, 14));
        badge.addView(Ui.icon(act, R.drawable.ic_notification, Theme.onAccent, 26), new FrameLayout.LayoutParams(Ui.dp(26), Ui.dp(26), Gravity.CENTER));
        head.addView(badge, Ui.lp(Ui.dp(46), Ui.dp(46)));
        LinearLayout col = Ui.vbox(act, 3);
        col.addView(Ui.h3(act, "Kapcsold be az értesítéseket"), Ui.lp(Ui.MATCH, Ui.WRAP));
        col.addView(Ui.small(act, "Szólunk, ha új jegyed, házid, dolgozatod vagy órarend-változásod van, és este emlékeztetünk a holnapra."), Ui.lp(Ui.MATCH, Ui.WRAP));
        head.addView(col, Ui.grow());
        card.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(steps(act, false), Ui.lp(Ui.MATCH, Ui.WRAP));
        LinearLayout row = Ui.hbox(act, 10);
        row.addView(Ui.button(act, "Bekapcsolás", Ui.B_PRIMARY, act::setupNotifications), Ui.grow());
        row.addView(Ui.button(act, "Most nem", Ui.B_DEFAULT, () -> { Notifs.dismissSetup(act); act.rerender(); }), Ui.grow());
        card.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        root.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    /** A szükséges engedélyek, pipával vagy gombbal. */
    private static View steps(MainActivity act, boolean buttons) {
        LinearLayout list = Ui.vbox(act, 8);
        list.addView(step(act, "Értesítések engedélyezése", "hogy a telefon megjeleníthesse őket", Notifs.permitted(act), buttons ? "Engedélyezés" : null, act::setupNotifications), Ui.lp(Ui.MATCH, Ui.WRAP));
        list.addView(step(act, "Futás a háttérben, korlátozás nélkül", "az energiatakarékos mód ne késleltesse az ellenőrzést", Notifs.unrestricted(act), buttons ? "Kikapcsolás" : null, act::requestUnrestricted), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (!Notifs.exactAllowed(act)) list.addView(step(act, "Pontos időzítés", "Ébresztők és emlékeztetők engedélye", false, buttons ? "Engedélyezés" : null, act::setupNotifications), Ui.lp(Ui.MATCH, Ui.WRAP));
        return list;
    }

    private static View step(MainActivity act, String title, String sub, boolean ok, String button, Runnable r) {
        LinearLayout it = Ui.item(act);
        it.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = Ui.text(act, ok ? "✓" : "!", 14, Theme.INK, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(Ui.shape(ok ? Theme.g[5] : Theme.g[3], 12));
        it.addView(mark, Ui.lp(Ui.dp(24), Ui.dp(24)));
        it.addView(Rows.twoLine(act, Ui.bold(act, title), Ui.small(act, sub)), Ui.grow());
        if (!ok && button != null) it.addView(Ui.smallButton(act, button, Ui.B_PRIMARY, r));
        return it;
    }

    // ---------- Beállítások ----------
    static View settings(MainActivity act) {
        LinearLayout card = Ui.card(act, "Változások a KRÉTA-ban", Notifs.enabled(act) ? (ready(act) ? "bekapcsolva" : "engedély kell") : "kikapcsolva");
        card.addView(Ui.switchRow(act, "Értesítések a háttérből", Notifs.enabled(act), on -> {
            Notifs.setEnabled(act, on);
            Alarms.reschedule(act);
            if (on && !ready(act)) act.setupNotifications(); else act.rerender();
        }), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (!Notifs.enabled(act)) {
            card.addView(Ui.small(act, "Kikapcsolva a ZsírKréta nem kérdez a háttérben, és nem küld értesítést."), Ui.lp(Ui.MATCH, Ui.WRAP));
            return card;
        }
        card.addView(steps(act, true), Ui.lp(Ui.MATCH, Ui.WRAP));
        String hint = Notifs.vendorHint();
        if (hint != null) card.addView(Ui.small(act, hint), Ui.lp(Ui.MATCH, Ui.WRAP));

        card.addView(Ui.small(act, "Miről szóljunk?"), Ui.lp(Ui.MATCH, Ui.WRAP));
        LinearLayout kinds = Ui.vbox(act, 0);
        for (String[] k : Notifs.KINDS) {
            kinds.addView(Ui.labeledSwitchSub(act, k[1], k[2], Notifs.kind(act, k[0]), on -> Notifs.setKind(act, k[0], on)), Ui.lp(Ui.MATCH, Ui.WRAP));
            if (k[0].equals(Notifs.EVENING)) kinds.addView(eveningRow(act), Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        card.addView(kinds, Ui.lp(Ui.MATCH, Ui.WRAP));

        long last = Notifs.lastRun(act);
        String err = Notifs.lastError(act);
        String status = "A háttérben " + intervalLabel(Notifs.intervalMin(act)) + "enként néz rá a KRÉTA-ra" + (last > 0 ? " · utoljára " + Dt.ago(last / 1000.0) : " · még nem futott");
        card.addView(Ui.small(act, status), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (!err.isEmpty()) card.addView(Ui.text(act, "Legutóbbi hiba: " + err, 12, Theme.g[1], false), Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout row = Ui.hbox(act, 10);
        row.addView(Ui.button(act, "Próba értesítés", Ui.B_DEFAULT, () -> {
            if (!Notifs.permitted(act)) { act.setupNotifications(); return; }
            Notifs.test(act);
        }), Ui.grow());
        row.addView(Ui.button(act, "Ellenőrzés most", Ui.B_DEFAULT, () -> checkNow(act)), Ui.grow());
        card.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(Ui.smallButton(act, "Az Android értesítési beállításai", Ui.B_GHOST, () -> {
            try { act.startActivity(Notifs.channelSettingsIntent(act)); } catch (Exception ignored) {}
        }), Ui.lp(Ui.WRAP, Ui.WRAP));
        return card;
    }

    // ---------- óra előtti emlékeztető ----------
    private static final String[] MODE_KEYS = {LessonReminder.OFF, LessonReminder.FIRST, LessonReminder.ALL};
    private static final String[] MODE_LABELS = {"Ki", "Csak az első", "Minden óra"};

    private static void lessonChanged(MainActivity act) {
        Async.run(() -> { LessonReminder.reschedule(act); return true; }, ok -> act.rerender(), e -> act.rerender());
    }

    static String minutesLabel(int m) { return m + " perccel"; }

    /** Óra előtti emlékeztető: mikor (ki / csak az első óra előtt / minden óra előtt) és hány perccel előtte. */
    static View lessons(MainActivity act) {
        String mode = LessonReminder.mode(act);
        boolean on = !LessonReminder.OFF.equals(mode);
        LinearLayout card = Ui.card(act, "Óra előtti emlékeztető", on ? (Notifs.permitted(act) ? "bekapcsolva" : "engedély kell") : "kikapcsolva");
        card.addView(Ui.small(act, "A telefon órája szerint szól, a telefonon tárolt órarendből. Ehhez nem kérdezi a KRÉTA-t, és elmaradó órára nem jelez."), Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(Ui.small(act, "Mikor szóljon?"), Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(Ui.seg(act, MODE_KEYS, MODE_LABELS, mode, k -> {
            LessonReminder.setMode(act, k);
            if (!LessonReminder.OFF.equals(k) && !Notifs.permitted(act)) act.askPermissions();
            lessonChanged(act);
        }), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (!on) return card;

        int min = LessonReminder.minutes(act);
        card.addView(Ui.small(act, LessonReminder.FIRST.equals(mode)
                ? "Reggel, a nap első (megtartott) órája előtt " + min + " perccel – a napi órák számával és a dolgozatokkal."
                : "Minden óra előtt " + min + " perccel, teremmel és tanárral."), Ui.lp(Ui.MATCH, Ui.WRAP));

        // hány perccel előtte: előre megadott értékek, vagy egyéni
        List<String> labels = new ArrayList<>();
        int sel = -1;
        for (int i = 0; i < LessonReminder.MINUTES.length; i++) {
            int v = LessonReminder.MINUTES[i];
            labels.add(minutesLabel(v) + " előtte" + (v == LessonReminder.DEFAULT_MIN ? " (alap)" : ""));
            if (v == min) sel = i;
        }
        labels.add(sel < 0 ? "Egyéni: " + minutesLabel(min) + " előtte" : "Egyéni…");
        if (sel < 0) sel = labels.size() - 1;
        card.addView(Ui.field(act, "Hány perccel az óra előtt?", Ui.select(act, labels, sel, i -> {
            if (i < LessonReminder.MINUTES.length) {
                LessonReminder.setMinutes(act, LessonReminder.MINUTES[i]);
                lessonChanged(act);
            } else customMinutes(act);
        })), Ui.lp(Ui.MATCH, Ui.WRAP));

        if (!Notifs.permitted(act)) card.addView(step(act, "Értesítések engedélyezése", "enélkül a telefon nem tudja megjeleníteni", false, "Engedélyezés", act::askPermissions), Ui.lp(Ui.MATCH, Ui.WRAP));
        else if (!Notifs.exactAllowed(act)) card.addView(step(act, "Pontos időzítés", "Ébresztők és emlékeztetők engedélye", false, "Engedélyezés", act::askPermissions), Ui.lp(Ui.MATCH, Ui.WRAP));

        long next = LessonReminder.nextAt(act);
        String what = LessonReminder.nextWhat(act);
        if (next > 0) {
            card.addView(Ui.small(act, "Következő emlékeztető: " + Dt.relDay(Dt.fromMillis(next)) + " " + Dt.fmtTime(Dt.fromMillis(next)) + (what.isEmpty() ? "" : " · " + what)), Ui.lp(Ui.MATCH, Ui.WRAP));
        } else {
            card.addView(Ui.text(act, "Most nincs beállított emlékeztető: a telefonon nincs eltárolva a következő órák órarendje. Nyisd meg az Órarendet, és a következő frissítés után beáll.", 12, Theme.g[2], false), Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        if (!Notifs.enabled(act)) {
            card.addView(Ui.small(act, "A háttér-ellenőrzés ki van kapcsolva, ezért az emlékeztető az appban legutóbb letöltött órarendből dolgozik: a változások (elmaradó óra, helyettesítés) csak akkor kerülnek bele, ha megnyitod az appot."), Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        card.addView(Ui.smallButton(act, "Próba: így fog kinézni", Ui.B_DEFAULT, () -> {
            if (!Notifs.permitted(act)) { act.askPermissions(); return; }
            Async.run(() -> LessonReminder.test(act), real -> { if (!real) act.toast("Nincs tárolt következő óra, egy példával mutattam"); }, e -> act.toast("Hiba: " + e.getMessage()));
        }), Ui.lp(Ui.WRAP, Ui.WRAP));
        return card;
    }

    private static void customMinutes(MainActivity act) {
        Ui.Sheet s = Ui.sheet(act, "Hány perccel az óra előtt?");
        android.widget.EditText in = Ui.numberInput(act, "pl. 7", String.valueOf(LessonReminder.minutes(act)));
        in.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        s.body.addView(Ui.field(act, "Perc (1–" + LessonReminder.MAX_MIN + ")", in), Ui.lp(Ui.MATCH, Ui.WRAP));
        s.body.addView(Ui.button(act, "Mentés", Ui.B_PRIMARY, () -> {
            int v;
            try { v = Integer.parseInt(in.getText().toString().trim()); } catch (Exception e) { act.toast("Adj meg egy számot"); return; }
            if (v < 1 || v > LessonReminder.MAX_MIN) { act.toast("1 és " + LessonReminder.MAX_MIN + " perc között lehet"); return; }
            LessonReminder.setMinutes(act, v);
            s.dismiss();
            lessonChanged(act);
        }), Ui.lp(Ui.MATCH, Ui.WRAP));
        s.dlg.setOnCancelListener(d -> act.rerender());   // a választó visszaáll a mostani értékre
        s.show();
    }

    private static View eveningRow(MainActivity act) {
        LinearLayout row = Ui.hbox(act, 10);
        row.setPadding(0, 0, 0, Ui.dp(6));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.muted(act, "Az esti emlékeztető időpontja"), Ui.grow());
        int m = Notifs.eveningMinute(act);
        row.addView(Ui.button(act, two(m / 60) + ":" + two(m % 60), Ui.B_DEFAULT, () -> new TimePickerDialog(act, (v, h, mi) -> {
            Notifs.setEveningMinute(act, h * 60 + mi);
            Alarms.reschedule(act);
            act.toast("Esti emlékeztető: " + two(h) + ":" + two(mi));
            act.rerender();
        }, m / 60, m % 60, true).show()));
        return row;
    }

    private static void checkNow(MainActivity act) {
        act.toast("Ellenőrzés…");
        Async.run(() -> {
            String e = Watcher.run(act, true);
            return e == null ? "" : e;
        }, e -> {
            act.toast(e.isEmpty() ? "Kész: ha volt új, értesítést kaptál" : "Hiba: " + e);
            act.rerender();
        }, e -> act.toast("Hiba: " + e.getMessage()));
    }

    // ---------- fejlesztői beállítás ----------
    static View devInterval(MainActivity act) {
        LinearLayout box = Ui.vbox(act, 6);
        List<String> labels = new ArrayList<>();
        int cur = Notifs.intervalMin(act), sel = 0;
        for (int i = 0; i < Notifs.INTERVALS.length; i++) {
            int v = Notifs.INTERVALS[i];
            labels.add(intervalLabel(v) + (v == Notifs.DEFAULT_INTERVAL ? " (alap)" : ""));
            if (v == cur) sel = i;
        }
        box.addView(Ui.field(act, "Háttér-lekérés gyakorisága (értesítésekhez)", Ui.select(act, labels, sel, i -> {
            Notifs.setIntervalMin(act, Notifs.INTERVALS[i]);
            Alarms.reschedule(act);
            act.toast("Háttér-lekérés: " + intervalLabel(Notifs.INTERVALS[i]) + "enként");
            act.rerender();
        })), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (cur < Notifs.DEFAULT_INTERVAL) box.addView(Ui.text(act, "15 percnél gyakoribb lekérés több akkumulátort és mobilnetet használ, és a KRÉTA felé is több kérés megy (adatfajtánként legfeljebb percenként egy).", 12, Theme.g[2], false), Ui.lp(Ui.MATCH, Ui.WRAP));
        long next = Alarms.nextTick(act);
        if (next > 0) box.addView(Ui.small(act, "Következő háttér-ellenőrzés: " + Dt.fmtTime(Dt.fromMillis(next))), Ui.lp(Ui.MATCH, Ui.WRAP));
        box.addView(Ui.smallButton(act, "Teszt: a legutóbbi jegy újra jelzése", Ui.B_DEFAULT, () -> Async.run(() -> {
            Watcher.forgetLatestGrade(act);
            String e = Watcher.run(act, true);
            return e == null ? "" : e;
        }, e -> act.toast(e.isEmpty() ? "Elküldve, ha az új jegy értesítés be van kapcsolva" : "Hiba: " + e), e -> act.toast(e.getMessage()))), Ui.lp(Ui.WRAP, Ui.WRAP));
        return box;
    }
}
