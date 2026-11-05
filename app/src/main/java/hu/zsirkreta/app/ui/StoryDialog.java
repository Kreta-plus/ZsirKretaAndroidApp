package hu.zsirkreta.app.ui;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.R;
import hu.zsirkreta.app.data.Models.Absence;
import hu.zsirkreta.app.data.Models.Grade;
import hu.zsirkreta.app.data.Models.SubjectAvg;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.util.Fmt;

/** Instagram story kép (1080×1920): amire büszke lehetsz + az alkalmazás logója. A kép a készüléken készül. */
public final class StoryDialog {
    private StoryDialog() {}

    private static final int W = 1080, H = 1920;

    private static final class Th {
        final String name; final int[] bg; final int text, sub, card, border;
        Th(String n, int[] bg, int text, int sub, int card, int border) { name = n; this.bg = bg; this.text = text; this.sub = sub; this.card = card; this.border = border; }
    }

    private static final class Tile {
        final String key, icon, value, label; final boolean on;
        Tile(String k, String i, String v, String l, boolean on) { key = k; icon = i; value = v; label = l; this.on = on; }
    }

    private static final class Data {
        Double avg; List<Tile> items = new ArrayList<>(); int count;
    }

    private static final int INK = Theme.INK;
    private static final int[] HL = {0xFFFFE14D, 0xFF5CC8FF, 0xFFFF5FA2, 0xFF45E3A0, 0xFFFF9F43, 0xFFB79CFF};   // szövegkiemelő színek a csempékhez

    /** A webes story.js témái: Íróasztal (sötét dió), Kiemelő (a kiemelő szín), Világos. card = az átlag sötét foltja. */
    private static Th theme(String k) {
        switch (k) {
            case "dark": return new Th("Sötét", new int[]{0xFF221A15, 0xFF221A15}, 0xFFF6EFE6, 0xB8F6EFE6, 0xFF2D231D, 0);
            case "light": return new Th("Világos", new int[]{0xFFEEF1FA, 0xFFEEF1FA}, INK, 0xA61D1510, INK, 0);
            default: {
                boolean light = Theme.onAccent == INK;
                return new Th("Színes", null, light ? INK : Color.WHITE, light ? 0xB81D1510 : 0xBFFFFFFF, INK, 0);
            }
        }
    }

    private static Data collect() {
        List<Grade> gs = St.numericGrades();
        List<Absence> ab = St.absences();
        LocalDateTime now = LocalDateTime.now();
        Data d = new Data();
        d.avg = St.avgOf(gs);
        d.count = gs.size();
        long fives = gs.stream().filter(g -> g.value == 5).count();
        List<SubjectAvg> subs = St.bySubject().stream().filter(s -> s.avg != null).sorted((a, b) -> Double.compare(b.avg, a.avg)).collect(Collectors.toList());
        List<Grade> last30 = new ArrayList<>(), prev = new ArrayList<>();
        for (Grade g : gs) { if (ChronoUnit.DAYS.between(g.date, now) < 30) last30.add(g); else prev.add(g); }
        Double avgPrev = St.avgOf(prev);
        Double delta = d.avg != null && avgPrev != null && !last30.isEmpty() ? d.avg - avgPrev : null;
        int streak = 0, cur = 0;
        for (int i = gs.size() - 1; i >= 0; i--) { cur = gs.get(i).value >= 4 ? cur + 1 : 0; streak = Math.max(streak, cur); }
        long unexc = ab.stream().filter(a -> !a.ok && !a.na).count();
        long good = subs.stream().filter(s -> s.avg >= 4.5).count();
        if (fives > 0) d.items.add(new Tile("fives", "⭐", String.valueOf(fives), "ötös jegy", true));
        if (!subs.isEmpty()) d.items.add(new Tile("best", "🏆", Fmt.f2(subs.get(0).avg), "legjobb tárgy: " + St.subjName(subs.get(0).subject), true));
        if (streak > 0) d.items.add(new Tile("streak", "🔥", String.valueOf(streak), "4–5-ös jegy egymás után", streak >= 3));
        if (delta != null) d.items.add(new Tile("delta", "📈", (delta >= 0 ? "+" : "−") + Fmt.f2(Math.abs(delta)), "átlag az elmúlt 30 napban", delta > 0.005));
        if (good > 0) d.items.add(new Tile("good", "💪", String.valueOf(good), "tantárgy 4,5 felett", true));
        if (unexc == 0) d.items.add(new Tile("abs", "✅", "0", "igazolatlan mulasztás", true));
        if (!gs.isEmpty()) d.items.add(new Tile("count", "📚", String.valueOf(gs.size()), "érdemjegy eddig", false));
        return d;
    }

    // ---------- rajzolás ----------
    private static Paint paint(int color, float size, boolean bold, Paint.Align align) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setTextAlign(align);
        return p;
    }

    private static void fit(Paint p, String text, float max, float size) {
        float s = size;
        do { p.setTextSize(s); s -= 2; } while (p.measureText(text) > max && s > 20);
    }

    private static void wrap(Canvas c, Paint p, String text, float x, float y, float max, float lh, int maxLines) {
        String[] words = text.split(" ");
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String w : words) {
            String t = line.isEmpty() ? w : line + " " + w;
            if (p.measureText(t) > max && !line.isEmpty()) { lines.add(line); line = w; } else line = t;
        }
        lines.add(line);
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            String l = lines.get(i);
            c.drawText(i == maxLines - 1 && lines.size() > maxLines ? l + "…" : l, x, y + i * lh, p);
        }
    }

    static void draw(Context ctx, Bitmap bmp, String themeKey, String name, String footer, Double avg, int count, List<Tile> items) {
        Canvas c = new Canvas(bmp);
        Th th = theme(themeKey);
        int acc = Theme.accent;
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        int c1, c2;
        if (th.bg == null) { c1 = acc; c2 = acc; }
        else { c1 = th.bg[0]; c2 = th.bg[1]; }
        bg.setShader(new LinearGradient(0, 0, W * 0.6f, H, c1, c2, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, bg);

        // logó + név
        Paint lg = new Paint(Paint.ANTI_ALIAS_FLAG);
        lg.setColor(0xFFFFE14D);
        lg.setShadowLayer(40, 0, 14, 0x4D000000);
        c.drawRoundRect(new RectF(W / 2f - 78, 252, W / 2f + 78, 408), 36, 36, lg);
        Drawable logo = AppCompatResources.getDrawable(ctx, R.drawable.ic_logo);
        logo.setBounds(W / 2 - 80, 250, W / 2 + 80, 410);
        logo.draw(c);
        Paint t1 = paint(th.text, 58, true, Paint.Align.CENTER);
        c.drawText("ZsírKréta", W / 2f, 490, t1);

        Paint hd = paint(th.sub, 44, false, Paint.Align.CENTER);
        c.drawText(name.isEmpty() ? "Erre vagyok büszke" : name + " ezzel büszkélkedhet", W / 2f, 580, hd);

        // nagy átlag: sötét folt, sárga számmal
        Paint spot = new Paint(Paint.ANTI_ALIAS_FLAG);
        spot.setColor(th.card);
        c.drawRoundRect(new RectF(80, 620, W - 80, 950), 56, 56, spot);
        c.drawText(Fmt.f2(avg), W / 2f, 820, paint(0xFFFFE14D, 200, true, Paint.Align.CENTER));
        c.drawText("átlag, " + count + " jegy alapján", W / 2f, 900, paint(0xC7F6EFE6, 40, false, Paint.Align.CENTER));

        // csempék
        final int cols = 2, tw = 440, tH = 200, gap = 40, startY = 990;
        // a háttérrel (pl. sárga kiemelő szín) majdnem egyező kiemelőszínt kihagyjuk, különben a csempe beleolvadna
        List<Integer> hl = new ArrayList<>();
        for (int h : HL) {
            int dr = Color.red(h) - Color.red(c1), dg = Color.green(h) - Color.green(c1), db = Color.blue(h) - Color.blue(c1);
            if (dr * dr + dg * dg + db * db > 70 * 70) hl.add(h);
        }
        if (hl.isEmpty()) hl.add(INK);
        for (int i = 0; i < items.size(); i++) {
            Tile it = items.get(i);
            float x = 80 + (i % cols) * (tw + gap), y = startY + (i / cols) * (tH + 28);
            // csempék: mindegyik egy kiemelőszín, sötét szöveggel
            Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
            tile.setColor(hl.get(i % hl.size()));
            c.drawRoundRect(new RectF(x, y, x + tw, y + tH), 40, 40, tile);
            c.drawText(it.icon, x + 32, y + 100, paint(INK, 64, false, Paint.Align.LEFT));
            Paint v = paint(INK, 92, true, Paint.Align.LEFT);
            fit(v, it.value, tw - 190, 92);
            c.drawText(it.value, x + 130, y + 104, v);
            Paint lb = paint(0xC71D1510, 30, false, Paint.Align.LEFT);
            wrap(c, lb, it.label, x + 32, y + 150, tw - 64, 34, 2);
        }

        // alsó felirat
        int rows = (int) Math.ceil(items.size() / (double) cols);
        int endY = startY + rows * (tH + 28);
        float fy = Math.min(1640, Math.max(endY + 30, 1500));
        c.drawText(footer, W / 2f, fy + 40, paint(th.text, 46, true, Paint.Align.CENTER));
        c.drawText("Jegyek, órarend, statisztikák egy helyen", W / 2f, fy + 92, paint(th.sub, 34, false, Paint.Align.CENTER));
    }

    // ---------- a párbeszédpanel ----------
    public static void open(MainActivity act) {
        final Data data = collect();
        if (data.count == 0) { act.toast("Még nincs jegy, amiből képet lehetne csinálni."); return; }

        final String[] themeKey = {"accent"};
        final String[] footer = {"Próbáld ki te is: ZsírKréta"};
        final boolean[] useName = {false};
        final Set<String> picked = new HashSet<>();
        for (Tile t : data.items) if (t.on) picked.add(t.key);
        final Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);

        Ui.Sheet s = Ui.sheet(act, "📸 Story kép");
        ImageView preview = new ImageView(act);
        preview.setAdjustViewBounds(true);
        preview.setImageBitmap(bmp);
        preview.setBackground(Ui.shape(Theme.surface2, 16));
        preview.setClipToOutline(true);
        LinearLayout.LayoutParams plp = Ui.lp(Ui.dp(210), Ui.dp(373));
        plp.gravity = Gravity.CENTER_HORIZONTAL;

        Runnable redraw = () -> {
            List<Tile> shown = data.items.stream().filter(t -> picked.contains(t.key)).limit(6).collect(Collectors.toList());
            bmp.eraseColor(Color.TRANSPARENT);
            draw(act, bmp, themeKey[0], useName[0] ? St.shownName() : "", footer[0], data.avg, data.count, shown);
            preview.invalidate();
        };

        s.body.addView(preview, plp);

        s.body.addView(Ui.small(act, "Stílus"), Ui.lp(Ui.MATCH, Ui.WRAP));
        s.body.addView(Ui.seg(act, new String[]{"accent", "dark", "light"}, new String[]{"Színes", "Sötét", "Világos"}, themeKey[0], k -> { themeKey[0] = k; redraw.run(); }), Ui.lp(Ui.MATCH, Ui.WRAP));

        s.body.addView(Ui.small(act, "Mi kerüljön rá? (max. 6)"), Ui.lp(Ui.MATCH, Ui.WRAP));
        Ui.Flow chips = Ui.flow(act, 8);
        for (Tile t : data.items) {
            LinearLayout chip = Ui.hbox(act, 4);
            chip.setBackground(Ui.shape(Theme.surface2, 20, Theme.border, 1));
            chip.setPadding(Ui.dp(8), Ui.dp(2), Ui.dp(12), Ui.dp(2));
            chip.addView(Ui.check(act, picked.contains(t.key), on -> { if (on) picked.add(t.key); else picked.remove(t.key); redraw.run(); }));
            String lab = t.label.contains(":") ? t.label.substring(0, t.label.indexOf(':')) : t.label;
            chip.addView(Ui.text(act, t.icon + " " + lab, 13, Theme.muted, true));
            chips.addView(chip);
        }
        s.body.addView(chips, Ui.lp(Ui.MATCH, Ui.WRAP));

        s.body.addView(Ui.switchRow(act, "A nevem is legyen rajta (" + St.shownName() + ")", false, on -> { useName[0] = on; redraw.run(); }), Ui.lp(Ui.MATCH, Ui.WRAP));
        EditText ft = Ui.input(act, "", footer[0]);
        Ui.onText(ft, v -> { footer[0] = v; redraw.run(); });
        s.body.addView(Ui.field(act, "Alsó felirat", ft), Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout btns = Ui.hbox(act, 10);
        final TextView saveBtn = Ui.button(act, "⬇ Mentés", Ui.B_PRIMARY, null);
        saveBtn.setOnClickListener(v -> {
            String res = save(act, bmp);
            if (res == null) return;   // a megosztó menü nyílt meg
            saveBtn.setText(res);
            saveBtn.postDelayed(() -> saveBtn.setText("⬇ Mentés"), 2500);
        });
        btns.addView(saveBtn, Ui.grow());
        btns.addView(Ui.button(act, "↗ Megosztás", Ui.B_DEFAULT, () -> share(act, bmp)), Ui.grow());
        s.body.addView(btns, Ui.lp(Ui.MATCH, Ui.WRAP));
        s.body.addView(Ui.small(act, "Az átlagodat és a jegyeid számát tartalmazza a kép, ezért csak akkor posztold, ha ezt meg szeretnéd mutatni. A kép a telefonodon készül, sehova nem töltődik fel."), Ui.lp(Ui.MATCH, Ui.WRAP));

        redraw.run();
        s.show();
    }

    /** Visszaad: a gombon megjelenő visszajelzés, vagy null, ha a megosztó menü nyílt meg. */
    private static String save(MainActivity act, Bitmap bmp) {
        if (Build.VERSION.SDK_INT < 29) { share(act, bmp); return null; }   // régi Androidon a megosztó menüből menthető
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, "zsirkreta-story-" + System.currentTimeMillis() + ".png");
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ZsírKréta");
            Uri uri = act.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException("nem hozható létre a fájl");
            try (OutputStream os = act.getContentResolver().openOutputStream(uri)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            return "✓ Elmentve a Képek közé";
        } catch (Exception e) {
            return "Mentés sikertelen";
        }
    }

    private static void share(Context act, Bitmap bmp) {
        try {
            File dir = new File(act.getCacheDir(), "share");
            dir.mkdirs();
            File f = new File(dir, "zsirkreta-story.png");
            try (FileOutputStream os = new FileOutputStream(f)) { bmp.compress(Bitmap.CompressFormat.PNG, 100, os); }
            Uri uri = FileProvider.getUriForFile(act, act.getPackageName() + ".files", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/png");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.putExtra(Intent.EXTRA_TITLE, "ZsírKréta");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            act.startActivity(Intent.createChooser(i, "ZsírKréta story"));
        } catch (Exception e) {
            Ui.toast(act, "Megosztás sikertelen: " + e.getMessage());
        }
    }
}
