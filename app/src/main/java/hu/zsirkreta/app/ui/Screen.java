package hu.zsirkreta.app.ui;

import android.content.Context;
import android.widget.LinearLayout;

import java.time.LocalDateTime;

import hu.zsirkreta.app.MainActivity;

/**
 * Egy képernyő (nézet). A render() a kapott konténert tölti fel; adat- vagy beállításváltozáskor
 * az activity újra meghívja (a webes render(root) függvények mintájára). A képernyők állapota
 * (szűrők, nyitott elemek) statikus mezőkben él, így az újrarajzolás nem veszti el.
 */
public abstract class Screen {
    protected final MainActivity act;

    protected Screen(MainActivity act) { this.act = act; }

    protected Context c() { return act; }

    /** A felső sávban megjelenő cím. */
    public abstract String title();

    public abstract void render(LinearLayout root);

    /** Másik képernyőről érkeztünk ide (nem újrarajzolás, nem témaváltás). */
    public void entered() {}

    /** Vissza gomb / gesztus: true, ha a képernyő maga kezelte (pl. egy alpanelt zárt be). */
    public boolean onBack() { return false; }

    /**
     * A telefon órája szólt (percváltás, időállítás). true: az idő múlása miatt megváltozott, ami látszik
     * (pl. véget ért egy óra, kezdődik a következő), ezért újra kell rajzolni. A KRÉTA-lekéréstől független.
     */
    public boolean clockChanged(LocalDateTime now) { return false; }

    /** Percenkénti apró frissítés újrarajzolás nélkül (pl. „még 12 perc”), ha a clockChanged false volt. */
    public void tick(LocalDateTime now) {}
}
