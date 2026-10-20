import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing key lives outside the repo. Point TVWALL_KEYSTORE_PROPERTIES
// (or the default path below) at a file with storeFile, storePassword, keyAlias
// and keyPassword. Without it, release builds are signed with the debug key.
val keystoreProps = file(
    System.getenv("TVWALL_KEYSTORE_PROPERTIES")
        ?: "${System.getProperty("user.home")}/.config/tvwall/keystore.properties"
).takeIf { it.exists() }?.let { f -> Properties().apply { f.inputStream().use(::load) } }

android {
    namespace = "com.ibala.tvwall"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ibala.tvwall"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
