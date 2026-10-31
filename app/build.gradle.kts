plugins {
    id("com.android.application")
}

android {
    namespace = "com.heme.iptvlive"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.heme.iptvlive"
        minSdk = 16
        targetSdk = 35
        versionCode = 626
        versionName = "6.0.26-legacy"
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
        create("pixel") {
            dimension = "device"
            applicationIdSuffix = ".pixel"
            versionNameSuffix = "-pixel"
            buildConfigField("String", "UPDATE_ASSET", "\"IPTV-Live-Pixel.apk\"")
            buildConfigField("boolean", "TV_UI", "false")
            resValue("string", "app_name", "My IPTV")
        }
        create("letv") {
            dimension = "device"
            applicationIdSuffix = ".letv"
            versionNameSuffix = "-letv"
            buildConfigField("String", "UPDATE_ASSET", "\"IPTV-Live-LeTV.apk\"")
            buildConfigField("boolean", "TV_UI", "true")
            resValue("string", "app_name", "My IPTV")
        }
        create("tcl") {
            dimension = "device"
            applicationIdSuffix = ".tcl"
            versionNameSuffix = "-tcl"
            buildConfigField("String", "UPDATE_ASSET", "\"IPTV-Live-TCL.apk\"")
            buildConfigField("boolean", "TV_UI", "true")
            resValue("string", "app_name", "My IPTV")
        }
        create("sony") {
            dimension = "device"
            applicationIdSuffix = ".sony"
            versionNameSuffix = "-sony"
            buildConfigField("String", "UPDATE_ASSET", "\"IPTV-Live-Sony.apk\"")
            buildConfigField("boolean", "TV_UI", "true")
            resValue("string", "app_name", "My IPTV")
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

configurations.all {
    resolutionStrategy {
        capabilitiesResolution.withCapability("org.jetbrains.kotlin:kotlin-stdlib-jdk7") {
            select("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
        }
        capabilitiesResolution.withCapability("org.jetbrains.kotlin:kotlin-stdlib-jdk8") {
            select("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
        }
    }
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
