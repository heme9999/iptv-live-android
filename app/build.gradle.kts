plugins {
    id("com.android.application")
}

android {
    namespace = "com.heme.iptvlive"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.heme.iptvlive"
        minSdk = 23
        targetSdk = 35
        versionCode = 611
        versionName = "6.0.11"
        buildConfigField("String", "GITHUB_REPO", "\"heme9999/iptv-live-android\"")
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("IPTV_KEYSTORE_PATH")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("IPTV_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("IPTV_KEY_ALIAS")
                keyPassword = System.getenv("IPTV_KEY_PASSWORD")
            }
        }
    }

    flavorDimensions += "device"
    productFlavors {
        create("tcl") {
            dimension = "device"
            applicationIdSuffix = ".tcl"
            versionNameSuffix = "-tcl"
            buildConfigField("String", "UPDATE_ASSET", "\"IPTV-Live-TCL.apk\"")
            buildConfigField("boolean", "TV_UI", "true")
            resValue("string", "app_name", "TCL IPTV Live")
        }
        create("sony") {
            dimension = "device"
            applicationIdSuffix = ".sony"
            versionNameSuffix = "-sony"
            buildConfigField("String", "UPDATE_ASSET", "\"IPTV-Live-Sony.apk\"")
            buildConfigField("boolean", "TV_UI", "true")
            resValue("string", "app_name", "Sony IPTV Live")
        }
        create("pixel") {
            dimension = "device"
            applicationIdSuffix = ".pixel"
            versionNameSuffix = "-pixel"
            buildConfigField("String", "UPDATE_ASSET", "\"IPTV-Live-Pixel.apk\"")
            buildConfigField("boolean", "TV_UI", "false")
            resValue("string", "app_name", "Pixel IPTV Live")
        }
    }

    buildFeatures { buildConfig = true }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
}
