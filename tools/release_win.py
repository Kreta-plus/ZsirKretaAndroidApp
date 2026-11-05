"""Új ZsírKréta-verzió kiadása Windowson (ugyanaz, mint a release.py, csak windowsos útvonalakkal).

Használat (a repó gyökeréből):

    python tools\\release_win.py 2.4 "Új: ..." "Javítva: ..."

Windowson:
  * a Gradle-t a gradlew.bat-tal indítja, az apksigner.bat-ot használja,
  * az SDK-t a local.properties-ből, az ANDROID_HOME-ból vagy a %LOCALAPPDATA%\\Android\\Sdk-ból veszi,
  * az aláíró kulcs helyét a keystore.properties storeFile.windows sorából (ha nincs, a közös storeFile-ból), pl.:
        storeFile.windows=C:/Zsirkreta/keys/zsirkreta-release.jks
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import release  # noqa: E402

if __name__ == "__main__":
    if sys.platform != "win32":
        release.die("ez a windowsos kiadó szkript; Linuxon a tools/release_linux.py-t futtasd")
    release.main("windows")
