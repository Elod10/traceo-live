import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signature des APK de release : clé privée fournie par variables d'environnement
// (en CI, depuis les secrets GitHub ; voir README). Elle n'est jamais stockée dans le dépôt.
// Sans ces variables, l'APK de release est produit non signé ; les builds debug utilisent
// la clé de debug locale d'Android.
val releaseKeystore: String? = System.getenv("RELEASE_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

// Adresse de Tracéo. Un build de release ne connaît que la production. Un build de debug peut en
// viser une autre, pour un essai sur téléphone : elle se lit dans `local.properties`, fichier que git
// ne suit pas (clé `traceo.debugUrl`), et n'entre donc jamais dans le dépôt.
val traceoProductionUrl = "https://gotraceo.fr"
val traceoDebugUrl: String = run {
    val file = rootProject.file("local.properties")
    val properties = Properties()
    if (file.exists()) file.inputStream().use(properties::load)
    val url = properties.getProperty("traceo.debugUrl").orEmpty().trim().trimEnd('/')
    if (url.isEmpty()) return@run traceoProductionUrl
    if (!Regex("https?://[A-Za-z0-9.-]+(:[0-9]+)?").matches(url)) {
        // Une clé mal écrite ne doit pas empêcher de construire, surtout pas un release qui ne s'en sert pas.
        logger.warn("traceo.debugUrl de local.properties n'est pas une adresse http(s) sans chemin : le build de debug vise la production.")
        return@run traceoProductionUrl
    }
    url
}

android {
    namespace = "fr.gotraceo.live"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.gotraceo.live"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "2.0.4"
        buildConfigField("String", "TRACEO_URL", "\"$traceoProductionUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "TRACEO_URL", "\"$traceoDebugUrl\"")
            // Un serveur d'essai sans HTTPS n'est joignable que si le build de debug l'autorise
            // (app/src/debug/AndroidManifest.xml).
            manifestPlaceholders["traceoCleartext"] = traceoDebugUrl.startsWith("http://").toString()
        }
        getByName("release") {
            isMinifyEnabled = false
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    // org.json réel pour les tests JVM (android.jar ne contient que des stubs).
    testImplementation("org.json:json:20240303")
}
