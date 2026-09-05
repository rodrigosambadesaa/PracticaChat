plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chat.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chat.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    // A release APK must be signed before it can be installed. For CI or
    // production builds, provide a dedicated keystore through environment
    // variables. The local debug keystore fallback keeps `assembleRelease`
    // usable on a developer machine without putting credentials in Git.
    signingConfigs {
        create("release") {
            val configuredKeystore = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
            if (configuredKeystore != null) {
                storeFile = file(configuredKeystore)
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
            } else {
                storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Keep the Java API snapshot in the repository for reference, but compile
    // only the Kotlin implementation in this Kotlin module to avoid duplicate
    // classes with the same fully-qualified name.
    sourceSets["main"].java.exclude("net/i2p/android/router/util/ConnectivityAndInternetAccess.java")
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
}
