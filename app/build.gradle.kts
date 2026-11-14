plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.morvehtv.envoltorio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.morvehtv.envoltorio"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SITE_URL", "\"https://morvehtv.com/\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Sin dependencias externas: el WebView y las vistas vienen con Android.
}
