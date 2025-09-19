plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.example.androidbox"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.androidbox"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // WebViewでGASにアクセスするため
        manifestPlaceholders["usesCleartextTraffic"] = "false"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { viewBinding = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // WebView 拡張（任意：UserAgent/安全対策）
    implementation("androidx.webkit:webkit:1.11.0")

    // SAF でのURI操作（DocumentFile）
    implementation("androidx.documentfile:documentfile:1.0.1")
}
