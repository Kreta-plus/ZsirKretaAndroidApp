package hu.zsirkreta.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.R;
import hu.zsirkreta.app.data.Config;
import hu.zsirkreta.app.data.Kreta;
import hu.zsirkreta.app.data.Kreta.KretaError;
import hu.zsirkreta.app.util.Async;

/**
 * Belépés, ugyanúgy, mint a weben: a KRÉTA saját oldalán (itt a beépített böngészőben, a kódot magától átveszi),
 * tartalékként a végső cím beillesztésével, vagy teszt fiókkal. A jelszó csak a KRÉTA oldalára kerül.
 */
public final class LoginScreen {
    private final MainActivity act;
    private final Runnable onDone;
    private String mode = "web";        // web | link
    private boolean advanced = false;
    private String errMsg, errDetail;
    private boolean busy = false;
    private LinearLayout card;
    // statikus, hogy forgatáskor ne vesszen el a beillesztett cím
    private static String pasted = "";
    // a „cím beillesztése” módszer folyamatban lévő PKCE-adatai
    private static Kreta.Pkce pending;

    public LoginScreen(MainActivity act, String message, Runnable onDone) {
        this.act = act; this.onDone = onDone;
        this.errMsg = message;
    }

    private Context c() { return act; }

    public View build() {
        ScrollView sv = new ScrollView(act);
        sv.setFillViewport(true);
        FrameLayout holder = new FrameLayout(act);
        Ui.MaxWidthLayout wrap = new Ui.MaxWidthLayout(act, 460);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(Ui.dp(18), Ui.dp(28), Ui.dp(18), Ui.dp(28));
        wrap.setDividerDrawable(Ui.gap(18, true));
        wrap.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);

        // hős blokk
        LinearLayout hero = Ui.vbox(act, 8);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView logo = new ImageView(act);
        logo.setImageResource(R.drawable.ic_logo);
        hero.addView(logo, Ui.lp(Ui.dp(76), Ui.dp(76)));
        hero.addView(Ui.text(act, "ZsírKréta", 28, Theme.text, true), Ui.margin(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 6, 0, 0));
        TextView tag = Ui.muted(act, "Jegyek, órarend, statisztikák, a saját neveiddel.");
        tag.setGravity(Gravity.CENTER);
        hero.addView(tag, Ui.lp(Ui.WRAP, Ui.WRAP));
        wrap.addView(hero, Ui.lp(Ui.MATCH, Ui.WRAP));

        card = Ui.card(act);
        wrap.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        draw();

        holder.addView(wrap, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.CENTER));
        sv.addView(holder, new ScrollView.LayoutParams(Ui.MATCH, Ui.MATCH));
        return sv;
    }

    private void draw() {
        card.removeAllViews();
        if (mode.equals("web")) drawWeb(); else drawLink();
        drawAdvanced();
    }

    private LinearLayout errorBox() {
        LinearLayout box = Ui.vbox(act, 6);
        box.setBackground(Ui.shape(Theme.withAlpha(Theme.g[1], 0.14f), 12, Theme.withAlpha(Theme.g[1], 0.5f), 1));
        box.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        box.addView(Ui.text(act, errMsg, 13.5f, Theme.g[1], true), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (errDetail != null && !errDetail.isEmpty()) {
            TextView det = Ui.text(act, errDetail, 11, Theme.muted, false);
            det.setTypeface(Typeface.MONOSPACE);
            det.setTextIsSelectable(true);
            det.setVisibility(View.GONE);
            TextView toggle = Ui.text(act, "Részletek ▾", 12.5f, Theme.accent, true);
            toggle.setOnClickListener(v -> {
                boolean show = det.getVisibility() != View.VISIBLE;
                det.setVisibility(show ? View.VISIBLE : View.GONE);
                toggle.setText(show ? "Részletek ▴" : "Részletek ▾");
            });
            box.addView(toggle, Ui.lp(Ui.WRAP, Ui.WRAP));
            box.addView(det, Ui.lp(Ui.MATCH, Ui.WRAP));
            box.addView(Ui.smallButton(act, "Másolás", Ui.B_DEFAULT, () -> {
                ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("KRÉTA belépés", errDetail));
                act.toast("Kimásolva");
            }), Ui.lp(Ui.WRAP, Ui.WRAP));
        }
        return box;
    }

    private void setError(String m, String detail) {
        errMsg = m; errDetail = detail;
        draw();
    }

    // ---------- fő út: belépés a KRÉTA oldalán, a beépített böngészőben ----------
    private void drawWeb() {
        String[][] steps = {
                {"Lépj be a KRÉTA-ban", "A gomb megnyitja a KRÉTA saját belépőoldalát. Ott a szokásos neveddel és jelszavaddal lépj be."},
                {"Kész is", "A belépés után az app magától átveszi a belépést, semmit nem kell másolnod."}};
        LinearLayout list = Ui.vbox(act, 10);
        for (int i = 0; i < steps.length; i++) {
            LinearLayout row = Ui.hbox(act, 10);
            row.setGravity(Gravity.TOP);
            TextView n = Ui.text(act, String.valueOf(i + 1), 13, i == 0 ? Theme.onAccent : Theme.text, true);
            n.setGravity(Gravity.CENTER);
            n.setBackground(Ui.shape(i == 0 ? Theme.accent : Theme.surface2, 13));
            row.addView(n, Ui.lp(Ui.dp(26), Ui.dp(26)));
            row.addView(Rows.twoLine(act, Ui.bold(act, steps[i][0]), Ui.small(act, steps[i][1])), Ui.grow());
            list.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        card.addView(list, Ui.lp(Ui.MATCH, Ui.WRAP));
        TextView go = Ui.button(act, busy ? "Belépés…" : "Belépés a KRÉTA-ban", Ui.B_PRIMARY, null);
        go.setEnabled(!busy);
        go.setAlpha(busy ? 0.6f : 1f);
        go.setOnClickListener(v -> startWebLogin());
        card.addView(go, Ui.lp(Ui.MATCH, Ui.WRAP));
        if (errMsg != null) card.addView(errorBox(), Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(Ui.small(act, "A jelszavad csak a KRÉTA oldalára kerül, az app nem látja és nem tárolja. A belépés után csak egy titkosított megújító token marad a telefonon (az Android Keystore védi), így nem kell újra belépned."), Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(Ui.divider(act));
        card.addView(Ui.button(act, "Kipróbálom teszt fiókkal", Ui.B_DEFAULT, this::demo), Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(Ui.small(act, "A teszt fiók kitalált jegyekkel, órákkal, házikkal és dolgozatokkal van feltöltve, a KRÉTA-hoz nem kapcsolódik."), Ui.lp(Ui.MATCH, Ui.WRAP));
        TextView alt = Ui.text(act, "Belépés a cím beillesztésével", 13, Theme.accent, true);
        alt.setPadding(0, Ui.dp(6), 0, Ui.dp(6));
        alt.setOnClickListener(v -> { mode = "link"; errMsg = null; errDetail = null; draw(); });
        card.addView(alt, Ui.lp(Ui.WRAP, Ui.WRAP));
    }

    private void demo() {
        Kreta.demoAccount();
        onDone.run();
    }

    private void startWebLogin() {
        Kreta.Pkce k = Kreta.newPkce();
        errMsg = null; errDetail = null;
        draw();
        act.launchWebLogin(Kreta.authorizeUrl(k.challenge, k.state), k.verifier, (code, verifier, err) -> {
            if (code == null) {
                if (err != null && !err.isEmpty()) setError(err, null);
                return;
            }
            finishWithCode(code, verifier);
        });
    }

    private void finishWithCode(String code, String verifier) {
        busy = true;
        draw();
        Async.run(() -> {
            Kreta.TokenResp t = Kreta.exchangeCode(code, verifier);
            if (!t.ok()) {
                String d = t.json.optString("error_description", t.json.optString("error", ""));
                throw new KretaError("A kód beváltása nem sikerült. Próbáld újra a „Belépés a KRÉTA-ban” gombbal.", 401, "login", d.isEmpty() ? "HTTP " + t.status : d);
            }
            return t.json;
        }, (JSONObject tok) -> {
            Kreta.saveUser("?", null, tok, true);
            onDone.run();
        }, e -> {
            busy = false;
            if (e instanceof KretaError) setError(e.getMessage(), ((KretaError) e).detail);
            else setError("A KRÉTA nem elérhető: " + e.getMessage(), null);
        });
    }

    // ---------- tartalék: a végső cím beillesztése (ha a beépített böngésző nem megy) ----------
    private void drawLink() {
        String[] steps = {
                "Nyomd meg a gombot, és lépj be a KRÉTA oldalán a böngésződben.",
                "A végén hibaoldal vagy üres lap jön, ez így jó. Másold ki a címsorból a teljes címet (benne a ?code= résszel).",
                "Gyere vissza ide, és illeszd be a címet."};
        LinearLayout list = Ui.vbox(act, 6);
        for (int i = 0; i < steps.length; i++) list.addView(Ui.muted(act, (i + 1) + ". " + steps[i]), Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(list, Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(Ui.button(act, "Belépés a KRÉTA-ban", Ui.B_PRIMARY, () -> {
            pending = Kreta.newPkce();
            try {
                act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Kreta.authorizeUrl(pending.challenge, pending.state))));
            } catch (Exception e) { setError("Nem tudtam megnyitni a böngészőt", e.getMessage()); }
        }), Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(Ui.button(act, "Beillesztés és belépés", Ui.B_DEFAULT, () -> {
            ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
            CharSequence clip = cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0 ? cm.getPrimaryClip().getItemAt(0).coerceToText(act) : null;
            if (clip == null || clip.length() == 0) { setError("A vágólap üres. Másold ki a címet a böngésző címsorából.", null); return; }
            pasted = clip.toString();
            finishLink();
        }), Ui.lp(Ui.MATCH, Ui.WRAP));
        EditText url = Ui.input(act, "https://…oauthredirect?code=…", pasted);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        Ui.onText(url, s -> pasted = s);
        card.addView(Ui.field(act, "Vagy illeszd be ide a címet", url), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (errMsg != null) card.addView(errorBox(), Ui.lp(Ui.MATCH, Ui.WRAP));
        card.addView(Ui.button(act, busy ? "Belépés…" : "Kész, belépek", Ui.B_DEFAULT, this::finishLink), Ui.lp(Ui.MATCH, Ui.WRAP));
        TextView back = Ui.text(act, "Vissza a normál belépéshez", 13, Theme.accent, true);
        back.setGravity(Gravity.CENTER);
        back.setPadding(0, Ui.dp(6), 0, Ui.dp(6));
        back.setOnClickListener(v -> { mode = "web"; errMsg = null; errDetail = null; draw(); });
        card.addView(back, Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    private void finishLink() {
        if (pending == null) { setError("Előbb nyomd meg a „Belépés a KRÉTA-ban” gombot.", null); return; }
        String raw = pasted.trim(), code = null;
        try { code = Uri.parse(raw).getQueryParameter("code"); } catch (Exception ignored) {}
        if ((code == null || code.isEmpty()) && !raw.contains("?") && !raw.contains("/") && raw.length() >= 20) code = raw;
        if (code == null || code.isEmpty()) { setError("Nem találtam kódot a címben. A cím végén ?code=... kell legyen.", null); return; }
        Kreta.Pkce p = pending;
        pending = null;   // a kód egyszer használható
        finishWithCode(code, p.verifier);
    }

    // ---------- haladó: KRÉTA végpontok (a webes változat környezeti változói) ----------
    private void drawAdvanced() {
        TextView head = Ui.text(act, advanced ? "Haladó beállítások ▴" : "Haladó beállítások ▾", 13, Theme.muted, true);
        head.setPadding(0, Ui.dp(8), 0, Ui.dp(4));
        head.setOnClickListener(v -> { advanced = !advanced; draw(); });
        card.addView(head, Ui.lp(Ui.WRAP, Ui.WRAP));
        if (!advanced) return;
        card.addView(Ui.small(act, "Ha a belépés nem működik, a KRÉTA végpontjait itt módosíthatod (üresen hagyva az alapértelmezett érvényes)."), Ui.lp(Ui.MATCH, Ui.WRAP));
        Map<String, String> defs = Config.defaults();
        Map<String, EditText> fields = new LinkedHashMap<>();
        for (int i = 0; i < Config.KEYS.length; i++) {
            String k = Config.KEYS[i];
            EditText e = Ui.input(act, defs.get(k).isEmpty() ? "alapértelmezett" : defs.get(k), Config.get(k));
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            e.setTextSize(13);
            fields.put(k, e);
            card.addView(Ui.field(act, Config.LABELS[i], e), Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        LinearLayout row = Ui.hbox(act, 10);
        row.addView(Ui.button(act, "Mentés", Ui.B_DEFAULT, () -> {
            Map<String, String> vals = new LinkedHashMap<>();
            for (Map.Entry<String, EditText> en : fields.entrySet()) vals.put(en.getKey(), en.getValue().getText().toString());
            Config.set(vals);
            act.toast("Mentve");
        }), Ui.grow());
        row.addView(Ui.button(act, "Alaphelyzet", Ui.B_DANGER, () -> {
            Config.reset();
            act.toast("Alaphelyzetbe állítva");
            draw();
        }), Ui.grow());
        card.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
    }
}
