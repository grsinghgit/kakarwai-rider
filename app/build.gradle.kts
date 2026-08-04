plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // ✅ Google Services Plugin (Firebase)
    id("com.google.gms.google-services") version "4.4.2"
}

android {
    namespace = "com.gr.kakarwairider"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gr.kakarwairider"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // ============================================================
    // 1. AndroidX Core Libraries
    // ============================================================
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // ============================================================
    // 2. Navigation Component
    // ============================================================
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // ============================================================
    // 3. Lifecycle (ViewModel + LiveData)
    // ============================================================
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // ============================================================
    // 4. Coroutines
    // ============================================================
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // ============================================================
    // 5. Firebase (BOM + Individual Libraries)
    // ============================================================
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)        // ✅ Phone Auth ke liye
    implementation(libs.firebase.firestore.ktx)   // ✅ Database ke liye
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.messaging.ktx)

    // ============================================================
    // 6. Google Play Services (Maps + Location)
    // ============================================================
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.android.maps.utils)

    // ============================================================
    // 7. Networking (Retrofit + OkHttp)
    // ============================================================
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)

    // ============================================================
    // 8. Image Loading (Glide)
    // ============================================================
    implementation(libs.glide)

    // ============================================================
    // 9. Google Places (Autocomplete)
    // ============================================================
    implementation(libs.places)

    // ============================================================
    // 10. Testing
    // ============================================================
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // GridLayout (for 2-column grid)
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    // ✅ Coroutines with Play Services (for Firestore await())
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    // ✅ Razorpay SDK
    implementation(libs.razorpay.checkout)
    // ✅ Add these if not present
    implementation("org.json:json:20230227")
    implementation("com.google.android.play:app-update:2.1.0")
    // ✅ Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.3.0")

}