import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signature des APK de release : clé privée fournie par variables d'environnement
// (en CI, depuis les secrets GitHub ; voir README). Elle n'est jamais stockée dans le dépôt.
// Sans ces variables, l'APK de release est produit non signé ; les builds debug utilisent
// la clé de debug locale d'Android.
val releaseKeystore: String? = System.getenv("RELEASE_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "fr.gotraceo.live"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.gotraceo.live"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "2.0.4"
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
