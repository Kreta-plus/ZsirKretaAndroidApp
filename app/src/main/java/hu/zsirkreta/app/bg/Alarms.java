package hu.zsirkreta.app.bg;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import hu.zsirkreta.app.data.Store;
import hu.zsirkreta.app.util.Async;

/**
 * Időzítés a háttér-ellenőrzéshez és az esti emlékeztetőhöz.
 *
 * Pontos ébresztőt használunk (setExactAndAllowWhileIdle), nem a JobSchedulert: az legfeljebb 15 percenként futtat, és
 * energiatakarékos (Doze) módban órákig is visszatarthatja a munkát. Az ébresztő Doze-ban is lefut; ha az app ki van véve
 * az akkumulátor-optimalizálás alól (a Beállítások ezt kéri), akkor hálózatot is kap, így nincs késés.
 * Minden kör a futás elején ütemezi a következőt, így a lánc akkor sem szakad meg, ha egy kör hibára fut.
 */
public final class Alarms {
    private Alarms() {}

    private static final String TICK = "hu.zsirkreta.app.TICK", EVENING = "hu.zsirkreta.app.EVENING";

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("alarms", Context.MODE_PRIVATE); }

    private static boolean active(Context c) { return Store.account() != null && Notifs.enabled(c); }

    private static PendingIntent pi(Context c, String action) {
        Intent i = new Intent(c, Tick.class).setAction(action);
        return PendingIntent.getBroadcast(c, action.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void at(Context c, String action, long when) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent p = pi(c, action);
        try {
            if (Notifs.exactAllowed(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p);
        }
        sp(c).edit().putLong(action, when).apply();
    }

    private static void scheduleTick(Context c, long delayMs) { at(c, TICK, System.currentTimeMillis() + delayMs); }

    private static void scheduleEvening(Context c) {
        int m = Notifs.eveningMinute(c);
        LocalDateTime when = LocalDate.now().atTime(m / 60, m % 60);
        if (!when.isAfter(LocalDateTime.now().plusSeconds(30))) when = when.plusDays(1);
        at(c, EVENING, when.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    public static long nextTick(Context c) { return sp(c).getLong(TICK, 0); }

    /**
     * Gondoskodik róla, hogy fusson a lánc (app indulásakor, belépés után, visszatéréskor). Ha az időzítés elmaradt
     * (pl. az appot leállították) vagy a beállított gyakoriságnál messzebb van, újraütemez.
     */
    public static void ensure(Context c) {
        if (!active(c)) { cancel(c); return; }
        long now = System.currentTimeMillis(), interval = Notifs.intervalMin(c) * 60_000L, next = nextTick(c);
        if (next == 0 || next < now - 60_000 || next > now + interval + 5_000) scheduleTick(c, Math.min(interval, 30_000));
        long ev = sp(c).getLong(EVENING, 0);
        if (ev == 0 || ev < now - 60_000 || ev > now + 25 * 3600_000L) scheduleEvening(c);
    }

    /** Beállítás változott (gyakoriság, esti idő, be/ki): azonnal az új szerint. */
    public static void reschedule(Context c) {
        cancel(c);
        if (!active(c)) return;
        scheduleTick(c, Math.min(Notifs.intervalMin(c) * 60_000L, 30_000));
        scheduleEvening(c);
    }

    public static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) { am.cancel(pi(c, TICK)); am.cancel(pi(c, EVENING)); }
        sp(c).edit().clear().apply();
    }

    /** Az ébresztők fogadója (nem exportált: csak az AlarmManager hívhatja, más app nem). */
    public static final class Tick extends BroadcastReceiver {
        @Override public void onReceive(Context ctx, Intent intent) {
            final Context c = ctx.getApplicationContext();
            final String action = intent.getAction();
            if (LessonReminder.ACTION.equals(action)) {
                // az óra előtti emlékeztető független a háttér-ellenőrzéstől (nem kérdezi a KRÉTA-t)
                final PendingResult pr = goAsync();
                Async.bg(() -> {
                    try { LessonReminder.fire(c); } finally { pr.finish(); }
                });
                return;
            }
            if (!active(c)) { cancel(c); return; }
            // előbb a következő időpont, hogy hiba esetén se szakadjon meg a lánc
            if (TICK.equals(action)) scheduleTick(c, Notifs.intervalMin(c) * 60_000L);
            else if (EVENING.equals(action)) scheduleEvening(c);
            else return;
            final PendingResult pr = goAsync();
            Async.bg(() -> {
                try {
                    if (TICK.equals(action)) Watcher.run(c); else Watcher.evening(c);
                } catch (RuntimeException e) {
                    Notifs.ran(c, e.toString());
                } finally {
                    pr.finish();
                }
            });
        }
    }

    /**
     * Újraindítás és app-frissítés után az ébresztők elvesznek; a telefon órájának átállításakor (kézzel, időzóna-váltás)
     * pedig rossz időpontra mutatnak. Itt állítjuk vissza őket.
     */
    public static final class Boot extends BroadcastReceiver {
        @Override public void onReceive(Context ctx, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a) || "android.intent.action.QUICKBOOT_POWERON".equals(a)
                    || Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
                final Context c = ctx.getApplicationContext();
                reschedule(c);
                final PendingResult pr = goAsync();
                Async.bg(() -> {
                    try { LessonReminder.reschedule(c); } finally { pr.finish(); }
                });
            }
        }
    }
}
