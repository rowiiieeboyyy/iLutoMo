plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // ADD THIS LINE
    id("com.google.gms.google-services") version "4.4.1" apply false
}