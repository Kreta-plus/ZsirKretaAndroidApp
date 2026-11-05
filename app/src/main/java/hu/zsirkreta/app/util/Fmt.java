package hu.zsirkreta.app.util;

import java.util.List;
import java.util.Locale;

/** Számformázás (tizedesvessző) és kis matematikai segédek. */
public final class Fmt {
    private Fmt() {}

    public static String f2(Double n) { return n == null || n.isNaN() ? "–" : String.format(Locale.US, "%.2f", n).replace('.', ','); }
    public static String f1(Double n) { return n == null || n.isNaN() ? "–" : String.format(Locale.US, "%.1f", n).replace('.', ','); }
    /** Egész, ha egész; különben legfeljebb 1 tizedes (pl. célátlag: 4,5). */
    public static String num(double n) {
        if (n == Math.rint(n)) return String.valueOf((long) n);
        return String.format(Locale.US, "%.1f", n).replace('.', ',');
    }

    public static Double median(List<Integer> a) {
        if (a.isEmpty()) return null;
        int[] s = a.stream().mapToInt(Integer::intValue).sorted().toArray();
        int m = s.length >> 1;
        return s.length % 2 == 1 ? (double) s[m] : (s[m - 1] + s[m]) / 2.0;
    }

    /** Tizedesvesszős bevitel is érvényes. Hibánál null. */
    public static Double parse(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (NumberFormatException e) { return null; }
    }

    public static String clip(String s, int n) { return s == null ? "" : s.length() <= n ? s : s.substring(0, n); }

    /** A KRÉTA szövegeiben előforduló HTML-címkék eltávolítása. */
    public static String stripHtml(String s) { return s == null ? "" : s.replaceAll("<[^>]+>", " "); }
}
