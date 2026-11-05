package hu.zsirkreta.app.util;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Pattern;

/** Dátum- és időkezelés, magyar formázás (a webes util.js megfelelője). */
public final class Dt {
    private Dt() {}

    public static final String[] DAYS = {"Hétfő", "Kedd", "Szerda", "Csütörtök", "Péntek", "Szombat", "Vasárnap"};
    public static final String[] MONTHS = {"jan", "feb", "már", "ápr", "máj", "jún", "júl", "aug", "szep", "okt", "nov", "dec"};

    private static final Pattern HAS_ZONE = Pattern.compile(".*T.*(Z|[+-]\\d{2}:\\d{2})$");

    /** A KRÉTA időbélyegei lehetnek UTC-sek (Z), eltolásosak vagy zóna nélküliek; mind helyi idővé alakul. */
    public static LocalDateTime parse(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            if (HAS_ZONE.matcher(s).matches()) {
                return OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            }
            if (s.length() == 10) return LocalDate.parse(s).atStartOfDay();
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(s.substring(0, Math.min(19, s.length())));
            } catch (Exception e2) {
                return null;
            }
        }
    }

    public static LocalDate today() { return LocalDate.now(); }
    public static LocalDate mondayOf(LocalDate d) { return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
    public static LocalDate day(LocalDateTime t) { return t.toLocalDate(); }
    public static boolean sameDay(LocalDateTime a, LocalDate b) { return a != null && a.toLocalDate().equals(b); }
    /** 0 = hétfő … 6 = vasárnap */
    public static int wd(LocalDate d) { return d.getDayOfWeek().getValue() - 1; }
    public static int wd(LocalDateTime d) { return d.getDayOfWeek().getValue() - 1; }
    public static String iso(LocalDate d) { return d.toString(); }
    public static LocalDate parseDate(String s) {
        try { return LocalDate.parse(s); } catch (Exception e) { return null; }
    }

    public static long toMillis(LocalDateTime t) { return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    public static LocalDateTime fromMillis(long ms) { return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()); }
    public static long days(LocalDate a, LocalDate b) { return ChronoUnit.DAYS.between(a, b); }

    // ---------- formázás ----------
    private static String two(int n) { return n < 10 ? "0" + n : String.valueOf(n); }

    public static String fmtDate(LocalDate d) { return d == null ? "" : MONTHS[d.getMonthValue() - 1] + " " + d.getDayOfMonth() + "."; }
    public static String fmtDate(LocalDateTime d) { return d == null ? "" : fmtDate(d.toLocalDate()); }
    public static String fmtFull(LocalDate d) { return d == null ? "" : d.getYear() + ". " + MONTHS[d.getMonthValue() - 1] + " " + d.getDayOfMonth() + "."; }
    public static String fmtFull(LocalDateTime d) { return d == null ? "" : fmtFull(d.toLocalDate()); }
    public static String fmtTime(LocalDateTime d) { return d == null ? "" : two(d.getHour()) + ":" + two(d.getMinute()); }

    /** „most”, „5 perce”, „3 órája”, „2 napja” – ts epoch másodpercben */
    public static String ago(double tsSec) {
        double s = Math.max(0, System.currentTimeMillis() / 1000.0 - tsSec);
        if (s < 60) return "most";
        if (s < 3600) return (int) (s / 60) + " perce";
        if (s < 86400) return (int) (s / 3600) + " órája";
        return (int) (s / 86400) + " napja";
    }

    /** Hány perc van még hátra a-tól b-ig, felfelé kerekítve (8:44:10-kor a 8:45-ös végig „1 perc”). */
    public static long minutesUntil(LocalDateTime from, LocalDateTime to) {
        long s = ChronoUnit.SECONDS.between(from, to);
        return s <= 0 ? 0 : (s + 59) / 60;
    }

    /** „45 perc”, „1 óra 5 perc”, „2 óra” */
    public static String duration(long min) {
        if (min < 60) return min + " perc";
        return (min / 60) + " óra" + (min % 60 == 0 ? "" : " " + (min % 60) + " perc");
    }

    public static String relDay(LocalDate d) {
        long diff = days(today(), d);
        if (diff == 0) return "ma";
        if (diff == 1) return "holnap";
        if (diff == -1) return "tegnap";
        if (diff > 1 && diff < 7) return diff + " nap múlva";
        return fmtDate(d);
    }
    public static String relDay(LocalDateTime d) { return relDay(d.toLocalDate()); }

    /** „péntek, szeptember 5.” */
    public static String longDay(LocalDate d) {
        String[] hu = {"január", "február", "március", "április", "május", "június", "július", "augusztus", "szeptember", "október", "november", "december"};
        return DAYS[wd(d)].toLowerCase(Locale.forLanguageTag("hu")) + ", " + hu[d.getMonthValue() - 1] + " " + d.getDayOfMonth() + ".";
    }
}
