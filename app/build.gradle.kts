plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // The Firebase Google Services plugin
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.ilutomo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.ilutomo"
        minSdk = 27
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
}

dependencies {
    // Firebase Bill of Materials (BOM) - keeps versions in sync
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))

    // --- ADDED THESE TWO LINES ---
    implementation("com.google.firebase:firebase-auth-ktx")      // For Login/Sign-up
    implementation("com.google.firebase:firebase-firestore-ktx") // For Account Type storage

    // Existing Firebase libraries
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // Standard Android Libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}