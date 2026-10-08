plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "de.timklge.headwind.client.example"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.timklge.headwind.client.example"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":client"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.androidx.lifeycle)
    implementation(libs.bundles.compose.ui)
}
