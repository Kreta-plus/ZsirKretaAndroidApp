package hu.zsirkreta.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import hu.zsirkreta.app.data.Models.Homework;
import hu.zsirkreta.app.data.Models.Lesson;
import hu.zsirkreta.app.data.Models.TestItem;

/** Vicces szövegek: a jótanuló mód nyilatkozatai és a kezdőlap holnapi mondata (a webes funny.js megfelelője, ugyanazokkal a szövegekkel). */
public final class Funny {
    private Funny() {}

    private static final Random RND = new Random();

    private static final String[] DISCLAIMERS = {
            "Nem vállalunk felelősséget azért, ha anyád megver, amikor kiderül az igazság.",
            "A fejlesztő kijelenti, hogy semmi köze ahhoz, ha a szüleid ötös alapján ígérnek neked jutalmat, és később csalódnak.",
            "Tudomásul veszem, hogy a tanár a valóságban is látja a valódi jegyeimet, és ezt az oldalt nem érdekli.",
            "Az alkalmazás nem felel a következményekért, ha nagyi ötösök alapján sütit sütni kezd, és az igazság után sírva fakad.",
            "Elfogadom, hogy ez csak a saját kijelzőmön igaz, és a KRÉTA-t nem fogja meghatni.",
            "Vállalom, hogy ezt a képernyőt nem mutatom meg az osztályfőnökömnek, és ha mégis, nem hivatkozom az oldalra.",
            "A készítők nem vállalnak felelősséget semmilyen szobafogságért, mobilelvételért vagy megvonott zsebpénzért.",
            "Tudomásul veszem, hogy az ötös jegy attól még nem lesz valódi, hogy nagyon szeretném.",
            "Esküszöm, hogy a hamis ötösök miatt nem fogok megsértődni, ha a félévi mégis kettes lesz.",
            "A fejlesztő a hatályos szülői jog értelmében minden felelősséget kizár a kiabálásért, sóhajtozásért és a „mit csináltál?!” kérdésért.",
            "Tudomásul veszem, hogy ha a szüleim meglátják a valódi jegyeimet, az nem az alkalmazás hibája.",
            "Kijelentem, hogy ez a mód a lelki békémet szolgálja, és nem a valóságot.",
            "Elfogadom, hogy a tanárok nem tanulnak jobban attól, hogy én ötösökkel látom magam.",
            "Tudomásul veszem, hogy az oldal nem áll jót a következő dolgozat eredményéért.",
            "A készítők nem vállalnak felelősséget az esetleges családi vacsorán kialakuló kínos csendért.",
            "Hozzájárulok, hogy a valóság eltűnjön a kijelzőmről, de a következményei nem.",
            "Megértettem, hogy a jótanuló mód csak mód, nem varázslat, és a magolást sem váltja ki.",
            "Kötelezem magam, hogy ha anyám megkérdezi, nem én voltam, és a lap magától hibásodott meg.",
            "Elfogadom, hogy a hamis jegyek nem számítanak bele az átlagba, csak az önbizalmamba.",
            "A fejlesztő nem felelős semmilyen megvont wifiért, lefoglalt telefonért vagy kiadott ejtőernyőért.",
    };
    private static final Object[][] SUBJ_QUIPS = {
            {Pattern.compile("testnev", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), new String[]{"Holnap testnevelés: a tornazsák már az ajtónál vár, a kifogások még nem.", "Testnevelés lesz. A fáradtságot otthon felejtheted, a zoknit viszont ne.", "Holnap tesi, ma este kezdj el nyújtani, vagy legalább gondolj rá."}},
            {Pattern.compile("matem", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), new String[]{"Holnap matek: x-et megint keresni kell, pedig tegnap is elveszett.", "Matekóra lesz. A számológép nem gondolkodik helyetted, de mellette kevésbé fáj.", "Matek: ahol a betűk is számok, és a számok is betűk lesznek."}},
            {Pattern.compile("fizik", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), new String[]{"Fizika lesz: Newton is megkapta a magáét az almától, te is túléled.", "Holnap fizika, az erő legyen veled, a képletek pedig a füzetedben."}},
            {Pattern.compile("k[ée]mi", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), new String[]{"Kémia lesz. Ha bármi füstölni kezd, az vagy a kísérlet, vagy a házid.", "Holnap kémia: remélem, nem te leszel a katalizátor a bajban."}},
            {Pattern.compile("biol", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), new String[]{"Biológia lesz, kiderül, melyik sejtorganellum vagy az osztályban.", "Holnap biosz: legalább az élet értelmét nem kérdezik, csak a mitokondriumot."}},
            {Pattern.compile("t[öo]rt[ée]n", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), new String[]{"Történelem: holnap megint minden háborúnak az évszáma lesz a lényeg.", "Holnap töri, ha eddig nem tanultad, legalább legyél kreatív az évszámokkal."}},
            {Pattern.compile("magyar", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), new String[]{"Magyar irodalom lesz: a költők szenvedtek, most te jössz.", "Holnap magyar, a vesszők helye még mindig rejtély, de legalább a nyelvtanban megoldható."}},
            {Pattern.compile("angol|n[ée]met", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), new String[]{"Holnap nyelvóra: remélem, tegnap este nem csak a Duolingo bagoly figyelmeztetett.", "Nyelvóra lesz. A szókincs nagy része nálad is megvan, csak épp rossz nyelven."}},
            {Pattern.compile("inform", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), new String[]{"Informatika lesz: ha lefagy a gép, az nem te vagy, hanem a rendszer.", "Holnap infó, és igen, megpróbálhatod ki- és bekapcsolni."}},
            {Pattern.compile("ének|zene", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), new String[]{"Ének-zene lesz. A hangod hibátlan, legfeljebb a hallgatóságnak kell erősnek lennie."}},
            {Pattern.compile("földr", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), new String[]{"Földrajz lesz, legalább már tudod, hol vagy, mert a térképen még keresni kell."}},
    };
    private static final String[] NONE = {"Holnap nincs óra az órarendben. Alhatsz, mint egy medve a téli álmában. 🐻", "Nincs tanítás holnap! Az ébresztőórád szabadságot kapott. 🎉", "Holnap nem kell iskolába menni, és ez nem hiba, ez a rendszer."};
    private static final String[] FEW = {"Csak pár óra lesz holnap, szinte már szabadnap.", "Rövid nap jön, a hátizsák is hálás lesz."};
    private static final String[] MANY = {"Holnap rengeteg óra lesz. Pakolj be sok kajával és sok türelemmel.", "Hosszú nap vár rád: a kávéd (vagy kakaód) is érdemel egy külön helyet a hátizsákban."};
    private static final String[] WD5 = {"Holnap péntek, a hétvége már a küszöbön áll, és kopog.", "Péntek lesz! Már csak egy nap, és a hét befejeződik, ahogy kell."};
    private static final String[] WD1 = {"Holnap hétfő. Szoktam mondani: a hét nem is olyan rossz, csak az első napja."};

    /** Jótanuló mód: minden bekapcsoláskor másik „jogi nyilatkozat” (az előzőt nem ismétli). */
    public static String randomDisclaimer(Context c) {
        SharedPreferences sp = c.getSharedPreferences("ui", Context.MODE_PRIVATE);
        int last = sp.getInt("last_disclaimer", -1), i;
        do { i = RND.nextInt(DISCLAIMERS.length); } while (i == last && DISCLAIMERS.length > 1);
        sp.edit().putInt("last_disclaimer", i).apply();
        return DISCLAIMERS[i];
    }

    private static String two(int n) { return n < 10 ? "0" + n : String.valueOf(n); }

    /** Kezdőlap: egy mondat a holnapi órák, dolgozatok és házik alapján. */
    public static String tomorrowQuip(LocalDate day, List<Lesson> lessons, List<TestItem> tests, List<Homework> homework, Function<String, String> subjName) {
        List<Lesson> ls = new ArrayList<>();
        for (Lesson l : lessons) if (!l.off) ls.add(l);
        int wd = day.getDayOfWeek().getValue();   // 1 = hétfő
        List<String> c = new ArrayList<>();
        if (ls.isEmpty()) c.addAll(Arrays.asList(NONE));
        else {
            Set<String> subs = new LinkedHashSet<>();
            for (Lesson l : ls) subs.add(l.subject);
            for (Object[] row : SUBJ_QUIPS) {
                Pattern re = (Pattern) row[0];
                boolean hit = false;
                for (String s : subs) if (re.matcher(s).find()) { hit = true; break; }
                if (hit) { c.addAll(Arrays.asList((String[]) row[1])); c.addAll(Arrays.asList((String[]) row[1])); }
            }
            if (ls.size() <= 3) c.addAll(Arrays.asList(FEW));
            if (ls.size() >= 7) c.addAll(Arrays.asList(MANY));
            int hh = ls.get(0).start.getHour() * 60 + ls.get(0).start.getMinute();
            if (hh <= 8 * 60) c.add("Az első óra " + two(ls.get(0).start.getHour()) + ":" + two(ls.get(0).start.getMinute()) + "-kor kezdődik, az ágy már most hiányozni fog. 😴");
            if (hh >= 9 * 60) c.add("Holnap később kezdesz, éld ki az extra alvást!");
            if (!tests.isEmpty()) {
                TestItem t = tests.get(0);
                c.add("Holnap " + subjName.apply(t.subject) + " dolgozat lesz" + (t.topic.isEmpty() ? "" : " (" + t.topic + ")") + ". A tanulás még belefér az estébe. 📚");
                c.add("Dolgozat holnap: " + subjName.apply(t.subject) + ". Ma este nem Netflix, hanem jegyzet.");
            }
            if (homework.size() >= 3) c.add("Holnapra " + homework.size() + " házi esedékes. A füzeted már most sóhajtozik.");
            else if (homework.size() == 1) c.add("Holnapra csak egy házi esedékes, szinte már nem is számít. (De számít.)");
            if (wd == 5) c.addAll(Arrays.asList(WD5));
            if (wd == 1) c.addAll(Arrays.asList(WD1));
            Set<String> names = new LinkedHashSet<>();
            for (Lesson l : ls) names.add(subjName.apply(l.subject));
            List<String> first3 = new ArrayList<>(names).subList(0, Math.min(3, names.size()));
            c.add("Holnap " + ls.size() + " óra, " + String.join(", ", first3) + (subs.size() > 3 ? " és még pár" : "") + ". Nem lesz ez olyan vészes.");
        }
        return c.get(RND.nextInt(c.size()));
    }
}
