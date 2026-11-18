plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.alexandre.focus.nat"
    compileSdk = 35

    defaultConfig {
        // Même identifiant que l'app actuelle : la bascule finale s'installera par-dessus sans perte.
        applicationId = "com.alexandre.focus"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "2.0-native"
        resValue("string", "app_name", "Focus")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        // Pendant le chantier : s'installe À CÔTÉ de l'app actuelle (suffixe .native), sans toucher à ses données.
        getByName("debug") {
            applicationIdSuffix = ".native"
            resValue("string", "app_name", "Focus Native")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
}
