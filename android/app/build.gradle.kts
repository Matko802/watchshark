import java.io.File

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "watchshark.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.10.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("io.ktor:ktor-client-core:3.6.0")
            implementation("io.ktor:ktor-client-content-negotiation:3.6.0")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
            implementation("io.coil-kt.coil3:coil:3.1.0")
            implementation("io.coil-kt.coil3:coil-compose:3.1.0")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.1.0")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.6.0")
            implementation("io.coil-kt.coil3:coil-video:3.1.0")
            implementation("androidx.core:core-ktx:1.16.0")
            implementation("androidx.appcompat:appcompat:1.7.1")
            implementation("com.google.android.material:material:1.13.0")
            implementation("androidx.activity:activity-compose:1.10.1")
            implementation("androidx.media3:media3-exoplayer:1.6.1")
            implementation("androidx.media3:media3-ui:1.6.1")
            implementation("androidx.media3:media3-datasource:1.6.1")
            implementation("androidx.media3:media3-database:1.6.1")
            implementation("androidx.work:work-runtime-ktx:2.10.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        }
        wasmJsMain.dependencies {
            implementation("io.ktor:ktor-client-js:3.6.0")
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "watchshark.duckdns.org"
    generateResClass = always
}

android {
    namespace = "watchshark.duckdns.org"
    compileSdk = 37

    defaultConfig {
        applicationId = "watchshark.duckdns.org"
        minSdk = 26
        targetSdk = 35
        versionCode = 10926
        versionName = "1.9.26"
        buildConfigField("String", "APP_URL", "\"https://watchshark.duckdns.org\"")
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("RELEASE_STORE_FILE")
                ?: project.findProperty("RELEASE_STORE_FILE") as String?
            val storePass = System.getenv("RELEASE_STORE_PASSWORD")
                ?: project.findProperty("RELEASE_STORE_PASSWORD") as String?
            val alias = System.getenv("RELEASE_KEY_ALIAS")
                ?: project.findProperty("RELEASE_KEY_ALIAS") as String?
            val keyPass = System.getenv("RELEASE_KEY_PASSWORD")
                ?: project.findProperty("RELEASE_KEY_PASSWORD") as String?
            if (!storePath.isNullOrEmpty() && File(storePath).exists()) {
                storeFile = File(storePath)
                storePassword = storePass ?: "android"
                keyAlias = alias ?: "watchshark"
                keyPassword = keyPass ?: storePass ?: "android"
            } else {
                storeFile = rootProject.file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}
