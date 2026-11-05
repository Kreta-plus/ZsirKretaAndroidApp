package hu.zsirkreta.app.bg;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.R;

/**
 * Értesítések: csatornák (fajtánként, így az Android saját beállításaiban is külön kezelhetők), a felhasználó
 * beállításai (melyik fajta, esti emlékeztető ideje, háttér-lekérés gyakorisága) és a kiküldés.
 * A beállítások készülékhez kötöttek (nem a web és a telefon között cserélhető beállítás-exportban vannak).
 */
public final class Notifs {
    private Notifs() {}

    // fajták: kulcs, csatorna neve, leírás, hova nyit koppintásra
    public static final String GRADES = "grades", HOMEWORK = "homework", TESTS = "tests", TIMETABLE = "timetable",
            ABSENCES = "absences", NOTES = "notes", EVENING = "evening", UPDATE = "update", ACCOUNT = "account";
    public static final String[][] KINDS = {
            {GRADES, "Új jegy", "Ha új jegyet kapsz", "grades"},
            {HOMEWORK, "Új házi", "Új házi feladat, tantárggyal és határidővel", "homework"},
            {TESTS, "Bejelentett számonkérés", "Új dolgozat vagy felelés a KRÉTA-ban", "homework"},
            {TIMETABLE, "Órarend-változás", "Elmaradó óra vagy helyettesítés ma és holnap", "timetable"},
            {ABSENCES, "Hiányzás, késés", "Új hiányzás vagy késés, és ha valami igazolatlan maradt", "absences"},
            {NOTES, "Új feljegyzés", "Dicséret, figyelmeztetés, értesítés a tanároktól", "notes"},
            {EVENING, "Esti emlékeztető", "Holnapi dolgozatok, házik és a táska tartalma", "pack"},
            {UPDATE, "Új app-verzió", "Ha elérhető a ZsírKréta új verziója", "dashboard"},
    };

    /** A háttér-lekérés választható gyakorisága (perc) a fejlesztői beállításokban. */
    public static final int[] INTERVALS = {1, 2, 5, 10, 15, 30, 45, 60, 90, 120};
    public static final int DEFAULT_INTERVAL = 15;

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("notify", Context.MODE_PRIVATE); }

    // ---------- beállítások ----------
    public static boolean enabled(Context c) { return sp(c).getBoolean("enabled", true); }
    public static void setEnabled(Context c, boolean on) { sp(c).edit().putBoolean("enabled", on).apply(); }
    public static boolean kind(Context c, String k) { return sp(c).getBoolean("k_" + k, true); }
    public static void setKind(Context c, String k, boolean on) { sp(c).edit().putBoolean("k_" + k, on).apply(); }
    public static int intervalMin(Context c) { return Math.max(1, Math.min(120, sp(c).getInt("interval", DEFAULT_INTERVAL))); }
    public static void setIntervalMin(Context c, int m) { sp(c).edit().putInt("interval", m).apply(); }
    public static int eveningMinute(Context c) { return sp(c).getInt("evening", 19 * 60); }   // perc éjfél óta
    public static void setEveningMinute(Context c, int m) { sp(c).edit().putInt("evening", m).apply(); }
    public static long lastRun(Context c) { return sp(c).getLong("lastRun", 0); }
    public static String lastError(Context c) { return sp(c).getString("lastError", ""); }
    static void ran(Context c, String err) { sp(c).edit().putLong("lastRun", System.currentTimeMillis()).putString("lastError", err == null ? "" : err).apply(); }
    public static boolean setupDismissed(Context c) { return sp(c).getBoolean("setupDismissed", false); }
    public static void dismissSetup(Context c) { sp(c).edit().putBoolean("setupDismissed", true).apply(); }

    // ---------- engedélyek ----------
    public static boolean permitted(Context c) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        return NotificationManagerCompat.from(c).areNotificationsEnabled();
    }

    /** Kivétel az energiatakarékos korlátozás alól: e nélkül a telefon késleltetheti vagy kihagyhatja a háttér-lekérést. */
    public static boolean unrestricted(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    @android.annotation.SuppressLint("BatteryLife")
    public static Intent unrestrictIntent(Context c) {
        return new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.getPackageName()));
    }

    /** Pontos időzítés (Android 12-től „Ébresztők és emlékeztetők”); a USE_EXACT_ALARM miatt általában megvan. */
    public static boolean exactAllowed(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    public static Intent exactIntent(Context c) {
        return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + c.getPackageName()));
    }

    public static Intent channelSettingsIntent(Context c) {
        return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName());
    }

    /** Gyártók, amelyek az Android saját beállításán túl is leállítják a háttérben futó appokat. */
    public static String vendorHint() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) return "Xiaomi telefonon még: Beállítások → Alkalmazások → ZsírKréta → Automatikus indítás bekapcsolása, és Akkumulátorkímélő → Nincs korlátozás.";
        if (m.contains("huawei") || m.contains("honor")) return "Huawei/Honor telefonon még: Beállítások → Akkumulátor → Alkalmazásindítás → ZsírKréta → Kézi kezelés, mindhárom kapcsoló bekapcsolva.";
        if (m.contains("samsung")) return "Samsung telefonon még: Beállítások → Akkumulátor → Háttérhasználati korlátok: a ZsírKréta ne legyen az „alvó” vagy „mélyalvó” appok között.";
        if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus") || m.contains("vivo")) return "Ezen a telefonon még: Beállítások → Akkumulátor → ZsírKréta → Háttértevékenység engedélyezése (és az automatikus indítás).";
        return null;
    }

    // ---------- csatornák és kiküldés ----------
    public static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        for (String[] k : KINDS) {
            NotificationChannel ch = new NotificationChannel(k[0], k[1],
                    k[0].equals(UPDATE) || k[0].equals(EVENING) ? NotificationManager.IMPORTANCE_DEFAULT : NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription(k[2]);
            nm.createNotificationChannel(ch);
        }
        NotificationChannel acc = new NotificationChannel(ACCOUNT, "Fiók", NotificationManager.IMPORTANCE_DEFAULT);
        acc.setDescription("Ha újra be kell lépned a KRÉTA-ba");
        nm.createNotificationChannel(acc);
    }

    static String openFor(String kind) {
        for (String[] k : KINDS) if (k[0].equals(kind)) return k[3];
        return "dashboard";
    }

    private static int nextId(Context c) {
        SharedPreferences s = sp(c);
        int id = s.getInt("nextId", 2000) + 1;
        if (id > 1_000_000) id = 2001;
        s.edit().putInt("nextId", id).apply();
        return id;
    }

    /**
     * Egy értesítés. lines != null és több elem: összevont („3 új jegy”) értesítés a sorokkal (InboxStyle).
     * Koppintásra az app a megfelelő képernyőn nyílik meg.
     */
    static void post(Context c, String kind, String title, String text, List<String> lines) {
        if (!permitted(c)) return;
        Intent open = new Intent(c, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, openFor(kind))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, kind.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, kind)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFFFFE14D)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setGroup(GROUP)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(kind.equals(UPDATE) || kind.equals(EVENING) ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_HIGH);
        if (lines != null && lines.size() > 1) {
            NotificationCompat.InboxStyle in = new NotificationCompat.InboxStyle().setBigContentTitle(title);
            for (int i = 0; i < Math.min(7, lines.size()); i++) in.addLine(lines.get(i));
            if (lines.size() > 7) in.setSummaryText("+ még " + (lines.size() - 7));
            b.setStyle(in).setNumber(lines.size());
        } else if (text != null && text.length() > 40) {
            b.setStyle(new NotificationCompat.BigTextStyle().bigText(text));
        }
        try {
            NotificationManagerCompat nm = NotificationManagerCompat.from(c);
            nm.notify(nextId(c), b.build());
            // saját csoport-összegző: így az Android nem a saját (koppintásra csak az appot megnyitó) összegzőjébe teszi őket,
            // a csoport koppintásra kinyílik, és minden értesítés a saját képernyőjét nyitja
            Intent home = new Intent(c, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "dashboard")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            nm.notify(SUMMARY_ID, new NotificationCompat.Builder(c, kind)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(0xFFFFE14D)
                    .setContentTitle("ZsírKréta")
                    .setContentText("Új értesítések")
                    .setContentIntent(PendingIntent.getActivity(c, SUMMARY_ID, home, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                    .setGroup(GROUP)
                    .setGroupSummary(true)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                    .setAutoCancel(true)
                    .build());
        } catch (SecurityException ignored) {}   // az engedélyt időközben visszavonták
    }

    private static final String GROUP = "zsirkreta";
    private static final int SUMMARY_ID = 1;

    /** „Próba értesítés” a Beállításokból. */
    public static void test(Context c) {
        post(c, GRADES, "📘 Új jegy: Matematika 5 (témazáró, 200%)", "Így fog kinézni, ha új jegyet kapsz. Koppints rá, és a Jegyek nyílik meg.", null);
    }
}
