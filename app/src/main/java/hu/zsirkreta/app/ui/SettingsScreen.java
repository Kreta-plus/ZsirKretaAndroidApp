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

    public SettingsScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Beállítások"; }

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
        Prefs p = St.prefs;
        root.addView(Ui.muted(c(), "Minden automatikusan mentődik."), Ui.lp(Ui.MATCH, Ui.WRAP));

        // ---------- értesítések ----------
        root.addView(NotifyCard.settings(act), Ui.lp(Ui.MATCH, Ui.WRAP));

        // ---------- megjelenés ----------
        LinearLayout look = card(root, "Megjelenés", null);
        look.addView(Ui.small(c(), "Téma"), Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(Ui.seg(c(), new String[]{"auto", "dark", "light", "amoled"}, new String[]{"Auto", "Sötét", "Világos", "AMOLED"}, p.theme, k -> { p.theme = k; apply(); }), Ui.lp(Ui.MATCH, Ui.WRAP));

        look.addView(Ui.small(c(), "Színtéma (a háttér és a kártyák színe; a kiemelő szín bármelyikhez párosítható)"), Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(palettes(p), Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(Ui.small(c(), "Kiemelő szín"), Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(swatches(p), Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(Ui.small(c(), "Háttér"), Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(seg(BG_STYLES, p.bgStyle, k -> { p.bgStyle = k; apply(); }), Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(Ui.small(c(), "Kártyák (az Üveg a Ragyogás vagy minta háttérrel mutat a legjobban)"), Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(seg(CARD_STYLES, p.cardStyle, k -> { p.cardStyle = k; apply(); }), Ui.lp(Ui.MATCH, Ui.WRAP));

        look.addView(Ui.small(c(), "Sűrűség"), Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(Ui.seg(c(), new String[]{"compact", "normal", "cozy"}, new String[]{"Tömör", "Normál", "Tágas"}, p.density, k -> { p.density = k; apply(); }), Ui.lp(Ui.MATCH, Ui.WRAP));

        final float fsNow = Ui.snap(0.85f, 1.3f, 0.05f, (float) p.fontScale);
        TextView fsLabel = Ui.small(c(), "Betűméret (" + Math.round(fsNow * 100) + "%)");
        look.addView(fsLabel, Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(Ui.slider(c(), 0.85f, 1.3f, 0.05f, fsNow, v -> fsLabel.setText("Betűméret (" + Math.round(v * 100) + "%)"),
                v -> { p.fontScale = Math.round(v * 100) / 100.0; apply(); }));

        TextView rdLabel = Ui.small(c(), "Lekerekítés (" + p.radius + "px)");
        look.addView(rdLabel, Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(Ui.slider(c(), 0, 28, 1, p.radius, v -> rdLabel.setText("Lekerekítés (" + Math.round(v) + "px)"),
                v -> { p.radius = Math.round(v); apply(); }));

        List<String> startKeys = Arrays.asList("dashboard", "grades", "stats", "timetable", "homework");
        List<String> startLabels = Arrays.asList("Kezdőlap", "Jegyek", "Statisztika", "Órarend", "Házi");
        look.addView(Ui.field(c(), "Kezdőoldal", Ui.select(c(), startLabels, Math.max(0, startKeys.indexOf(p.startView)), i -> { p.startView = startKeys.get(i); St.persist(); })), Ui.lp(Ui.MATCH, Ui.WRAP));
        look.addView(Ui.switchRow(c(), "Vicces szöveg a kezdőlapon (a holnapi órák alapján)", p.funnyQuips, on -> { p.funnyQuips = on; St.persist(); }), Ui.lp(Ui.MATCH, Ui.WRAP));

        // ---------- jegyek és átlag ----------
        LinearLayout gr = card(root, "Jegyek és átlag", null);
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

        gr.addView(Ui.small(c(), "Kezdőlap elemei"), Ui.lp(Ui.MATCH, Ui.WRAP));
        LinearLayout wl = Ui.vbox(c(), 0);
        gr.addView(wl, Ui.lp(Ui.MATCH, Ui.WRAP));
        for (String[] w : WIDGET_LABELS) {
            wl.addView(Ui.switchRow(c(), w[1], p.widgets.contains(w[0]), on -> {
                List<String> next = new ArrayList<>();
                for (String k : Prefs.WIDGETS) if (k.equals(w[0]) ? on : p.widgets.contains(k)) next.add(k);
                p.widgets = next;
                St.persist();
            }), Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        gr.addView(Ui.small(c(), "Elrejtett tantárgyak (kiesnek az átlagból)"), Ui.lp(Ui.MATCH, Ui.WRAP));
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
        gr.addView(hs, Ui.lp(Ui.MATCH, Ui.WRAP));

        // ---------- egyedi stílus ----------
        customStyle(card(root, "Egyedi stílus", "saját kinézet, csak nálad"), p);

        // ---------- jótanuló mód ----------
        LinearLayout gs = card(root, "Jótanuló mód", "🎓");
        gs.addView(Ui.switchRow(c(), "Jótanuló mód: minden jegy 5-ös", p.goodStudent, on -> {
            if (!on) { p.goodStudent = false; St.persist(); act.rerender(); return; }
            askDisclaimer();
        }), Ui.lp(Ui.MATCH, Ui.WRAP));
        gs.addView(Ui.small(c(), "Csak a te kijelződön látszik így, a KRÉTA-ban a jegyek nem változnak. Bekapcsoláskor el kell fogadnod egy nyilatkozatot."), Ui.lp(Ui.MATCH, Ui.WRAP));

        // ---------- developer mód ----------
        LinearLayout dev = card(root, "Developer mód", "fejlesztői eszközök");
        dev.addView(Ui.switchRow(c(), "Developer mód bekapcsolása", p.devMode, on -> {
            p.devMode = on;
            if (!on) { p.devFillLessons = false; p.devFillTimetable = false; }
            St.persist();
            act.rerender();
        }), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (p.devMode) {
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

        // ---------- adatok és biztonság ----------
        // ---------- alkalmazás és frissítések ----------
        root.addView(new UpdateCard(act, false), Ui.lp(Ui.MATCH, Ui.WRAP));

        data(card(root, "Adatok és biztonság", null), p);
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

    // ---------- adatok és biztonság ----------
    private void data(LinearLayout card, Prefs p) {
        Store.Account a = Store.account();
        card.addView(Ui.small(c(), "A jelszavad nincs eltárolva. A megújító tokent titkosítva tároljuk a készüléken (Android Keystore), ezért nem kell újra belépned. Semmilyen adat nem hagyja el a telefont a KRÉTA-kapcsolaton kívül."), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (a != null) {
            LinearLayout it = Ui.item(c());
            LinearLayout col = Ui.vbox(c(), 2);
            col.addView(Ui.bold(c(), a.demo ? "Teszt fiók" : (a.name.isEmpty() ? "Bejelentkezve" : a.name)), Ui.lp(Ui.MATCH, Ui.WRAP));
            col.addView(Ui.small(c(), a.demo ? "véletlen adatokkal, a KRÉTA-hoz nem kapcsolódik" : a.inst + (a.username.isEmpty() ? "" : " · " + a.username)), Ui.lp(Ui.MATCH, Ui.WRAP));
            it.addView(col, Ui.grow());
            it.addView(Ui.chip(c(), "ez az eszköz", Ui.C_OK));
            card.addView(it, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
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
        card.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout row2 = Ui.hbox(c(), 10);
        row2.addView(Ui.button(c(), "Kilépés", Ui.B_DEFAULT, () -> Ui.confirm(c(), "Kilépés", a != null && a.demo ? "A teszt fiók véletlen adatai a beállításaiddal együtt törlődnek."
                : "A beállításaid megmaradnak, a tokenek és a letöltött adatok törlődnek.", "Kilépés", false, () -> act.logout(false))), Ui.grow());
        row2.addView(Ui.button(c(), "Fiók és adatok törlése", Ui.B_DANGER, () -> Ui.confirm(c(), "Fiók és adatok törlése",
                "Biztosan törlöd a fiókodat és az összes mentett adatot (beállítások, nevek, saját házik, táska)? Ezt nem lehet visszavonni.", "Törlés", true, () -> act.logout(true))), Ui.grow());
        card.addView(row2, Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(Ui.small(c(), "ZsírKréta " + versionName() + " · telefonos változat"), Ui.lp(Ui.MATCH, Ui.WRAP));
    }
}
