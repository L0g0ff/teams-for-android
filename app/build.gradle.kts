plugins {
    id("com.android.application")
}

val releaseKeystore = System.getenv("ANDROID_KEYSTORE_PATH")
val ciBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toInt()
val baseVersionCode = 3
val baseVersionName = "0.1.2"

android {
    namespace = "nl.wapnet.teamswrapper"
    compileSdk = 37

    defaultConfig {
        applicationId = "nl.wapnet.teamswrapper"
        minSdk = 26
        targetSdk = 35
        // ponytail: supports 100,000 total CI runs before version-code ranges overlap.
        versionCode = ciBuildNumber?.let { baseVersionCode * 100_000 + it } ?: baseVersionCode
        versionName = ciBuildNumber?.let { "$baseVersionName+build.$it" } ?: baseVersionName
        // PR test builds only: allow chrome://inspect over USB to diagnose Teams in the WebView.
        resValue("bool", "webview_debugging", (System.getenv("GITHUB_EVENT_NAME") == "pull_request").toString())
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
