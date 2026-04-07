// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false

    // Explicitly add the version here so the app module can find it
    id("com.google.gms.google-services") version "4.4.1" apply false
}