"""Új ZsírKréta-verzió kiadása Linuxon (ugyanaz, mint a release.py, csak linuxos útvonalakkal).

Használat (a repó gyökeréből):

    python3 tools/release_linux.py 2.4 "Új: ..." "Javítva: ..."

Linuxon:
  * a Gradle-t sh-val indítja (a Windowsról jött repóban a gradlew-n nincs futtatási jog),
  * az SDK-t a local.properties-ből, az ANDROID_HOME-ból vagy a ~/Android/Sdk-ból veszi,
  * az aláíró kulcs helyét a keystore.properties storeFile.linux sorából (ha nincs, a közös storeFile-ból), pl.:
        storeFile.linux=/home/<felhasználó>/keys/zsirkreta-release.jks
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import release  # noqa: E402

if __name__ == "__main__":
    if not sys.platform.startswith("linux"):
        release.die("ez a linuxos kiadó szkript; Windowson a tools/release_win.py-t futtasd")
    release.main("linux")
