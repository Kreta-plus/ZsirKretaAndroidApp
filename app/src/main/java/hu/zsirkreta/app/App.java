package hu.zsirkreta.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import hu.zsirkreta.app.bg.Alarms;
import hu.zsirkreta.app.bg.KeepAlive;
import hu.zsirkreta.app.bg.Notifs;
import hu.zsirkreta.app.data.Config;
import hu.zsirkreta.app.data.Store;
import hu.zsirkreta.app.data.Updater;
import hu.zsirkreta.app.ui.Ui;

public class App extends Application {
    private static int started = 0;

    /** Látszik-e épp az app (ilyenkor a háttér-ellenőrzés nem küld értesítést: az app maga mutatja az újat). */
    public static boolean inForeground() { return started > 0; }

    @Override public void onCreate() {
        super.onCreate();
        Ui.init(this);
        Config.init(this);
        Store.init(this);
        Updater.init(this);
        Notifs.createChannels(this);
        Alarms.ensure(this);
        KeepAlive.ensure(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) { started++; }
            @Override public void onActivityStopped(Activity a) { started = Math.max(0, started - 1); }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }
}
