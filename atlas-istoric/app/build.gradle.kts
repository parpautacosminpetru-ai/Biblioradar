plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val permanentKeystore = System.getenv("ATLAS_RELEASE_KEYSTORE_PATH")

android {
    namespace = "ro.atlasistoric"
    compileSdk = 35

    defaultConfig {
        applicationId = "ro.atlasistoric"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.5.0-rc1"
    }

    signingConfigs {
        // Real production releases require a permanent private key, never checked into Git.
        if (!permanentKeystore.isNullOrBlank()) {
            create("atlasRelease") {
                storeFile = file(permanentKeystore)
                storePassword = System.getenv("ATLAS_RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("ATLAS_RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("ATLAS_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (!permanentKeystore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("atlasRelease")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    // Bundled model: OCR Latin (including Romanian) works without a network connection.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}
