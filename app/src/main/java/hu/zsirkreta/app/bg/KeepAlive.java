package hu.zsirkreta.app.bg;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import hu.zsirkreta.app.data.Kreta;
import hu.zsirkreta.app.util.Async;

/**
 * A belépés életben tartása: naponta kétszer megújítja a KRÉTA-tokent, ha addig semmi más nem tette meg
 * (az app nem volt megnyitva, és a háttér-ellenőrzés sem fut, mert ki vannak kapcsolva az értesítések).
 * A KRÉTA megújító tokenje ugyanis néhány nap használat nélkül lejár, és akkor újra be kell lépni.
 * JobSchedulert használunk: itt nem számít a pontosság, csak hogy legyen hálózat, és újraindítás után is megmaradjon.
 */
public final class KeepAlive extends JobService {
    private static final int JOB_ID = 0x2b4e1;
    private static final long PERIOD_MS = 12 * 3600_000L;

    /** App indulásakor: ha még nincs ütemezve, ütemezi (a meglévőt nem írja felül, így az időzítése nem tolódik). */
    public static void ensure(Context c) {
        // az app indulásakor fut (App.onCreate): egy ütemezési hiba nem döntheti le az egész appot
        try {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js == null || js.getPendingJob(JOB_ID) != null) return;
            js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, KeepAlive.class))
                    .setPeriodic(PERIOD_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build());
        } catch (RuntimeException e) {
            android.util.Log.w("ZsirKreta", "keepalive ütemezés", e);
        }
    }

    @Override public boolean onStartJob(JobParameters params) {
        Async.bg(() -> {
            boolean done = true;
            try {
                done = Kreta.keepAlive();
            } catch (RuntimeException e) {
                android.util.Log.w("ZsirKreta", "keepalive", e);
            }
            // átmeneti hibánál a rendszer később (fokozatosan ritkítva) újrapróbálja
            jobFinished(params, !done);
        });
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
