package hu.zsirkreta.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Beállítások (a webes config.py megfelelője). A KRÉTA-végpontok a belépő képernyő „Haladó beállítások” részén
 * felülírhatók; az üresen hagyott mező az alapértéket jelenti.
 */
public final class Config {
    private Config() {}

    public static final String[] KEYS = {"idp", "authorize", "clientId", "redirectUri", "scope", "ua"};
    public static final String[] LABELS = {"Azonosító szerver (IDP)", "Engedélyezési cím", "Kliens-azonosító", "Visszairányítási cím", "Hatókör (scope)", "User-Agent"};

    // --- udvarias lekérdezés: gyorsítótár és tempó (ugyanaz, mint a webes szerveren) ---
    public static final Map<String, Long> CACHE_TTL = new HashMap<>();
    static {
        CACHE_TTL.put("tanulo", 24 * 3600L);
        CACHE_TTL.put("jegyek", 10 * 60L);
        CACHE_TTL.put("orarend", 15 * 60L);
        CACHE_TTL.put("mulasztasok", 15 * 60L);
        CACHE_TTL.put("hazi", 10 * 60L);
        CACHE_TTL.put("szamonkeres", 15 * 60L);
        CACHE_TTL.put("feljegyzesek", 30 * 60L);
    }
    public static final Map<String, String> ENDPOINTS = new LinkedHashMap<>();
    static {
        ENDPOINTS.put("tanulo", "Sajat/TanuloAdatlap");
        ENDPOINTS.put("jegyek", "Sajat/Ertekelesek");
        ENDPOINTS.put("orarend", "Sajat/OrarendElemek");
        ENDPOINTS.put("mulasztasok", "Sajat/Mulasztasok");
        ENDPOINTS.put("hazi", "Sajat/HaziFeladatok");
        ENDPOINTS.put("szamonkeres", "Sajat/BejelentettSzamonkeresek");
        ENDPOINTS.put("feljegyzesek", "Sajat/Feljegyzesek");
    }
    public static final long MIN_GAP_MS = 1000;          // két KRÉTA-hívás között legalább ennyi (+ kis véletlen)
    public static final long FORCE_MIN_AGE = 60;         // frissítés (gomb vagy automatikus): legfeljebb percenként, mp
    public static final long AUTO_REFRESH_MS = 60_000;   // automatikus frissítés, amíg az app előtérben van
    public static final long COOLDOWN_429 = 300;         // 429/403 után ennyi mp-ig nem hívjuk a KRÉTÁ-t
    public static final long STALE_MAX = 7 * 24 * 3600;  // hiba esetén ennél régebbi adatot már nem mutatunk

    private static SharedPreferences sp;

    public static void init(Context c) { sp = c.getSharedPreferences("config", Context.MODE_PRIVATE); }

    public static Map<String, String> defaults() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("idp", "https://idp.e-kreta.hu");
        d.put("authorize", "");   // üresen: {idp}/connect/authorize
        d.put("clientId", "kreta-ellenorzo-student-mobile-ios");
        d.put("redirectUri", "https://mobil.e-kreta.hu/ellenorzo-student/prod/oauthredirect");
        d.put("scope", "openid email offline_access kreta-ellenorzo-webapi.public");
        d.put("ua", "hu.ekreta.student/2.0.0/Android/0/0");
        return d;
    }

    /** A felhasználó által megadott érték (üres, ha az alapértelmezett érvényes). */
    public static String get(String k) { return sp == null ? "" : sp.getString(k, ""); }

    /** A ténylegesen használt érték. */
    public static String value(String k) {
        String v = get(k).trim();
        if (!v.isEmpty()) return v;
        if (k.equals("authorize")) return idp() + "/connect/authorize";
        String d = defaults().get(k);
        return d == null ? "" : d;
    }

    public static void set(Map<String, String> vals) {
        SharedPreferences.Editor e = sp.edit();
        for (Map.Entry<String, String> en : vals.entrySet()) {
            String v = en.getValue() == null ? "" : en.getValue().trim();
            if (v.isEmpty()) e.remove(en.getKey()); else e.putString(en.getKey(), v);
        }
        e.apply();
    }

    public static void reset() { sp.edit().clear().apply(); }

    public static String idp() { return value("idp").replaceAll("/+$", ""); }
    public static String authorize() { return value("authorize"); }
    public static String clientId() { return value("clientId"); }
    public static String redirectUri() { return value("redirectUri"); }
    public static String scope() { return value("scope"); }
    public static String ua() { return value("ua"); }
}
