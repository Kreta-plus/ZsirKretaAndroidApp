"""Új ZsírKréta-verzió kiadása az updates/ mappába (innen frissül az app).

Használat (a repó gyökeréből):

    python tools/release.py 2.2 "Új: ..." "Javítva: ..."

Operációs rendszerenként ugyanez: tools/release_win.py (Windows) és tools/release_linux.py (Linux), ugyanazokkal a
paraméterekkel. Ez a fájl a közös rész, magától is fut (a gép rendszerét magától felismeri).

Mit csinál:
  1. beállítja a verziót az app/build.gradle.kts-ben (versionName = 2.2, versionCode + 1),
  2. lebuildeli az aláírt kiadási APK-t (gradlew assembleRelease),
  3. ellenőrzi az aláírást: nem lehet a fejlesztői (debug) kulcs, és ugyanaz kell legyen, mint az előző kiadásé
     (más kulccsal az Android nem frissíti a telepített appot),
  4. bemásolja: updates/ZsirKreta-2.2.apk, és megírja az updates/latest.json-t (méret, SHA-256, újdonságok, előzmények).

Utána csak commitolni és pusholni kell (a végén kiírja a parancsokat).
Verzió nélkül (python tools/release.py "Javítva: ...") a mostani verziószámot adja ki újra, ha az még nincs kiadva.

Ami gépenként más:
  * Android SDK: a local.properties sdk.dir-je, ha ezen a gépen létezik; különben ANDROID_HOME / ANDROID_SDK_ROOT, vagy az
    Android Studio alapmappája (Windows: %LOCALAPPDATA%\\Android\\Sdk, Linux: ~/Android/Sdk). Ha így találja meg, beírja a
    local.properties-be, hogy a Gradle is azt használja.
  * Aláíró kulcs: a keystore.properties storeFile.windows / storeFile.linux sora, ha van, különben a közös storeFile.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
GRADLE = ROOT / "app" / "build.gradle.kts"
UPDATES = ROOT / "updates"
LATEST = UPDATES / "latest.json"
APK_OUT = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
MIN_SDK = 26
KEEP_HISTORY = 30
# "windows" vagy "linux": a release_win.py / release_linux.py rögzíti, magától futtatva a gép rendszere
OS_NAME = "windows" if sys.platform == "win32" else "linux"


def die(msg):
    print("HIBA: " + msg, file=sys.stderr)
    sys.exit(1)


def read_version():
    s = GRADLE.read_text(encoding="utf-8")
    code = int(re.search(r"versionCode\s*=\s*(\d+)", s).group(1))
    name = re.search(r'versionName\s*=\s*"([^"]+)"', s).group(1)
    return code, name


def write_version(code, name):
    s = GRADLE.read_text(encoding="utf-8")
    s = re.sub(r"versionCode\s*=\s*\d+", f"versionCode = {code}", s, count=1)
    s = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{name}"', s, count=1)
    GRADLE.write_text(s, encoding="utf-8", newline="")


def read_props(path):
    """Java .properties fájl (local.properties, keystore.properties) egyszerű olvasása."""
    out = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith(("#", "!")) and "=" in line:
                k, v = line.split("=", 1)
                out[k.strip()] = v.strip().replace("\\:", ":").replace("\\\\", "\\")
    return out


def sdk_candidates():
    lp = read_props(ROOT / "local.properties").get("sdk.dir")
    if lp:
        yield lp
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(var):
            yield os.environ[var]
    if OS_NAME == "windows":
        if os.environ.get("LOCALAPPDATA"):
            yield os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk")
    else:
        yield os.path.expanduser("~/Android/Sdk")


def write_sdk_dir(sdk):
    """sdk.dir beírása a local.properties-be (a többi sort meghagyja), hogy a Gradle is ezt az SDK-t használja."""
    lp = ROOT / "local.properties"
    value = str(sdk).replace("\\", "\\\\").replace(":", "\\:") if OS_NAME == "windows" else str(sdk)
    lines = [l for l in (lp.read_text(encoding="utf-8").splitlines() if lp.exists() else []) if not l.strip().startswith("sdk.dir")]
    lp.write_text("\n".join(lines + ["sdk.dir=" + value]) + "\n", encoding="utf-8", newline="")
    print(f"local.properties: sdk.dir={sdk}")


def sdk_dir():
    current = read_props(ROOT / "local.properties").get("sdk.dir")
    for c in sdk_candidates():
        p = pathlib.Path(c)
        if (p / "build-tools").is_dir():
            if c != current:
                write_sdk_dir(p)
            return p
    die("nem találom az Android SDK-t: állítsd be az ANDROID_HOME-ot, vagy írd a local.properties-be: sdk.dir=...")


def keystore_file():
    """Az aláíró kulcs helye ezen a gépen (ugyanúgy, ahogy az app/build.gradle.kts keresi)."""
    k = read_props(ROOT / "keystore.properties")
    if not k:
        die("hiányzik a keystore.properties (a kiadási kulcs adatai), enélkül nem lehet aláírni")
    key = "storeFile." + OS_NAME
    path = k.get(key) or k.get("storeFile")
    if not path:
        die(f"a keystore.properties-ben nincs megadva a kulcs helye ({key}=... vagy storeFile=...)")
    p = pathlib.Path(path)
    if not p.is_absolute():
        p = ROOT / "app" / p   # a Gradle file() az app mappához képest érti
    if not p.exists():
        die(f"nem találom az aláíró kulcsot: {p}\n"
            f"  Másold erre a gépre a kiadási kulcsot (ugyanazt, amivel az előző verziók készültek), és írd be a helyét\n"
            f"  a keystore.properties-be:  {key}=...")
    return p


def gradle_cmd():
    if OS_NAME == "windows":
        return [str(ROOT / "gradlew.bat")]
    # sh-val indítjuk, így akkor is fut, ha a gradlew-n nincs futtatási jog (pl. Windowsról másolt repó)
    return ["sh", str(ROOT / "gradlew")]


def apksigner():
    bt = sdk_dir() / "build-tools"
    versions = sorted((p for p in bt.iterdir() if p.is_dir()), key=lambda p: [int(x) if x.isdigit() else 0 for x in re.split(r"[.-]", p.name)])
    for v in reversed(versions):
        for name in ("apksigner.bat", "apksigner"):
            if (v / name).exists():
                return v / name
    die("nem találom az apksigner-t az SDK build-tools mappájában")


def signer_of(apk):
    out = subprocess.run([str(apksigner()), "verify", "--print-certs", str(apk)], capture_output=True, text=True)
    if out.returncode != 0:
        die("az APK aláírása érvénytelen:\n" + out.stdout + out.stderr)
    dn = re.search(r"certificate DN: (.+)", out.stdout)
    sha = re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", out.stdout)
    if not sha:
        die("nem sikerült kiolvasni az aláíró tanúsítványt:\n" + out.stdout)
    return (dn.group(1).strip() if dn else ""), sha.group(1)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main(os_name=None):
    global OS_NAME
    if os_name:
        OS_NAME = os_name
    ap = argparse.ArgumentParser(description="Új ZsírKréta-verzió kiadása az updates/ mappába")
    ap.add_argument("items", nargs="+", help="[verzió] majd az újdonságok, pl.: 2.2 \"Új: ...\" \"Javítva: ...\"")
    ap.add_argument("--no-build", action="store_true", help="ne buildeljen, a meglévő app-release.apk-t adja ki")
    a = ap.parse_args()

    # előbb a gépfüggő dolgok, hogy hiánynál ne maradjon átírt verziószám
    if not (ROOT / "keystore.properties").exists():
        die("hiányzik a keystore.properties (a kiadási kulcs adatai), enélkül nem lehet aláírni")
    if not a.no_build:
        keystore_file()
    sdk_dir()

    items = list(a.items)
    code, name = read_version()
    if re.fullmatch(r"\d+(\.\d+){1,2}", items[0]):
        new_name = items.pop(0)
        if new_name != name:
            code, name = code + 1, new_name
            write_version(code, name)
            print(f"Verzió: {name} (versionCode {code})")
    notes = [n.strip() for n in items if n.strip()]
    if not notes:
        die("adj meg legalább egy újdonságot, pl.: python tools/release.py 2.2 \"Új: ...\"")

    old = json.loads(LATEST.read_text(encoding="utf-8")) if LATEST.exists() else None
    if old and old.get("versionCode", 0) >= code:
        die(f"a {old.get('versionName')} ({old.get('versionCode')}) már ki van adva; adj meg újabb verziót, pl.: python tools/release.py 2.2 ...")

    if not a.no_build:
        print("Build: gradlew assembleRelease …")
        r = subprocess.run(gradle_cmd() + ["--no-daemon", "-q", ":app:assembleRelease"], cwd=ROOT)
        if r.returncode != 0:
            die("a build nem sikerült")
    if not APK_OUT.exists():
        die(f"nincs meg a kiadási APK: {APK_OUT}")

    dn, cert = signer_of(APK_OUT)
    if "Android Debug" in dn:
        die("az APK a fejlesztői (debug) kulccsal van aláírva; ilyet nem szabad kiadni")
    if old and old.get("signer") and old["signer"] != cert:
        die("az APK más kulccsal van aláírva, mint az előző kiadás! A telepített appok nem tudnák frissíteni.\n"
            f"  előző: {old['signer']}\n  most:  {cert}")

    UPDATES.mkdir(exist_ok=True)
    apk_name = f"ZsirKreta-{name}.apk"
    shutil.copyfile(APK_OUT, UPDATES / apk_name)
    today = dt.date.today().isoformat()
    history = [{"versionCode": code, "versionName": name, "date": today, "notes": notes}]
    if old:
        history += [v for v in old.get("versions", []) if v.get("versionCode", 0) < code]
    latest = {
        "versionCode": code,
        "versionName": name,
        "date": today,
        "minSdk": MIN_SDK,
        "apk": apk_name,
        "size": (UPDATES / apk_name).stat().st_size,
        "sha256": sha256(UPDATES / apk_name),
        "signer": cert,
        "notes": notes,
        "versions": history[:KEEP_HISTORY],
    }
    LATEST.write_text(json.dumps(latest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"\nKész: updates/{apk_name} ({latest['size'] / 1048576:.1f} MB), updates/latest.json")
    print("Aláíró: " + dn)
    print("\nKözzététel:")
    print(f'  git add updates app/build.gradle.kts && git commit -m "ZsírKréta {name}" && git push')


if __name__ == "__main__":
    main()
