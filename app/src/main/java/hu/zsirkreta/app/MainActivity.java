package hu.zsirkreta.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import hu.zsirkreta.app.bg.Alarms;
import hu.zsirkreta.app.bg.Notifs;
import hu.zsirkreta.app.data.Kreta;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.data.Store;
import hu.zsirkreta.app.data.Updater;
import hu.zsirkreta.app.ui.AbsencesScreen;
import hu.zsirkreta.app.ui.DashboardScreen;
import hu.zsirkreta.app.ui.GradesScreen;
import hu.zsirkreta.app.ui.HomeworkScreen;
import hu.zsirkreta.app.ui.LoginScreen;
import hu.zsirkreta.app.ui.NamesScreen;
import hu.zsirkreta.app.ui.NotesScreen;
import hu.zsirkreta.app.ui.PackScreen;
import hu.zsirkreta.app.ui.PlannerScreen;
import hu.zsirkreta.app.ui.Screen;
import hu.zsirkreta.app.ui.SettingsScreen;
import hu.zsirkreta.app.ui.StatsScreen;
import hu.zsirkreta.app.ui.Theme;
import hu.zsirkreta.app.ui.TimetableScreen;
import hu.zsirkreta.app.ui.Ui;
import hu.zsirkreta.app.ui.WebLoginActivity;
import hu.zsirkreta.app.util.Async;
import hu.zsirkreta.app.util.Dt;

/**
 * Az egyetlen fő activity: felső sáv, tartalom (húzásra frissítés), alsó navigáció. A nézeteket
 * programból építjük, hogy a felhasználó témája (szín, lekerekítés, sűrűség, betűméret) futás közben váltható legyen.
 */
public class MainActivity extends AppCompatActivity {
    /** Értesítésből: melyik képernyő nyíljon meg (pl. „grades”). */
    public static final String EXTRA_OPEN = "open";
    public static final String[] KEYS = {"dashboard", "grades", "stats", "timetable", "homework", "absences", "planner", "pack", "notes", "names", "settings"};
    public static final String[] LABELS = {"Kezdőlap", "Jegyek", "Statisztika", "Órarend", "Házi", "Hiányzás", "Tervező", "Táska", "Feljegyzések", "Nevek", "Beállítások"};
    private static final int[] ICONS = {R.drawable.ic_home, R.drawable.ic_book, R.drawable.ic_chart, R.drawable.ic_calendar, R.drawable.ic_assignment,
            R.drawable.ic_clock, R.drawable.ic_playlist_add, R.drawable.ic_bag, R.drawable.ic_chat, R.drawable.ic_label, R.drawable.ic_settings};
    private static final String[] PRIMARY = {"dashboard", "grades", "timetable", "homework"};   // alsó sáv; a többi a „Továbbiak” lapon

    private FrameLayout frame;
    private LinearLayout shell;
    private SwipeRefreshLayout swipe;
    private ScrollView scroll;
    private Ui.MaxWidthLayout content;
    private LinearLayout bottomBar;
    private TextView titleView, freshView, avatarView;
    private ImageView syncIcon;
    private FrameLayout syncButton;
    private ObjectAnimator spin;

    private String current = "dashboard";
    private Screen screen;
    private boolean inApp = false, refreshing = false, bootLoading = false, resumed = false, pendingRender = false;
    // frissítés: percenként automatikusan, a gombbal kézzel is, de a kettő együtt is legfeljebb percenként
    private long nextAt = 0, autoAt = 0;        // mikortól lehet újra kényszeríteni / mikor jön az automatikus frissítés (ms)
    private String note = "", lastErrs = "";
    private boolean installAfterPermission = false;   // az „ismeretlen forrás” engedély után magától indul a telepítés
    private ActivityResultLauncher<Intent> webLogin;
    private WebLoginHandler webLoginHandler;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;
    private String pendingExport;
    private java.util.function.Consumer<String> importCb;
    private ActivityResultLauncher<String> notifPermission;
    private String pendingOpen;
    private boolean setupReturn = false;   // az értesítés-beállításból visszatérve frissítjük az állapotot
    private String drawnPermSig = "";      // az engedélyek állapota a legutóbbi rajzoláskor

    public interface WebLoginHandler { void onResult(String code, String verifier, String error); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        webLogin = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            WebLoginHandler h = webLoginHandler;
            webLoginHandler = null;
            if (h == null) return;
            Intent d = r.getData();
            if (r.getResultCode() == RESULT_OK && d != null) h.onResult(d.getStringExtra("code"), d.getStringExtra("verifier"), null);
            else h.onResult(null, null, d != null ? d.getStringExtra("error") : null);
        });
        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            String text = pendingExport;
            pendingExport = null;
            if (uri == null || text == null) return;
            try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                os.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                toast("Exportálva");
            } catch (Exception e) { toast("Az exportálás nem sikerült: " + e.getMessage()); }
        });
        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            java.util.function.Consumer<String> cb = importCb;
            importCb = null;
            if (uri == null || cb == null) return;
            try (java.io.InputStream is = getContentResolver().openInputStream(uri); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) { out.write(buf, 0, n); if (out.size() > 2_000_000) throw new java.io.IOException("túl nagy fájl"); }
                cb.accept(out.toString("UTF-8"));
            } catch (Exception e) { toast("Érvénytelen fájl"); }
        });
        notifPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted && !Notifs.unrestricted(this)) requestUnrestricted();
            else if (!granted && Build.VERSION.SDK_INT >= 33 && !shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                // véglegesen elutasítva: a rendszer már nem kérdez, a beállítások oldalán lehet engedélyezni
                setupReturn = true;
                try { startActivity(Notifs.channelSettingsIntent(this)); } catch (Exception e) { toast("Beállítások → Alkalmazások → ZsírKréta → Értesítések"); }
            } else if (!granted) toast("Értesítések nélkül nem tudunk szólni az új jegyekről. Később a Beállításokban bekapcsolhatod.");
            Alarms.reschedule(this);
            rerender();
        });
        pendingOpen = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_OPEN);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (inApp && !current.equals(St.prefs.startView)) show(St.prefs.startView);
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
            }
        });
        Store.Account a = Store.account();
        if (a == null) showLogin(null);
        else enterApp();
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (inApp) { if (St.prefs.theme.equals("auto")) restyle(); else rerender(); }
        else showLogin(null);
    }

    @Override protected void onPause() {
        super.onPause();
        St.flushNow();
        resumed = false;
        Async.handler().removeCallbacks(autoTick);
        Async.handler().removeCallbacks(statusTick);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        // az állapotsor-időzítő a belépő képernyőn is fut (maga figyeli, belépett-e), így belépés után is frissül
        Async.handler().removeCallbacks(statusTick);
        Async.handler().post(statusTick);
        if (installAfterPermission && Updater.state() == Updater.State.READY && Updater.canInstall(this)) {
            installAfterPermission = false;
            installUpdate();
        }
        Updater.checkIfDue(this);
        if (!inApp) return;
        Alarms.ensure(this);
        // az engedély-ablakokból visszatérve: ha változott valamelyik engedély, frissül a kártya
        if (setupReturn || !permSig().equals(drawnPermSig)) { setupReturn = false; rerender(); }
        // háttérben nem kérdezünk; visszatéréskor pótoljuk, ha már esedékes
        if (autoAt > 0 && System.currentTimeMillis() >= autoAt) refresh(true, true);
        else if (autoAt > 0) Async.ui(autoTick, autoAt - System.currentTimeMillis());
    }

    private boolean systemDark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    // ---------- belépés ----------
    public void showLogin(String message) {
        inApp = false;
        stopAuto();
        screen = null;
        St.reset();
        Theme.apply(St.prefs, systemDark());
        paintWindow();
        FrameLayout root = newFrame();
        root.addView(new LoginScreen(this, message, this::enterApp).build(), new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        setContentView(root);
        ViewCompat.requestApplyInsets(root);
    }

    /** Szöveg mentése a felhasználó által választott fájlba (beállítások exportja). */
    public void exportText(String filename, String text) {
        pendingExport = text;
        exportLauncher.launch(filename);
    }

    /** Fájl kiválasztása és tartalmának átadása (beállítások importja). */
    public void importText(java.util.function.Consumer<String> cb) {
        importCb = cb;
        importLauncher.launch(new String[]{"application/json", "text/plain", "*/*"});
    }

    public void launchWebLogin(String authUrl, String verifier, WebLoginHandler h) {
        webLoginHandler = h;
        Intent i = new Intent(this, WebLoginActivity.class);
        i.putExtra("url", authUrl);
        i.putExtra("verifier", verifier);
        webLogin.launch(i);
    }

    // ---------- az alkalmazás ----------
    public void enterApp() {
        Store.Account a = Store.account();
        if (a == null) { showLogin(null); return; }
        St.reset();
        St.prefs = Store.loadPrefs(a.ukey);
        inApp = true;
        current = pendingOpen != null && indexOf(pendingOpen) >= 0 ? pendingOpen : St.prefs.startView;
        pendingOpen = null;
        Alarms.ensure(this);
        buildShell();
        bootLoading = true;
        show(current);
        nextAt = 0; autoAt = 0; note = ""; lastErrs = "";
        Async.run(St::cachedBundle, cached -> {
            if (!inApp) return;
            St.applyBundle(cached, false);
            if (!St.raw.isEmpty()) bootLoading = false;
            rerender();
            refresh(true, true);
        }, e -> refresh(true, true));
    }

    public Screen currentScreen() { return screen; }
    public boolean isLoading() { return bootLoading && St.raw.isEmpty(); }

    private FrameLayout newFrame() {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(bgDrawable());
        ViewCompat.setOnApplyWindowInsetsListener(f, (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(sb.left, sb.top, sb.right, Math.max(sb.bottom, ime.bottom));
            if (bottomBar != null) bottomBar.setVisibility(ime.bottom > Ui.dp(120) ? View.GONE : View.VISIBLE);
            return WindowInsetsCompat.CONSUMED;
        });
        return f;
    }

    /** Háttér a Beállítások → Háttér szerint: egyszínű, ragyogás, pöttyös vagy kockás füzet (mint a weben). */
    private android.graphics.drawable.Drawable bgDrawable() { return Ui.background(); }

    private void paintWindow() {
        getWindow().getDecorView().setBackgroundColor(Theme.bg);
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(!Theme.dark);
        c.setAppearanceLightNavigationBars(!Theme.dark);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
    }

    private boolean restyleQueued = false;

    /**
     * Téma változott (szín, lekerekítés, betűméret …): az egész felület újraépül.
     * Sosem szinkron: a hívó (pl. egy csúszka vagy gomb) még a saját érintés-/kattintáskezelésében van, és a nézetfa
     * közbeni lecserélése összeomlást okozott. Egymás utáni kérések egyetlen újraépítéssé olvadnak.
     */
    public void restyle() {
        if (!inApp || restyleQueued) return;
        restyleQueued = true;
        Async.ui(() -> {
            restyleQueued = false;
            if (inApp) restyleNow();
        });
    }

    private void restyleNow() {
        int y = scroll != null ? scroll.getScrollY() : 0;
        buildShell();
        show(current, false);
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void buildShell() {
        Theme.apply(St.prefs, systemDark());
        paintWindow();
        frame = newFrame();
        shell = Ui.vbox(this, 0);

        // felső sáv
        LinearLayout top = Ui.hbox(this, 12);
        top.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(8), Ui.dp(4));
        LinearLayout titles = Ui.vbox(this, 2);
        titleView = Ui.text(this, "", 22, Theme.text, true);
        freshView = Ui.small(this, "");
        titles.addView(titleView, Ui.lp(Ui.WRAP, Ui.WRAP));
        titles.addView(freshView, Ui.lp(Ui.WRAP, Ui.WRAP));
        top.addView(titles, Ui.grow());
        syncButton = Ui.iconButton(this, R.drawable.ic_refresh, Theme.muted, () -> refresh(true, false));
        syncIcon = (ImageView) syncButton.getChildAt(0);
        top.addView(syncButton);
        avatarView = Ui.text(this, St.prefs.emoji, 18, Theme.text, false);
        avatarView.setGravity(Gravity.CENTER);
        avatarView.setBackground(Ui.ripple(Ui.shape(Theme.accentSoft, 14, Theme.border, 1), 14));
        avatarView.setOnClickListener(v -> show("settings"));
        top.addView(avatarView, Ui.lp(Ui.dp(40), Ui.dp(40)));
        shell.addView(top, Ui.lp(Ui.MATCH, Ui.WRAP));

        // tartalom
        swipe = new SwipeRefreshLayout(this);
        swipe.setColorSchemeColors(Theme.accent);
        swipe.setProgressBackgroundColorSchemeColor(Theme.surface);
        swipe.setOnRefreshListener(() -> refresh(true, false));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content = new Ui.MaxWidthLayout(this, 680);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setDividerDrawable(Ui.gap(Theme.gap, true));
        content.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        content.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(24));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.CENTER_HORIZONTAL);
        FrameLayout holder = new FrameLayout(this);
        holder.addView(content, clp);
        scroll.addView(holder, new ScrollView.LayoutParams(Ui.MATCH, Ui.WRAP));
        swipe.addView(scroll, new SwipeRefreshLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        shell.addView(swipe, Ui.lp(Ui.MATCH, 0, 1f));

        // alsó sáv
        bottomBar = Ui.hbox(this, 0);
        bottomBar.setBackgroundColor(Theme.withAlpha(Theme.surface, 0.97f));
        bottomBar.setPadding(Ui.dp(4), Ui.dp(6), Ui.dp(4), Ui.dp(6));
        View line = Ui.divider(this);
        shell.addView(line);
        shell.addView(bottomBar, Ui.lp(Ui.MATCH, Ui.WRAP));
        drawBottomBar();

        frame.addView(shell, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        setContentView(frame);
        ViewCompat.requestApplyInsets(frame);
    }

    private int indexOf(String key) { return Arrays.asList(KEYS).indexOf(key); }

    private void drawBottomBar() {
        bottomBar.removeAllViews();
        List<String> primary = new ArrayList<>(Arrays.asList(PRIMARY));
        for (String k : primary) bottomBar.addView(navItem(ICONS[indexOf(k)], LABELS[indexOf(k)], k.equals(current), () -> show(k)), Ui.lp(0, Ui.WRAP, 1f));
        boolean moreOn = !primary.contains(current);
        bottomBar.addView(navItem(R.drawable.ic_more, "Továbbiak", moreOn, this::openMore), Ui.lp(0, Ui.WRAP, 1f));
    }

    private View navItem(int icon, String label, boolean on, Runnable r) {
        LinearLayout l = Ui.vbox(this, 3);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(0, Ui.dp(2), 0, Ui.dp(2));
        FrameLayout pill = new FrameLayout(this);
        pill.setBackground(on ? Ui.shape(Theme.accentSoft, 16) : null);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(on ? Theme.accent : Theme.muted);
        pill.addView(iv, new FrameLayout.LayoutParams(Ui.dp(24), Ui.dp(24), Gravity.CENTER));
        l.addView(pill, Ui.lp(Ui.dp(56), Ui.dp(32)));
        TextView t = Ui.text(this, label, 11, on ? Theme.text : Theme.muted, on);
        t.setSingleLine(true);
        l.addView(t, Ui.lp(Ui.WRAP, Ui.WRAP));
        l.setBackground(Ui.ripple(Ui.shape(Color.TRANSPARENT, 14), 14));
        l.setOnClickListener(v -> r.run());
        return l;
    }

    private void openMore() {
        Ui.Sheet s = Ui.sheet(this, "Továbbiak");
        LinearLayout grid = Ui.vbox(this, 10);
        String[] more = {"stats", "absences", "planner", "pack", "notes", "names", "settings"};
        for (int i = 0; i < more.length; i += 2) {
            LinearLayout row = Ui.hbox(this, 10);
            for (int j = i; j < Math.min(i + 2, more.length); j++) {
                final String k = more[j];
                int idx = indexOf(k);
                LinearLayout tile = Ui.hbox(this, 12);
                boolean on = k.equals(current);
                tile.setBackground(Ui.ripple(Ui.shape(on ? Theme.accentSoft : Theme.surface2, 14, Theme.border, 1), 14));
                tile.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));
                tile.addView(Ui.icon(this, ICONS[idx], on ? Theme.accent : Theme.muted, 24));
                tile.addView(Ui.text(this, LABELS[idx], 15, Theme.text, true), Ui.grow());
                tile.setOnClickListener(v -> { s.dismiss(); show(k); });
                row.addView(tile, Ui.lp(0, Ui.WRAP, 1f));
            }
            if (row.getChildCount() == 1) row.addView(new View(this), Ui.lp(0, 1, 1f));
            grid.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        s.body.addView(grid, Ui.lp(Ui.MATCH, Ui.WRAP));
        s.show();
    }

    // ---------- navigáció és megjelenítés ----------
    public void show(String key) { show(key, true); }

    private Screen make(String key) {
        switch (key) {
            case "grades": return new GradesScreen(this);
            case "stats": return new StatsScreen(this);
            case "timetable": return new TimetableScreen(this);
            case "homework": return new HomeworkScreen(this);
            case "absences": return new AbsencesScreen(this);
            case "planner": return new PlannerScreen(this);
            case "pack": return new PackScreen(this);
            case "notes": return new NotesScreen(this);
            case "names": return new NamesScreen(this);
            case "settings": return new SettingsScreen(this);
            default: return new DashboardScreen(this);
        }
    }

    private void show(String key, boolean animate) {
        if (indexOf(key) < 0) key = "dashboard";
        boolean changed = !key.equals(current) || screen == null;
        current = key;
        screen = make(key);
        drawBottomBar();
        titleView.setText(key.equals("dashboard") ? "ZsírKréta" : LABELS[indexOf(key)]);
        draw();
        updateFresh();
        if (changed) scroll.scrollTo(0, 0);
        if (animate && changed) {
            content.setAlpha(0f);
            content.setTranslationY(Ui.dp(10));
            content.animate().alpha(1f).translationY(0).setDuration(180).start();
        }
    }

    private String permSig() { return Notifs.permitted(this) + "," + Notifs.unrestricted(this) + "," + Notifs.exactAllowed(this); }

    private void draw() {
        drawnPermSig = permSig();
        content.removeAllViews();
        if (isLoading()) {
            for (int i = 0; i < 3; i++) content.addView(skeleton(), Ui.lp(Ui.MATCH, Ui.dp(96)));
            return;
        }
        try {
            screen.render(content);
        } catch (RuntimeException e) {
            content.removeAllViews();
            LinearLayout card = Ui.card(this);
            card.addView(Ui.muted(this, "Hiba a megjelenítésben: " + e));
            content.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
            android.util.Log.e("ZsirKreta", "render", e);
        }
    }

    private View skeleton() {
        View v = new View(this);
        v.setBackground(Ui.shape(Theme.surface, Theme.radius));
        ObjectAnimator a = ObjectAnimator.ofFloat(v, View.ALPHA, 0.45f, 1f);
        a.setDuration(800);
        a.setRepeatMode(ObjectAnimator.REVERSE);
        a.setRepeatCount(ObjectAnimator.INFINITE);
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View x) { a.start(); }
            @Override public void onViewDetachedFromWindow(View x) { a.cancel(); }
        });
        return v;
    }

    /** Újrarajzolás a görgetési pozíció megtartásával. */
    public void rerender() {
        if (!inApp || screen == null) return;
        pendingRender = false;
        int y = scroll.getScrollY();
        draw();
        updateFresh();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private static long secs(long at) { return Math.max(1, (at - System.currentTimeMillis() + 999) / 1000); }

    /** Állapotsor a cím alatt: „Frissítés…”, vagy „frissítve 2 perce · újra 42 mp múlva”, vagy a KRÉTA állapota. */
    private void updateFresh() {
        if (freshView == null) return;
        long now = System.currentTimeMillis();
        String t;
        if (refreshing) t = "Frissítés…";
        else {
            Double f = St.freshest();
            String base = !note.isEmpty() ? note : f != null ? "frissítve " + Dt.ago(f) : "";
            String next = autoAt > now ? "újra " + secs(autoAt) + " mp múlva" : "";
            t = base.isEmpty() ? next : next.isEmpty() ? base : base + " · " + next;
        }
        freshView.setText(t);
        freshView.setVisibility(t.isEmpty() ? View.GONE : View.VISIBLE);
        avatarView.setText(St.prefs.emoji);
        if (syncButton != null) syncButton.setAlpha(refreshing || now < nextAt ? 0.45f : 1f);
    }

    /** Másodpercenként: állapotsor, és a gépelés miatt elhalasztott újrarajzolás pótlása. */
    private final Runnable statusTick = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            if (inApp) {
                updateFresh();
                if (pendingRender && !typing()) rerender();
            }
            Async.ui(this, 1000);
        }
    };

    private final Runnable autoTick = () -> { if (inApp && resumed) refresh(true, true); };

    private void scheduleAuto(St.Bundle b) {
        Async.handler().removeCallbacks(autoTick);
        long ms = Math.max(hu.zsirkreta.app.data.Config.AUTO_REFRESH_MS, Math.max(b == null ? 0 : b.waitSec, b == null ? 0 : b.cooldownSec) * 1000)
                + 500 + (long) (Math.random() * 1500);
        autoAt = System.currentTimeMillis() + ms;
        if (resumed) Async.ui(autoTick, ms);
    }

    /** Épp ír valamit a felhasználó? Akkor nem rajzolunk alá új adatot. */
    private boolean typing() {
        View f = getCurrentFocus();
        return f instanceof android.widget.EditText && content != null && isInside(f, content);
    }

    private static boolean isInside(View v, View root) {
        for (android.view.ViewParent p = v.getParent(); p != null; p = p.getParent()) if (p == root) return true;
        return false;
    }

    public void toast(String msg) { Ui.toast(this, msg); }

    private static String names(java.util.Collection<String> keys) {
        List<String> out = new ArrayList<>();
        for (String k : keys) out.add(St.LABELS.containsKey(k) ? St.LABELS.get(k) : k);
        return String.join(", ", out);
    }

    // ---------- adatok frissítése ----------
    /** Kézi frissítés (gomb, lehúzás) – a képernyők is ezt hívják. */
    public void refresh(boolean force) { refresh(force, false); }

    /**
     * force: friss adat a KRÉTA-ból (legfeljebb percenként); auto: automatikus (csendes, csak új adatnál szól).
     * Új adatnál az aktuális képernyő a görgetési pozíció megtartásával rajzolódik újra.
     */
    public void refresh(boolean force, boolean auto) {
        if (refreshing || !inApp) { if (swipe != null) swipe.setRefreshing(false); return; }
        if (force && System.currentTimeMillis() < nextAt) {
            if (swipe != null) swipe.setRefreshing(false);
            if (!auto) toast("Túl gyakran frissítenél, " + secs(nextAt) + " mp múlva lehet újra");
            return;
        }
        refreshing = true;
        setSpinning(true);
        updateFresh();
        Async.run(() -> St.fetchBundle(force), b -> {
            refreshing = false;
            setSpinning(false);
            if (swipe != null) swipe.setRefreshing(false);
            if (!inApp) return;
            if (b.relogin != null) {
                stopAuto();
                showLogin("Lejárt a belépés, jelentkezz be újra");
                return;
            }
            boolean wasLoading = bootLoading;
            bootLoading = false;
            List<String> changed = St.applyBundle(b, true);
            note = b.cooldownSec > 0 ? "a KRÉTA lassít, kicsit várunk" : b.stale ? "a KRÉTA nem elérhető, régebbi adat" : "";
            nextAt = System.currentTimeMillis() + b.waitSec * 1000;
            String errKey = String.join(",", St.errors.keySet());
            if (!St.errors.isEmpty() && (!auto || !errKey.equals(lastErrs))) toast("Nem minden adat töltődött be (" + names(St.errors.keySet()) + ")");
            lastErrs = errKey;
            if (!changed.isEmpty()) toast("Frissítve, új adat: " + names(changed));
            else if (!auto && b.throttled) toast("Túl gyakran frissítenél, " + secs(nextAt) + " mp múlva lehet újra");
            else if (!auto && force && St.errors.isEmpty()) toast("Frissítve, nincs új adat");
            if (b.throttled || changed.contains("orarend")) St.weekCache.keySet().retainAll(java.util.Collections.singleton(Dt.iso(Dt.mondayOf(Dt.today()))));
            scheduleAuto(b);
            if (wasLoading || !changed.isEmpty()) { if (typing()) pendingRender = true; else rerender(); }
            updateFresh();
        }, e -> {
            refreshing = false;
            bootLoading = false;
            setSpinning(false);
            if (swipe != null) swipe.setRefreshing(false);
            note = "a frissítés nem sikerült";
            scheduleAuto(null);
            rerender();
            if (!auto) toast(e.getMessage() == null ? "Hiba a frissítéskor" : e.getMessage());
        });
    }

    private void stopAuto() {
        Async.handler().removeCallbacks(autoTick);
        autoAt = 0;
    }

    private void setSpinning(boolean on) {
        if (syncIcon == null) return;
        if (on) {
            spin = ObjectAnimator.ofFloat(syncIcon, View.ROTATION, 0f, 360f);
            spin.setDuration(900);
            spin.setInterpolator(new LinearInterpolator());
            spin.setRepeatCount(ObjectAnimator.INFINITE);
            spin.start();
        } else if (spin != null) {
            spin.cancel();
            syncIcon.setRotation(0);
        }
    }

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String v = intent.getStringExtra(EXTRA_OPEN);
        if (v == null) return;
        if (inApp) show(v); else pendingOpen = v;
    }

    // ---------- értesítések bekapcsolása ----------
    /**
     * A kezdőlapi kártya és a Beállítások „Bekapcsolás” gombja: értesítési engedély (Android 13-tól), majd az
     * akkumulátor-optimalizálás kikapcsolása, hogy a háttér-ellenőrzés késés nélkül fusson.
     */
    public void setupNotifications() {
        Notifs.setEnabled(this, true);
        Alarms.reschedule(this);
        if (!Notifs.permitted(this)) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // az engedély megvan, de az értesítések ki vannak kapcsolva az Android beállításaiban
                setupReturn = true;
                try { startActivity(Notifs.channelSettingsIntent(this)); } catch (Exception e) { toast("Beállítások → Alkalmazások → ZsírKréta → Értesítések"); }
            }
            return;
        }
        if (!Notifs.unrestricted(this)) { requestUnrestricted(); return; }
        if (!Notifs.exactAllowed(this)) { setupReturn = true; try { startActivity(Notifs.exactIntent(this)); } catch (Exception ignored) {} return; }
        rerender();
    }

    /** „Futhat a háttérben korlátozás nélkül?” rendszerablak (akkumulátor-optimalizálás kikapcsolása). */
    public void requestUnrestricted() {
        setupReturn = true;
        try {
            startActivity(Notifs.unrestrictIntent(this));
        } catch (Exception e) {
            try { startActivity(new android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
            catch (Exception e2) { toast("Beállítások → Akkumulátor → ZsírKréta → Korlátozás nélkül"); }
        }
    }

    /** A letöltött frissítés átadása az Android telepítőjének; ha még nincs engedély, előbb azt kérjük. */
    public void installUpdate() {
        if (!Updater.canInstall(this)) {
            installAfterPermission = true;
            try { startActivity(Updater.permissionIntent(this)); }
            catch (Exception e) { toast("Nyisd meg: Beállítások → Alkalmazások → ZsírKréta → Ismeretlen alkalmazások telepítése"); }
            return;
        }
        android.content.Intent i = Updater.installIntent(this);
        if (i == null) { toast("A letöltött frissítés eltűnt, töltsd le újra"); Updater.check(this, true); return; }
        try { startActivity(i); } catch (Exception e) { toast("Nem sikerült elindítani a telepítőt: " + e.getMessage()); }
    }

    /** Kilépés (a beállításokból): vissza a belépő képernyőre. */
    public void logout(boolean deleteData) {
        St.flushNow();
        stopAuto();
        Alarms.cancel(this);
        if (deleteData) Store.deleteAll(); else Store.logout();
        showLogin(null);
    }
}
