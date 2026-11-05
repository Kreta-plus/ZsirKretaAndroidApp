package hu.zsirkreta.app.data;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import hu.zsirkreta.app.data.Kreta.KretaError;
import hu.zsirkreta.app.data.Models.Absence;
import hu.zsirkreta.app.data.Models.Grade;
import hu.zsirkreta.app.data.Models.Homework;
import hu.zsirkreta.app.data.Models.Lesson;
import hu.zsirkreta.app.data.Models.Note;
import hu.zsirkreta.app.data.Models.Plan;
import hu.zsirkreta.app.data.Models.Raw;
import hu.zsirkreta.app.data.Models.SubjectAvg;
import hu.zsirkreta.app.data.Models.TestItem;
import hu.zsirkreta.app.util.Async;
import hu.zsirkreta.app.util.Dt;

/** Közös állapot: beállítások, adatok, névfeloldás (a webes state.js megfelelője). */
public final class St {
    private St() {}

    public static final String[] BUNDLE = {"tanulo", "jegyek", "orarend", "mulasztasok", "hazi", "szamonkeres", "feljegyzesek"};
    public static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("tanulo", "adatlap");
        LABELS.put("jegyek", "jegyek");
        LABELS.put("orarend", "órarend");
        LABELS.put("mulasztasok", "hiányzások");
        LABELS.put("hazi", "házi");
        LABELS.put("szamonkeres", "számonkérések");
        LABELS.put("feljegyzesek", "feljegyzések");
    }

    public static Prefs prefs = new Prefs();
    public static final Map<String, Raw> raw = new ConcurrentHashMap<>();
    public static final Map<String, String> errors = new LinkedHashMap<>();
    public static final Map<String, Raw> weekCache = new ConcurrentHashMap<>();   // órarend hetek (hétfő ISO dátuma szerint)
    private static Set<String> hwDone = new TreeSet<>();
    private static double lastForce = 0;   // utolsó kényszerített frissítés (gomb vagy automatikus), epoch mp

    public static void reset() {
        prefs = new Prefs();
        raw.clear();
        errors.clear();
        weekCache.clear();
        lastForce = 0;
        Store.Account a = Store.account();
        hwDone = a == null ? new TreeSet<>() : new TreeSet<>(Store.hwDone(a.ukey));
    }

    // ---------- mentés ----------
    private static final Runnable SAVE = St::flushNow;

    /** Beállítások mentése kis késleltetéssel (gépelés közben nem ír minden betűnél). */
    public static void persist() {
        Async.handler().removeCallbacks(SAVE);
        Async.ui(SAVE, 450);
    }

    public static void flushNow() {
        Async.handler().removeCallbacks(SAVE);
        Store.Account a = Store.account();
        if (a == null) return;
        Prefs p = prefs;
        final String ukey = a.ukey;
        final Prefs snap = Prefs.fromJson(p.toJson());
        Async.bg(() -> Store.savePrefs(ukey, snap));
    }

    // ---------- nevek ----------
    public static String subjName(String n) {
        String v = n == null ? null : prefs.subjectNames.get(n);
        return v == null || v.isEmpty() ? (n == null ? "" : n) : v;
    }

    public static String teacherName(String n) {
        if (n == null || n.isEmpty()) return "";
        String v = prefs.teacherNames.get(n);
        return v == null || v.isEmpty() ? n : v;
    }

    /** A KRÉTA szerinti teljes név (az adatlapból), vagy a fiók neve. */
    public static String studentName() {
        Raw t = raw.get("tanulo");
        if (t != null && t.data instanceof JSONObject) {
            String n = ((JSONObject) t.data).optString("Nev", "");
            if (!n.isEmpty()) return n;
        }
        Store.Account a = Store.account();
        return a == null ? "" : a.name;
    }

    public static String shownName() {
        if (!prefs.nickname.isEmpty()) return prefs.nickname;
        if (!prefs.displayName.isEmpty()) return prefs.displayName;
        String n = studentName().trim();
        if (n.isEmpty()) return "Diák";
        String[] parts = n.split("\\s+");
        return parts[parts.length - 1];
    }

    // ---------- adatok ----------
    private static Object data(String k) { Raw r = raw.get(k); return r == null ? null : r.data; }

    /** Jótanuló mód: minden számértékű jegy ötösként jelenik meg (csak a kijelzőn, a KRÉTA-ban nem változik). */
    public static List<Grade> grades() {
        List<Grade> gs = Norm.grades(data("jegyek"));
        if (prefs.goodStudent) for (Grade g : gs) if (g.value != null) g.value = 5;
        return gs;
    }

    public static List<Grade> numericGrades() {
        List<Grade> out = new ArrayList<>();
        for (Grade g : grades()) if (g.value != null && !g.isTerm && !prefs.hiddenSubjects.contains(g.subject)) out.add(g);
        return out;
    }

    public static List<Grade> termGrades() {
        List<Grade> out = new ArrayList<>();
        for (Grade g : grades()) if (g.isTerm) out.add(g);
        return out;
    }

    public static List<Lesson> lessons() { return fillTestLessons(Norm.lessons(data("orarend")), Dt.mondayOf(Dt.today())); }

    public static List<Absence> absences() { return Norm.absences(data("mulasztasok")); }

    private static LocalDateTime day(String iso) {
        LocalDate d = Dt.parseDate(iso);
        return (d == null ? Dt.today() : d).atStartOfDay();
    }

    /** A KRÉTA-házik és a Tervezőben felvett saját házik együtt, határidő szerint. */
    public static List<Homework> homework() {
        List<Homework> out = new ArrayList<>();
        for (Homework h : Norm.homework(data("hazi"))) {
            if (hwDone.contains(h.id)) h.done = true;
            out.add(h);
        }
        for (Plan p : prefs.myHomework) {
            Homework h = new Homework();
            h.id = "own_" + p.id; h.subject = p.subject; h.text = p.body; h.given = day(p.date); h.due = h.given; h.done = p.done; h.own = true;
            out.add(h);
        }
        out.sort((a, b) -> a.due.compareTo(b.due));
        return out;
    }

    public static void setHomeworkDone(Homework h, boolean on) {
        if (h.own) {
            String id = h.id.substring(4);
            for (Plan p : prefs.myHomework) if (p.id.equals(id)) p.done = on;
            persist();
            return;
        }
        if (on) hwDone.add(h.id); else hwDone.remove(h.id);
        Store.Account a = Store.account();
        if (a != null) {
            final String ukey = a.ukey;
            final Set<String> snap = new TreeSet<>(hwDone);
            Async.bg(() -> Store.saveHwDone(ukey, snap));
        }
    }

    public static List<TestItem> tests() {
        List<TestItem> out = new ArrayList<>(Norm.tests(data("szamonkeres")));
        for (Plan p : prefs.myTests) {
            TestItem t = new TestItem();
            t.id = "own_" + p.id; t.subject = p.subject; t.topic = p.body; t.kind = "saját dolgozat"; t.date = day(p.date); t.own = true;
            out.add(t);
        }
        out.sort((a, b) -> a.date.compareTo(b.date));
        return out;
    }

    public static List<Note> notes() { return Norm.notes(data("feljegyzesek")); }

    // ---------- átlagok ----------
    public static Double avgOf(List<Grade> list) {
        boolean weighted = prefs.weighted;
        double s = 0, w = 0;
        int n = 0;
        for (Grade g : list) {
            if (g.value == null) continue;
            double wt = weighted ? g.weight : 1;
            s += g.value * wt;
            w += wt;
            n++;
        }
        if (n == 0 || w == 0) return null;
        return s / w;
    }

    public static List<SubjectAvg> bySubject() {
        Map<String, List<Grade>> m = new LinkedHashMap<>();
        for (Grade g : numericGrades()) m.computeIfAbsent(g.subject, k -> new ArrayList<>()).add(g);
        List<SubjectAvg> out = new ArrayList<>();
        for (Map.Entry<String, List<Grade>> e : m.entrySet()) out.add(new SubjectAvg(e.getKey(), e.getValue(), avgOf(e.getValue())));
        java.text.Collator hu = java.text.Collator.getInstance(new java.util.Locale("hu"));
        out.sort((a, b) -> hu.compare(subjName(a.subject), subjName(b.subject)));
        return out;
    }

    public static List<String> allSubjects() {
        Set<String> s = new LinkedHashSet<>();
        for (Grade g : grades()) s.add(g.subject);
        for (Lesson l : lessons()) if (!l.fake) s.add(l.subject);
        for (Absence a : absences()) s.add(a.subject);
        for (Homework h : homework()) s.add(h.subject);
        for (TestItem t : tests()) s.add(t.subject);
        List<String> out = new ArrayList<>();
        for (String x : s) if (x != null && !x.isEmpty()) out.add(x);
        java.text.Collator hu = java.text.Collator.getInstance(new java.util.Locale("hu"));
        out.sort(hu);
        return out;
    }

    public static List<String> allTeachers() {
        Set<String> s = new LinkedHashSet<>();
        for (Grade g : grades()) s.add(g.teacher);
        for (Lesson l : lessons()) { s.add(l.teacher); s.add(l.sub); }
        for (Absence a : absences()) s.add(a.teacher);
        for (Homework h : homework()) s.add(h.teacher);
        for (TestItem t : tests()) s.add(t.teacher);
        for (Note n : notes()) s.add(n.teacher);
        List<String> out = new ArrayList<>();
        for (String x : s) if (x != null && !x.isEmpty()) out.add(x);
        java.text.Collator hu = java.text.Collator.getInstance(new java.util.Locale("hu"));
        out.sort(hu);
        return out;
    }

    // ---------- developer mód: teszt órák az üres tanítási napokra ----------
    private static List<String> testPool(List<Lesson> ls) {
        Set<String> s = new LinkedHashSet<>();
        for (Lesson l : ls) s.add(l.subject);
        for (Grade g : Norm.grades(data("jegyek"))) s.add(g.subject);
        for (Homework h : Norm.homework(data("hazi"))) s.add(h.subject);
        for (TestItem t : Norm.tests(data("szamonkeres"))) s.add(t.subject);
        List<String> pool = new ArrayList<>();
        for (String x : s) if (x != null && !x.isEmpty() && !x.equals("Egyéb")) pool.add(x);
        if (pool.size() >= 3) return pool;
        List<String> def = new ArrayList<>();
        Collections.addAll(def, "Matematika", "Magyar nyelv és irodalom", "Történelem", "Angol nyelv", "Fizika", "Testnevelés");
        return def;
    }

    public static List<Lesson> fillTestLessons(List<Lesson> ls, LocalDate monday) {
        if (!prefs.devMode || !prefs.devFillTimetable) return ls;
        List<String> pool = testPool(ls);
        List<Lesson> out = new ArrayList<>(ls);
        for (int i = 0; i < 5; i++) {
            LocalDate d = monday.plusDays(i);
            boolean has = false;
            for (Lesson l : ls) if (Dt.sameDay(l.start, d)) { has = true; break; }
            if (has) continue;
            java.util.Random rnd = new java.util.Random(Dt.iso(d).hashCode());   // napra szóló, stabil
            List<String> left = new ArrayList<>(pool);
            int n = Math.min(left.size(), 5 + rnd.nextInt(3));
            for (int k = 0; k < n; k++) {
                String subject = left.remove(rnd.nextInt(left.size()));
                Lesson l = new Lesson();
                l.id = "test_" + Dt.iso(d) + k;
                l.start = d.atTime(8, 0).plusMinutes(55L * k);
                l.end = l.start.plusMinutes(45);
                l.subject = subject; l.name = subject; l.room = "T" + (10 + k); l.nr = k + 1; l.fake = true;
                out.add(l);
            }
        }
        out.sort((a, b) -> a.start.compareTo(b.start));
        return out;
    }

    /** A legrégebbi adat ideje (az adatlap nem számít, azt a frissítés ritkán kéri le). */
    public static Double freshest() {
        Double min = null;
        for (Map.Entry<String, Raw> e : raw.entrySet()) {
            if (e.getKey().equals("tanulo") || e.getValue().fetchedAt <= 0) continue;
            if (min == null || e.getValue().fetchedAt < min) min = e.getValue().fetchedAt;
        }
        return min;
    }

    // ---------- betöltés ----------
    /** Egy kör eredménye (a webes /api/data/bundle válasza). */
    public static final class Bundle {
        public final Map<String, Raw> items = new LinkedHashMap<>();
        public final Map<String, String> errors = new LinkedHashMap<>();
        public String relogin;                 // nem null: újra be kell lépni
        public boolean refreshed, stale, throttled;
        public long waitSec, cooldownSec;      // hány mp múlva lehet újra kényszeríteni / mennyi a KRÉTA-szünet
    }

    /** A lekérdezés dátumtartománya adatfajtánként (a háttér-ellenőrzés is ugyanezt használja). */
    public static Map<String, String> params(String what) { return defaults(what); }

    private static Map<String, String> defaults(String what) {
        Map<String, String> p = new HashMap<>();
        LocalDate today = Dt.today();
        if (what.equals("orarend")) {
            LocalDate mon = Dt.mondayOf(today);
            p.put("datumTol", Dt.iso(mon));
            p.put("datumIg", Dt.iso(mon.plusDays(6)));
        } else if (what.equals("hazi")) p.put("datumTol", Dt.iso(today.minusDays(21)));
        else if (what.equals("szamonkeres")) p.put("datumTol", Dt.iso(today.minusDays(60)));
        return p;
    }

    /** Csak a tárolt adat, hálózat nélkül (gyors indulás). */
    public static Bundle cachedBundle() {
        Bundle b = new Bundle();
        for (String w : BUNDLE) {
            Raw r = Kreta.cached(w, defaults(w));
            if (r != null) b.items.put(w, r);
        }
        return b;
    }

    private static synchronized double forceWait() { return Math.max(0, lastForce + Config.FORCE_MIN_AGE - Kreta.now()); }

    /**
     * Minden adat egy körben (háttérszálon). force: friss adat a KRÉTA-ból, de legfeljebb percenként (a gomb és az
     * automatikus frissítés közös korlátja); ha túl korán jön, a tárolt adatot adja és throttled = true.
     * Az időt a kör VÉGÉN is beírjuk, így a következő körre minden elem elég régi lesz az újbóli lekéréshez.
     */
    public static Bundle fetchBundle(boolean force) {
        Bundle b = new Bundle();
        if (force) {
            synchronized (St.class) {
                if (forceWait() > 0) { b.throttled = true; force = false; }
                else lastForce = Kreta.now();
            }
        }
        for (String w : BUNDLE) {
            try {
                Raw r = Kreta.fetch(w, defaults(w), force && !w.equals("tanulo"));   // az adatlap ritkán változik
                b.items.put(w, r);
                if (w.equals("orarend") && !r.cached) timetableFetched();
                if (!r.cached) b.refreshed = true;
                if (r.stale) b.stale = true;
            } catch (KretaError e) {
                if (e.relogin()) { b.relogin = e.getMessage(); return b; }
                b.errors.put(w, e.getMessage());
            }
        }
        if (force) synchronized (St.class) { lastForce = Kreta.now(); }
        b.waitSec = (long) Math.ceil(forceWait());
        b.cooldownSec = Kreta.cooldownLeft();
        return b;
    }

    /**
     * Az eredmény átvétele (főszálon). Visszaad: azok az adatfajták, amelyek tartalma tényleg megváltozott
     * (track = false, vagy az első betöltés esetén üres).
     */
    public static List<String> applyBundle(Bundle b, boolean track) {
        boolean first = raw.isEmpty();
        List<String> changed = new ArrayList<>();
        if (track) errors.clear();
        errors.putAll(b.errors);
        for (Map.Entry<String, Raw> e : b.items.entrySet()) {
            Raw old = raw.get(e.getKey());
            if (track && !first && (old == null || !old.body.equals(e.getValue().body))) changed.add(e.getKey());
            raw.put(e.getKey(), e.getValue());
            if (e.getKey().equals("orarend")) weekCache.put(Dt.iso(Dt.mondayOf(Dt.today())), e.getValue());
        }
        // a fiók nevét az adatlapból jegyezzük meg (a beállításoknál és kilépéskor ezt mutatjuk)
        Store.Account a = Store.account();
        String n = studentName();
        if (a != null && !a.demo && !n.isEmpty() && !n.equals(a.name)) { a.name = n; Async.bg(() -> Store.saveAccount(a)); }
        return changed;
    }

    private static Map<String, String> weekParams(LocalDate monday) {
        Map<String, String> p = new HashMap<>();
        p.put("datumTol", Dt.iso(monday));
        p.put("datumIg", Dt.iso(monday.plusDays(6)));
        return p;
    }

    /** Egy hét órarendje (hétfőtől vasárnapig). */
    public static Raw fetchWeek(LocalDate monday, boolean force) throws KretaError {
        Raw r = Kreta.fetch("orarend", weekParams(monday), force);
        if (!r.cached) timetableFetched();
        return r;
    }

    /** Egy hét órarendje a telefonon tárolt adatból, hálózat nélkül (null, ha még nem töltöttük le). */
    public static Raw storedWeek(LocalDate monday) { return Kreta.local("orarend", weekParams(monday)); }

    /** Friss órarend jött a KRÉTA-ból (az app vagy a háttér-ellenőrzés kérte): az óra előtti emlékeztető újraszámol. */
    public static volatile Runnable onTimetable;

    private static void timetableFetched() {
        Runnable r = onTimetable;
        if (r != null) r.run();
    }
}
