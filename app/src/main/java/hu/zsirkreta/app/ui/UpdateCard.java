package hu.zsirkreta.app.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.R;
import hu.zsirkreta.app.data.Updater;
import hu.zsirkreta.app.data.Updater.Info;
import hu.zsirkreta.app.data.Updater.State;
import hu.zsirkreta.app.util.Dt;

/**
 * A frissítés kártyája. A kezdőlapon csak akkor látszik, ha van új verzió (home = true); a Beállításokban mindig ott van,
 * a verzióval, az utolsó ellenőrzéssel és a „Frissítés keresése” gombbal. Az Updater állapotváltozásaira csak a saját
 * tartalmát rajzolja újra (letöltés közben gyakran), a képernyő többi része nem villog.
 */
public final class UpdateCard extends FrameLayout {
    private final MainActivity act;
    private final boolean home;
    private final Runnable onChange = this::draw;
    private static final int MAX_NOTES = 3;

    public UpdateCard(MainActivity act, boolean home) {
        super(act);
        this.act = act;
        this.home = home;
        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { Updater.listen(onChange); draw(); }
            @Override public void onViewDetachedFromWindow(View v) { Updater.unlisten(onChange); }
        });
        draw();
    }

    private static String day(String iso) {
        java.time.LocalDate d = Dt.parseDate(iso);
        return d == null ? iso : Dt.fmtFull(d);
    }

    private static String mb(long bytes) { return String.format(Locale.US, "%.1f", bytes / 1048576.0).replace('.', ',') + " MB"; }

    private void draw() {
        removeAllViews();
        State s = Updater.state();
        Info i = Updater.info();
        if (home && !Updater.showCard()) { setVisibility(GONE); return; }
        setVisibility(VISIBLE);
        LinearLayout card = Ui.card(act);
        if (i != null && s != State.UP_TO_DATE && s != State.CHECKING) {
            // kiemelő színű keret: ezt nem szabad átsiklani
            card.setBackground(Ui.shape(Theme.withAlpha(Theme.surface, Math.max(0.85f, Theme.cardAlpha)), Theme.radius, Theme.accent, 2));
        }
        addView(card, new LayoutParams(Ui.MATCH, Ui.WRAP));
        if (!home) {
            card.addView(Ui.h3(act, "Alkalmazás és frissítések"), Ui.lp(Ui.MATCH, Ui.WRAP));
            card.addView(appRow(s), Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        if (i == null || s == State.UP_TO_DATE || s == State.CHECKING || (s == State.ERROR && i == null)) {
            if (s == State.ERROR) card.addView(errorBox(), Ui.lp(Ui.MATCH, Ui.WRAP));
            if (!home) settingsFoot(card, s);
            return;
        }

        // fejléc: ikon, cím, alcím, (kezdőlapon) „Később”
        LinearLayout head = Ui.hbox(act, 12);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badge = new FrameLayout(act);
        badge.setBackground(Ui.shape(Theme.accent, 14));
        ImageView ic = Ui.icon(act, R.drawable.ic_download, Theme.onAccent, 24);
        badge.addView(ic, new LayoutParams(Ui.dp(24), Ui.dp(24), Gravity.CENTER));
        head.addView(badge, Ui.lp(Ui.dp(46), Ui.dp(46)));
        String title, sub;
        switch (s) {
            case DOWNLOADING: {
                long t = Updater.total(), d = Updater.done();
                title = "Letöltés… " + (t > 0 ? Math.min(100, d * 100 / t) + "%" : "");
                sub = mb(d) + (t > 0 ? " / " + mb(t) : "");
                break;
            }
            case READY:
                title = "Kész a " + i.versionName + " telepítésre";
                sub = "Az Android megkérdezi, frissítheti-e az appot. A beállításaid és a belépésed megmaradnak.";
                break;
            case ERROR:
                title = "Nem sikerült a frissítés";
                sub = "A " + i.versionName + " verzió letöltése vagy ellenőrzése elakadt.";
                break;
            default:
                title = "Új verzió: " + i.versionName;
                sub = "Most: " + Updater.currentName(act) + (i.size > 0 ? " · " + mb(i.size) : "") + (i.date.isEmpty() ? "" : " · " + day(i.date));
        }
        LinearLayout col = Ui.vbox(act, 3);
        col.addView(Ui.h3(act, title), Ui.lp(Ui.MATCH, Ui.WRAP));
        col.addView(Ui.small(act, sub), Ui.lp(Ui.MATCH, Ui.WRAP));
        head.addView(col, Ui.grow());
        if (home && s == State.AVAILABLE) head.addView(Ui.iconButton(act, R.drawable.ic_close, Theme.muted, Updater::snooze));
        card.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));

        if (s == State.DOWNLOADING) {
            long t = Updater.total();
            card.addView(Ui.bar(act, t > 0 ? Updater.done() / (float) t : 0.05f, Theme.accent));
            card.addView(Ui.smallButton(act, "Mégse", Ui.B_DEFAULT, Updater::cancelDownload), Ui.lp(Ui.WRAP, Ui.WRAP));
            return;
        }
        if (s == State.ERROR) card.addView(errorBox(), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (s == State.AVAILABLE || s == State.ERROR) notes(card, i);
        if (s == State.READY && !Updater.canInstall(act)) {
            card.addView(Ui.small(act, "Először engedélyezd, hogy a ZsírKréta frissítést telepíthessen: a gomb megnyitja a beállítást, és amikor visszajössz, a telepítés magától elindul."), Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        LinearLayout row = Ui.hbox(act, 10);
        switch (s) {
            case READY:
                row.addView(Ui.button(act, Updater.canInstall(act) ? "Telepítés" : "Engedélyezés és telepítés", Ui.B_PRIMARY, act::installUpdate), Ui.grow());
                break;
            case ERROR:
                row.addView(Ui.button(act, "Újra", Ui.B_PRIMARY, () -> Updater.download(act)), Ui.grow());
                break;
            default:
                row.addView(Ui.button(act, "Frissítés", Ui.B_PRIMARY, () -> Updater.download(act)), Ui.grow());
        }
        if (i.changes.size() > MAX_NOTES || (home && !i.changes.isEmpty() && s == State.READY)) row.addView(Ui.button(act, "Mi újság?", Ui.B_DEFAULT, () -> openNotes(i)), Ui.grow());
        card.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        if (!home) settingsFoot(card, s);
    }

    /** Beállítások: a telepített verzió és az ellenőrzés állapota. */
    private View appRow(State s) {
        LinearLayout it = Ui.item(act);
        ImageView logo = new ImageView(act);
        logo.setImageResource(R.drawable.ic_logo);
        it.addView(logo, Ui.lp(Ui.dp(40), Ui.dp(40)));
        LinearLayout col = Ui.vbox(act, 2);
        col.addView(Ui.bold(act, "ZsírKréta " + Updater.currentName(act)), Ui.lp(Ui.MATCH, Ui.WRAP));
        long last = Updater.lastCheck();
        String when = last > 0 ? "ellenőrizve " + Dt.ago(last / 1000.0) : "még nem kerestünk frissítést";
        String st;
        switch (s) {
            case CHECKING: st = "Frissítés keresése…"; break;
            case UP_TO_DATE: st = "Naprakész · " + when; break;
            case AVAILABLE: case READY: case DOWNLOADING: st = "Van új verzió · " + when; break;
            default: st = when.substring(0, 1).toUpperCase() + when.substring(1);
        }
        col.addView(Ui.small(act, st), Ui.lp(Ui.MATCH, Ui.WRAP));
        it.addView(col, Ui.grow());
        if (s == State.UP_TO_DATE) it.addView(Ui.chip(act, "naprakész", Ui.C_OK));
        return it;
    }

    private void settingsFoot(LinearLayout card, State s) {
        boolean busy = s == State.CHECKING || s == State.DOWNLOADING;
        TextView b = Ui.button(act, s == State.CHECKING ? "Keresés…" : "Frissítés keresése", Ui.B_DEFAULT, () -> Updater.check(act, true));
        b.setEnabled(!busy);
        b.setAlpha(busy ? 0.55f : 1f);
        card.addView(b, Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(Ui.switchRow(act, "Frissítések automatikus keresése (indításkor, legfeljebb 6 óránként)", Updater.autoCheck(), Updater::setAutoCheck), Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(Ui.small(act, "A frissítések a ZsírKréta GitHub-oldaláról jönnek. Telepítés előtt az app ellenőrzi, hogy a letöltött fájl sértetlen, és ugyanattól a kiadótól származik."), Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    private View errorBox() {
        LinearLayout box = Ui.vbox(act, 4);
        box.setBackground(Ui.shape(Theme.withAlpha(Theme.g[1], 0.14f), 12, Theme.withAlpha(Theme.g[1], 0.5f), 1));
        box.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        box.addView(Ui.text(act, Updater.error(), 13, Theme.g[1], true), Ui.lp(Ui.MATCH, Ui.WRAP));
        return box;
    }

    /** Az újdonságok első pár pontja (verziónként csoportosítva, ha több verzió maradt ki). */
    private void notes(LinearLayout card, Info i) {
        if (i.changes.isEmpty()) return;
        LinearLayout list = Ui.vbox(act, 6);
        boolean multi = !i.changes.get(0)[0].equals(i.changes.get(i.changes.size() - 1)[0]);
        for (int k = 0; k < Math.min(MAX_NOTES, i.changes.size()); k++) list.addView(bullet(i.changes.get(k), multi), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (i.changes.size() > MAX_NOTES) list.addView(Ui.small(act, "+ még " + (i.changes.size() - MAX_NOTES) + " újdonság"), Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(list, Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    private View bullet(String[] ch, boolean withVersion) {
        LinearLayout row = Ui.hbox(act, 10);
        row.setGravity(Gravity.TOP);
        View dot = Ui.dot(act, Theme.accent);
        row.addView(dot, Ui.margin(Ui.lp(Ui.dp(8), Ui.dp(8)), 2, 6, 0, 0));
        row.addView(Ui.body(act, (withVersion ? ch[0] + ": " : "") + ch[1]), Ui.grow());
        return row;
    }

    private void openNotes(Info i) {
        Ui.Sheet sh = Ui.sheet(act, "Mi újság a " + i.versionName + " verzióban?");
        String last = null;
        for (String[] ch : i.changes) {
            if (!ch[0].equals(last)) {
                sh.body.addView(Ui.small(act, ch[0] + (ch[0].equals(i.versionName) && !i.date.isEmpty() ? " · " + day(i.date) : "")), Ui.lp(Ui.MATCH, Ui.WRAP));
                last = ch[0];
            }
            sh.body.addView(bullet(ch, false), Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        sh.show();
    }

    /** A kezdőlap teteje: csak a kártya, ha kell. */
    static void addTo(LinearLayout root, MainActivity act) {
        root.addView(new UpdateCard(act, true), Ui.lp(Ui.MATCH, Ui.WRAP));
    }
}
