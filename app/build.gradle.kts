import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version "2.0.20"
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
}

android {
    namespace = "de.timklge.karooheadwind"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.timklge.karooheadwind"
        minSdk = 26
        targetSdk = 35
        versionCode = 100 + (System.getenv("BUILD_NUMBER")?.toInt() ?: 1)
        versionName = System.getenv("RELEASE_VERSION") ?: "1.0"
    }

    signingConfigs {
        create("release") {
            val env: MutableMap<String, String> = System.getenv()
            keyAlias = env["KEY_ALIAS"]
            keyPassword = env["KEY_PASSWORD"]

            val base64keystore: String = env["KEYSTORE_BASE64"] ?: ""
            val keystoreFile: File = File.createTempFile("keystore", ".jks")
            keystoreFile.writeBytes(Base64.getDecoder().decode(base64keystore))
            storeFile = keystoreFile
            storePassword = env["KEYSTORE_PASSWORD"]
        }
    }
    buildTypes {
        debug {
            isMinifyEnabled = false
            firebaseCrashlytics {
                mappingFileUploadEnabled = false
            }
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.register("addGoogleServicesJson") {
    description = "Adds google-services.json to the project"
    group = "build"

    doLast {
        val googleServicesJson = System.getenv("GOOGLE_SERVICES_JSON_BASE64")
            ?.let { Base64.getDecoder().decode(it) }
            ?.let { String(it) }
        if (googleServicesJson != null) {
            val jsonFile = file("$projectDir/google-services.json")
            jsonFile.writeText(googleServicesJson)
            println("Added google-services.json to the project")
        } else {
            println("No GOOGLE_SERVICES_JSON_BASE64 environment variable found, skipping...")
        }
    }
}

tasks.register("generateManifest") {
    description = "Generates manifest.json with current version information"
    group = "build"

    doLast {
        val manifestFile = file("$projectDir/manifest.json")
        val manifest = mapOf(
            "label" to "Headwind",
            "packageName" to "de.timklge.karooheadwind",
            "iconUrl" to "https://github.com/timklge/karoo-headwind/releases/latest/download/karoo-headwind.png",
            "latestApkUrl" to "https://github.com/timklge/karoo-headwind/releases/latest/download/app-release.apk",
            "latestVersion" to android.defaultConfig.versionName,
            "latestVersionCode" to android.defaultConfig.versionCode,
            "developer" to "github.com/timklge",
            "description" to "Open-source extension that provides headwind direction, wind speed, forecast and other weather data fields.",
            "releaseNotes" to "* Fix weather data download from Open-Meteo via iOS companion app (thx @keefar!)\n" +
                    "* Add relative grade, relative elevation gain data fields\n" +
                    "* Fix precipitation forecast field\n" +
                    "* Interpolate between forecasted and current weather data\n" +
                    "* Add OpenWeatherMap support contributed by lockevod\n",
            "screenshotUrls" to listOf(
                "https://github.com/timklge/karoo-headwind/releases/latest/download/preview1.png",
                "https://github.com/timklge/karoo-headwind/releases/latest/download/preview3.png",
                "https://github.com/timklge/karoo-headwind/releases/latest/download/preview2.png",
                "https://github.com/timklge/karoo-headwind/releases/latest/download/preview0.png",
            )
        )

        val gson = groovy.json.JsonBuilder(manifest).toPrettyString()
        manifestFile.writeText(gson)
        println("Generated manifest.json with version ${android.defaultConfig.versionName} (${android.defaultConfig.versionCode})")
    }
}

tasks.named("assemble") {
    dependsOn("generateManifest")
    dependsOn("addGoogleServicesJson")
}

dependencies {
    implementation(libs.mapbox.sdk.turf)
    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.androidx.lifeycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.appwidget.preview)
    implementation(libs.androidx.glance.preview)
    implementation(libs.firebase.crashlytics)
    testImplementation(kotlin("test"))
}
