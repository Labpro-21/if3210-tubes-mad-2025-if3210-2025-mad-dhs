plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.google.services)
    id("kotlin-parcelize")
    id("kotlin-kapt")
}

android {
    namespace = "com.tubes.purry"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tubes.purry"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }

    kapt {
        correctErrorTypes = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0") // ganti 1.15.0 → 1.12.0
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.6.1") // 1.7.0 → 1.6.1
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // 2.2.1 → 2.1.4
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.6.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.6.0")
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.ui.graphics.android)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("com.google.code.gson:gson:2.10.1")

    // Retrofit for network calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")

    // Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.14.2")
    annotationProcessor("com.github.bumptech.glide:compiler:4.14.2")

    // Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha03")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")

    // for keying models
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Google Maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // CardView untuk UI
    implementation("androidx.cardview:cardview:1.0.0")

    implementation("com.google.firebase:firebase-dynamic-links-ktx:21.0.1")

    // ZXing untuk generate dan scan QR
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // For charts (MPAndroidChart)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // For CSV export
    implementation("com.opencsv:opencsv:5.7.1")

    // For coroutines (if not already added)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // For lifecycle components (if not already added)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // For RecyclerView and CardView (if not already added)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // Material Design Components (if not already added)
    implementation("com.google.android.material:material:1.11.0")

    // For image loading (Glide - if not already added)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Google Maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")



//        implementation(libs.androidx.core.ktx)
//        implementation(libs.androidx.appcompat)
//        implementation(libs.material)
//        implementation(libs.androidx.constraintlayout)
//        implementation(libs.androidx.lifecycle.livedata.ktx)
//        implementation(libs.androidx.lifecycle.viewmodel.ktx)
//        implementation(libs.androidx.navigation.fragment.ktx)
//        implementation(libs.androidx.navigation.ui.ktx)
//        testImplementation(libs.junit)
//        androidTestImplementation(libs.androidx.junit)
//        androidTestImplementation(libs.androidx.espresso.core)
}

kapt {
    correctErrorTypes = true
}
