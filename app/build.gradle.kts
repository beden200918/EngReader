plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreFile = rootProject.file("signing/keystore.jks")

android {
    namespace = "com.example.engreader"
    compileSdk = 35

    signingConfigs {
        if (keystoreFile.exists()) {
            create("stable") {
                storeFile = keystoreFile
                storePassword = "engreader123"
                keyAlias = "engreader"
                keyPassword = "engreader123"
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.engreader.v2"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        getByName("debug") {
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    androidResources { noCompress += "db" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
