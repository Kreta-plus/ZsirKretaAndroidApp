package hu.zsirkreta.app.util;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Null-biztos JSON-olvasás. Az org.json az explicit null értékre „null” szöveget ad az optString-ből,
 * ezért ezt kerüljük: hiányzó vagy null mező mindig üres szöveg / null.
 */
public final class J {
    private J() {}

    public static String str(JSONObject o, String k) {
        if (o == null) return "";
        Object v = o.opt(k);
        if (v == null || v == JSONObject.NULL) return "";
        return String.valueOf(v);
    }

    public static JSONObject obj(JSONObject o, String k) {
        if (o == null) return null;
        Object v = o.opt(k);
        return v instanceof JSONObject ? (JSONObject) v : null;
    }

    /** A JS „!!x” megfelelője szövegekre/számokra. */
    public static boolean truthy(JSONObject o, String k) {
        if (o == null) return false;
        Object v = o.opt(k);
        if (v == null || v == JSONObject.NULL) return false;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        return !String.valueOf(v).isEmpty();
    }

    public static Double num(JSONObject o, String k) {
        if (o == null) return null;
        Object v = o.opt(k);
        return v instanceof Number ? ((Number) v).doubleValue() : null;
    }

    /** Első nem üres szöveg a megadott kulcsok közül (a JS „a || b || c”). */
    public static String first(JSONObject o, String... keys) {
        for (String k : keys) {
            String s = str(o, k);
            if (!s.isEmpty()) return s;
        }
        return "";
    }

    public static JSONArray arr(Object o) { return o instanceof JSONArray ? (JSONArray) o : new JSONArray(); }
}
