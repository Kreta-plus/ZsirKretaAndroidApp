package hu.zsirkreta.app.ui;

import android.content.Context;
import android.widget.LinearLayout;

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
}
