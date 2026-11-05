import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Kiadási aláírás: a kulcs a repón kívül van, az adatait a (gitből kihagyott) keystore.properties adja.
// Ugyanaz a kulcs kell minden kiadáshoz, különben a telefon nem tudja frissíteni az appot.
val signing = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// A frissítések leírója (updates/latest.json) a GitHub-repóban. Tesztbuildhez felülírható: -PupdateUrl=...
val defaultUpdateUrl = "https://raw.githubusercontent.com/Kreta-plus/ZsirKretaAndroidApp/main/updates/latest.json"

android {
    namespace = "hu.zsirkreta.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "hu.zsirkreta.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 9
        versionName = "2.5"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (!signing.isEmpty) {
            create("release") {
                // a kulcs helye gépenként más lehet: a storeFile.windows / storeFile.linux sor felülírja a közös storeFile-t
                val os = if (System.getProperty("os.name").startsWith("Windows")) "windows" else "linux"
                storeFile = file(signing.getProperty("storeFile.$os") ?: signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["sentryEnvironment"] = "debug"
            buildConfigField("String", "UPDATE_URL", "\"${(project.findProperty("updateUrl") as String?) ?: defaultUpdateUrl}\"")
        }
        release {
            manifestPlaceholders["sentryEnvironment"] = "production"
            buildConfigField("String", "UPDATE_URL", "\"$defaultUpdateUrl\"")
            if (!signing.isEmpty) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.swiperefreshlayout)
    // hibajelentés a GlitchTipbe (Sentry-kompatibilis SDK, a manifestből indul el magától)
    implementation(libs.sentry.android)
}
