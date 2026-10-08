plugins {
    alias(libs.plugins.android.library)
    kotlin("plugin.serialization") version "2.4.20"
    `maven-publish`
}

group = "de.timklge.headwind"
version = System.getenv("RELEASE_VERSION") ?: "1.0-SNAPSHOT"

android {
    namespace = "de.timklge.headwind.client"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        aidl = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    sourceSets {
        getByName("main") {
            // Compile the same AIDL contract as the karoo-headwind app so the binder interface descriptors always match.
            aidl.directories.add("../app/src/main/aidl")
        }
    }
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
            }
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/timklge/karoo-headwind")
            credentials {
                username = System.getenv("GPR_USER")
                password = System.getenv("GPR_KEY")
            }
        }
    }
}
