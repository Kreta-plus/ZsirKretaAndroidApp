package hu.zsirkreta.app.data;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import hu.zsirkreta.app.BuildConfig;
import hu.zsirkreta.app.util.Async;

/**
 * Beépített frissítés a GitHub-repó updates/ mappájából (ott: latest.json + az APK-k).
 *
 *   1. ellenőrzés: indításkor / visszatéréskor legfeljebb 6 óránként, vagy kézzel a Beállításokban,
 *   2. letöltés az app gyorsítótárába, folyamatjelzéssel, megszakíthatóan,
 *   3. ellenőrzés telepítés előtt: SHA-256, csomagnév, verziószám, és hogy UGYANAZ a kulcs írta-e alá, mint a telepített appot
 *      (különben az Android úgyis elutasítaná, csak érthetetlen hibával),
 *   4. telepítés: az Android saját telepítője (a felhasználó jóváhagyja); ha kell, előbb az „ismeretlen forrás” engedélyt kéri.
 */
public final class Updater {
    private Updater() {}

    public enum State { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR }

    /** Egy kiadás a latest.json-ból. notes: az összes kimaradt verzió újdonságai, a legújabbal kezdve. */
    public static final class Info {
        public int versionCode, minSdk;
        public String versionName = "", date = "", apkUrl = "", sha256 = "";
        public long size;
        public final List<String[]> changes = new ArrayList<>();   // {verzió, újdonság}
    }

    private static final long AUTO_EVERY_MS = 6 * 3600_000L;     // automatikus ellenőrzés legfeljebb ennyi időnként
    private static final long SNOOZE_MS = 3 * 24 * 3600_000L;    // „Később”: ennyi ideig nem mutatjuk a kártyát ugyanarra a verzióra
    private static final String APK_MIME = "application/vnd.android.package-archive";

    private static SharedPreferences sp;
    private static File dir;
    private static volatile State state = State.IDLE;
    private static volatile Info info;
    private static volatile File ready;
    private static volatile String error = "";
    private static volatile long done, total;
    private static volatile boolean cancel;
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public static void init(Context c) {
        sp = c.getSharedPreferences("updates", Context.MODE_PRIVATE);
        dir = new File(c.getCacheDir(), "updates");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        // a legutóbbi ellenőrzés eredménye, hogy indításkor hálózat nélkül is látszódjon, ha van új verzió
        try {
            String cached = sp.getString("manifest", null);
            if (cached != null) {
                Info i = parse(new JSONObject(cached), sp.getString("manifestUrl", BuildConfig.UPDATE_URL));
                if (i.versionCode > currentCode(c)) { info = i; state = State.AVAILABLE; }
                else state = State.UP_TO_DATE;
            }
        } catch (Exception ignored) {}
        cleanup(c);
        File f = info == null ? null : apkFile(info);
        if (f != null && f.exists() && verified(c, f, info) == null) { ready = f; state = State.READY; }
    }

    // ---------- állapot ----------
    public static State state() { return state; }
    public static Info info() { return info; }
    public static String error() { return error; }
    public static long done() { return done; }
    public static long total() { return total; }
    public static long lastCheck() { return sp == null ? 0 : sp.getLong("lastCheck", 0); }
    public static boolean autoCheck() { return sp == null || sp.getBoolean("auto", true); }
    public static void setAutoCheck(boolean on) { sp.edit().putBoolean("auto", on).apply(); }

    /** A felület feliratkozik: minden állapotváltozáskor (főszálon) meghívódik. */
    public static void listen(Runnable r) { if (!listeners.contains(r)) listeners.add(r); }
    public static void unlisten(Runnable r) { listeners.remove(r); }

    private static void set(State s) {
        state = s;
        Async.ui(() -> { for (Runnable r : listeners) r.run(); });
    }

    public static String currentName(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception e) { return BuildConfig.VERSION_NAME; }
    }

    @SuppressWarnings("deprecation")
    public static long currentCode(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
        } catch (Exception e) { return BuildConfig.VERSION_CODE; }
    }

    /** Mutassuk-e a kezdőlapi kártyát (van új verzió, és nem nyomta rá a „Később”-t mostanában). */
    public static boolean showCard() {
        if (info == null || (state != State.AVAILABLE && state != State.DOWNLOADING && state != State.READY && state != State.ERROR)) return false;
        if (state == State.DOWNLOADING || state == State.READY) return true;
        return !(sp.getInt("snoozeCode", -1) == info.versionCode && System.currentTimeMillis() < sp.getLong("snoozeUntil", 0));
    }

    public static void snooze() {
        if (info == null) return;
        sp.edit().putInt("snoozeCode", info.versionCode).putLong("snoozeUntil", System.currentTimeMillis() + SNOOZE_MS).apply();
        set(state);
    }

    // ---------- ellenőrzés ----------
    /** Automatikus ellenőrzés, ha bekapcsolva és már esedékes. */
    public static void checkIfDue(Context c) {
        if (!autoCheck() || state == State.CHECKING || state == State.DOWNLOADING) return;
        if (System.currentTimeMillis() - lastCheck() < AUTO_EVERY_MS) return;
        check(c, false);
    }

    /** manual: a felhasználó kérte (ilyenkor a hibát is mutatjuk, és nem nézzük, mikor volt az előző). */
    public static void check(Context c, boolean manual) {
        if (state == State.CHECKING || state == State.DOWNLOADING) return;
        final Context app = c.getApplicationContext();
        final State before = state;
        set(State.CHECKING);
        Async.bg(() -> {
            try {
                // percenként más cím: a GitHub raw gyorsítótára ne adjon régi leírót
                String url = BuildConfig.UPDATE_URL + (BuildConfig.UPDATE_URL.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis() / 60000;
                JSONObject j = new JSONObject(get(url));
                Info i = parse(j, BuildConfig.UPDATE_URL);
                sp.edit().putLong("lastCheck", System.currentTimeMillis()).putString("manifest", j.toString()).putString("manifestUrl", BuildConfig.UPDATE_URL).apply();
                if (i.versionCode <= currentCode(app)) { info = null; set(State.UP_TO_DATE); return; }
                if (i.minSdk > Build.VERSION.SDK_INT) {
                    info = null;
                    error = "A " + i.versionName + " verzióhoz újabb Android kell (legalább API " + i.minSdk + ").";
                    set(manual ? State.ERROR : State.UP_TO_DATE);
                    return;
                }
                boolean same = info != null && info.versionCode == i.versionCode;
                info = i;
                File f = apkFile(i);
                if (same && before == State.READY && ready != null && ready.exists()) set(State.READY);
                else if (f.exists() && verified(app, f, i) == null) { ready = f; set(State.READY); }
                else set(State.AVAILABLE);
            } catch (Exception e) {
                error = "Nem sikerült megnézni, van-e frissítés (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ").";
                set(manual ? State.ERROR : info != null ? State.AVAILABLE : State.IDLE);
            }
        });
    }

    /**
     * Háttérből (értesítéshez): ugyanaz az ellenőrzés, de a hívó szálon, és csak ha a legutóbbi régebbi maxAgeMs-nál.
     * Visszaad: az elérhető újabb verzió, vagy null.
     */
    public static Info checkNow(Context c, long maxAgeMs) {
        if (state == State.DOWNLOADING || state == State.CHECKING) return info;
        if (System.currentTimeMillis() - lastCheck() < maxAgeMs) return state == State.AVAILABLE || state == State.READY ? info : null;
        try {
            String url = BuildConfig.UPDATE_URL + (BuildConfig.UPDATE_URL.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis() / 60000;
            JSONObject j = new JSONObject(get(url));
            Info i = parse(j, BuildConfig.UPDATE_URL);
            sp.edit().putLong("lastCheck", System.currentTimeMillis()).putString("manifest", j.toString()).putString("manifestUrl", BuildConfig.UPDATE_URL).apply();
            if (i.versionCode <= currentCode(c) || i.minSdk > Build.VERSION.SDK_INT) {
                if (state != State.READY) { info = null; set(State.UP_TO_DATE); }
                return null;
            }
            if (state != State.READY || info == null || info.versionCode != i.versionCode) { info = i; set(State.AVAILABLE); }
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    private static Info parse(JSONObject j, String base) throws Exception {
        Info i = new Info();
        i.versionCode = j.getInt("versionCode");
        i.versionName = j.optString("versionName", String.valueOf(i.versionCode));
        i.date = j.optString("date", "");
        i.minSdk = j.optInt("minSdk", 26);
        i.size = j.optLong("size", 0);
        i.sha256 = j.optString("sha256", "").toLowerCase();
        String apk = j.getString("apk");
        i.apkUrl = URI.create(base).resolve(apk).toString();   // a leíróhoz képest relatív név is lehet
        if (!i.apkUrl.startsWith("https://") && !BuildConfig.DEBUG) throw new IOException("nem biztonságos letöltési cím");
        // újdonságok: a „versions” lista azon elemei, amelyek újabbak a telepítettnél (a leíró a legújabbal kezdődik)
        JSONArray vs = j.optJSONArray("versions");
        if (vs != null) {
            for (int k = 0; k < vs.length(); k++) {
                JSONObject v = vs.optJSONObject(k);
                if (v == null || v.optInt("versionCode", 0) <= BuildConfig.VERSION_CODE) continue;
                JSONArray n = v.optJSONArray("notes");
                if (n != null) for (int m = 0; m < n.length(); m++) i.changes.add(new String[]{v.optString("versionName", ""), n.optString(m, "")});
            }
        }
        JSONArray top = j.optJSONArray("notes");
        if (i.changes.isEmpty() && top != null) for (int m = 0; m < top.length(); m++) i.changes.add(new String[]{i.versionName, top.optString(m, "")});
        return i;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Cache-Control", "no-cache");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream is = c.getInputStream(); java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
                byte[] b = new byte[64 * 1024];
                int n;
                while ((n = is.read(b)) > 0) {
                    buf.write(b, 0, n);
                    if (buf.size() > 512 * 1024) throw new IOException("túl nagy leíró");
                }
                return buf.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    // ---------- letöltés ----------
    private static File apkFile(Info i) { return new File(dir, "ZsirKreta-" + i.versionCode + ".apk"); }

    public static void download(Context c) {
        final Info i = info;
        if (i == null || state == State.DOWNLOADING) return;
        final Context app = c.getApplicationContext();
        cancel = false;
        done = 0;
        total = i.size;
        set(State.DOWNLOADING);
        Async.bg(() -> {
            File part = new File(dir, "ZsirKreta-" + i.versionCode + ".part"), out = apkFile(i);
            try {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                // mobilnet: ha megszakad, legfeljebb négyszer próbálja, és ahol abbamaradt, onnan folytatja (HTTP Range)
                for (int attempt = 1; ; attempt++) {
                    try {
                        fetchInto(i, part);
                        break;
                    } catch (IOException e) {
                        if (cancel) throw new InterruptedException();
                        if (attempt >= 4) throw e;
                        Thread.sleep(1500L * attempt);
                    }
                }
                if (!i.sha256.isEmpty() && !i.sha256.equals(sha256(part))) throw new IOException("sérült letöltés (az ellenőrzőösszeg nem egyezik), próbáld újra");
                //noinspection ResultOfMethodCallIgnored
                out.delete();
                if (!part.renameTo(out)) throw new IOException("nem sikerült menteni a letöltést");
                String bad = verified(app, out, i);
                if (bad != null) {
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                    error = bad;
                    set(State.ERROR);
                    return;
                }
                ready = out;
                set(State.READY);
            } catch (InterruptedException e) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                set(State.AVAILABLE);
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                error = "A letöltés nem sikerült: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                set(State.ERROR);
            }
        });
    }

    /** Egy letöltési kísérlet: ha a .part fájlban már van valami, a hiányzó résszel folytatja (206), különben elölről (200). */
    private static void fetchInto(Info i, File part) throws IOException, InterruptedException {
        long have = part.exists() ? part.length() : 0;
        HttpURLConnection con = (HttpURLConnection) new URL(i.apkUrl).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(30000);
        if (have > 0) con.setRequestProperty("Range", "bytes=" + have + "-");
        try {
            int code = con.getResponseCode();
            boolean append = code == 206 && have > 0;
            if (code != 200 && !append) throw new IOException("HTTP " + code);
            if (!append) have = 0;
            long len = con.getContentLengthLong();
            if (len > 0) total = have + len;
            done = have;
            long lastPing = 0;
            try (InputStream is = con.getInputStream(); FileOutputStream os = new FileOutputStream(part, append)) {
                byte[] b = new byte[64 * 1024];
                int n;
                while ((n = is.read(b)) > 0) {
                    if (cancel) throw new InterruptedException();
                    os.write(b, 0, n);
                    done += n;
                    if (done > 200L * 1024 * 1024) throw new IOException("túl nagy fájl");
                    long now = System.currentTimeMillis();
                    if (now - lastPing > 120) { lastPing = now; set(State.DOWNLOADING); }
                }
            }
            if (total > 0 && done < total) throw new IOException("megszakadt a letöltés");
        } finally {
            con.disconnect();
        }
    }

    private static String sha256(File f) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream is = new java.io.FileInputStream(f)) {
            byte[] b = new byte[64 * 1024];
            int n;
            while ((n = is.read(b)) > 0) sha.update(b, 0, n);
        }
        return hex(sha.digest());
    }

    public static void cancelDownload() { cancel = true; }

    private static String hex(byte[] d) {
        StringBuilder b = new StringBuilder();
        for (byte x : d) b.append(String.format("%02x", x));
        return b.toString();
    }

    /** null, ha a letöltött APK telepíthető; különben a hiba szövege. */
    @SuppressWarnings("deprecation")
    private static String verified(Context c, File apk, Info i) {
        PackageManager pm = c.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo got = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (got == null) return "A letöltött fájl nem érvényes Android-alkalmazás.";
        if (!c.getPackageName().equals(got.packageName)) return "A letöltött fájl nem a ZsírKréta frissítése.";
        long code = Build.VERSION.SDK_INT >= 28 ? got.getLongVersionCode() : got.versionCode;
        if (code <= currentCode(c)) return "A letöltött verzió nem újabb a telepítettnél.";
        if (code != i.versionCode) return "A letöltött verzió (" + code + ") nem az, amit a leíró ígért (" + i.versionCode + ").";
        try {
            PackageInfo mine = pm.getPackageInfo(c.getPackageName(), flags);
            if (!sameSigner(mine, got)) return "A frissítés más kulccsal van aláírva, mint a telepített app, ezért az Android nem tudja frissíteni. "
                    + "Töröld a mostani appot, és telepítsd újra a ZsírKrétát a GitHubról (a beállításaidat előtte mentsd ki).";
        } catch (Exception e) {
            return "Nem sikerült ellenőrizni a frissítés aláírását.";
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static boolean sameSigner(PackageInfo a, PackageInfo b) {
        Signature[] x, y;
        if (Build.VERSION.SDK_INT >= 28) {
            if (a.signingInfo == null || b.signingInfo == null) return false;
            x = a.signingInfo.getApkContentsSigners();
            y = b.signingInfo.getApkContentsSigners();
        } else {
            x = a.signatures;
            y = b.signatures;
        }
        if (x == null || y == null || x.length == 0) return false;
        List<String> xs = new ArrayList<>(), ys = new ArrayList<>();
        for (Signature s : x) xs.add(s.toCharsString());
        for (Signature s : y) ys.add(s.toCharsString());
        java.util.Collections.sort(xs);
        java.util.Collections.sort(ys);
        return xs.equals(ys);
    }

    // ---------- telepítés ----------
    /** Engedi-e az Android, hogy ez az app telepítsen (Beállítások → Ismeretlen alkalmazások telepítése). */
    public static boolean canInstall(Context c) { return c.getPackageManager().canRequestPackageInstalls(); }

    public static Intent permissionIntent(Context c) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName()));
    }

    public static Intent installIntent(Context c) {
        File f = ready;
        if (f == null || !f.exists()) return null;
        Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".files", f);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, APK_MIME);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** Régi, már telepített verziók letöltéseinek törlése. */
    private static void cleanup(Context c) {
        long cur = currentCode(c);
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            String n = f.getName();
            if (n.endsWith(".part")) { //noinspection ResultOfMethodCallIgnored
                f.delete(); continue; }
            try {
                long v = Long.parseLong(n.replace("ZsirKreta-", "").replace(".apk", ""));
                if (v <= cur) { //noinspection ResultOfMethodCallIgnored
                    f.delete(); }
            } catch (NumberFormatException ignored) {}
        }
        if (info != null && info.versionCode <= cur) { info = null; state = State.IDLE; }
    }
}
