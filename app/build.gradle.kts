plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.mobilgaleri"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mobilgaleri"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Compose Compiler artık Kotlin 2.0 ile plugin tarafından yönetiliyor; composeOptions kaldırıldı.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packagingOptions {
        pickFirst("lib/**/libtensorflowlite_jni.so")
        pickFirst("lib/**/libtensorflowlite_flex_jni.so")
        pickFirst("lib/**/libtensorflowlite_gpu_jni.so")
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    aaptOptions { noCompress("tflite","lite","task") }

}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Coil (görselleri yüklemek için)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    implementation("com.google.android.material:material:1.12.0")
// Theme.Material3.* stilleri için
    implementation("androidx.core:core-splashscreen:1.0.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ML Kit Face Detection (on-device)
    implementation("com.google.mlkit:face-detection:16.1.6")

    // TensorFlow Lite (embedding için placeholder)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")   // ✅ FileUtil burada
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("com.quickbirdstudios:opencv:4.5.3.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    implementation("com.google.mediapipe:tasks-vision:latest.release")

    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.0")
}