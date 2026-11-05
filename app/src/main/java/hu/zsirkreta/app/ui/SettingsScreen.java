package hu.zsirkreta.app.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.R;
import hu.zsirkreta.app.data.Funny;
import hu.zsirkreta.app.data.Models.Plan;
import hu.zsirkreta.app.data.Prefs;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.data.Store;
import hu.zsirkreta.app.util.Dt;
import hu.zsirkreta.app.util.Fmt;

public final class SettingsScreen extends Screen {
    private static final String[][] BG_STYLES = {{"flat", "Egyszínű"}, {"glow", "Ragyogás"}, {"dots", "Pöttyös"}, {"grid", "Kockás"}};
    private static final String[][] CARD_STYLES = {{"fill", "Kitöltött"}, {"outline", "Keretes"}, {"glass", "Üveg"}};
    private static final String[][] WIDGET_LABELS = {{"avg", "Összesítő számok"}, {"next", "Mai órák"}, {"grades", "Friss jegyek"}, {"homework", "Házik"},
            {"tests", "Számonkérések"}, {"absences", "Hiányzások"}, {"trend", "Átlag-diagram"}, {"subjects", "Tantárgyi átlagok"}};
    private static boolean varsOpen = false;

    /** A csoportok (mint a webes beállítások oldalsávja): kulcs, emoji, név, rövid leírás a menüben. */
    private static final String[][] GROUPS = {
            {"look", "🎨", "Megjelenés", "Téma, színek, háttér, betűméret"},
            {"home", "🏠", "Kezdőlap", "Kezdőoldal, widgetek, vicces szöveg"},
            {"grades", "📘", "Jegyek", "Átlag, célátlag, elrejtett tantárgyak"},
            {"notify", "🔔", "Értesítések", "Óra előtti emlékeztető, új jegy, házi"},
            {"css", "🧩", "Egyedi CSS", "Saját színek és méretek"},
            {"account", "🔐", "Fiók és adatok", "Belépés, export, import, kilépés"},
            {"app", "📲", "Frissítések", "Verzió, új verzió keresése"},
            {"dev", "🛠️", "Fejlesztői", "Developer mód, tesztadatok"},
    };
    /** Telefonon: a megnyitott csoport (null = a csoportok listája). */
    private static String group = null;
    /** A legutóbb megnyitott csoport: a listában ez van kiemelve, széles kijelzőn ez látszik. */
    private static String lastGroup = "look";

    public SettingsScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Beállítások"; }

    /** Másik képernyőről jövet a csoportok listájával kezdünk. */
    @Override public void entered() { group = null; }

    /** Vissza: egy megnyitott csoportból a csoportok listájára. */
    @Override public boolean onBack() {
        if (wide() || group == null) return false;
        group = null;
        act.rerenderTop();
        return true;
    }

    /** Széles kijelzőn (tablet, fekvő telefon) a lista balra, a csoport jobbra, mint a weben. */
    private boolean wide() { return act.getResources().getConfiguration().screenWidthDp >= 640; }

    private static String[] groupOf(String key) {
        for (String[] g : GROUPS) if (g[0].equals(key)) return g;
        return GROUPS[0];
    }

    private void open(String key) {
        group = key;
        lastGroup = key;
        act.rerenderTop();
    }

    private LinearLayout card(LinearLayout root, String title, String small) {
        LinearLayout c = Ui.card(c(), title, small);
        root.addView(c, Ui.lp(Ui.MATCH, Ui.WRAP));
        return c;
    }

    private void apply() { St.persist(); act.restyle(); }

    private String versionName() {
        try { return act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    @Override public void render(LinearLayout root) {
        if (wide()) {
            String g = group != null ? group : lastGroup;
            LinearLayout row = Ui.hbox(c(), 16);
            row.setGravity(Gravity.TOP);
            LinearLayout side = Ui.vbox(c(), 10);
            side.addView(menu(g, true), Ui.lp(Ui.MATCH, Ui.WRAP));
            side.addView(footer(), Ui.lp(Ui.MATCH, Ui.WRAP));
            row.addView(side, Ui.lp(Ui.dp(210), Ui.WRAP));
            LinearLayout col = Ui.vbox(c(), Theme.gap);
            String[] gr = groupOf(g);
            col.addView(Ui.text(c(), gr[1] + "  " + gr[2], 20, Theme.text, true), Ui.lp(Ui.MATCH, Ui.WRAP));
            renderGroup(g, col);
            row.addView(col, Ui.grow());
            root.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
            return;
        }
        if (group == null) {
            root.addView(menu(lastGroup, false), Ui.lp(Ui.MATCH, Ui.WRAP));
            root.addView(footer(), Ui.lp(Ui.MATCH, Ui.WRAP));
            return;
        }
        String[] gr = groupOf(group);
        LinearLayout head = Ui.hbox(c(), 4);
        head.addView(Ui.iconButton(c(), R.drawable.ic_chevron_left, Theme.text, () -> { group = null; act.rerenderTop(); }));
        head.addView(Ui.text(c(), gr[1] + "  " + gr[2], 20, Theme.text, true), Ui.grow());
        root.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));
        renderGroup(group, root);
    }

    /** A csoportok listája: emoji + név, a kiválasztott kiemelő színű „pirulában” (mint a webes oldalsáv). */
    private View menu(String selected, boolean compact) {
        LinearLayout list = Ui.vbox(c(), 4);
        float r = Math.max(12, Theme.radius);
        for (String[] g : GROUPS) {
            boolean on = g[0].equals(selected);
            LinearLayout row = Ui.hbox(c(), 12);
            row.setBackground(Ui.ripple(Ui.shape(on ? Theme.accent : Color.TRANSPARENT, r), r));
            int pv = Ui.dp(compact ? 10 : 12);
            row.setPadding(Ui.dp(14), pv, Ui.dp(12), pv);
            row.setMinimumHeight(Ui.dp(compact ? 44 : 56));
            row.addView(Ui.text(c(), g[1], 18, Theme.text, false), Ui.lp(Ui.dp(26), Ui.WRAP));
            LinearLayout col = Ui.vbox(c(), 2);
            TextView name = Ui.text(c(), g[2], 15, on ? Theme.onAccent : Theme.text, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(name, Ui.lp(Ui.MATCH, Ui.WRAP));
            if (!compact) col.addView(Ui.text(c(), g[3], 12, on ? Theme.withAlpha(Theme.onAccent, 0.8f) : Theme.muted, false), Ui.lp(Ui.MATCH, Ui.WRAP));
            row.addView(col, Ui.grow());
            if (!compact) row.addView(Ui.icon(c(), R.drawable.ic_chevron_right, on ? Theme.onAccent : Theme.muted, 20));
            row.setOnClickListener(v -> open(g[0]));
            list.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        return list;
    }

    private View footer() {
        TextView t = Ui.small(c(), "Minden automatikusan mentődik · ZsírKréta " + versionName());
        t.setPadding(Ui.dp(14), Ui.dp(4), Ui.dp(14), 0);
        return t;
    }

    private void renderGroup(String key, LinearLayout root) {
        Prefs p = St.prefs;
        switch (key) {
            case "home": home(root, p); break;
            case "grades": grades(root, p); break;
            case "notify":
                root.addView(NotifyCard.lessons(act), Ui.lp(Ui.MATCH, Ui.WRAP));
                root.addView(NotifyCard.settings(act), Ui.lp(Ui.MATCH, Ui.WRAP));
                break;
            case "css": customStyle(card(root, "Egyedi CSS", "saját kinézet, csak nálad"), p); break;
            case "account": account(root, p); break;
            case "app":
                root.addView(new UpdateCard(act, false), Ui.lp(Ui.MATCH, Ui.WRAP));
                root.addView(Ui.small(c(), "ZsírKréta " + versionName() + " · telefonos változat"), Ui.lp(Ui.MATCH, Ui.WRAP));
                break;
            case "dev": dev(root, p); break;
            default: look(root, p);
        }
    }

    // ---------- megjelenés ----------
    private void look(LinearLayout root, Prefs p) {
        LinearLayout look = card(root, "Téma és színek", null);
        look.addView(Ui.small(c(), "Téma"), Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(Ui.seg(c(), new String[]{"auto", "dark", "light", "amoled"}, new String[]{"Auto", "Sötét", "Világos", "AMOLED"}, p.theme, k -> { p.theme = k; apply(); }), Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(Ui.small(c(), "Színtéma (a háttér és a kártyák színe; a kiemelő szín bármelyikhez párosítható)"), Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(palettes(p), Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(Ui.small(c(), "Kiemelő szín"), Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(swatches(p), Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout bg = card(root, "Háttér és kártyák", null);
        bg.addView(Ui.small(c(), "Háttér"), Ui.lp(Ui.MATCH, Ui.WRAP));
        bg.addView(seg(BG_STYLES, p.bgStyle, k -> { p.bgStyle = k; apply(); }), Ui.lp(Ui.MATCH, Ui.WRAP));
        bg.addView(Ui.small(c(), "Kártyák (az Üveg a Ragyogás vagy minta háttérrel mutat a legjobban)"), Ui.lp(Ui.MATCH, Ui.WRAP));
        bg.addView(seg(CARD_STYLES, p.cardStyle, k -> { p.cardStyle = k; apply(); }), Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout size = card(root, "Méretek", null);
        size.addView(Ui.small(c(), "Sűrűség"), Ui.lp(Ui.MATCH, Ui.WRAP));
        size.addView(Ui.seg(c(), new String[]{"compact", "normal", "cozy"}, new String[]{"Tömör", "Normál", "Tágas"}, p.density, k -> { p.density = k; apply(); }), Ui.lp(Ui.MATCH, Ui.WRAP));

        final float fsNow = Ui.snap(0.85f, 1.3f, 0.05f, (float) p.fontScale);
        TextView fsLabel = Ui.small(c(), "Betűméret (" + Math.round(fsNow * 100) + "%)");
        size.addView(fsLabel, Ui.lp(Ui.MATCH, Ui.WRAP));
        size.addView(Ui.slider(c(), 0.85f, 1.3f, 0.05f, fsNow, v -> fsLabel.setText("Betűméret (" + Math.round(v * 100) + "%)"),
                v -> { p.fontScale = Math.round(v * 100) / 100.0; apply(); }));

        TextView rdLabel = Ui.small(c(), "Lekerekítés (" + p.radius + "px)");
        size.addView(rdLabel, Ui.lp(Ui.MATCH, Ui.WRAP));
        size.addView(Ui.slider(c(), 0, 28, 1, p.radius, v -> rdLabel.setText("Lekerekítés (" + Math.round(v) + "px)"),
                v -> { p.radius = Math.round(v); apply(); }));
    }

    // ---------- kezdőlap ----------
    private void home(LinearLayout root, Prefs p) {
        LinearLayout start = card(root, "Indulás", null);
        List<String> startKeys = Arrays.asList("dashboard", "grades", "stats", "timetable", "homework");
        List<String> startLabels = Arrays.asList("Kezdőlap", "Jegyek", "Statisztika", "Órarend", "Házi");
        start.addView(Ui.field(c(), "Kezdőoldal (ez nyílik meg indításkor)", Ui.select(c(), startLabels, Math.max(0, startKeys.indexOf(p.startView)), i -> { p.startView = startKeys.get(i); St.persist(); })), Ui.lp(Ui.MATCH, Ui.WRAP));
        start.addView(Ui.switchRow(c(), "Vicces szöveg a kezdőlapon (a holnapi órák alapján)", p.funnyQuips, on -> { p.funnyQuips = on; St.persist(); }), Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout wl = card(root, "Kezdőlap elemei", "ki-be kapcsolhatók");
        for (String[] w : WIDGET_LABELS) {
            wl.addView(Ui.switchRow(c(), w[1], p.widgets.contains(w[0]), on -> {
                List<String> next = new ArrayList<>();
                for (String k : Prefs.WIDGETS) if (k.equals(w[0]) ? on : p.widgets.contains(k)) next.add(k);
                p.widgets = next;
                St.persist();
            }), Ui.lp(Ui.MATCH, Ui.WRAP));
        }
    }

    // ---------- jegyek ----------
    private void grades(LinearLayout root, Prefs p) {
        LinearLayout gr = card(root, "Átlag", null);
        gr.addView(Ui.switchRow(c(), "Súlyozott átlag (a KRÉTA súlyai szerint)", p.weighted, on -> { p.weighted = on; St.persist(); }), Ui.lp(Ui.MATCH, Ui.WRAP));
        EditText ta = Ui.numberInput(c(), "", Fmt.num(p.targetAvg));
        ta.setOnFocusChangeListener((v, f) -> {
            if (f) return;
            Double val = Fmt.parse(ta.getText().toString());
            p.targetAvg = val == null ? 4.5 : Math.min(5, Math.max(1, val));
            ta.setText(Fmt.num(p.targetAvg));
            St.persist();
        });
        gr.addView(Ui.field(c(), "Célátlag", ta), Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout hid = card(root, "Elrejtett tantárgyak", "kiesnek az átlagból");
        Ui.Flow hs = Ui.flow(c(), 8);
        List<String> subs = St.allSubjects();
        if (subs.isEmpty()) hs.addView(Ui.small(c(), "Még nincs betöltött tantárgy"));
        for (String s : subs) {
            LinearLayout chip = Ui.hbox(c(), 2);
            chip.setBackground(Ui.shape(Theme.surface2, 20, Theme.border, 1));
            chip.setPadding(Ui.dp(8), Ui.dp(2), Ui.dp(12), Ui.dp(2));
            chip.addView(Ui.check(c(), p.hiddenSubjects.contains(s), on -> {
                if (on) { if (!p.hiddenSubjects.contains(s)) p.hiddenSubjects.add(s); } else p.hiddenSubjects.remove(s);
                St.persist();
            }));
            chip.addView(Ui.text(c(), St.subjName(s), 13, Theme.muted, true));
            hs.addView(chip);
        }
        hid.addView(hs, Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout gs = card(root, "Jótanuló mód", "🎓");
        gs.addView(Ui.switchRow(c(), "Jótanuló mód: minden jegy 5-ös", p.goodStudent, on -> {
            if (!on) { p.goodStudent = false; St.persist(); act.rerender(); return; }
            askDisclaimer();
        }), Ui.lp(Ui.MATCH, Ui.WRAP));
        gs.addView(Ui.small(c(), "Csak a te kijelződön látszik így, a KRÉTA-ban a jegyek nem változnak. Bekapcsoláskor el kell fogadnod egy nyilatkozatot."), Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    // ---------- fejlesztői ----------
    private void dev(LinearLayout root, Prefs p) {
        LinearLayout dev = card(root, "Developer mód", "fejlesztői eszközök");
        dev.addView(Ui.switchRow(c(), "Developer mód bekapcsolása", p.devMode, on -> {
            p.devMode = on;
            if (!on) { p.devFillLessons = false; p.devFillTimetable = false; }
            St.persist();
            act.rerender();
        }), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (!p.devMode) {
            dev.addView(Ui.small(c(), "Tesztadatok, a háttér-lekérés gyakorisága és más, fejlesztéshez való beállítások."), Ui.lp(Ui.MATCH, Ui.WRAP));
            return;
        }
        dev.addView(NotifyCard.devInterval(act), Ui.lp(Ui.MATCH, Ui.WRAP));
        dev.addView(Ui.switchRow(c(), "Táska: ha holnap nincs óra, véletlen órákkal töltse fel", p.devFillLessons, on -> { p.devFillLessons = on; St.persist(); }), Ui.lp(Ui.MATCH, Ui.WRAP));
        dev.addView(Ui.switchRow(c(), "Órarend: az üres tanítási napokat töltse fel teszt órákkal", p.devFillTimetable, on -> { p.devFillTimetable = on; St.persist(); }), Ui.lp(Ui.MATCH, Ui.WRAP));
        LinearLayout row = Ui.hbox(c(), 10);
        row.addView(Ui.button(c(), "+ Teszt házi holnapra", Ui.B_DEFAULT, () -> {
            List<String> all = St.allSubjects();
            if (all.isEmpty()) { act.toast("Nincs betöltött tantárgy"); return; }
            String subject = all.get((int) (Math.random() * all.size()));
            p.myHomework.add(new Plan("t" + Long.toString(System.currentTimeMillis(), 36), subject, Dt.iso(LocalDate.now().plusDays(1)),
                    "[teszt] Gyakorló feladatok " + (1 + (int) (Math.random() * 40)) + ". oldal", false));
            St.persist();
            act.toast("Teszt házi hozzáadva: " + St.subjName(subject));
        }), Ui.grow());
        row.addView(Ui.button(c(), "Teszt házik törlése", Ui.B_DANGER, () -> {
            p.myHomework.removeIf(h -> h.body.startsWith("[teszt]"));
            St.persist();
            act.toast("Teszt házik törölve");
        }), Ui.grow());
        dev.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        dev.addView(Ui.small(c(), "A teszt házi egy véletlen tantárgyhoz kerül, holnapi határidővel (saját házik közé, „[teszt]” előtaggal)."), Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    private View seg(String[][] items, String sel, java.util.function.Consumer<String> cb) {
        String[] keys = new String[items.length], labels = new String[items.length];
        for (int i = 0; i < items.length; i++) { keys[i] = items[i][0]; labels[i] = items[i][1]; }
        return Ui.seg(c(), keys, labels, sel, cb::accept);
    }

    // ---------- színtéma ----------
    private View palettes(Prefs p) {
        LinearLayout col = Ui.vbox(c(), 10);
        Ui.Flow f = Ui.flow(c(), 8);
        int o = Theme.dark ? 3 : 17;   // az előnézet a mostani módhoz igazodik
        for (String[] pal : Theme.PALETTES) {
            boolean on = pal[0].equals(p.palette);
            LinearLayout tile = Ui.hbox(c(), 8);
            tile.setPadding(Ui.dp(8), Ui.dp(7), Ui.dp(12), Ui.dp(7));
            tile.setBackground(Ui.ripple(Ui.shape(on ? Theme.accentSoft : Theme.surface2, 14, on ? Theme.accent : Theme.border, on ? 2 : 1), 14));
            View pv = new View(c());
            android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor(pal[o]), Color.parseColor(pal[o + 3])});
            d.setCornerRadius(Ui.dp(8));
            d.setStroke(Ui.dp(2), Color.parseColor(pal[2]));
            pv.setBackground(d);
            tile.addView(pv, Ui.lp(Ui.dp(26), Ui.dp(26)));
            tile.addView(Ui.text(c(), pal[1], 13, Theme.text, on));
            tile.setOnClickListener(v -> { p.palette = pal[0]; apply(); });
            f.addView(tile);
        }
        col.addView(f, Ui.lp(Ui.MATCH, Ui.WRAP));
        col.addView(Ui.smallButton(c(), "🎲 Véletlen párosítás", Ui.B_DEFAULT, () -> {
            String[] pal = Theme.PALETTES[(int) (Math.random() * Theme.PALETTES.length)];
            p.palette = pal[0];
            p.accent = Math.random() < 0.5 ? pal[2] : Theme.SWATCHES[(int) (Math.random() * Theme.SWATCHES.length)];
            apply();
        }), Ui.lp(Ui.WRAP, Ui.WRAP));
        return col;
    }

    // ---------- kiemelő szín ----------
    private View swatches(Prefs p) {
        Ui.Flow f = Ui.flow(c(), 10);
        for (String hex : Theme.SWATCHES) {
            int col = Color.parseColor(hex);
            View sw = new View(c());
            boolean on = hex.equalsIgnoreCase(p.accent);
            android.graphics.drawable.GradientDrawable d = Ui.shape(col, 20);
            if (on) d.setStroke(Ui.dp(3), Theme.text);
            sw.setBackground(d);
            sw.setLayoutParams(new android.view.ViewGroup.LayoutParams(Ui.dp(40), Ui.dp(40)));
            sw.setOnClickListener(v -> { p.accent = hex; apply(); });
            f.addView(sw);
        }
        TextView custom = Ui.text(c(), "Saját szín…", 13, Theme.text, true);
        custom.setBackground(Ui.ripple(Ui.shape(Theme.surface2, 20, Theme.border, 1), 20));
        custom.setGravity(Gravity.CENTER);
        custom.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
        custom.setOnClickListener(v -> colorPicker(p));
        f.addView(custom);
        String[] pal = Theme.palette(p.palette);
        TextView match = Ui.text(c(), "● Témához illő", 13, Theme.text, true);
        match.setBackground(Ui.ripple(Ui.shape(Theme.surface2, 20, Color.parseColor(pal[2]), 2), 20));
        match.setGravity(Gravity.CENTER);
        match.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
        match.setOnClickListener(v -> { p.accent = pal[2]; apply(); });
        f.addView(match);
        return f;
    }

    private void colorPicker(Prefs p) {
        Ui.Sheet s = Ui.sheet(c(), "Saját kiemelő szín");
        float[] hsv = new float[3];
        Color.colorToHSV(Theme.parseColor(p.accent, Theme.accent), hsv);
        View preview = new View(c());
        preview.setLayoutParams(Ui.lp(Ui.MATCH, Ui.dp(56)));
        TextView hexLabel = Ui.bold(c(), "");
        Runnable upd = () -> {
            int col = Color.HSVToColor(hsv);
            preview.setBackground(Ui.shape(col, 16));
            hexLabel.setText(String.format("#%06x", col & 0xFFFFFF));
        };
        s.body.addView(preview);
        s.body.addView(hexLabel);
        s.body.addView(Ui.small(c(), "Árnyalat"));
        s.body.addView(Ui.slider(c(), 0, 360, 1, hsv[0], v -> { hsv[0] = v; upd.run(); }, null));
        s.body.addView(Ui.small(c(), "Telítettség"));
        s.body.addView(Ui.slider(c(), 0.2f, 1f, 0.01f, Math.max(0.2f, hsv[1]), v -> { hsv[1] = v; upd.run(); }, null));
        s.body.addView(Ui.small(c(), "Fényerő"));
        s.body.addView(Ui.slider(c(), 0.3f, 1f, 0.01f, Math.max(0.3f, hsv[2]), v -> { hsv[2] = v; upd.run(); }, null));
        upd.run();
        s.body.addView(Ui.button(c(), "Alkalmaz", Ui.B_PRIMARY, () -> {
            p.accent = String.format("#%06x", Color.HSVToColor(hsv) & 0xFFFFFF);
            s.dismiss();
            apply();
        }), Ui.lp(Ui.MATCH, Ui.WRAP));
        s.show();
    }

    // ---------- egyedi stílus ----------
    private void customStyle(LinearLayout card, Prefs p) {
        card.addView(Ui.small(c(), "A webes CSS helyett itt a téma változóit állíthatod (pl. --accent: #ff5500;). A szelektorokat (.card { … }) natív alkalmazásban nem lehet értelmezni, ezeket figyelmen kívül hagyja."), Ui.lp(Ui.MATCH, Ui.WRAP));
        Ui.Flow pre = Ui.flow(c(), 8);
        EditText css = Ui.textArea(c(), "pl.\n--accent: #ff5500;\n--radius: 6px;", p.customCss, 5);
        css.setTypeface(Typeface.MONOSPACE);
        css.setTextSize(13);
        css.setHorizontallyScrolling(false);
        css.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        TextView status = Ui.small(c(), "");
        Runnable upd = () -> status.setText(recognised(css.getText().toString()));
        Ui.onText(css, v -> { p.customCss = v; St.persist(); upd.run(); });
        for (Map.Entry<String, String> e : Theme.CSS_PRESETS.entrySet()) {
            TextView b = Ui.smallButton(c(), "+ " + e.getKey(), Ui.B_DEFAULT, () -> {
                String cur = css.getText().toString();
                css.setText((cur.isEmpty() ? "" : cur.trim() + "\n") + e.getValue());
                css.setSelection(css.getText().length());
            });
            pre.addView(b);
        }
        pre.addView(Ui.smallButton(c(), "Törlés", Ui.B_DANGER, () -> { css.setText(""); }));
        card.addView(pre, Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout head = Ui.hbox(c(), 8);
        head.addView(Ui.text(c(), "Változó beszúrása (" + Theme.VARS.length + ")", 13.5f, Theme.accent, true), Ui.grow());
        ImageView chev = Ui.icon(c(), R.drawable.ic_expand_more, Theme.muted, 22);
        chev.setRotation(varsOpen ? 180 : 0);
        head.addView(chev);
        card.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));
        LinearLayout list = Ui.vbox(c(), 6);
        list.setVisibility(varsOpen ? View.VISIBLE : View.GONE);
        for (String[] v : Theme.VARS) {
            LinearLayout row = Ui.hbox(c(), 10);
            row.setBackground(Ui.ripple(Ui.shape(Theme.surface2, 10), 10));
            row.setPadding(Ui.dp(12), Ui.dp(9), Ui.dp(12), Ui.dp(9));
            TextView n = Ui.text(c(), v[0], 13, Theme.text, true);
            n.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            row.addView(n, Ui.lp(Ui.dp(120), Ui.WRAP));
            TextView d = Ui.small(c(), v[1]);
            row.addView(d, Ui.grow());
            row.setOnClickListener(x -> {
                String snippet = v[0] + ": ;";
                int a = Math.max(0, css.getSelectionStart()), b = Math.max(0, css.getSelectionEnd());
                String cur = css.getText().toString();
                String pre0 = cur.substring(0, Math.min(a, cur.length()));
                String lead = !pre0.isEmpty() && !pre0.endsWith("\n") ? "\n" : "";
                css.setText(pre0 + lead + snippet + cur.substring(Math.min(Math.max(a, b), cur.length())));
                css.setSelection(pre0.length() + lead.length() + snippet.length() - 1);
                css.requestFocus();
            });
            list.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        head.setOnClickListener(x -> {
            varsOpen = !varsOpen;
            list.setVisibility(varsOpen ? View.VISIBLE : View.GONE);
            chev.setRotation(varsOpen ? 180 : 0);
        });
        card.addView(list, Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(css, Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(status, Ui.lp(Ui.MATCH, Ui.WRAP));
        upd.run();
        card.addView(Ui.button(c(), "Alkalmaz", Ui.B_PRIMARY, () -> { p.customCss = css.getText().toString(); apply(); }), Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    private String recognised(String css) {
        Set<String> known = new LinkedHashSet<>();
        for (String[] v : Theme.VARS) known.add(v[0].substring(2));
        List<String> ok = new ArrayList<>(), bad = new ArrayList<>();
        Matcher m = Pattern.compile("--([a-zA-Z0-9-]+)\\s*:\\s*([^;{}]+)").matcher(css);
        while (m.find()) {
            String name = m.group(1).toLowerCase(), val = m.group(2).trim();
            boolean color = name.equals("radius") || name.equals("card-alpha") || name.equals("border-width") || name.equals("kpi-scale");
            boolean valid = known.contains(name) && (color ? val.matches("^-?\\d*\\.?\\d+.*") : Theme.parseColorOrNull(val) != null);
            (valid ? ok : bad).add("--" + name);
        }
        if (ok.isEmpty() && bad.isEmpty()) return "Még nincs felismert változó.";
        return (ok.isEmpty() ? "" : "Felismert: " + String.join(", ", ok)) + (bad.isEmpty() ? "" : (ok.isEmpty() ? "" : " · ") + "Nem érvényes: " + String.join(", ", bad));
    }

    // ---------- jótanuló nyilatkozat ----------
    private void askDisclaimer() {
        Ui.Sheet s = Ui.sheet(c(), "🎓 Jótanuló mód");
        TextView t = Ui.body(c(), Funny.randomDisclaimer(c()));
        s.body.addView(t, Ui.lp(Ui.MATCH, Ui.WRAP));
        final TextView[] yes = new TextView[1];
        s.body.addView(Ui.switchRow(c(), "Elolvastam, és elfogadom", false, on -> {
            yes[0].setEnabled(on);
            yes[0].setAlpha(on ? 1f : 0.5f);
        }), Ui.lp(Ui.MATCH, Ui.WRAP));
        LinearLayout row = Ui.hbox(c(), 10);
        row.addView(Ui.button(c(), "Mégsem", Ui.B_DEFAULT, () -> { s.dismiss(); act.rerender(); }), Ui.grow());
        yes[0] = Ui.button(c(), "Bekapcsolom", Ui.B_PRIMARY, () -> {
            St.prefs.goodStudent = true;
            St.persist();
            s.dismiss();
            act.rerender();
            act.toast("Jótanuló mód bekapcsolva 🎉");
        });
        yes[0].setEnabled(false);
        yes[0].setAlpha(0.5f);
        row.addView(yes[0], Ui.grow());
        s.body.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        s.dlg.setOnCancelListener(d -> act.rerender());
        s.show();
    }

    // ---------- fiók és adatok ----------
    private void account(LinearLayout root, Prefs p) {
        Store.Account a = Store.account();
        LinearLayout card = card(root, "Fiók", null);
        if (a != null) {
            LinearLayout it = Ui.item(c());
            LinearLayout col = Ui.vbox(c(), 2);
            col.addView(Ui.bold(c(), a.demo ? "Teszt fiók" : (a.name.isEmpty() ? "Bejelentkezve" : a.name)), Ui.lp(Ui.MATCH, Ui.WRAP));
            col.addView(Ui.small(c(), a.demo ? "véletlen adatokkal, a KRÉTA-hoz nem kapcsolódik" : a.inst + (a.username.isEmpty() ? "" : " · " + a.username)), Ui.lp(Ui.MATCH, Ui.WRAP));
            it.addView(col, Ui.grow());
            it.addView(Ui.chip(c(), "ez az eszköz", Ui.C_OK));
            card.addView(it, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        card.addView(Ui.small(c(), "A jelszavad nincs eltárolva. A megújító tokent titkosítva tároljuk a készüléken (Android Keystore), ezért nem kell újra belépned. Semmilyen adat nem hagyja el a telefont a KRÉTA-kapcsolaton kívül."), Ui.lp(Ui.MATCH, Ui.WRAP));
        LinearLayout row2 = Ui.hbox(c(), 10);
        row2.addView(Ui.button(c(), "Kilépés", Ui.B_DEFAULT, () -> Ui.confirm(c(), "Kilépés", a != null && a.demo ? "A teszt fiók véletlen adatai a beállításaiddal együtt törlődnek."
                : "A beállításaid megmaradnak, a tokenek és a letöltött adatok törlődnek.", "Kilépés", false, () -> act.logout(false))), Ui.grow());
        row2.addView(Ui.button(c(), "Fiók és adatok törlése", Ui.B_DANGER, () -> Ui.confirm(c(), "Fiók és adatok törlése",
                "Biztosan törlöd a fiókodat és az összes mentett adatot (beállítások, nevek, saját házik, táska)? Ezt nem lehet visszavonni.", "Törlés", true, () -> act.logout(true))), Ui.grow());
        card.addView(row2, Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout backup = card(root, "Beállítások mentése", null);
        backup.addView(Ui.small(c(), "Az export a webes és a telefonos ZsírKréta között is cserélhető. Az értesítés-beállítások ehhez a telefonhoz tartoznak, azok nincsenek benne."), Ui.lp(Ui.MATCH, Ui.WRAP));
        LinearLayout row = Ui.hbox(c(), 10);
        row.addView(Ui.button(c(), "Beállítások exportja", Ui.B_DEFAULT, () -> act.exportText("zsirkreta-beallitasok.json", p.toJson().toString())), Ui.grow());
        row.addView(Ui.button(c(), "Import", Ui.B_DEFAULT, () -> act.importText(text -> {
            try {
                St.prefs = St.prefs.mergedWith(new JSONObject(text));
                St.persist();
                act.toast("Importálva");
                act.restyle();
            } catch (Exception e) { act.toast("Érvénytelen fájl"); }
        })), Ui.grow());
        backup.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
    }
}
