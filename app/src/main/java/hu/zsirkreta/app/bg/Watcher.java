package hu.zsirkreta.app.bg;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import hu.zsirkreta.app.App;
import hu.zsirkreta.app.data.Kreta;
import hu.zsirkreta.app.data.Kreta.KretaError;
import hu.zsirkreta.app.data.Models.Absence;
import hu.zsirkreta.app.data.Models.Grade;
import hu.zsirkreta.app.data.Models.Homework;
import hu.zsirkreta.app.data.Models.Lesson;
import hu.zsirkreta.app.data.Models.Note;
import hu.zsirkreta.app.data.Models.Plan;
import hu.zsirkreta.app.data.Models.Raw;
import hu.zsirkreta.app.data.Models.TestItem;
import hu.zsirkreta.app.data.Norm;
import hu.zsirkreta.app.data.Prefs;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.data.Store;
import hu.zsirkreta.app.data.Updater;
import hu.zsirkreta.app.util.Dt;
import hu.zsirkreta.app.util.Fmt;

/**
 * A háttér-ellenőrzés: lekéri a KRÉTA-adatokat (ugyanazzal a kíméletes korláttal, mint az app: adatfajtánként legfeljebb
 * percenként), összeveti a legutóbb látottal, és az újakról értesítést küld.
 *
 * Fajtánként egy „látott” lista van; ha egy fajtának még nincs (első futás, vagy most kapcsolták be), az első kör csak
 * felveszi a mostani állapotot, és nem küld értesítést, így nem jön egyszerre ötven régi jegy.
 */
public final class Watcher {
    private Watcher() {}

    private interface Step { void run() throws Exception; }

    private static final long BUDGET_MS = 40_000;   // a háttérben ennyi idő alatt végezni kell (utána a rendszer leállíthatja)

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("watch", Context.MODE_PRIVATE); }

    private static JSONObject load(Context c, String ukey) {
        try { return new JSONObject(sp(c).getString("s_" + ukey.hashCode(), "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    private static void save(Context c, String ukey, JSONObject s) { sp(c).edit().putString("s_" + ukey.hashCode(), s.toString()).apply(); }

    private static Set<String> ids(JSONObject s, String key) {
        Set<String> out = new HashSet<>();
        JSONArray a = s.optJSONArray(key);
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    /** Az eddig nem látott azonosítók; ha a fajtának még nincs listája, üres (csak felveszi az alapállapotot). */
    private static List<String> fresh(JSONObject s, String key, List<String> now) throws Exception {
        boolean known = s.has(key);
        Set<String> seen = ids(s, key);
        List<String> out = new ArrayList<>();
        if (known) for (String id : now) if (!seen.contains(id)) out.add(id);
        s.put(key, new JSONArray(new LinkedHashSet<>(now)));
        return out;
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

    private static String lowerFirst(String s) { return s.isEmpty() ? s : s.substring(0, 1).toLowerCase(Locale.forLanguageTag("hu")) + s.substring(1); }

    private static String clip(String s, int n) {
        s = Fmt.stripHtml(s).replaceAll("\\s+", " ").trim();
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    /** Ha a gyűjtött sorok közül egy van: részletes értesítés; több: összevont, a sorokkal. */
    private static void emit(Context c, String kind, List<String[]> items, String manyTitle) {
        if (items.isEmpty()) return;
        if (items.size() == 1) { Notifs.post(c, kind, items.get(0)[0], items.get(0)[1], null); return; }
        List<String> lines = new ArrayList<>();
        for (String[] it : items) lines.add(it[2]);
        Notifs.post(c, kind, manyTitle.replace("%d", String.valueOf(items.size())), String.join(" · ", lines), lines);
    }

    /**
     * Egy háttér-kör. post = false: csak az állapotot frissíti (pl. ha az app épp előtérben van, ott úgyis látszik az új adat).
     * Visszaad: hibaüzenet, vagy null.
     */
    public static String run(Context c) { return run(c, false); }

    /** evenInForeground: a Beállítások „Ellenőrzés most” gombja – akkor is szól, ha az app épp látszik. */
    public static synchronized String run(Context c, boolean evenInForeground) {
        Store.Account a = Store.account();
        if (a == null) return "nincs belépve";
        long deadline = System.currentTimeMillis() + BUDGET_MS;
        boolean post = Notifs.enabled(c) && Notifs.permitted(c) && (evenInForeground || !App.inForeground());
        Prefs p = Store.loadPrefs(a.ukey);
        JSONObject s = load(c, a.ukey);
        String err = null;
        Object[][] steps = {
                {Notifs.GRADES, "grades", (Step) () -> grades(c, s, p, post)},
                {Notifs.HOMEWORK, "hw", (Step) () -> homework(c, s, p, post)},
                {Notifs.TESTS, "tests", (Step) () -> tests(c, s, p, post)},
                {Notifs.TIMETABLE, "lessons", (Step) () -> timetable(c, s, p, post)},
                {Notifs.ABSENCES, "abs", (Step) () -> absences(c, s, p, post)},
                {Notifs.NOTES, "notes", (Step) () -> notes(c, s, p, post)},
        };
        boolean relogin = false;
        for (Object[] st : steps) {
            // kikapcsolt fajta: nem kérdezzük le, és az alapállapotát is elfelejtjük (újra bekapcsolva nem jön régi tétel)
            if (!Notifs.kind(c, (String) st[0])) { s.remove((String) st[1]); continue; }
            if (System.currentTimeMillis() > deadline) break;
            try {
                ((Step) st[2]).run();
            } catch (KretaError e) {
                err = e.getMessage();
                if (e.relogin()) { relogin = true; break; }
            } catch (Exception e) {
                err = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }
        if (relogin) {
            String today = Dt.iso(Dt.today());
            if (Notifs.enabled(c) && Notifs.permitted(c) && !today.equals(s.optString("relogin"))) {
                Notifs.post(c, Notifs.ACCOUNT, "🔑 Lépj be újra a ZsírKrétába", "A KRÉTA-belépésed lejárt, ezért most nem tudunk szólni az új jegyekről és házikról.", null);
                try { s.put("relogin", today); } catch (Exception ignored) {}
            }
        } else s.remove("relogin");
        // új app-verzió (legfeljebb 3 óránként nézi meg)
        if (Notifs.kind(c, Notifs.UPDATE) && System.currentTimeMillis() < deadline) {
            Updater.Info u = Updater.checkNow(c, 3 * 3600_000L);
            if (u != null && s.optInt("update", 0) != u.versionCode && Notifs.enabled(c) && Notifs.permitted(c)) {
                String first = u.changes.isEmpty() ? "Koppints rá, és az app letölti és telepíti." : u.changes.get(0)[1];
                Notifs.post(c, Notifs.UPDATE, "⬆️ Elérhető a ZsírKréta " + u.versionName, first, null);
                try { s.put("update", u.versionCode); } catch (Exception ignored) {}
            }
        }
        save(c, a.ukey, s);
        Notifs.ran(c, err);
        // ha közben friss órarend jött, az óra előtti emlékeztető ahhoz igazodik (a tárolt adatból, hálózat nélkül)
        LessonReminder.reschedule(c);
        return err;
    }

    // ---------- fajták ----------
    private static void grades(Context c, JSONObject s, Prefs p, boolean post) throws Exception {
        Raw r = Kreta.fetch("jegyek", St.params("jegyek"), true);
        List<Grade> gs = Norm.grades(r.data);
        List<String> now = new ArrayList<>();
        for (Grade g : gs) now.add(g.id);
        Set<String> fresh = new HashSet<>(fresh(s, "grades", now));
        List<String[]> items = new ArrayList<>();
        for (Grade g : gs) {
            if (!fresh.contains(g.id) || p.hiddenSubjects.contains(g.subject)) continue;
            Integer v = g.value != null && p.goodStudent ? Integer.valueOf(5) : g.value;   // jótanuló mód: itt is ötös
            String val = v != null ? String.valueOf(v) : g.text.isEmpty() ? "" : g.text;
            List<String> meta = new ArrayList<>();
            String mode = g.mode.isEmpty() ? g.topic : g.mode;
            if (!mode.isEmpty()) meta.add(lowerFirst(mode));
            if (Math.round(g.weight * 100) != 100) meta.add(Math.round(g.weight * 100) + "%");
            String what = subj(p, g.subject) + (val.isEmpty() ? "" : " " + val) + (meta.isEmpty() ? "" : " (" + String.join(", ", meta) + ")");
            String body = String.join(" · ", nonEmpty(g.topic, teacher(p, g.teacher)));
            items.add(new String[]{(g.isTerm ? "📘 Új félévi/év végi jegy: " : "📘 Új jegy: ") + what, body.isEmpty() ? "Koppints rá a részletekért." : body, what});
        }
        if (post) emit(c, Notifs.GRADES, items, "📘 %d új jegy");
    }

    private static void homework(Context c, JSONObject s, Prefs p, boolean post) throws Exception {
        Raw r = Kreta.fetch("hazi", St.params("hazi"), true);
        List<Homework> hw = Norm.homework(r.data);
        List<String> now = new ArrayList<>();
        for (Homework h : hw) now.add(h.id);
        Set<String> fresh = new HashSet<>(fresh(s, "hw", now));
        LocalDate today = Dt.today();
        List<String[]> items = new ArrayList<>();
        for (Homework h : hw) {
            if (!fresh.contains(h.id) || h.done || h.due.toLocalDate().isBefore(today)) continue;
            String due = Dt.relDay(h.due);
            items.add(new String[]{"📝 Új házi: " + subj(p, h.subject) + ", határidő: " + due, clip(h.text, 300), subj(p, h.subject) + " · " + due + ": " + clip(h.text, 60)});
        }
        if (post) emit(c, Notifs.HOMEWORK, items, "📝 %d új házi");
    }

    private static void tests(Context c, JSONObject s, Prefs p, boolean post) throws Exception {
        Raw r = Kreta.fetch("szamonkeres", St.params("szamonkeres"), true);
        List<TestItem> ts = Norm.tests(r.data);
        List<String> now = new ArrayList<>();
        for (TestItem t : ts) now.add(t.id);
        Set<String> fresh = new HashSet<>(fresh(s, "tests", now));
        LocalDate today = Dt.today();
        List<String[]> items = new ArrayList<>();
        for (TestItem t : ts) {
            if (!fresh.contains(t.id) || t.date.toLocalDate().isBefore(today)) continue;
            String when = Dt.relDay(t.date) + (Dt.days(today, t.date.toLocalDate()) >= 2 ? " (" + Dt.fmtDate(t.date) + ")" : "");
            String body = String.join(" · ", nonEmpty(t.kind, t.topic, t.nr != null ? t.nr + ". óra" : ""));
            items.add(new String[]{"📅 Új számonkérés: " + subj(p, t.subject) + ", " + when, body.isEmpty() ? "Bejelentett számonkérés" : body, subj(p, t.subject) + " · " + when});
        }
        if (post) emit(c, Notifs.TESTS, items, "📅 %d új számonkérés");
    }

    /** Ma (a még hátralévő) és holnap: elmaradó óra vagy helyettesítés. */
    private static void timetable(Context c, JSONObject s, Prefs p, boolean post) throws Exception {
        LocalDate today = Dt.today(), tomorrow = today.plusDays(1);
        LocalDateTime nowT = LocalDateTime.now();
        List<Lesson> ls = new ArrayList<>(Norm.lessons(St.fetchWeek(Dt.mondayOf(today), true).data));
        if (!Dt.mondayOf(tomorrow).equals(Dt.mondayOf(today))) ls.addAll(Norm.lessons(St.fetchWeek(Dt.mondayOf(tomorrow), true).data));
        boolean known = s.has("lessons");
        JSONObject old = s.optJSONObject("lessons");
        JSONObject cur = new JSONObject();
        List<String[]> items = new ArrayList<>();
        for (Lesson l : ls) {
            LocalDate d = l.start.toLocalDate();
            if (!(d.equals(today) && l.end.isAfter(nowT)) && !d.equals(tomorrow)) continue;
            String key = (l.id.isEmpty() ? Dt.iso(d) + "_" + l.nr + "_" + l.subject : l.id);
            String st = l.off ? "off" : !l.sub.isEmpty() ? "sub:" + l.sub : "ok";
            cur.put(key, st);
            String before = old == null ? null : old.optString(key, null);
            if (!known || st.equals("ok") || st.equals(before)) continue;
            String day = d.equals(today) ? "Ma" : "Holnap";
            String name = (l.nr != null ? l.nr + ". óra " : "") + subj(p, l.subject);
            if (l.off) items.add(new String[]{"🗓️ " + day + " elmarad: " + name, Dt.fmtTime(l.start) + "–" + Dt.fmtTime(l.end) + (l.room.isEmpty() ? "" : " · " + l.room), day.toLowerCase() + " elmarad: " + name});
            else items.add(new String[]{"🔁 " + day + " helyettesítés: " + name + " (" + teacher(p, l.sub) + ")",
                    Dt.fmtTime(l.start) + "–" + Dt.fmtTime(l.end) + (l.room.isEmpty() ? "" : " · " + l.room) + (l.teacher.isEmpty() ? "" : " · helyette: " + teacher(p, l.teacher)),
                    day.toLowerCase() + " helyettesítés: " + name + " (" + teacher(p, l.sub) + ")"});
        }
        s.put("lessons", cur);
        if (post) emit(c, Notifs.TIMETABLE, items, "🗓️ %d órarend-változás");
    }

    private static void absences(Context c, JSONObject s, Prefs p, boolean post) throws Exception {
        Raw r = Kreta.fetch("mulasztasok", St.params("mulasztasok"), true);
        List<Absence> ab = Norm.absences(r.data);
        boolean known = s.has("abs");
        JSONObject old = s.optJSONObject("abs");
        JSONObject cur = new JSONObject();
        List<String[]> items = new ArrayList<>();
        for (Absence x : ab) {
            cur.put(x.id, x.status);
            if (!known) continue;
            String before = old == null ? null : old.optString(x.id, null);
            String where = subj(p, x.subject) + (x.nr != null ? ", " + x.nr + ". óra" : "") + " (" + Dt.fmtDate(x.date) + ")";
            boolean unexcused = x.status.equalsIgnoreCase("Igazolatlan");
            if (before == null) {
                String state = x.ok ? "igazolt" : x.na ? "nem igazolandó" : unexcused ? "igazolatlan" : "igazolásra vár";
                if (x.late > 0) items.add(new String[]{"⏱️ Új késés: " + subj(p, x.subject) + ", " + x.late + " perc (" + Dt.fmtDate(x.date) + ")", state, "késés: " + where});
                else items.add(new String[]{"⏱️ Új hiányzás: " + where, state + (teacher(p, x.teacher).isEmpty() ? "" : " · " + teacher(p, x.teacher)), "hiányzás: " + where});
            } else if (unexcused && !before.equalsIgnoreCase("Igazolatlan")) {
                items.add(new String[]{"⚠️ Igazolatlan maradt: " + where, "Ezt a mulasztást a KRÉTA igazolatlanként jelöli.", "igazolatlan maradt: " + where});
            }
        }
        s.put("abs", cur);
        if (post) emit(c, Notifs.ABSENCES, items, "⏱️ %d változás a hiányzásoknál");
    }

    private static void notes(Context c, JSONObject s, Prefs p, boolean post) throws Exception {
        Raw r = Kreta.fetch("feljegyzesek", St.params("feljegyzesek"), true);
        List<Note> ns = Norm.notes(r.data);
        List<String> now = new ArrayList<>();
        for (Note n : ns) now.add(n.id);
        Set<String> fresh = new HashSet<>(fresh(s, "notes", now));
        List<String[]> items = new ArrayList<>();
        for (Note n : ns) {
            if (!fresh.contains(n.id)) continue;
            String title = n.title.isEmpty() ? (n.type.isEmpty() ? "Feljegyzés" : n.type) : n.title;
            String body = String.join(" · ", nonEmpty(clip(n.text, 300), teacher(p, n.teacher)));
            items.add(new String[]{"💬 Új feljegyzés: " + title, body, title + (teacher(p, n.teacher).isEmpty() ? "" : " (" + teacher(p, n.teacher) + ")")});
        }
        if (post) emit(c, Notifs.NOTES, items, "💬 %d új feljegyzés");
    }

    // ---------- esti emlékeztető ----------
    /** A beállított időpontban: mi lesz holnap (dolgozat, esedékes házi) és mit kell bepakolni. */
    public static synchronized void evening(Context c) {
        Store.Account a = Store.account();
        if (a == null || !Notifs.enabled(c) || !Notifs.kind(c, Notifs.EVENING) || !Notifs.permitted(c)) return;
        Prefs p = Store.loadPrefs(a.ukey);
        LocalDate tomorrow = Dt.today().plusDays(1);
        JSONObject s = load(c, a.ukey);
        if (Dt.iso(tomorrow).equals(s.optString("evening"))) return;   // ma este már szóltunk
        try {
            List<Lesson> ls = new ArrayList<>();
            for (Lesson l : Norm.lessons(St.fetchWeek(Dt.mondayOf(tomorrow), false).data)) if (Dt.sameDay(l.start, tomorrow) && !l.off) ls.add(l);
            List<String> tests = new ArrayList<>();
            for (TestItem t : Norm.tests(Kreta.fetch("szamonkeres", St.params("szamonkeres"), false).data)) if (Dt.sameDay(t.date, tomorrow)) tests.add(subj(p, t.subject));
            for (Plan t : p.myTests) if (Dt.iso(tomorrow).equals(t.date)) tests.add(subj(p, t.subject));
            Set<String> done = Store.hwDone(a.ukey);
            int hw = 0;
            for (Homework h : Norm.homework(Kreta.fetch("hazi", St.params("hazi"), false).data)) if (Dt.sameDay(h.due, tomorrow) && !h.done && !done.contains(h.id)) hw++;
            for (Plan h : p.myHomework) if (Dt.iso(tomorrow).equals(h.date) && !h.done) hw++;
            if (ls.isEmpty() && tests.isEmpty() && hw == 0) { s.put("evening", Dt.iso(tomorrow)); save(c, a.ukey, s); return; }   // holnap nincs iskola

            List<String> parts = new ArrayList<>();
            if (!tests.isEmpty()) parts.add(tests.size() + " dolgozat");
            if (hw > 0) parts.add(hw + " házi esedékes");
            String title = parts.isEmpty() ? "Holnap " + ls.size() + " órád lesz" : "Holnap: " + String.join(", ", parts);
            // táska: a minden órához valók, plusz a holnapi tantárgyak felszerelése (ismétlés nélkül)
            Set<String> gear = new LinkedHashSet<>(p.packAll);
            for (Lesson l : ls) { List<String> own = p.packItems.get(l.subject); if (own != null) gear.addAll(own); }
            List<String> lines = new ArrayList<>();
            if (!tests.isEmpty()) lines.add("Dolgozat: " + String.join(", ", new LinkedHashSet<>(tests)));
            if (!gear.isEmpty()) lines.add("Táska: " + String.join(", ", gear));
            if (!ls.isEmpty()) lines.add("Első óra: " + Dt.fmtTime(ls.get(0).start) + " " + subj(p, ls.get(0).subject) + " · " + ls.size() + " óra");
            Notifs.post(c, Notifs.EVENING, "🎒 " + title, String.join("\n", lines), null);
            s.put("evening", Dt.iso(tomorrow));
            save(c, a.ukey, s);
        } catch (Exception e) {
            Notifs.ran(c, "esti emlékeztető: " + e.getMessage());
        }
    }

    private static List<String> nonEmpty(String... xs) {
        List<String> out = new ArrayList<>();
        for (String x : xs) if (x != null && !x.trim().isEmpty()) out.add(x.trim());
        return out;
    }

    /** Fejlesztői próba: a legutóbbi jegyet „elfelejti”, így a következő kör újként jelzi. */
    public static void forgetLatestGrade(Context c) {
        Store.Account a = Store.account();
        if (a == null) return;
        JSONObject s = load(c, a.ukey);
        JSONArray g = s.optJSONArray("grades");
        if (g == null) return;   // még nincs alapállapot: az első kör úgysem jelez
        List<Grade> gs;
        try { gs = Norm.grades(Kreta.fetch("jegyek", St.params("jegyek"), false).data); } catch (Exception e) { return; }
        if (gs.isEmpty()) return;
        String drop = gs.get(0).id;
        JSONArray keep = new JSONArray();
        for (int i = 0; i < g.length(); i++) if (!drop.equals(g.optString(i))) keep.put(g.optString(i));
        try { s.put("grades", keep); } catch (Exception ignored) {}
        save(c, a.ukey, s);
    }
}
