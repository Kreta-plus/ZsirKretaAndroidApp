package hu.zsirkreta.app.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Hálózat és lemez a háttérszálon, az eredmény a főszálon. */
public final class Async {
    private Async() {}

    public interface Work<T> { T run() throws Exception; }
    public interface Done<T> { void ok(T value); }
    public interface Fail { void err(Exception e); }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static <T> void run(Work<T> work, Done<T> done, Fail fail) {
        POOL.execute(() -> {
            try {
                T v = work.run();
                MAIN.post(() -> guard(() -> { if (done != null) done.ok(v); }));
            } catch (Exception e) {
                MAIN.post(() -> guard(() -> { if (fail != null) fail.err(e); }));
            }
        });
    }

    /**
     * Egy lekérdezés eredményének feldolgozása közben fellépő hiba (pl. már elhagyott képernyő) ne ölje meg az egész alkalmazást:
     * naplózzuk, a felület a következő frissítéskor helyreáll.
     */
    private static void guard(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            android.util.Log.e("ZsirKreta", "Visszahívási hiba", e);
        }
    }

    public static void bg(Runnable r) { POOL.execute(r); }
    public static void ui(Runnable r) { MAIN.post(r); }
    public static void ui(Runnable r, long delayMs) { MAIN.postDelayed(r, delayMs); }
    public static Handler handler() { return MAIN; }
}
