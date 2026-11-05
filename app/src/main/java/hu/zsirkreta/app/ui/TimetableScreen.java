package hu.zsirkreta.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import hu.zsirkreta.app.MainActivity;
import hu.zsirkreta.app.R;
import hu.zsirkreta.app.data.Kreta.KretaError;
import hu.zsirkreta.app.data.Models.Lesson;
import hu.zsirkreta.app.data.Models.Raw;
import hu.zsirkreta.app.data.Norm;
import hu.zsirkreta.app.data.St;
import hu.zsirkreta.app.util.Async;
import hu.zsirkreta.app.util.Dt;

/**
 * Órarend (nap / hét nézet). Hogy melyik óra tart épp, mennyi van hátra belőle és mennyi múlva kezdődik a következő,
 * azt a telefon órája alapján számoljuk, és percváltáskor frissítjük (MainActivity.clockTick), nem a KRÉTA-lekéréskor.
 */
public final class TimetableScreen extends Screen {
    private static LocalDate monday = Dt.mondayOf(Dt.today());
    private static int sel = -1;                // a kiválasztott nap (0 = hétfő)
    private static boolean weekMode = false;
    /** Amíg nem lapoztak el a mai naptól, a nézet a telefon szerinti mai napot követi (éjfél után is). */
    private static boolean follow = true;
    // a legutóbb mutatott hét: amíg a frissebb adat megjön, ez látszik („Betöltés…” villanás helyett)
    private static String lastKey;
    private static Raw lastRaw, toasted;

    private LinearLayout dayBody;
    private List<Lesson> shown = new ArrayList<>();         // a most kirajzolt órák
    private String drawnKey = "";                           // melyik óra volt múlt / most / következő a rajzoláskor
    private final List<Consumer<LocalDateTime>> live = new ArrayList<>();   // percenként frissülő feliratok

    public TimetableScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Órarend"; }

    /** Vízszintes húzás: előző/következő nap. Az érintést nem fogyasztja el. */
    private static final class SwipeLayout extends LinearLayout {
        interface OnSwipe { void swipe(int dir); }
        private final GestureDetector det;
        SwipeLayout(Context c, OnSwipe cb) {
            super(c);
            det = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                    if (a == null || b == null) return false;
                    float dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
                    if (Math.abs(vx) > 900 && Math.abs(dx) > Ui.dp(70) && Math.abs(dx) > 2 * Math.abs(dy)) {
                        final int dir = dx < 0 ? 1 : -1;
                        // az érintés feldolgozása után építjük újra a képernyőt, nem közben
                        Async.ui(() -> cb.swipe(dir));
                        return true;
                    }
                    return false;
                }
            });
        }
        @Override public boolean dispatchTouchEvent(MotionEvent ev) {
            det.onTouchEvent(ev);
            return super.dispatchTouchEvent(ev);
        }
    }

    /** Vékony haladásjelző: az épp tartó óra eltelt része. */
    private static final class Progress extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float frac;
        Progress(Context c, int color) {
            super(c);
            track.setColor(Theme.withAlpha(Theme.muted, 0.22f));
            fill.setColor(color);
        }
        void set(float f) { frac = Math.max(0f, Math.min(1f, f)); invalidate(); }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            cv.drawRoundRect(0, 0, w, h, r, r, track);
            if (frac > 0) cv.drawRoundRect(0, 0, Math.max(h, w * frac), h, r, r, fill);
        }
    }

    // ---------- a mai nap a telefon órája szerint ----------
    /** A „mai” nap: hétköznap ma, hétvégén a következő hétfő. */
    private static LocalDate todayTarget() {
        LocalDate t = Dt.today();
        int wd = Dt.wd(t);
        return wd < 5 ? t : Dt.mondayOf(t).plusDays(7);
    }

    private static void syncToday() {
        LocalDate t = todayTarget();
        monday = Dt.mondayOf(t);
        sel = Dt.wd(t);
    }

    private static boolean atToday() {
        LocalDate t = todayTarget();
        return monday.equals(Dt.mondayOf(t)) && (weekMode || sel == Dt.wd(t));
    }

    @Override public void entered() { follow = true; }

    private void goDay(int d) {
        sel += d;
        if (sel > 4) { monday = monday.plusDays(7); sel = 0; }
        else if (sel < 0) { monday = monday.minusDays(7); sel = 4; }
        follow = atToday();
        act.rerender();
    }

    private void goWeek(int d) {
        monday = monday.plusDays(7L * d);
        follow = atToday();
        act.rerender();
    }

    private void goToday() {
        syncToday();
        follow = true;
        act.rerender();
    }

    // ---------- óra-állapot (a telefon órájából) ----------
    private static boolean isNow(Lesson l, LocalDateTime now) { return !l.start.isAfter(now) && now.isBefore(l.end); }
    private static boolean isPast(Lesson l, LocalDateTime now) { return !l.end.isAfter(now); }

    /** Ha épp szünet van (vagy még nem kezdődött a nap): a ma következő, el nem maradó óra. Óra közben null. */
    private static Lesson nextLesson(List<Lesson> ls, LocalDateTime now) {
        for (Lesson l : ls) {
            if (l.off || !Dt.sameDay(l.start, now.toLocalDate())) continue;
            if (isNow(l, now)) return null;
            if (l.start.isAfter(now)) return l;
        }
        return null;
    }

    /** Óránként egy betű: elmúlt / most / következő / később. Ha változik, újra kell rajzolni. */
    private static String clockKey(List<Lesson> ls, LocalDateTime now) {
        StringBuilder b = new StringBuilder(ls.size());
        Lesson next = nextLesson(ls, now);
        for (Lesson l : ls) b.append(isPast(l, now) ? 'p' : isNow(l, now) ? 'n' : l == next ? 'x' : 'f');
        return b.toString();
    }

    @Override public boolean clockChanged(LocalDateTime now) {
        if (follow && !atToday()) return true;   // új nap kezdődött: a nézet ugorjon rá
        return dayBody != null && !clockKey(shown, now).equals(drawnKey);
    }

    @Override public void tick(LocalDateTime now) { for (Consumer<LocalDateTime> u : live) u.accept(now); }

    private void addLive(Consumer<LocalDateTime> u, LocalDateTime now) { u.accept(now); live.add(u); }

    // ---------- kirajzolás ----------
    @Override public void render(LinearLayout root) {
        if (follow || sel < 0) syncToday();
        LocalDate today = Dt.today();

        // vezérlők
        LinearLayout nav = Ui.hbox(c(), 8);
        nav.addView(Ui.iconButton(c(), R.drawable.ic_chevron_left, Theme.text, () -> goWeek(-1)));
        TextView range = Ui.bold(c(), Dt.fmtDate(monday) + " – " + Dt.fmtDate(monday.plusDays(6)));
        range.setGravity(Gravity.CENTER);
        nav.addView(range, Ui.grow());
        nav.addView(Ui.iconButton(c(), R.drawable.ic_chevron_right, Theme.text, () -> goWeek(1)));
        root.addView(nav, Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout row = Ui.hbox(c(), 10);
        row.addView(Ui.seg(c(), new String[]{"day", "week"}, new String[]{"Nap", "Hét"}, weekMode ? "week" : "day", k -> { weekMode = k.equals("week"); follow = atToday(); act.rerender(); }), Ui.grow());
        row.addView(Ui.button(c(), "Ma", Ui.B_DEFAULT, this::goToday));
        root.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));

        if (!weekMode) {
            LinearLayout strip = Ui.hbox(c(), 6);
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                LocalDate d = monday.plusDays(i);
                boolean on = i == sel, isToday = d.equals(today);
                LinearLayout chip = Ui.vbox(c(), 3);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(0, Ui.dp(8), 0, Ui.dp(8));
                chip.setBackground(Ui.ripple(on ? Ui.shape(Theme.accent, Math.max(10, Theme.radius - 4))
                        : Ui.shape(Theme.surface, Math.max(10, Theme.radius - 4), isToday ? Theme.accent : Theme.border, isToday ? 1.5f : 1), Math.max(10, Theme.radius - 4)));
                chip.addView(Ui.text(c(), Dt.DAYS[i].substring(0, 2), 12, on ? Theme.onAccent : Theme.muted, true), Ui.lp(Ui.WRAP, Ui.WRAP));
                chip.addView(Ui.text(c(), String.valueOf(d.getDayOfMonth()), 17, on ? Theme.onAccent : Theme.text, true), Ui.lp(Ui.WRAP, Ui.WRAP));
                chip.setOnClickListener(v -> { sel = idx; follow = atToday(); act.rerender(); });
                strip.addView(chip, Ui.lp(0, Ui.WRAP, 1f));
            }
            root.addView(strip, Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        SwipeLayout body = new SwipeLayout(c(), dir -> { if (!weekMode) goDay(dir); });
        body.setOrientation(LinearLayout.VERTICAL);
        body.setDividerDrawable(Ui.gap(Theme.gap, true));
        body.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        body.setMinimumHeight(Ui.dp(360));
        dayBody = body;
        shown = new ArrayList<>();
        live.clear();
        root.addView(body, Ui.lp(Ui.MATCH, Ui.WRAP));
        load();
    }

    private void load() {
        final String key = Dt.iso(monday);
        final LocalDate mon = monday;
        Raw cached = St.weekCache.get(key);
        if (cached != null) { fill(cached); return; }
        final Raw prev = key.equals(lastKey) ? lastRaw : null;
        if (prev != null) fill(prev);
        else {
            dayBody.removeAllViews();
            TextView wait = Ui.muted(c(), "Betöltés…");
            wait.setGravity(Gravity.CENTER);
            wait.setPadding(0, Ui.dp(40), 0, Ui.dp(40));
            dayBody.addView(wait, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        Async.run(() -> St.fetchWeek(mon, false), r -> {
            St.weekCache.put(key, r);
            if (act.currentScreen() == this && mon.equals(monday) && (prev == null || !prev.body.equals(r.body))) fill(r);
        }, e -> {
            if (prev != null || act.currentScreen() != this || !mon.equals(monday)) return;
            dayBody.removeAllViews();
            LinearLayout card = Ui.card(c());
            card.addView(Ui.empty(c(), e.getMessage() == null ? "Hiba az órarend betöltésekor" : e.getMessage()));
            dayBody.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
            if (e instanceof KretaError && ((KretaError) e).relogin()) act.showLogin("Lejárt a belépés, jelentkezz be újra");
        });
    }

    private void fill(Raw res) {
        dayBody.removeAllViews();
        live.clear();
        lastKey = Dt.iso(monday);
        lastRaw = res;
        List<Lesson> ls = St.fillTestLessons(Norm.lessons(res.data), monday);
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = Dt.today();
        List<Lesson> vis = new ArrayList<>();
        if (weekMode) {
            for (int i = 0; i < 5; i++) {
                LocalDate d = monday.plusDays(i);
                List<Lesson> dl = ls.stream().filter(l -> Dt.sameDay(l.start, d)).collect(Collectors.toList());
                vis.addAll(dl);
                dayBody.addView(weekDay(i, d, dl, now, d.equals(today)), Ui.lp(Ui.MATCH, Ui.WRAP));
            }
        } else {
            LocalDate d = monday.plusDays(sel);
            List<Lesson> dl = ls.stream().filter(l -> Dt.sameDay(l.start, d)).collect(Collectors.toList());
            vis.addAll(dl);
            TextView head = Ui.muted(c(), Dt.longDay(d) + (dl.isEmpty() ? "" : " · " + dl.size() + " óra"));
            dayBody.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));
            if (dl.isEmpty()) {
                LinearLayout card = Ui.card(c());
                card.addView(Ui.empty(c(), "Nincs óra 🎉"));
                dayBody.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
            }
            Lesson next = nextLesson(dl, now);
            for (Lesson l : dl) dayBody.addView(lessonCard(l, now, l == next), Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        shown = vis;
        drawnKey = clockKey(vis, now);
        if (res.stale && res != toasted) { toasted = res; act.toast("Régebbi adatot mutatok, a KRÉTA most nem elérhető"); }
    }

    private static float progress(Lesson l, LocalDateTime now) {
        long all = ChronoUnit.SECONDS.between(l.start, l.end);
        return all <= 0 ? 1f : ChronoUnit.SECONDS.between(l.start, now) / (float) all;
    }

    private LinearLayout lessonCard(Lesson l, LocalDateTime now, boolean next) {
        boolean nowOn = isNow(l, now), past = isPast(l, now);
        int accent = Theme.subjColor(l.subject);
        LinearLayout card = Ui.hbox(c(), 12);
        card.setBackground(Ui.shape(Theme.withAlpha(Theme.surface, Theme.cardAlpha), Theme.radius,
                nowOn ? Theme.accent : l.sub.isEmpty() ? Theme.border : Theme.g[3], nowOn || !l.sub.isEmpty() ? 2 : Theme.borderW));
        int p = Ui.dp(Theme.pad);
        card.setPadding(p, p, p, p);
        card.setAlpha(l.off ? 0.55f : past ? 0.6f : 1f);

        LinearLayout time = Ui.vbox(c(), 2);
        time.setGravity(Gravity.CENTER_HORIZONTAL);
        time.addView(Ui.text(c(), l.nr != null ? l.nr + "." : "", 12, Theme.muted, true), Ui.lp(Ui.WRAP, Ui.WRAP));
        time.addView(Ui.text(c(), Dt.fmtTime(l.start), 15, Theme.text, true), Ui.lp(Ui.WRAP, Ui.WRAP));
        time.addView(Ui.small(c(), Dt.fmtTime(l.end)), Ui.lp(Ui.WRAP, Ui.WRAP));
        card.addView(time, Ui.lp(Ui.dp(48), Ui.WRAP));

        View bar = new View(c());
        bar.setBackground(Ui.shape(accent, 3));
        card.addView(bar, Ui.lp(Ui.dp(4), Ui.MATCH));

        LinearLayout info = Ui.vbox(c(), 4);
        TextView name = Ui.h3(c(), St.subjName(l.subject));
        if (l.off) name.setPaintFlags(name.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        info.addView(name, Ui.lp(Ui.MATCH, Ui.WRAP));
        String who = St.teacherName(l.sub.isEmpty() ? l.teacher : l.sub);
        info.addView(Ui.muted(c(), (l.room.isEmpty() ? "—" : l.room) + (who.isEmpty() ? "" : " · " + who)), Ui.lp(Ui.MATCH, Ui.WRAP));
        if (!l.topic.isEmpty()) {
            TextView t = Ui.small(c(), l.topic);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(t, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        Ui.Flow chips = Ui.flow(c(), 6);
        if (nowOn && !l.off) {
            TextView ch = Ui.chip(c(), "most", Ui.C_ACCENT);
            chips.addView(ch);
            addLive(t -> ch.setText("most · még " + Dt.duration(Dt.minutesUntil(t, l.end))), now);
        } else if (nowOn) chips.addView(Ui.chip(c(), "most", Ui.C_ACCENT));
        if (next) {
            TextView ch = Ui.chip(c(), "", Ui.C_ACCENT);
            chips.addView(ch);
            addLive(t -> {
                long m = Dt.minutesUntil(t, l.start);
                ch.setText(m <= 0 ? "most kezdődik" : Dt.duration(m) + " múlva");
            }, now);
        }
        if (l.off) chips.addView(Ui.chip(c(), "elmarad", Ui.C_BAD));
        if (!l.sub.isEmpty()) chips.addView(Ui.chip(c(), "helyettesítés", Ui.C_WARN));
        if (l.test) chips.addView(Ui.chip(c(), "dolgozat", Ui.C_WARN));
        if (l.hw) chips.addView(Ui.chip(c(), "házi"));
        if (l.fake) chips.addView(Ui.chip(c(), "teszt óra"));
        if (chips.getChildCount() > 0) info.addView(chips, Ui.lp(Ui.MATCH, Ui.WRAP));
        if (nowOn && !l.off) {
            Progress pr = new Progress(c(), Theme.accent);
            info.addView(pr, Ui.margin(Ui.lp(Ui.MATCH, Ui.dp(4)), 0, 2, 0, 0));
            addLive(t -> pr.set(progress(l, t)), now);
        }
        card.addView(info, Ui.grow());
        return card;
    }

    private View weekDay(int i, LocalDate d, List<Lesson> dl, LocalDateTime now, boolean isToday) {
        LinearLayout box = Ui.vbox(c(), 8);
        LinearLayout head = Ui.hbox(c(), 8);
        head.setBackground(Ui.shape(isToday ? Theme.accent : Theme.surface2, Math.max(10, Theme.radius - 4)));
        head.setPadding(Ui.dp(12), Ui.dp(9), Ui.dp(12), Ui.dp(9));
        int tc = isToday ? Theme.onAccent : Theme.text;
        head.addView(Ui.text(c(), Dt.DAYS[i], 15, tc, true), Ui.grow());
        head.addView(Ui.text(c(), Dt.fmtDate(d), 13, isToday ? Theme.withAlpha(tc, 0.85f) : Theme.muted, false));
        box.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));
        if (dl.isEmpty()) box.addView(Ui.small(c(), "Nincs óra"), Ui.margin(Ui.lp(Ui.MATCH, Ui.WRAP), 12, 0, 0, 0));
        Lesson next = nextLesson(dl, now);
        for (Lesson l : dl) {
            LinearLayout it = Ui.item(c());
            boolean nowOn = isNow(l, now);
            if (nowOn || l == next) it.setBackground(Ui.shape(Theme.surface2, Math.max(8, Theme.radius - 4), Theme.accent, 1.5f));
            it.setAlpha(l.off ? 0.55f : isPast(l, now) ? 0.6f : 1f);
            it.addView(Ui.dot(c(), Theme.subjColor(l.subject)));
            TextView name = Ui.bold(c(), (l.nr != null ? l.nr + ". " : "") + St.subjName(l.subject));
            if (l.off) name.setPaintFlags(name.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            String who = St.teacherName(l.sub.isEmpty() ? l.teacher : l.sub);
            TextView sub = Ui.small(c(), Dt.fmtTime(l.start) + "–" + Dt.fmtTime(l.end) + " · " + (l.room.isEmpty() ? "—" : l.room) + (who.isEmpty() ? "" : " · " + who)
                    + (l.sub.isEmpty() ? "" : " · helyettesítés") + (l.off ? " · elmarad" : ""));
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            it.addView(Rows.twoLine(c(), name, sub), Ui.grow());
            if (nowOn && !l.off) {
                TextView ch = Ui.chip(c(), "", Ui.C_ACCENT);
                it.addView(ch);
                addLive(t -> ch.setText("még " + Dt.duration(Dt.minutesUntil(t, l.end))), now);
            } else if (l == next) {
                TextView ch = Ui.chip(c(), "", Ui.C_ACCENT);
                it.addView(ch);
                addLive(t -> ch.setText(Dt.duration(Dt.minutesUntil(t, l.start)) + " múlva"), now);
            } else if (l.test) it.addView(Ui.chip(c(), "dolgozat", Ui.C_WARN));
            else if (l.hw) it.addView(Ui.chip(c(), "házi"));
            box.addView(it, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        return box;
    }
}
