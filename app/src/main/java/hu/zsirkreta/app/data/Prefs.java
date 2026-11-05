package hu.zsirkreta.app.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import hu.zsirkreta.app.data.Models.Plan;

/**
 * Személyes beállítások. Ugyanazok a JSON-kulcsok, alapértékek és tisztítás, mint a webes prefs.py-ban,
 * így a beállítások exportja a webes és a telefonos változat között cserélhető.
 */
public final class Prefs {
    public static final List<String> WIDGETS = Arrays.asList("avg", "next", "grades", "homework", "tests", "absences", "trend", "subjects");
    public static final List<String> THEMES = Arrays.asList("auto", "dark", "light", "amoled");
    public static final List<String> PALETTES = Arrays.asList("desk", "ocean", "forest", "grape", "rose", "slate", "midnight", "sunset", "teal", "sand", "ice", "neon");
    public static final List<String> BG_STYLES = Arrays.asList("flat", "glow", "dots", "grid");
    public static final List<String> CARD_STYLES = Arrays.asList("fill", "outline", "glass");
    public static final List<String> DENSITY = Arrays.asList("compact", "normal", "cozy");
    public static final List<String> VIEWS = Arrays.asList("dashboard", "grades", "stats", "timetable", "homework", "absences", "notes", "names", "planner", "pack", "settings");
    public static final List<String> PACK_LAYOUTS = Arrays.asList("list", "grid", "check", "strip");
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    public String displayName = "", nickname = "", emoji = "🎓";
    public String theme = "dark", palette = "desk", bgStyle = "flat", cardStyle = "fill", accent = "#ffe14d", density = "normal";
    public double fontScale = 1.0;
    public int radius = 16;
    public String startView = "dashboard";
    public double targetAvg = 4.5;
    public boolean weighted = true;
    public String customCss = "";
    public Map<String, String> subjectNames = new LinkedHashMap<>(), teacherNames = new LinkedHashMap<>();
    public Map<String, Double> subjectGoals = new LinkedHashMap<>();
    public List<String> hiddenSubjects = new ArrayList<>();
    public List<Plan> myHomework = new ArrayList<>(), myTests = new ArrayList<>();
    public Map<String, List<String>> packItems = new LinkedHashMap<>();
    public List<String> packAll = new ArrayList<>();
    public String packLayout = "list";
    public boolean funnyQuips = true, goodStudent = false, devMode = false, devFillLessons = false, devFillTimetable = false;
    public List<String> widgets = new ArrayList<>(WIDGETS);

    // ---------- tisztítás (prefs.py: sanitize) ----------
    private static String str(JSONObject d, String k, int n) {
        Object v = d.opt(k);
        return v instanceof String ? clip(((String) v).trim(), n) : "";
    }

    private static String clip(String s, int n) { return s.length() <= n ? s : s.substring(0, n); }

    private static String oneOf(JSONObject d, String k, List<String> allowed, String def) {
        Object v = d.opt(k);
        return v instanceof String && allowed.contains(v) ? (String) v : def;
    }

    private static double num(JSONObject d, String k, double def, double min, double max) {
        Object v = d.opt(k);
        double x = def;
        if (v instanceof Number) x = ((Number) v).doubleValue();
        else if (v instanceof String) try { x = Double.parseDouble((String) v); } catch (NumberFormatException ignored) {}
        return Double.isNaN(x) ? def : Math.min(max, Math.max(min, x));
    }

    private static boolean bool(JSONObject d, String k, boolean def) {
        Object v = d.opt(k);
        if (v == null || v == JSONObject.NULL) return def;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        return v instanceof String && !((String) v).isEmpty();
    }

    private static Map<String, String> strMap(Object v) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!(v instanceof JSONObject)) return out;
        JSONObject o = (JSONObject) v;
        Iterator<String> it = o.keys();
        for (int i = 0; it.hasNext() && i < 300; i++) {
            String k = it.next();
            Object val = o.opt(k);
            if (val instanceof String && !((String) val).trim().isEmpty()) out.put(clip(k, 120), clip(((String) val).trim(), 80));
        }
        return out;
    }

    private static List<String> strList(Object v, int max, int n) {
        List<String> out = new ArrayList<>();
        if (!(v instanceof JSONArray)) return out;
        JSONArray a = (JSONArray) v;
        for (int i = 0; i < Math.min(a.length(), max); i++) {
            Object x = a.opt(i);
            if (x instanceof String && !((String) x).trim().isEmpty()) out.add(clip(((String) x).trim(), n));
        }
        return out;
    }

    /** Biztonságos CSS (prefs.py: clean_css): nincs @import, script, külső url(). */
    static String cleanCss(String css) {
        if (css == null) return "";
        css = clip(css.trim(), 20000);
        css = css.replaceAll("(?i)@import[^;]*;?", "");
        css = css.replaceAll("(?i)expression\\s*\\(|javascript:|</?style|behavior\\s*:|-moz-binding", "");
        css = css.replaceAll("(?i)url\\(\\s*['\"]?(?!data:)[^)]*\\)", "none");
        return css;
    }

    private static List<Plan> plans(Object v, String textKey) {
        List<Plan> out = new ArrayList<>();
        if (!(v instanceof JSONArray)) return out;
        JSONArray a = (JSONArray) v;
        for (int i = 0; i < Math.min(a.length(), 300); i++) {
            JSONObject x = a.optJSONObject(i);
            if (x == null) continue;
            String date = str(x, "date", 10);
            if (!ISO_DATE.matcher(date).matches()) continue;
            String id = str(x, "id", 40);
            if (id.isEmpty()) id = String.valueOf(System.currentTimeMillis()) + i;
            out.add(new Plan(id, str(x, "subject", 120), date, str(x, textKey, 500), textKey.equals("text") && bool(x, "done", false)));
        }
        return out;
    }

    public static Prefs fromJson(JSONObject d) {
        Prefs p = new Prefs();
        if (d == null) return p;
        p.displayName = str(d, "displayName", 60);
        p.nickname = str(d, "nickname", 40);
        String em = str(d, "emoji", 8);
        p.emoji = em.isEmpty() ? "🎓" : em;
        p.theme = oneOf(d, "theme", THEMES, "dark");
        p.palette = oneOf(d, "palette", PALETTES, "desk");
        p.bgStyle = oneOf(d, "bgStyle", BG_STYLES, "flat");
        p.cardStyle = oneOf(d, "cardStyle", CARD_STYLES, "fill");
        Object acc = d.opt("accent");
        p.accent = acc instanceof String && HEX.matcher((String) acc).matches() ? (String) acc : "#ffe14d";
        p.density = oneOf(d, "density", DENSITY, "normal");
        p.fontScale = num(d, "fontScale", 1.0, 0.8, 1.3);
        p.radius = (int) num(d, "radius", 16, 0, 28);
        p.targetAvg = num(d, "targetAvg", 4.5, 1, 5);
        p.startView = oneOf(d, "startView", VIEWS, "dashboard");
        p.weighted = bool(d, "weighted", true);
        p.customCss = cleanCss(d.optString("customCss", ""));
        p.subjectNames = strMap(d.opt("subjectNames"));
        p.teacherNames = strMap(d.opt("teacherNames"));
        JSONObject goals = d.optJSONObject("subjectGoals");
        if (goals != null) {
            Iterator<String> it = goals.keys();
            for (int i = 0; it.hasNext() && i < 200; i++) {
                String k = it.next();
                double g = num(goals, k, Double.NaN, 1, 5);
                if (!Double.isNaN(g)) p.subjectGoals.put(clip(k, 120), g);
            }
        }
        p.hiddenSubjects = strList(d.opt("hiddenSubjects"), 200, 120);
        p.myHomework = plans(d.opt("myHomework"), "text");
        p.myTests = plans(d.opt("myTests"), "topic");
        JSONObject pack = d.optJSONObject("packItems");
        if (pack != null) {
            Iterator<String> it = pack.keys();
            for (int i = 0; it.hasNext() && i < 200; i++) {
                String k = it.next();
                List<String> items = strList(pack.opt(k), 40, 60);
                if (!items.isEmpty()) p.packItems.put(clip(k, 120), items);
            }
        }
        p.packAll = strList(d.opt("packAll"), 40, 60);
        p.packLayout = oneOf(d, "packLayout", PACK_LAYOUTS, "list");
        p.funnyQuips = bool(d, "funnyQuips", true);
        p.goodStudent = bool(d, "goodStudent", false);
        p.devMode = bool(d, "devMode", false);
        p.devFillLessons = p.devMode && bool(d, "devFillLessons", false);
        p.devFillTimetable = p.devMode && bool(d, "devFillTimetable", false);
        JSONArray w = d.optJSONArray("widgets");
        if (w != null) {
            p.widgets = new ArrayList<>();
            for (int i = 0; i < w.length(); i++) {
                String k = w.optString(i, "");
                if (WIDGETS.contains(k) && !p.widgets.contains(k)) p.widgets.add(k);
            }
        }
        return p;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("displayName", displayName).put("nickname", nickname).put("emoji", emoji)
                    .put("theme", theme).put("palette", palette).put("bgStyle", bgStyle).put("cardStyle", cardStyle)
                    .put("accent", accent).put("density", density).put("fontScale", fontScale).put("radius", radius)
                    .put("startView", startView).put("targetAvg", targetAvg).put("weighted", weighted).put("customCss", customCss)
                    .put("subjectNames", new JSONObject(subjectNames)).put("teacherNames", new JSONObject(teacherNames))
                    .put("subjectGoals", new JSONObject(subjectGoals)).put("hiddenSubjects", new JSONArray(hiddenSubjects));
            JSONArray hw = new JSONArray(), ts = new JSONArray();
            for (Plan p : myHomework) hw.put(new JSONObject().put("id", p.id).put("subject", p.subject).put("date", p.date).put("text", p.body).put("done", p.done));
            for (Plan p : myTests) ts.put(new JSONObject().put("id", p.id).put("subject", p.subject).put("date", p.date).put("topic", p.body));
            o.put("myHomework", hw).put("myTests", ts);
            JSONObject pack = new JSONObject();
            for (Map.Entry<String, List<String>> e : packItems.entrySet()) if (!e.getValue().isEmpty()) pack.put(e.getKey(), new JSONArray(e.getValue()));
            o.put("packItems", pack).put("packAll", new JSONArray(packAll)).put("packLayout", packLayout)
                    .put("funnyQuips", funnyQuips).put("goodStudent", goodStudent).put("devMode", devMode)
                    .put("devFillLessons", devFillLessons).put("devFillTimetable", devFillTimetable)
                    .put("widgets", new JSONArray(widgets));
        } catch (JSONException ignored) {}
        return o;
    }

    /** Import: a fájlban lévő kulcsok felülírják a mostaniakat, a hiányzók megmaradnak; utána ugyanaz a tisztítás. */
    public Prefs mergedWith(JSONObject in) throws JSONException {
        JSONObject cur = toJson();
        Iterator<String> it = in.keys();
        while (it.hasNext()) {
            String k = it.next();
            cur.put(k, in.get(k));
        }
        return fromJson(cur);
    }
}
