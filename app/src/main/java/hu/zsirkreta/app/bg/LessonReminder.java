package hu.zsirkreta.app.bg;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import hu.zsirkreta.app.data.Models.Lesson;
import hu.zsirkreta.app.data.Models.Raw;
import hu.zsirkreta.app.data.Norm;
import hu.zsirkreta.app.data.Prefs;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.data.Store;
import hu.zsirkreta.app.util.Async;
import hu.zsirkreta.app.util.Dt;

/**
 * Óra előtti emlékeztető: X perccel minden óra előtt, vagy csak a nap első órája előtt.
 *
 * Nem a KRÉTA-ból jön: a telefonon tárolt órarendből (amit az app vagy a háttér-ellenőrzés legutóbb letöltött) számoljuk
 * ki a következő időpontot, és a telefon órájára pontos ébresztőt állítunk. Az ébresztő idején semmilyen hálózati kérés
 * nincs. Elmaradó órára nem szól. Mindig csak a legközelebbi emlékeztető van beállítva; ha lefutott, az állítja be a
 * következőt. Újraszámol: beállítás-változáskor, friss órarendnél, app-indításkor, újraindítás és időállítás után.
 */
public final class LessonReminder {
    private LessonReminder() {}

    public static final String OFF = "off", ALL = "all", FIRST = "first";
    /** A választható előidő (perc); ezen kívül egyéni érték is megadható 1 és MAX_MIN között. */
    public static final int[] MINUTES = {1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120};
    public static final int DEFAULT_MIN = 5, MAX_MIN = 180;
    static final String ACTION = "hu.zsirkreta.app.LESSON";
    /** Ha az ébresztő késve fut (pl. energiatakarékos mód), legfeljebb ennyivel később még szól, utána kihagyja. */
    private static final long LATE_OK_MS = 3 * 60_000L;

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("notify", Context.MODE_PRIVATE); }

    // ---------- beállítások (készülékhez kötöttek, mint a többi értesítés-beállítás) ----------
    public static String mode(Context c) {
        String m = sp(c).getString("lessonMode", OFF);
        return ALL.equals(m) || FIRST.equals(m) ? m : OFF;
    }
    public static void setMode(Context c, String m) { sp(c).edit().putString("lessonMode", m).apply(); }
    public static int minutes(Context c) { return Math.max(1, Math.min(MAX_MIN, sp(c).getInt("lessonMin", DEFAULT_MIN))); }
    public static void setMinutes(Context c, int m) { sp(c).edit().putInt("lessonMin", Math.max(1, Math.min(MAX_MIN, m))).apply(); }

    /** A beállított következő emlékeztető ideje (epoch ms, 0 ha nincs) és hogy melyik óráról szól. */
    public static long nextAt(Context c) { return sp(c).getLong("lessonNext", 0); }
    public static String nextWhat(Context c) { return sp(c).getString("lessonNextWhat", ""); }

    // ---------- tervezés ----------
    /** Egy esedékes emlékeztető. */
    static final class Due {
        final Lesson lesson;
        final LocalDateTime at;
        final String key;
        Due(Lesson l, LocalDateTime at) {
            this.lesson = l;
            this.at = at;
            this.key = Dt.iso(l.start.toLocalDate()) + "|" + l.start.toLocalTime() + "|" + (l.id.isEmpty() ? l.subject : l.id);
        }
    }

    /** Ez a hét és a következő a telefonon tárolt órarendből (hálózat nélkül), időrendben. */
    static List<Lesson> stored(LocalDate from) {
        List<Lesson> out = new ArrayList<>();
        LocalDate mon = Dt.mondayOf(from);
        for (int w = 0; w < 2; w++) {
            Raw r = St.storedWeek(mon.plusDays(7L * w));
            if (r != null) out.addAll(Norm.lessons(r.data));
        }
        out.sort((a, b) -> a.start.compareTo(b.start));
        return out;
    }

    /** A még esedékes emlékeztetők időrendben (a mód szerint; elmaradó órára nem, a már elkezdődöttekre sem). */
    static List<Due> plan(String mode, int min, List<Lesson> ls, LocalDateTime now) {
        List<Due> out = new ArrayList<>();
        if (OFF.equals(mode)) return out;
        LocalDate lastDay = null;
        for (Lesson l : ls) {
            if (l.off) continue;
            LocalDate d = l.start.toLocalDate();
            boolean first = !d.equals(lastDay);   // a nap első megtartott órája
            lastDay = d;
            if (FIRST.equals(mode) && !first) continue;
            if (!l.start.isAfter(now)) continue;
            LocalDateTime at = l.start.minusMinutes(min);
            if (Dt.toMillis(at) < System.currentTimeMillis() - LATE_OK_MS) continue;
            out.add(new Due(l, at));
        }
        return out;
    }

    private static Set<String> done(Context c) { return new HashSet<>(sp(c).getStringSet("lessonDone", new HashSet<>())); }

    /** A már kiküldött emlékeztetők (hogy egy késve futó vagy ismételt ébresztő ne szóljon kétszer); a régieket elfelejti. */
    private static void saveDone(Context c, Set<String> keys) {
        String today = Dt.iso(Dt.today());
        Set<String> keep = new HashSet<>();
        for (String k : keys) if (k.substring(0, Math.min(10, k.length())).compareTo(today) >= 0) keep.add(k);
        sp(c).edit().putStringSet("lessonDone", keep).apply();
    }

    private static PendingIntent pi(Context c) {
        Intent i = new Intent(c, Alarms.Tick.class).setAction(ACTION);
        return PendingIntent.getBroadcast(c, ACTION.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pi(c));
        sp(c).edit().remove("lessonNext").remove("lessonNextWhat").apply();
    }

    /** A legközelebbi emlékeztető beállítása (háttérszálon hívandó: fájlt olvas). */
    public static synchronized void reschedule(Context c) {
        c = c.getApplicationContext();
        Store.Account a = Store.account();
        String mode = mode(c);
        if (a == null || OFF.equals(mode)) { cancel(c); return; }
        LocalDateTime now = LocalDateTime.now();
        Set<String> done = done(c);
        Due next = null;
        for (Due d : plan(mode, minutes(c), stored(now.toLocalDate()), now)) if (!done.contains(d.key)) { next = d; break; }
        if (next == null) { cancel(c); return; }
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long when = Math.max(System.currentTimeMillis() + 1000, Dt.toMillis(next.at));
        PendingIntent p = pi(c);
        try {
            if (Notifs.exactAllowed(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p);
        }
        Prefs pr = Store.loadPrefs(a.ukey);
        sp(c).edit().putLong("lessonNext", when)
                .putString("lessonNextWhat", subj(pr, next.lesson.subject) + " (" + Dt.relDay(next.lesson.start) + " " + Dt.fmtTime(next.lesson.start) + ")").apply();
    }

    private static volatile Context pending;
    private static final Runnable SOON = () -> {
        Context c = pending;
        if (c != null) Async.bg(() -> reschedule(c));
    };

    /** Újraszámolás kicsit később, háttérszálon (több gyors egymás utáni ok egyetlen számolássá olvad). */
    public static void rescheduleSoon(Context c) {
        pending = c.getApplicationContext();
        Async.handler().removeCallbacks(SOON);
        Async.ui(SOON, 1500);
    }

    // ---------- kiküldés ----------
    /** Az ébresztő lefutott: az esedékes óra (vagy órák) értesítése, majd a következő beállítása. */
    static synchronized void fire(Context c) {
        try {
            Store.Account a = Store.account();
            String mode = mode(c);
            if (a == null || OFF.equals(mode)) return;
            Prefs p = Store.loadPrefs(a.ukey);
            LocalDateTime now = LocalDateTime.now();
            List<Lesson> ls = stored(now.toLocalDate());
            Set<String> done = done(c);
            for (Due d : plan(mode, minutes(c), ls, now)) {
                if (d.at.isAfter(now.plusSeconds(30))) break;   // a többi később esedékes
                if (done.contains(d.key)) continue;
                post(c, p, d.lesson, ls, FIRST.equals(mode), now);
                done.add(d.key);
            }
            saveDone(c, done);
        } catch (RuntimeException e) {
            Notifs.ran(c, "óra előtti emlékeztető: " + e);
        } finally {
            reschedule(c);
        }
    }

    private static String subj(Prefs p, String n) {
        String v = p.subjectNames.get(n);
        return v == null || v.isEmpty() ? n : v;
    }

    private static String teacher(Prefs p, String n) {
        if (n == null || n.isEmpty()) return "";
        String v = p.teacherNames.get(n);
        return v == null || v.isEmpty() ? n : v;
    }

    private static void post(Context c, Prefs p, Lesson l, List<Lesson> all, boolean firstMode, LocalDateTime now) {
        long m = Dt.minutesUntil(now, l.start);
        String when = m <= 0 ? "Most kezdődik" : Dt.duration(m) + " múlva";
        String name = subj(p, l.subject);
        List<String> parts = new ArrayList<>();
        if (firstMode) parts.add(Dt.relDay(l.start).equals("ma") ? "A mai első órád" : "Az első órád (" + Dt.relDay(l.start) + ")");
        else if (l.nr != null) parts.add(l.nr + ". óra");
        parts.add(Dt.fmtTime(l.start) + "–" + Dt.fmtTime(l.end));
        if (!l.room.isEmpty()) parts.add(l.room);
        String who = teacher(p, l.sub.isEmpty() ? l.teacher : l.sub);
        if (!who.isEmpty()) parts.add(l.sub.isEmpty() ? who : "helyettesít: " + who);
        if (l.test) parts.add("dolgozat!");
        // a tantárgy a cím elején, hogy összecsukott értesítésben is látsszon
        String title = (firstMode ? "☀️ " : "⏰ ") + when + ": " + name;
        if (firstMode) {
            // a nap áttekintése: hány óra, meddig, és ha van, a dolgozatok
            int count = 0;
            LocalDateTime lastEnd = l.end;
            Set<String> tests = new LinkedHashSet<>();
            for (Lesson x : all) {
                if (x.off || !Dt.sameDay(x.start, l.start.toLocalDate())) continue;
                count++;
                if (x.end.isAfter(lastEnd)) lastEnd = x.end;
                if (x.test) tests.add(subj(p, x.subject));
            }
            parts.add(count + " óra, " + Dt.fmtTime(lastEnd) + "-ig");
            if (!tests.isEmpty() && !(tests.size() == 1 && l.test && tests.contains(name))) parts.add("dolgozat: " + String.join(", ", tests));
        }
        long untilEnd = Dt.toMillis(l.end) - System.currentTimeMillis();
        Notifs.post(c, Notifs.LESSON, title, String.join(" · ", parts), null, Math.max(60_000L, untilEnd));
    }

    /**
     * „Próba” a Beállításokból: a következő tárolt órával (vagy egy példával, ha nincs) azonnal mutat egy emlékeztetőt.
     * Háttérszálon hívandó. Visszaad: true, ha valódi órát talált.
     */
    public static boolean test(Context c) {
        c = c.getApplicationContext();
        Store.Account a = Store.account();
        Prefs p = a == null ? new Prefs() : Store.loadPrefs(a.ukey);
        LocalDateTime now = LocalDateTime.now();
        List<Lesson> ls = stored(now.toLocalDate());
        boolean first = FIRST.equals(mode(c));
        List<Due> due = plan(first ? FIRST : ALL, minutes(c), ls, now);
        if (!due.isEmpty()) {
            Lesson l = due.get(0).lesson;
            post(c, p, l, ls, first, l.start.minusMinutes(minutes(c)));
            return true;
        }
        Lesson l = new Lesson();
        l.subject = "Matematika";
        l.room = "104";
        l.nr = 1;
        l.start = now.plusMinutes(minutes(c));
        l.end = l.start.plusMinutes(45);
        List<Lesson> one = new ArrayList<>();
        one.add(l);
        post(c, p, l, one, first, now);
        return false;
    }
}
