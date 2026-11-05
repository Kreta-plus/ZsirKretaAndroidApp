package hu.zsirkreta.app.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;

/** Teszt fiók: véletlen (de fiókonként stabil) adatok a KRÉTA válaszaival megegyező szerkezetben (a webes demo.py megfelelője). */
public final class Demo {
    private Demo() {}

    public static final String INST = "demo";

    private static final String[][] SUBJECTS = {
            {"Matematika", "Kovács Éva"}, {"Magyar nyelv és irodalom", "Szabó Katalin"}, {"Történelem", "Tóth Gábor"},
            {"Angol nyelv", "Nagy Anna"}, {"Fizika", "Horváth Péter"}, {"Kémia", "Varga Zsuzsa"},
            {"Biológia", "Molnár Ildikó"}, {"Földrajz", "Kiss László"}, {"Informatika", "Farkas Dávid"},
            {"Testnevelés", "Balogh Tamás"}, {"Ének-zene", "Papp Mária"}, {"Német nyelv", "Simon Judit"},
    };
    private static final Object[][] GRADE_KINDS = {{"Dolgozat", 200}, {"Témazáró dolgozat", 300}, {"Felelet", 100}, {"Röpdolgozat", 100}, {"Házi feladat", 50}, {"Órai munka", 100}};
    private static final String[] TOPICS = {"Másodfokú egyenletek", "Az első világháború", "Present perfect", "Newton törvényei", "Sav-bázis reakciók", "A sejt felépítése",
            "Európa éghajlata", "Függvények", "Programozási alapok", "Verselemzés", "Állóképesség", "Mozgások"};
    private static final String[] HW_TEXTS = {"Tankönyv %d. oldal 3–5. feladat", "Szószedet tanulása a következő órára", "Munkafüzet %d. oldal kitöltése",
            "Készíts vázlatot a tanult anyagból", "Gyakorló feladatok a %d. oldalról", "Olvasd el a leckét, és jegyzetelj"};
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static String iso(LocalDateTime d) { return d.format(ISO); }
    private static LocalDateTime today() { return LocalDate.now().atStartOfDay(); }

    private static Random rng(String uid, String salt) {
        String s = uid + ":" + salt;
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        return new Random(h);
    }

    private static <T> T pick(Random r, T[] a) { return a[r.nextInt(a.length)]; }
    private static int between(Random r, int a, int b) { return a + r.nextInt(b - a + 1); }

    private static JSONObject nev(String n) throws JSONException { return new JSONObject().put("Nev", n); }

    private static String[][][] timetable(String uid) {
        Random r = rng(uid, "tt");
        String[][][] tt = new String[5][][];
        int[] lens = {5, 6, 7};
        for (int d = 0; d < 5; d++) {
            int n = lens[r.nextInt(3)];
            tt[d] = new String[n][];
            for (int i = 0; i < n; i++) tt[d][i] = SUBJECTS[r.nextInt(SUBJECTS.length)];
        }
        return tt;
    }

    private static JSONObject tanulo() throws JSONException {
        return new JSONObject().put("Nev", "Teszt Elek").put("IntezmenyNev", "Teszt Gimnázium").put("Oktatasi_Azonosito", "00000000000");
    }

    private static JSONArray jegyek(String uid) throws JSONException {
        Random r = rng(uid, "gr");
        LocalDateTime today = today();
        JSONArray out = new JSONArray();
        int[] weights = {1, 4, 8, 12, 14};   // 1-es … 5-ös gyakorisága
        for (int si = 0; si < SUBJECTS.length; si++) {
            int n = between(r, 7, 12);
            for (int i = 0; i < n; i++) {
                Object[] kind = pick(r, GRADE_KINDS);
                LocalDateTime day = today.minusDays(between(r, 0, 120));
                int roll = r.nextInt(39), v = 1;
                for (int k = 0, acc = 0; k < 5; k++) { acc += weights[k]; if (roll < acc) { v = k + 1; break; } }
                out.put(new JSONObject().put("Uid", "g" + si + "_" + i).put("KeszitesDatuma", iso(day)).put("RogzitesDatuma", iso(day))
                        .put("Tantargy", nev(SUBJECTS[si][0])).put("SzamErtek", v).put("Tema", pick(r, TOPICS)).put("ErtekeloTanarNeve", SUBJECTS[si][1])
                        .put("SulySzazalekErteke", kind[1]).put("Tipus", nev("evkozi_jegy_ertekeles")).put("ErtekFajta", nev("Osztalyzat"))
                        .put("Mod", new JSONObject().put("Leiras", kind[0])));
            }
        }
        return out;
    }

    private static JSONArray orarend(String uid, Map<String, String> params) throws JSONException {
        String[][][] tt = timetable(uid);
        LocalDate a, b;
        try {
            a = LocalDate.parse(params.get("datumTol").substring(0, 10));
            b = LocalDate.parse(params.get("datumIg").substring(0, 10));
        } catch (Exception e) {
            a = LocalDate.now().with(DayOfWeek.MONDAY);
            b = a.plusDays(6);
        }
        JSONArray out = new JSONArray();
        for (LocalDate day = a; !day.isAfter(b); day = day.plusDays(1)) {
            int wd = day.getDayOfWeek().getValue() - 1;
            if (wd >= 5) continue;
            Random r = rng(uid, "d" + day.toString().replace("-", ""));
            for (int n = 0; n < tt[wd].length; n++) {
                String subj = tt[wd][n][0], teacher = tt[wd][n][1];
                LocalDateTime start = day.atTime(8, 0).plusMinutes(55L * n);
                boolean off = r.nextDouble() < 0.04, sub = r.nextDouble() < 0.05;
                int sum = 0;
                for (int i = 0; i < subj.length(); i++) sum += subj.charAt(i);
                String room = "ABC".charAt(r.nextInt(3)) + String.valueOf(10 + sum % 20);
                boolean hw = r.nextDouble() < 0.15, test = r.nextDouble() < 0.06;
                out.put(new JSONObject().put("Uid", "l" + day.toString().replace("-", "") + n).put("KezdetIdopont", iso(start)).put("VegIdopont", iso(start.plusMinutes(45)))
                        .put("Tantargy", nev(subj)).put("Nev", subj).put("TanarNeve", teacher).put("HelyettesitoTanarNeve", sub ? "Teszt Helyettes" : "")
                        .put("TeremNeve", room).put("Oraszam", n + 1).put("Allapot", nev(off ? "Elmaradt" : "Megtartott")).put("Tema", "")
                        .put("HaziFeladatUid", hw ? "x" : JSONObject.NULL).put("BejelentettSzamonkeresUid", test ? "x" : JSONObject.NULL));
            }
        }
        return out;
    }

    private static JSONArray hazi(String uid) throws JSONException {
        Random r = rng(uid, "hw");
        LocalDateTime today = today();
        JSONArray out = new JSONArray();
        for (int i = 0; i < 9; i++) {
            String[] s = pick(r, SUBJECTS);
            LocalDateTime due = today.plusDays(between(r, -6, 7));
            String text = String.format(pick(r, HW_TEXTS), between(r, 10, 150));
            out.put(new JSONObject().put("Uid", "h" + i).put("TantargyNeve", s[0]).put("RogzitoTanarNeve", s[1]).put("Szoveg", text)
                    .put("FeladasDatuma", iso(due.minusDays(between(r, 1, 5)))).put("HataridoDatuma", iso(due))
                    .put("IsMegoldva", due.isBefore(today) && r.nextDouble() < 0.7));
        }
        return out;
    }

    private static JSONArray szamonkeres(String uid) throws JSONException {
        Random r = rng(uid, "ts");
        LocalDateTime today = today();
        String[] modes = {"Dolgozat", "Témazáró dolgozat", "Röpdolgozat"};
        JSONArray out = new JSONArray();
        for (int i = 0; i < 7; i++) {
            String[] s = pick(r, SUBJECTS);
            out.put(new JSONObject().put("Uid", "s" + i).put("Datum", iso(today.plusDays(between(r, -30, 14)))).put("TantargyNeve", s[0]).put("RogzitoTanarNeve", s[1])
                    .put("Temaja", pick(r, TOPICS)).put("Modja", new JSONObject().put("Leiras", pick(r, modes))).put("OrarendiOraOraszama", between(r, 1, 6)));
        }
        return out;
    }

    private static JSONArray mulasztasok(String uid) throws JSONException {
        Random r = rng(uid, "ab");
        LocalDateTime today = today();
        JSONArray out = new JSONArray();
        for (int i = 0; i < 8; i++) {
            String[] s = pick(r, SUBJECTS);
            boolean ok = r.nextDouble() < 0.75, late = r.nextDouble() < 0.3;
            out.put(new JSONObject().put("Uid", "m" + i).put("Datum", iso(today.minusDays(between(r, 1, 90)))).put("Tantargy", nev(s[0])).put("Oraszam", between(r, 1, 7))
                    .put("RogzitoTanarNeve", s[1]).put("IgazolasAllapota", ok ? "Igazolt" : "Igazolatlan")
                    .put("IgazolasTipusa", ok ? nev("Orvosi igazolás") : JSONObject.NULL).put("KesesPercben", late ? between(r, 3, 20) : 0)
                    .put("Tipus", nev(late ? "keses" : "hianyzas")));
        }
        return out;
    }

    private static JSONArray feljegyzesek(String uid) throws JSONException {
        Random r = rng(uid, "nt");
        LocalDateTime today = today();
        String[][] items = {{"Dicséret", "Kiváló órai munkáért."}, {"Figyelmeztetés", "Késés az első óráról."}, {"Értesítés", "Szülői értekezlet jövő héten."}};
        JSONArray out = new JSONArray();
        for (int i = 0; i < items.length; i++) {
            out.put(new JSONObject().put("Uid", "n" + i).put("Cim", items[i][0]).put("Tartalom", items[i][1]).put("FeljegyzesDatuma", iso(today.minusDays(between(r, 1, 40))))
                    .put("KeszitoTanarNeve", pick(r, SUBJECTS)[1]).put("Tipus", nev(items[i][0])));
        }
        return out;
    }

    public static Object data(String uid, String what, Map<String, String> params) {
        try {
            switch (what) {
                case "tanulo": return tanulo();
                case "jegyek": return jegyek(uid);
                case "orarend": return orarend(uid, params);
                case "mulasztasok": return mulasztasok(uid);
                case "hazi": return hazi(uid);
                case "szamonkeres": return szamonkeres(uid);
                case "feljegyzesek": return feljegyzesek(uid);
                default: return new JSONArray();
            }
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
}
