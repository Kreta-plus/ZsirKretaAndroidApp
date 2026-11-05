# ZsírKréta – Android alkalmazás

A [ZsírKréta](https://github.com/Kreta-plus/Weboldal) webes alkalmazás natív, **Java** nyelvű Android változata:
jegyek, órarend, házi, hiányzások és statisztikák a saját KRÉTA-fiókodból, szerver nélkül, a telefonon.

## Telepítés

1. Töltsd le a legújabb APK-t az [`updates/`](updates/) mappából (a verzió a [`latest.json`](updates/latest.json)-ban látszik).
2. Nyisd meg a telefonon. Ha kéri, engedélyezd a böngésződnek az „ismeretlen alkalmazások telepítését”.
3. Utána a frissítéseket az app maga jelzi és telepíti (lásd lent), nem kell többé letölteni.

> Ha korábban a 2.0-s (fejlesztői kulccsal aláírt) változat volt fent, azt egyszer törölni kell, mert a 2.1-től saját
> kiadási kulccsal van aláírva, és az Android más kulccsal aláírt appot nem frissít. A beállításokat előtte a
> Beállítások → Fiók és adatok → Beállítások exportja gombbal mentsd ki, és telepítés után töltsd vissza.

## Frissítés az appon belül

* Indításkor (legfeljebb 6 óránként) az app megnézi az [`updates/latest.json`](updates/latest.json)-t. Ha van újabb verzió,
  a Kezdőlap tetején megjelenik egy kártya az újdonságokkal; a Beállítások → Frissítések résznél kézzel is kereshető.
* „Frissítés”: letölti az APK-t (megszakadásnál folytatja), majd telepítés előtt ellenőrzi az SHA-256 ellenőrzőösszeget, a csomagnevet,
  a verziószámot és azt, hogy **ugyanazzal a kulccsal** van-e aláírva, mint a telepített app.
* Ezután az Android saját telepítője kér megerősítést (első alkalommal az „ismeretlen forrás” engedélyt is). A belépés és a
  beállítások megmaradnak.

## Értesítések

* Az app a háttérben **15 percenként** megnézi a KRÉTA-t (a Beállítások → Fejlesztői résznél, developer módban **1 perc és 2 óra** között állítható),
  és szól, ha van: új jegy, új házi, bejelentett számonkérés, órarend-változás (ma és holnap: elmarad / helyettesítés),
  új hiányzás vagy késés (és ha valami igazolatlan maradt), új feljegyzés, új app-verzió. Este (alapból 19:00) emlékeztet
  a holnapi dolgozatokra, házikra és a táska tartalmára. Fajtánként ki-be kapcsolható, koppintásra a megfelelő képernyő nyílik.
* Az első kör csak megjegyzi a mostani állapotot, így bekapcsoláskor nem jön egyszerre sok régi tétel.
* Késés nélküli futáshoz az app kéri az **értesítési engedélyt** és az **akkumulátor-optimalizálás kikapcsolását**
  („Futhat a háttérben korlátozás nélkül?”). Pontos ébresztőt használ, ami energiatakarékos (Doze) módban is lefut;
  emulátoron Doze-ban kipróbálva percre pontos volt. Néhány gyártónál (Xiaomi, Huawei, Samsung…) ezen felül az
  automatikus indítást is engedélyezni kell, erre az app a Beállításokban útmutatót ad.
* Ha az app épp nyitva van, nem küld értesítést (ott a felület maga jelzi az új adatot).
* A KRÉTA felé ugyanaz a korlát érvényes, mint az appban: adatfajtánként legfeljebb percenként egy kérés.
* **Óra előtti emlékeztető** (Beállítások → Értesítések): X perccel (1–180, alapból 5) **minden óra előtt**, vagy **csak a nap első
  órája előtt** (ilyenkor a napi órák számával, a nap végével és a dolgozatokkal). Ez nem a KRÉTA-ból jön: a telefonon tárolt
  órarendből számol, és a telefon órájára pontos ébresztőt állít, az ébresztőkor nincs hálózati kérés. Elmaradó órára nem szól,
  az óra végén az értesítés magától eltűnik. Friss órarendnél, újraindítás és óraállítás után újraszámol. A háttér-ellenőrzéstől
  független (az kikapcsolva is működik, csak akkor az appban legutóbb letöltött órarendből dolgozik).

## Órarend a telefon órája szerint

Hogy melyik óra tart épp („most · még 23 perc”, haladásjelzővel), mennyi múlva kezdődik a következő, és melyek múltak már el
(halványak), azt az Órarend és a Kezdőlap a telefon órájából számolja, és percváltáskor frissíti, nem a KRÉTA-lekéréskor.
Az Órarend a mai napot követi (éjfél után is átlép; hétvégén a következő hétfőt mutatja), amíg el nem lapozol róla.

## Új verzió kiadása

```
python tools/release.py 2.2 "Új: ..." "Javítva: ..."
git add updates app/build.gradle.kts && git commit -m "ZsírKréta 2.2" && git push
```

Ugyanez operációs rendszerenként: `python tools\release_win.py ...` (Windows) és `python3 tools/release_linux.py ...` (Linux),
ugyanazokkal a paraméterekkel. Ami gépenként más, azt maguk intézik: a Gradle indítása (Linuxon `sh gradlew`, így a futtatási jog
sem kell), az Android SDK megkeresése (ha a `local.properties` másik gép útvonalát tartalmazza, átírja a helyire), és az aláíró
kulcs helye: a `keystore.properties`-ben a `storeFile.windows=` / `storeFile.linux=` sor felülírja a közös `storeFile=`-t
(ezt az Android Studio-s build is így érti). Ha a kulcs nincs meg a gépen, még a verzió átírása előtt leáll.

A szkript beállítja a verziót (versionName = 2.2, versionCode + 1), lebuildeli az aláírt APK-t, ellenőrzi, hogy nem fejlesztői
kulccsal és ugyanazzal a kulccsal van-e aláírva, mint az előző kiadás, majd megírja az `updates/ZsirKreta-2.2.apk`-t és az
`updates/latest.json`-t. Push után a telefonok a következő ellenőrzéskor látják az új verziót (a GitHub pár percig gyorsítótáraz).

**Az aláíró kulcs** (`zsirkreta-release.jks`) és a jelszavai (`keystore.properties`) nincsenek a repóban, és soha ne is kerüljenek
bele. Ha a kulcs elveszik, a már telepített appokra többé nem lehet frissítést kiadni: tarts róla biztonsági másolatot.

## Fejlesztés

* minSdk 26 (Android 8.0), targetSdk/compileSdk 37, Java 17, AGP 9.4, Gradle 9.6
* Függőségek: AppCompat, Material Components, SwipeRefreshLayout, Sentry Android SDK (hibajelentés a GlitchTipbe; DSN és beállítások az `AndroidManifest.xml`-ben) – semmi más (diagramok, HTTP, JSON saját/beépített)
* Android Studióban nyisd meg a repó mappáját; parancssorból: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
* A frissítés tesztelhető helyi szerverről is (csak a debug buildben):
  `./gradlew assembleDebug -PupdateUrl=http://10.0.2.2:8766/updates/latest.json` (az emulátorból a 10.0.2.2 a gép maga).

## Más felépítés, mint a webnél: nincs szerver

A webes változatban egy Flask szerver intézte a KRÉTA-belépést, a gyorsítótárat és a beállítások tárolását.
A telefonon ez mind az appban fut:

| Web (Flask)                     | Telefon                                                                        |
|---------------------------------|--------------------------------------------------------------------------------|
| `kreta.py` (belépés, lekérdezés)| `data/Kreta.java`, `data/Http.java` – ugyanaz a lánc (OAuth2 + PKCE, űrlapkitöltés, token-megújítás, „udvarias” gyorsítótár: TTL, percenkénti frissítés, 429/403 utáni szünet, régi adat hiba esetén) |
| Fernet-titkosított token        | `data/SecureBox.java` – Android Keystore (AES-GCM)                             |
| SQLite (`cache`, `prefs`, …)    | `data/Store.java` – fájlok az app privát tárhelyén                             |
| `prefs.py` (beállítások)        | `data/Prefs.java` – ugyanazok a JSON-kulcsok és tisztítás: a **beállítások exportja a webes és a telefonos app között cserélhető** |
| `demo.py` (teszt fiók)          | `data/Demo.java`                                                               |
| `state.js`, `util.js`           | `data/St.java`, `data/Norm.java`, `util/`                                      |
| `views/*.js`, `story.js`, `charts.js` | `ui/*Screen.java`, `ui/StoryDialog.java`, `ui/Charts.java`               |

## Funkciók (mind megvan)

Kezdőlap (widgetek, mai órák, vicces mondat, story) · Jegyek (félév-szűrő, keresés, jegyszámoló, célok, „hány 5-ös kell”) ·
Statisztika (11 diagram) · Órarend (nap/hét nézet, húzás) · Házi + számonkérések (helyi pipálás) · Hiányzások ·
Feljegyzések · Nevek (saját tanár-/tantárgynevek, becenév) · Tervező (saját házik, dolgozatok) ·
Táska (felszerelés tantárgyanként + teljes képernyős holnapi táska 4 elrendezéssel) ·
Beállítások, csoportokba rendezve, mint a weben (🎨 Megjelenés: téma auto/sötét/világos/AMOLED, a weboldal 12 színtémája, kiemelő szín, háttér- és
kártyastílus, sűrűség, betűméret, lekerekítés · 🏠 Kezdőlap: kezdőoldal, widgetek, vicces szöveg · 📘 Jegyek: súlyozott átlag, célátlag,
elrejtett tantárgyak, jótanuló mód · 🔔 Értesítések: óra előtti emlékeztető, háttér-értesítések · 🧩 Egyedi CSS · 🔐 Fiók és adatok:
export/import, kilépés, fiók törlése · 📲 Frissítések · 🛠️ Fejlesztői; telefonon csoportlista, széles kijelzőn oldalsáv) ·
Story kép (1080×1920, mentés a Képek közé vagy megosztás) · Belépés, mint a weben: a KRÉTA saját oldalán (beépített böngésző), cím beillesztésével, teszt fiók ·
Frissítés, mint a weben: percenként automatikusan (csak amíg az app előtérben van), belépés után azonnal friss adat, a gomb és az
automatikus frissítés együtt is legfeljebb percenként kér a KRÉTA-tól, új adatnál „Frissítve, új adat: …” üzenet.

## Ami szándékosan másképp van

* **Egyedi CSS → egyedi stílus.** Natív alkalmazásban nincs CSS. Helyette a téma változóit (`--accent`, `--bg`, `--surface`, `--g1…--g5`,
  `--radius`, `--card-alpha`, `--border-width`, `--kpi-scale` …) lehet CSS-szerű `--név: érték;` sorokkal állítani; az előbeállítások és a
  „Változó beszúrása” lista megvan. A szelektorokat (`.card { … }`) nem értelmezi.
* **Munkamenetek.** Nincs szerver, így nincs „Kilépés mindenhonnan” és eszközlista; a kilépés a készüléken törli a tokent és a gyorsítótárat
  (a beállítások fiókonként megmaradnak).
* **KRÉTA-végpontok.** A webes README környezeti változói helyett a belépő képernyő „Haladó beállítások” részén szerkeszthetők.
  Ha a belépés nem sikerül, a hibaüzenet „Részletek” része megmutatja a KRÉTA válaszát.
* Új: húzásra frissítés, élő témaváltás újraindítás nélkül, beépített böngészős belépés (a kódot automatikusan elfogja).

## Fontos

A belépés a KRÉTA saját oldalán történik (a jelszó az appba nem kerül), a kódot a beépített böngésző fogja el. Teszt fiókkal
emulátoron kipróbálva, **valódi KRÉTA-fiókkal nem**. Ha a belépés elutasít, a „Részletek” rész és a Haladó beállítások segít;
ha a beépített böngésző nem megy, a „Belépés a cím beillesztésével” a tartalék.

A belépés akkor is megmarad, ha az app napokig nincs megnyitva: a háttérben naponta kétszer megújítja a KRÉTA-tokent
(értesítések nélkül is), és csak akkor kér új belépést, ha a KRÉTA kifejezetten érvénytelennek mondja a tokent
(`invalid_grant`); hálózati vagy szerverhibánál megtartja, és később újrapróbálja.
