package hu.zsirkreta.app.data;

import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import hu.zsirkreta.app.data.Models.Raw;

/**
 * KRÉTA kliens: belépés (OAuth2 + PKCE, a KRÉTA saját oldalán), tokenkezelés, udvarias lekérdezés gyorsítótárral (a webes kreta.py megfelelője).
 *
 * Alapelvek, hogy a KRÉTA szervere felé minél kevesebb és egyenletes forgalom menjen:
 *   * minden válasz gyorsítótárba kerül (TTL adatfajtánként),
 *   * a „Frissítés” és a percenkénti automatikus frissítés együtt is csak percenként hív valójában,
 *   * soros hívások, köztük minimális szünettel (+ kis véletlen eltérés),
 *   * 429/403 után szünet: addig csak a tárolt adatot mutatjuk,
 *   * a token megújítása csak lejáratkor történik, a jelszót soha nem tároljuk.
 */
public final class Kreta {
    private Kreta() {}

    public static final class KretaError extends Exception {
        public final int status;
        public final String code, detail;
        public KretaError(String msg, int status, String code, String detail) {
            super(msg);
            this.status = status; this.code = code; this.detail = detail;
        }
        public KretaError(String msg, int status, String code) { this(msg, status, code, null); }
        public boolean relogin() { return "relogin".equals(code); }
    }

    private static final SecureRandom RND = new SecureRandom();
    private static final Object LOCK = new Object();
    private static volatile long lastCall = 0, cooldownUntil = 0;

    // ---------- PKCE ----------
    public static final class Pkce {
        public final String verifier, challenge, state;
        Pkce(String v, String c, String s) { verifier = v; challenge = c; state = s; }
    }

    private static String b64url(byte[] b) { return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP); }

    private static byte[] random(int n) { byte[] b = new byte[n]; RND.nextBytes(b); return b; }

    public static Pkce newPkce() {
        String verifier = b64url(random(48));
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return new Pkce(verifier, b64url(h), b64url(random(12)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String authorizeUrl(String challenge, String state) {
        return Config.authorize() + "?response_type=code&client_id=" + Http.enc(Config.clientId())
                + "&redirect_uri=" + Http.enc(Config.redirectUri()) + "&scope=" + Http.enc(Config.scope())
                + "&code_challenge=" + challenge + "&code_challenge_method=S256&state=" + state + "&prompt=login";
    }

    static JSONObject jwtClaims(String token) {
        try {
            String p = token.split("\\.")[1];
            return new JSONObject(new String(Base64.decode(p, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    // ---------- token végpont ----------
    public static final class TokenResp {
        public int status;
        public JSONObject json = new JSONObject();
        public boolean ok() { return status >= 200 && status < 300 && json.has("access_token"); }
    }

    private static TokenResp tokenCall(Map<String, String> fields) throws IOException {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("User-Agent", Config.ua());
        h.put("Accept", "application/json");
        Http.Resp r = Http.send("POST", Config.idp() + "/connect/token", h, Http.form(fields), 20000);
        TokenResp t = new TokenResp();
        t.status = r.status;
        try { t.json = new JSONObject(r.body); } catch (Exception ignored) {}
        return t;
    }

    public static TokenResp exchangeCode(String code, String verifier) throws IOException {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("grant_type", "authorization_code");
        f.put("code", code);
        f.put("code_verifier", verifier);
        f.put("redirect_uri", Config.redirectUri());
        f.put("client_id", Config.clientId());
        return tokenCall(f);
    }

    // ---------- fiók ----------
    public static void saveUser(String inst, String username, JSONObject tok, boolean remember) {
        String access = tok.optString("access_token", "");
        JSONObject claims = jwtClaims(access);
        String in = claims.optString("kreta:institute_code", "");
        if (in.isEmpty()) in = inst == null ? "" : inst.trim();
        String sub = claims.optString("sub", "");
        if (sub.isEmpty()) sub = username == null ? "" : username.toLowerCase();
        Store.Account a = new Store.Account();
        a.ukey = in + ":" + sub;
        a.inst = in;
        a.username = username == null ? "" : username;
        a.remember = remember;
        a.accessEnc = SecureBox.enc(access);
        a.accessExp = now() + tok.optDouble("expires_in", 3000) - 30;
        a.refreshEnc = SecureBox.enc(tok.optString("refresh_token", ""));
        Store.Account old = Store.account();
        if (old != null && old.ukey.equals(a.ukey)) a.name = old.name;
        Store.saveAccount(a);
        cooldownUntil = 0;
    }

    /** Teszt fiók: minden belépés új, különálló fiók véletlen adatokkal (jelszó és KRÉTA nélkül). */
    public static void demoAccount() {
        Store.Account a = new Store.Account();
        a.ukey = Demo.INST + ":" + Long.toString(System.currentTimeMillis(), 36) + Integer.toString(RND.nextInt(1296), 36);
        a.inst = Demo.INST;
        a.name = "Teszt Elek";
        a.demo = true;
        a.remember = true;
        Store.saveAccount(a);
    }

    // ---------- tokenek ----------
    private static String refresh(Store.Account a) throws KretaError {
        String rt = SecureBox.dec(a.refreshEnc);
        if (rt == null) throw new KretaError("Lejárt a belépés, jelentkezz be újra", 401, "relogin");
        TokenResp t;
        try {
            Map<String, String> f = new LinkedHashMap<>();
            f.put("grant_type", "refresh_token");
            f.put("refresh_token", rt);
            f.put("client_id", Config.clientId());
            t = tokenCall(f);
        } catch (IOException e) {
            throw new KretaError("A KRÉTA nem elérhető", 502, "net", e.getMessage());
        }
        if (!t.ok()) {
            android.util.Log.w("ZsirKreta", "token megújítás: " + t.status + " " + clip(t.json.toString(), 200));
            // csak akkor léptetünk ki, ha a KRÉTA kifejezetten azt mondja, hogy a megújító token már nem érvényes;
            // minden más (szerverhiba, proxy-oldal, karbantartás) átmeneti: a token megmarad, később újrapróbáljuk
            if ((t.status == 400 || t.status == 401) && "invalid_grant".equals(t.json.optString("error"))) {
                a.refreshEnc = null; a.accessEnc = null; a.accessExp = 0;
                Store.saveAccount(a);
                throw new KretaError("Lejárt a belépés, jelentkezz be újra", 401, "relogin");
            }
            throw new KretaError("A KRÉTA ideiglenesen nem elérhető", 502, "net");
        }
        String access = t.json.optString("access_token");
        a.accessEnc = SecureBox.enc(access);
        a.accessExp = now() + t.json.optDouble("expires_in", 3000) - 30;
        String nrt = t.json.optString("refresh_token", "");
        if (!nrt.isEmpty()) a.refreshEnc = SecureBox.enc(nrt);
        Store.saveAccount(a);
        return access;
    }

    private static String access(Store.Account a, boolean forceRefresh) throws KretaError {
        if (!forceRefresh && a.accessExp > now()) {
            String t = SecureBox.dec(a.accessEnc);
            if (t != null) return t;
        }
        return refresh(a);
    }

    /**
     * Háttérből (KeepAlive): ha mostanában nem volt token-megújítás, megújítja, hogy a KRÉTA akkor se léptessen ki,
     * ha napokig nincs megnyitva az app (és nincs bekapcsolva értesítés). false: átmeneti hiba, később újra kell próbálni.
     */
    public static boolean keepAlive() {
        synchronized (LOCK) {
            Store.Account a = Store.account();
            if (a == null || a.demo || a.refreshEnc == null) return true;
            if (a.accessExp > now()) return true;   // a hozzáférési token még él, tehát nemrég volt megújítás
            try {
                refresh(a);
                return true;
            } catch (KretaError e) {
                return e.relogin();
            }
        }
    }

    // ---------- udvarias lekérdezés ----------
    static double now() { return System.currentTimeMillis() / 1000.0; }

    private static String clip(String s, int n) { return s == null ? "" : s.length() <= n ? s : s.substring(0, n); }

    /** Hány mp van még hátra a 429/403 utáni szünetből (0, ha nincs szünet). */
    public static long cooldownLeft() { return Math.max(0, (cooldownUntil - System.currentTimeMillis() + 999) / 1000); }

    static Object parse(String body) {
        try {
            Object v = new JSONTokener(body).nextValue();
            return v instanceof JSONArray || v instanceof JSONObject ? v : new JSONArray();
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static Raw wrap(JSONObject row, boolean stale, boolean cached) {
        String body = row.optString("body", "[]");
        return new Raw(parse(body), body, row.optDouble("fetchedAt", 0), stale, cached);
    }

    private static Raw staleOr(JSONObject row, KretaError err) throws KretaError {
        if (row != null && now() - row.optDouble("fetchedAt", 0) < Config.STALE_MAX) return wrap(row, true, true);
        throw err;
    }

    /** Csak a gyorsítótárból (indulásnál, hálózat nélkül); null, ha nincs. */
    public static Raw cached(String what, Map<String, String> params) {
        Store.Account a = Store.account();
        if (a == null || a.demo) return null;
        JSONObject row = Store.cacheGet(a.ukey, ckey(what, params));
        return row == null ? null : wrap(row, false, true);
    }

    private static Map<String, String> clean(Map<String, String> params) {
        Map<String, String> p = new TreeMap<>();
        if (params != null) for (String k : new String[]{"datumTol", "datumIg"}) if (params.get(k) != null) p.put(k, params.get(k));
        return p;
    }

    private static String ckey(String what, Map<String, String> params) {
        Map<String, String> p = clean(params);
        return p.isEmpty() ? what : what + "?" + Http.form(p);
    }

    /**
     * Egy adatfajta (tanulo, jegyek, orarend …). force: friss adatot kér, de csak ha a tárolt legalább FORCE_MIN_AGE mp-es.
     * Hálózati vagy KRÉTA-hibánál a legfeljebb egy hetes tárolt adatot adja (stale), különben KretaError.
     */
    public static Raw fetch(String what, Map<String, String> params, boolean force) throws KretaError {
        String ep = Config.ENDPOINTS.get(what);
        if (ep == null) throw new KretaError("Ismeretlen végpont", 404, "nf");
        Store.Account a = Store.account();
        if (a == null) throw new KretaError("Nem vagy belépve", 401, "relogin");
        Map<String, String> p = clean(params);
        if (a.demo) {
            String body = Demo.data(a.ukey, what, p).toString();
            return new Raw(parse(body), body, now(), false, false);
        }
        String ck = ckey(what, p);
        JSONObject row = Store.cacheGet(a.ukey, ck);
        if (row != null) {
            double age = now() - row.optDouble("fetchedAt", 0);
            Long ttl = Config.CACHE_TTL.get(what);
            if (age < (ttl == null ? 600 : ttl) && !(force && age >= Config.FORCE_MIN_AGE)) return wrap(row, false, true);
        }
        if (cooldownUntil > System.currentTimeMillis()) return staleOr(row, new KretaError("A KRÉTA lassít minket, próbáld később", 429, "cooldown"));

        String url = "https://" + a.inst + ".e-kreta.hu/ellenorzo/V3/" + ep + (p.isEmpty() ? "" : "?" + Http.form(p));
        Http.Resp r = null;
        synchronized (LOCK) {
            // amíg vártunk a zárra, más szál már frissíthette
            JSONObject row2 = Store.cacheGet(a.ukey, ck);
            if (row2 != null && row2.optDouble("fetchedAt", 0) > (row == null ? 0 : row.optDouble("fetchedAt", 0))) return wrap(row2, false, true);
            long wait = lastCall + Config.MIN_GAP_MS + 100 + RND.nextInt(500) - System.currentTimeMillis();
            if (wait > 0) try { Thread.sleep(wait); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            try {
                for (int attempt = 1; attempt <= 2; attempt++) {
                    String token = access(a, attempt == 2);
                    Map<String, String> h = new LinkedHashMap<>();
                    h.put("Authorization", "Bearer " + token);
                    h.put("User-Agent", Config.ua());
                    h.put("Accept", "application/json");
                    r = Http.send("GET", url, h, null, 15000);
                    lastCall = System.currentTimeMillis();
                    if (r.status == 401 && attempt == 1) continue;
                    break;
                }
            } catch (IOException e) {
                android.util.Log.w("ZsirKreta", what + ": hálózati hiba: " + e);
                return staleOr(row, new KretaError("A KRÉTA nem elérhető", 502, "net", e.getMessage()));
            } catch (KretaError e) {
                if (e.relogin()) throw e;
                return staleOr(row, e);
            }
        }
        if (r.status == 429 || r.status == 403) {
            cooldownUntil = System.currentTimeMillis() + Config.COOLDOWN_429 * 1000;
            android.util.Log.w("ZsirKreta", what + ": " + r.status + ", " + Config.COOLDOWN_429 + " mp szünet");
            return staleOr(row, new KretaError("A KRÉTA lassít minket, próbáld később", 429, "cooldown"));
        }
        // friss tokennel is 401: a KRÉTA hibája, nem a belépésé (a belépés lejártát a token-megújítás jelzi)
        if (r.status == 401) return staleOr(row, new KretaError("A KRÉTA ideiglenesen nem elérhető", 502, "kreta"));
        if (!r.ok()) return staleOr(row, new KretaError("A KRÉTA " + r.status + " hibát adott (" + what + ")", 502, "kreta", clip(r.body, 200)));
        String body = r.body.isEmpty() ? "[]" : r.body;
        Object data;
        try {
            data = new JSONTokener(body).nextValue();
            if (!(data instanceof JSONArray) && !(data instanceof JSONObject)) throw new Exception("nem JSON");
        } catch (Exception e) {
            return staleOr(row, new KretaError("A KRÉTA érvénytelen választ adott", 502, "kreta"));
        }
        double at = Store.cachePut(a.ukey, ck, body);
        return new Raw(data, body, at, false, false);
    }
}
