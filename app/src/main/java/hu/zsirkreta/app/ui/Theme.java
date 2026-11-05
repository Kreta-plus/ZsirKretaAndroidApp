package hu.zsirkreta.app.ui;

import android.graphics.Color;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hu.zsirkreta.app.data.Prefs;

/**
 * Futásidejű téma: a webes app CSS-változóinak (--bg, --surface, --accent, --g1…--g5, --radius …) megfelelője.
 * A felhasználó témája, kiemelő színe, sűrűsége, betűmérete és az „Egyedi stílus” változói alakítják.
 */
public final class Theme {
    private Theme() {}

    public static boolean dark;
    public static int bg, bg2, surface, surface2, border, text, muted;
    public static int accent, accentSoft, accentStrong, onAccent;
    public static int cardBorder;                 // a kártyák kerete (a kártyastílus szerint)
    public static String bgStyle = "flat", cardStyle = "fill";
    public static final int INK = 0xFF1D1510;     // sötét szöveg a kiemelőfoltokon (jegyek, kiemelő szín)
    public static final int[] g = new int[6];   // g[1] … g[5]: a jegyek színei
    public static float radius = 16, pad = 14, gap = 10, fs = 1f, borderW = 1f, kpiScale = 1f, cardAlpha = 1f;

    /**
     * Színtémák (a webes app.css html[data-palette] blokkjaiból): kulcs, név, ajánlott kiemelő szín, majd
     * sötét / AMOLED / világos módban: bg, bg2, surface, surface2, border, text, muted.
     */
    public static final String[][] PALETTES = {
            {"desk", "Íróasztal", "#ffe14d", "#221a15", "#1a1410", "#2d231d", "#3a2e26", "#4a3b31", "#f6efe6", "#b8a594", "#000000", "#000000", "#120e0b", "#1c1612", "#2e2520", "#f6efe6", "#a8998b", "#eef1fa", "#e5e9f6", "#ffffff", "#f1f3fb", "#d3d9ee", "#121a33", "#59658c"},
            {"ocean", "Óceán", "#5cc8ff", "#0f1a24", "#0b141c", "#16242f", "#1f3140", "#2b4255", "#e8f2f8", "#94acbd", "#000000", "#000000", "#0a1218", "#111c25", "#1f2e3a", "#e8f2f8", "#8aa1b2", "#eaf4f9", "#dfedf5", "#ffffff", "#edf5f9", "#cadfea", "#0e2230", "#4f6b7e"},
            {"forest", "Erdő", "#45e3a0", "#141c16", "#0f1611", "#1c2720", "#26342b", "#34463a", "#eef4ec", "#a3b5a6", "#000000", "#000000", "#0b120d", "#121b15", "#22302a", "#eef4ec", "#95a898", "#eef4ec", "#e3ecdf", "#ffffff", "#eff5ed", "#d0dfcc", "#14261a", "#587060"},
            {"grape", "Szőlő", "#b79cff", "#1b1626", "#15111e", "#241e33", "#302841", "#3f3554", "#f1edf8", "#ab9fc4", "#000000", "#000000", "#100c16", "#18121f", "#2a2236", "#f1edf8", "#9d92b4", "#f3f0fa", "#e9e4f4", "#ffffff", "#f3effa", "#ddd4ee", "#1f1633", "#65588a"},
            {"rose", "Rózsa", "#ff5fa2", "#241519", "#1c1013", "#301d22", "#3d262d", "#50333b", "#faeef0", "#c09ea6", "#000000", "#000000", "#140b0d", "#1d1114", "#33212a", "#faeef0", "#b0939a", "#fbf0f2", "#f5e4e8", "#ffffff", "#fbf1f3", "#efd3da", "#33161e", "#8a5a66"},
            {"slate", "Grafit", "#ff9f43", "#1a1b1e", "#141517", "#232428", "#2e3035", "#3d3f45", "#f0f1f3", "#a2a6ae", "#000000", "#000000", "#0e0f10", "#16171a", "#26282c", "#f0f1f3", "#969aa2", "#f3f4f6", "#e9ebee", "#ffffff", "#f3f4f6", "#d8dbe0", "#17191d", "#5c626c"},
            {"midnight", "Éjfél", "#8a7dff", "#10122a", "#0c0e21", "#181b3a", "#22264d", "#2f3463", "#eceeff", "#9ca1d4", "#000000", "#000000", "#0a0b1a", "#12142a", "#22264a", "#eceeff", "#8f94c4", "#eef0fd", "#e3e6fa", "#ffffff", "#f0f2fd", "#d5d9f4", "#161a3d", "#5d6396"},
            {"sunset", "Naplemente", "#ff7a59", "#26160f", "#1d100b", "#331f16", "#42291e", "#573729", "#fff0e8", "#c9a594", "#000000", "#000000", "#150c08", "#1f120c", "#3a251b", "#fff0e8", "#b8988a", "#fdf1ea", "#f8e5d9", "#ffffff", "#fdf2ec", "#f1d5c4", "#3a1d10", "#8f5f49"},
            {"teal", "Menta", "#00d1c1", "#0f1f1e", "#0b1817", "#162a29", "#1f3836", "#2b4a47", "#e6f7f5", "#93b8b4", "#000000", "#000000", "#091312", "#0f1c1b", "#1e302e", "#e6f7f5", "#86aaa6", "#ebf7f5", "#dff0ed", "#ffffff", "#edf7f5", "#c9e3df", "#0f2826", "#4d736f"},
            {"sand", "Homok", "#e8c468", "#1f1c14", "#18150f", "#29251b", "#353025", "#463f30", "#f7f2e4", "#b8ad92", "#000000", "#000000", "#110f0a", "#1a1710", "#2e2a1f", "#f7f2e4", "#a89e85", "#f8f4e9", "#f1ebdb", "#ffffff", "#f9f5ec", "#e6dcc4", "#2a2414", "#7a6d4f"},
            {"ice", "Jég", "#88c0d0", "#1c2230", "#171c28", "#242c3d", "#2e384c", "#3d4960", "#eceff4", "#a3adc2", "#000000", "#000000", "#0d1017", "#141822", "#262d3d", "#eceff4", "#97a1b5", "#eef2f7", "#e3e9f1", "#ffffff", "#f0f3f8", "#d3dce8", "#1c2433", "#5a6782"},
            {"neon", "Neon", "#ff3df0", "#140d1f", "#0f0918", "#1e1430", "#2a1c42", "#3b2859", "#f6eaff", "#b49acb", "#000000", "#000000", "#0c0714", "#140c20", "#2a1c40", "#f6eaff", "#a68dbd", "#f8effd", "#f0e2f9", "#ffffff", "#f8f0fd", "#e5d0f2", "#24103a", "#74569a"},
    };

    /** Szövegkiemelő színek a kiemelő szín választásához (mint a weben). */
    public static final String[] SWATCHES = {"#ffe14d", "#e8c468", "#ff9f43", "#ff7a59", "#ff4d4d", "#ff5fa2", "#ff3df0", "#b79cff",
            "#8a7dff", "#3d5cff", "#5cc8ff", "#88c0d0", "#00d1c1", "#45e3a0", "#c6f25c", "#ffffff"};

    public static String[] palette(String key) {
        for (String[] p : PALETTES) if (p[0].equals(key)) return p;
        return PALETTES[0];
    }

    /** Az „Egyedi stílusban” használható változók (név, magyarázat). */
    public static final String[][] VARS = {
            {"--accent", "Kiemelő szín (pl. #ff5500)"},
            {"--bg", "Háttér"},
            {"--surface", "Kártyák háttere"},
            {"--surface2", "Listaelemek, beviteli mezők háttere"},
            {"--border", "Keretek színe"},
            {"--text", "Szöveg színe"},
            {"--muted", "Halványabb szöveg színe"},
            {"--g1", "1-es jegy színe"},
            {"--g2", "2-es jegy színe"},
            {"--g3", "3-as jegy színe"},
            {"--g4", "4-es jegy színe"},
            {"--g5", "5-ös jegy színe"},
            {"--radius", "Lekerekítés (pl. 4px)"},
            {"--card-alpha", "Kártyák átlátszatlansága (0–1, pl. 0.65: üveghatás)"},
            {"--border-width", "Keretvastagság (pl. 2px)"},
            {"--kpi-scale", "Nagy számok mérete (pl. 1.4)"},
    };

    public static final Map<String, String> CSS_PRESETS = new LinkedHashMap<>();
    static {
        CSS_PRESETS.put("Üveg", "--card-alpha: 0.65;");
        CSS_PRESETS.put("Éles sarkok", "--radius: 4px;");
        CSS_PRESETS.put("Neon keret", "--border: #ff3df0;\n--border-width: 2px;");
        CSS_PRESETS.put("Nagy számok", "--kpi-scale: 1.4;");
    }

    public static int withAlpha(int c, float a) { return Color.argb(Math.round(a * 255), Color.red(c), Color.green(c), Color.blue(c)); }

    public static int mix(int a, int b, float t) {
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private static double luminance(int c) {
        double[] v = {Color.red(c) / 255.0, Color.green(c) / 255.0, Color.blue(c) / 255.0};
        for (int i = 0; i < 3; i++) v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    public static void apply(Prefs p, boolean systemDark) {
        boolean amoled = p.theme.equals("amoled");
        dark = p.theme.equals("dark") || amoled || (p.theme.equals("auto") && systemDark);
        String[] pal = palette(p.palette);
        int o = 3 + (amoled ? 7 : dark ? 0 : 14);
        bg = Color.parseColor(pal[o]); bg2 = Color.parseColor(pal[o + 1]); surface = Color.parseColor(pal[o + 2]); surface2 = Color.parseColor(pal[o + 3]);
        border = Color.parseColor(pal[o + 4]); text = Color.parseColor(pal[o + 5]); muted = Color.parseColor(pal[o + 6]);
        if (dark) { g[5] = 0xFF45E3A0; g[4] = 0xFF5CC8FF; g[3] = 0xFFFFE14D; g[2] = 0xFFFF9F43; g[1] = 0xFFFF5FA2; }
        else { g[5] = 0xFF17B878; g[4] = 0xFF2AA7E8; g[3] = 0xFFE8BF00; g[2] = 0xFFF08A1C; g[1] = 0xFFF0478D; }
        accent = parseColor(p.accent, 0xFFFFE14D);
        bgStyle = p.bgStyle;
        cardStyle = p.cardStyle;

        // a mobilos webes értékek (app.css @media max-width: 800px)
        switch (p.density) {
            case "compact": pad = 12; gap = 10; break;
            case "cozy": pad = 18; gap = 14; break;
            default: pad = 14; gap = 10;
        }
        fs = (float) p.fontScale;
        radius = p.radius;
        borderW = 1f; kpiScale = 1f;
        // kártyák: kitöltött (sötétben keret nélkül, világosban halvány kerettel), keretes, üveg
        switch (p.cardStyle) {
            case "outline": cardAlpha = 0f; cardBorder = border; break;
            case "glass": cardAlpha = 0.62f; cardBorder = withAlpha(text, 0.09f); break;
            default: cardAlpha = 1f; cardBorder = dark ? Color.TRANSPARENT : border;
        }

        // Egyedi stílus: „--változó: érték;” párok (a szelektorokat és a kapcsos zárójeleket figyelmen kívül hagyjuk)
        Matcher m = Pattern.compile("--([a-zA-Z0-9-]+)\\s*:\\s*([^;{}]+)").matcher(p.customCss == null ? "" : p.customCss);
        while (m.find()) {
            String k = m.group(1).toLowerCase(), v = m.group(2).trim();
            Integer col = parseColorOrNull(v);
            switch (k) {
                case "accent": if (col != null) accent = col; break;
                case "bg": if (col != null) { bg = col; bg2 = col; } break;
                case "surface": if (col != null) surface = col; break;
                case "surface2": if (col != null) surface2 = col; break;
                case "border": if (col != null) { border = col; cardBorder = col; } break;
                case "text": if (col != null) text = col; break;
                case "muted": if (col != null) muted = col; break;
                case "g1": case "g2": case "g3": case "g4": case "g5": if (col != null) g[k.charAt(1) - '0'] = col; break;
                case "radius": { Float f = number(v); if (f != null) radius = Math.max(0, Math.min(40, f)); break; }
                case "card-alpha": { Float f = number(v); if (f != null) cardAlpha = Math.max(0f, Math.min(1f, f)); break; }
                case "border-width": { Float f = number(v); if (f != null) { borderW = Math.max(0, Math.min(6, f)); if (cardBorder == Color.TRANSPARENT) cardBorder = border; } break; }
                case "kpi-scale": { Float f = number(v); if (f != null) kpiScale = Math.max(0.6f, Math.min(2f, f)); break; }
                default: break;
            }
        }
        accentSoft = withAlpha(accent, 0.16f);
        accentStrong = dark ? mix(accent, Color.WHITE, 0.12f) : mix(accent, text, 0.55f);
        onAccent = luminance(accent) > 0.35 ? INK : Color.WHITE;
    }

    private static Float number(String v) {
        Matcher m = Pattern.compile("^(-?\\d*\\.?\\d+)").matcher(v.trim());
        if (!m.find()) return null;
        try { return Float.parseFloat(m.group(1)); } catch (NumberFormatException e) { return null; }
    }

    public static int parseColor(String s, int def) {
        Integer c = parseColorOrNull(s);
        return c == null ? def : c;
    }

    /** #rgb, #rrggbb, #rrggbbaa (CSS-sorrend), rgb()/rgba() és az Android színnevei. */
    public static Integer parseColorOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        try {
            if (s.startsWith("#")) {
                String h = s.substring(1);
                if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
                if (h.length() == 6) return Color.parseColor("#" + h);
                if (h.length() == 8) return Color.parseColor("#" + h.substring(6) + h.substring(0, 6));  // CSS: RRGGBBAA → ARGB
                return null;
            }
            Matcher m = Pattern.compile("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(?:,\\s*([\\d.]+)\\s*)?\\)", Pattern.CASE_INSENSITIVE).matcher(s);
            if (m.matches()) {
                float a = m.group(4) == null ? 1f : Float.parseFloat(m.group(4));
                return Color.argb(Math.round(a * 255), Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            }
            if (s.matches("[a-zA-Z]+")) return Color.parseColor(s.toLowerCase());
        } catch (IllegalArgumentException ignored) {}
        return null;
    }

    public static int gradeColor(Double v) {
        if (v == null) return accent;
        return g[Math.min(5, Math.max(1, (int) Math.round(v)))];
    }

    public static int onGrade(Double v) { return v == null ? onAccent : INK; }

    /** Tantárgyszín: stabil hash alapján, ugyanaz, mint a webes subjColor: hsl(h 70% 62%). */
    public static int subjColor(String name) {
        int h = 0;
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            h = h * 31 + cp;
            i += Character.charCount(cp);
        }
        return hsl((float) ((h & 0xFFFFFFFFL) % 360), 0.70f, 0.62f);
    }

    private static int hsl(float h, float s, float l) {
        float c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = l - c / 2;
        float r = 0, gg = 0, b = 0;
        if (h < 60) { r = c; gg = x; } else if (h < 120) { r = x; gg = c; } else if (h < 180) { gg = c; b = x; }
        else if (h < 240) { gg = x; b = c; } else if (h < 300) { r = x; b = c; } else { r = c; b = x; }
        return Color.rgb(Math.round((r + m) * 255), Math.round((gg + m) * 255), Math.round((b + m) * 255));
    }
}
