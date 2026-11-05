package hu.zsirkreta.app.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import hu.zsirkreta.app.data.Models.Absence;
import hu.zsirkreta.app.data.Models.Grade;
import hu.zsirkreta.app.data.Models.Homework;
import hu.zsirkreta.app.data.Models.Lesson;
import hu.zsirkreta.app.data.Models.Note;
import hu.zsirkreta.app.data.Models.TestItem;
import hu.zsirkreta.app.util.Dt;
import hu.zsirkreta.app.util.J;

/** A KRÉTA válaszainak normalizálása (a webes util.js normGrades/normLessons/… megfelelője, ugyanazokkal a mezőnevekkel). */
public final class Norm {
    private Norm() {}

    private static final Pattern SZAZAL = Pattern.compile("szazal", Pattern.CASE_INSENSITIVE);
    private static final Pattern TERM = Pattern.compile("felevi|evvegi|ev_vegi|vegi", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVKOZI = Pattern.compile("evkozi", Pattern.CASE_INSENSITIVE);
    private static final Pattern ELMARADT = Pattern.compile("elmaradt", Pattern.CASE_INSENSITIVE);
    // nem valódi órák: szünetek, munkaszüneti napok (a KRÉTA órarend-elemként adja őket)
    private static final Pattern NOT_LESSON = Pattern.compile("(munkasz[üu]net|[őo]szi sz[üu]net|t[ée]li sz[üu]net|tavaszi sz[üu]net|ny[áa]ri sz[üu]net"
            + "|tan[íi]t[áa]si sz[üu]net|tan[íi]t[áa]s n[ée]lk[üu]li|sz[üu]netnap|ünnepnap)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern IGAZOLT = Pattern.compile("^igazolt$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEM_IGAZOL = Pattern.compile("nem_igazol", Pattern.CASE_INSENSITIVE);

    private static List<JSONObject> items(Object raw) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray a = J.arr(raw);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(o);
        }
        return out;
    }

    /** obj.sub.key (a JS „o.Sub?.Key || ''”) */
    private static String in(JSONObject o, String sub, String key) { return J.str(J.obj(o, sub), key); }

    private static Integer intOrNull(JSONObject o, String k) {
        Double d = J.num(o, k);
        return d == null ? null : (int) Math.round(d);
    }

    private static String or(String a, String b) { return a.isEmpty() ? b : a; }

    public static List<Grade> grades(Object raw) {
        List<Grade> out = new ArrayList<>();
        for (JSONObject g : items(raw)) {
            LocalDateTime date = Dt.parse(J.first(g, "KeszitesDatuma", "RogzitesDatuma"));
            if (date == null) continue;
            Grade x = new Grade();
            x.type = in(g, "Tipus", "Nev");
            x.kind = in(g, "ErtekFajta", "Nev");
            Double v = J.num(g, "SzamErtek");
            x.value = v != null && v >= 1 && v <= 5 ? (int) Math.round(v) : null;
            if (SZAZAL.matcher(x.kind).find()) x.value = null;
            x.id = J.str(g, "Uid");
            x.date = date;
            x.subject = or(in(g, "Tantargy", "Nev"), "Egyéb");
            x.topic = J.str(g, "Tema");
            x.teacher = J.str(g, "ErtekeloTanarNeve");
            x.text = J.first(g, "SzovegesErtek", "SzovegesErtekelesRovidNev");
            Double w = J.num(g, "SulySzazalekErteke");
            x.weight = (w == null || w == 0 ? 100 : w) / 100.0;
            x.mode = in(g, "Mod", "Leiras");
            x.isTerm = TERM.matcher(x.type).find() && !EVKOZI.matcher(x.type).find();
            out.add(x);
        }
        out.sort((a, b) -> b.date.compareTo(a.date));
        return out;
    }

    public static List<Lesson> lessons(Object raw) {
        List<Lesson> out = new ArrayList<>();
        for (JSONObject l : items(raw)) {
            String probe = String.join(" ", in(l, "Tantargy", "Nev"), J.str(l, "Nev"), J.str(l, "Tema"), in(l, "Tipus", "Nev"));
            if (NOT_LESSON.matcher(probe).find()) continue;
            LocalDateTime start = Dt.parse(J.str(l, "KezdetIdopont"));
            if (start == null) continue;
            Lesson x = new Lesson();
            x.id = J.str(l, "Uid");
            x.start = start;
            LocalDateTime end = Dt.parse(J.str(l, "VegIdopont"));
            x.end = end != null ? end : start.plusMinutes(45);
            x.subject = or(in(l, "Tantargy", "Nev"), or(J.str(l, "Nev"), "Óra"));
            x.name = J.str(l, "Nev");
            x.teacher = J.str(l, "TanarNeve");
            x.sub = J.str(l, "HelyettesitoTanarNeve");
            Object terem = l.opt("Tanterem");
            String tr = terem instanceof String ? (String) terem : terem instanceof JSONObject ? J.str((JSONObject) terem, "Nev") : "";
            x.room = or(J.str(l, "TeremNeve"), or(tr, J.str(l, "Terem")));
            x.nr = intOrNull(l, "Oraszam");
            x.off = ELMARADT.matcher(in(l, "Allapot", "Nev")).find();
            x.topic = J.str(l, "Tema");
            x.test = J.truthy(l, "BejelentettSzamonkeresUid");
            x.hw = J.truthy(l, "HaziFeladatUid");
            out.add(x);
        }
        out.sort((a, b) -> a.start.compareTo(b.start));
        return out;
    }

    public static List<Absence> absences(Object raw) {
        List<Absence> out = new ArrayList<>();
        for (JSONObject m : items(raw)) {
            LocalDateTime date = Dt.parse(J.str(m, "Datum"));
            if (date == null) continue;
            Absence x = new Absence();
            x.id = J.str(m, "Uid");
            x.date = date;
            x.subject = or(in(m, "Tantargy", "Nev"), "Egyéb");
            x.nr = intOrNull(m, "Oraszam");
            x.teacher = J.str(m, "RogzitoTanarNeve");
            x.status = J.str(m, "IgazolasAllapota");
            x.type = or(in(m, "IgazolasTipusa", "Nev"), in(m, "IgazolasTipusa", "Leiras"));
            Double late = J.num(m, "KesesPercben");
            x.late = late == null ? 0 : (int) Math.round(late);
            x.kind = in(m, "Tipus", "Nev");
            x.ok = IGAZOLT.matcher(x.status).find();
            x.na = NEM_IGAZOL.matcher(x.status).find();
            out.add(x);
        }
        out.sort((a, b) -> b.date.compareTo(a.date));
        return out;
    }

    public static List<Homework> homework(Object raw) {
        List<Homework> out = new ArrayList<>();
        for (JSONObject h : items(raw)) {
            LocalDateTime due = Dt.parse(J.str(h, "HataridoDatuma"));
            if (due == null) continue;
            Homework x = new Homework();
            x.id = J.str(h, "Uid");
            x.subject = or(J.str(h, "TantargyNeve"), "Egyéb");
            x.teacher = J.str(h, "RogzitoTanarNeve");
            x.text = J.str(h, "Szoveg");
            x.given = Dt.parse(J.str(h, "FeladasDatuma"));
            if (x.given == null) x.given = due;
            x.due = due;
            x.done = J.truthy(h, "IsMegoldva");
            out.add(x);
        }
        out.sort((a, b) -> a.due.compareTo(b.due));
        return out;
    }

    public static List<TestItem> tests(Object raw) {
        List<TestItem> out = new ArrayList<>();
        for (JSONObject t : items(raw)) {
            LocalDateTime date = Dt.parse(J.str(t, "Datum"));
            if (date == null) continue;
            TestItem x = new TestItem();
            x.id = J.str(t, "Uid");
            x.date = date;
            x.subject = or(J.str(t, "TantargyNeve"), or(in(t, "Tantargy", "Nev"), "Egyéb"));
            x.teacher = J.str(t, "RogzitoTanarNeve");
            x.topic = J.str(t, "Temaja");
            x.kind = or(in(t, "Modja", "Leiras"), in(t, "Modja", "Nev"));
            x.nr = intOrNull(t, "OrarendiOraOraszama");
            out.add(x);
        }
        out.sort((a, b) -> a.date.compareTo(b.date));
        return out;
    }

    public static List<Note> notes(Object raw) {
        List<Note> out = new ArrayList<>();
        for (JSONObject n : items(raw)) {
            Note x = new Note();
            x.id = J.str(n, "Uid");
            x.title = J.str(n, "Cim");
            x.text = J.str(n, "Tartalom");
            x.date = Dt.parse(J.first(n, "FeljegyzesDatuma", "Datum"));
            x.teacher = J.str(n, "KeszitoTanarNeve");
            x.type = in(n, "Tipus", "Nev");
            out.add(x);
        }
        out.sort((a, b) -> {
            if (a.date == null || b.date == null) return a.date == null ? (b.date == null ? 0 : 1) : -1;
            return b.date.compareTo(a.date);
        });
        return out;
    }
}
