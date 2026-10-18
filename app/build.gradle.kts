import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "studio.cheng.daily"
    compileSdk = 34

    defaultConfig {
        applicationId = "studio.cheng.daily"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "API_BASE", "\"http://10.0.2.2:8000\"")
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val local = Properties()
            val file = rootProject.file("local.properties")
            if (file.exists()) file.inputStream().use { local.load(it) }
            storeFile = rootProject.file(local.getProperty("WUXING_STORE_FILE", "keystore/wuxing-release.jks"))
            storePassword = local.getProperty("WUXING_STORE_PASSWORD", "")
            keyAlias = local.getProperty("WUXING_KEY_ALIAS", "wuxing")
            keyPassword = local.getProperty("WUXING_KEY_PASSWORD", "")
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE", "\"http://10.0.2.2:8000\"")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "API_BASE", "\"http://106.54.61.165:8000\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/INDEX.LIST",
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
}
