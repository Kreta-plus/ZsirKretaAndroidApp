package hu.zsirkreta.app.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/**
 * Tárolás az app privát tárhelyén (a webes SQLite megfelelője): fiók és tokenek, beállítások fiókonként,
 * a KRÉTA-válaszok gyorsítótára és a kipipált házik. A tokenek a SecureBox-szal titkosítva kerülnek lemezre.
 */
public final class Store {
    private Store() {}

    /** A belépett fiók. A tokenek titkosítva (refreshEnc, accessEnc). */
    public static final class Account {
        public String ukey = "", inst = "", username = "", name = "";
        public boolean demo, remember = true;
        public String refreshEnc, accessEnc;
        public double accessExp;

        JSONObject toJson() throws Exception {
            return new JSONObject().put("ukey", ukey).put("inst", inst).put("username", username).put("name", name)
                    .put("demo", demo).put("remember", remember).put("refreshEnc", refreshEnc == null ? JSONObject.NULL : refreshEnc)
                    .put("accessEnc", accessEnc == null ? JSONObject.NULL : accessEnc).put("accessExp", accessExp);
        }

        static Account fromJson(JSONObject o) {
            Account a = new Account();
            a.ukey = o.optString("ukey", "");
            a.inst = o.optString("inst", "");
            a.username = o.optString("username", "");
            a.name = o.optString("name", "");
            a.demo = o.optBoolean("demo", false);
            a.remember = o.optBoolean("remember", true);
            a.refreshEnc = o.isNull("refreshEnc") ? null : o.optString("refreshEnc", null);
            a.accessEnc = o.isNull("accessEnc") ? null : o.optString("accessEnc", null);
            a.accessExp = o.optDouble("accessExp", 0);
            return a;
        }
    }

    private static File root;
    private static Account account;

    public static synchronized void init(Context c) {
        root = new File(c.getFilesDir(), "zsirkreta");
        //noinspection ResultOfMethodCallIgnored
        root.mkdirs();
        account = null;
        try {
            String s = read(new File(root, "account.json"));
            if (s != null) account = Account.fromJson(new JSONObject(s));
        } catch (Exception ignored) {}
        // „Maradjak belépve” nélkül a belépés csak az app futásáig él
        if (account != null && !account.remember) logout();
        if (account != null && account.ukey.isEmpty()) account = null;
    }

    public static synchronized Account account() { return account; }

    public static synchronized void saveAccount(Account a) {
        account = a;
        try { write(new File(root, "account.json"), a.toJson().toString()); } catch (Exception e) { android.util.Log.e("ZsirKreta", "fiók mentése", e); }
    }

    // ---------- beállítások ----------
    private static File prefsFile(String ukey) { return new File(root, "prefs_" + hash(ukey) + ".json"); }

    public static synchronized Prefs loadPrefs(String ukey) {
        try {
            String s = read(prefsFile(ukey));
            return Prefs.fromJson(s == null ? new JSONObject() : new JSONObject(s));
        } catch (Exception e) {
            return new Prefs();
        }
    }

    public static synchronized void savePrefs(String ukey, Prefs p) {
        try { write(prefsFile(ukey), p.toJson().toString()); } catch (Exception e) { android.util.Log.e("ZsirKreta", "beállítások mentése", e); }
    }

    // ---------- gyorsítótár ----------
    private static File cacheDir(String ukey) { return new File(root, "cache_" + hash(ukey)); }

    /** {body, fetchedAt} vagy null */
    public static synchronized JSONObject cacheGet(String ukey, String ckey) {
        try {
            String s = read(new File(cacheDir(ukey), hash(ckey) + ".json"));
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    public static synchronized double cachePut(String ukey, String ckey, String body) {
        double now = System.currentTimeMillis() / 1000.0;
        try {
            File d = cacheDir(ukey);
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
            write(new File(d, hash(ckey) + ".json"), new JSONObject().put("ckey", ckey).put("fetchedAt", now).put("body", body).toString());
        } catch (Exception e) { android.util.Log.e("ZsirKreta", "gyorsítótár", e); }
        return now;
    }

    // ---------- kipipált KRÉTA-házik (a webes localStorage kp_hw_done_<uid> megfelelője) ----------
    private static File hwFile(String ukey) { return new File(root, "hwdone_" + hash(ukey) + ".json"); }

    public static synchronized Set<String> hwDone(String ukey) {
        Set<String> out = new HashSet<>();
        try {
            String s = read(hwFile(ukey));
            if (s != null) { JSONArray a = new JSONArray(s); for (int i = 0; i < a.length(); i++) out.add(a.getString(i)); }
        } catch (Exception ignored) {}
        return out;
    }

    public static synchronized void saveHwDone(String ukey, Set<String> ids) {
        try { write(hwFile(ukey), new JSONArray(ids).toString()); } catch (Exception ignored) {}
    }

    // ---------- kilépés / törlés ----------
    /** Kilépés: a tokenek és a letöltött adatok törlődnek, a beállítások megmaradnak (teszt fióknál azok is törlődnek). */
    public static synchronized void logout() {
        Account a = account;
        account = null;
        //noinspection ResultOfMethodCallIgnored
        new File(root, "account.json").delete();
        if (a == null) return;
        deleteTree(cacheDir(a.ukey));
        if (a.demo) {
            //noinspection ResultOfMethodCallIgnored
            prefsFile(a.ukey).delete();
            //noinspection ResultOfMethodCallIgnored
            hwFile(a.ukey).delete();
        }
    }

    /** Fiók és minden mentett adat törlése. */
    public static synchronized void deleteAll() {
        Account a = account;
        logout();
        if (a == null) return;
        //noinspection ResultOfMethodCallIgnored
        prefsFile(a.ukey).delete();
        //noinspection ResultOfMethodCallIgnored
        hwFile(a.ukey).delete();
    }

    // ---------- segédek ----------
    private static String read(File f) {
        try {
            return f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Atomi írás: ideiglenes fájlba, majd átnevezés, hogy egy félbeszakadt mentés ne rontsa el a régit. */
    private static void write(File f, String s) throws Exception {
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            os.write(s.getBytes(StandardCharsets.UTF_8));
            os.getFD().sync();
        }
        if (!tmp.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            if (!tmp.renameTo(f)) throw new java.io.IOException("nem sikerült menteni: " + f.getName());
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 12; i++) b.append(String.format("%02x", d[i]));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
