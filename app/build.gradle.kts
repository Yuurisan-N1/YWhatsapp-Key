plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yuurisan.ywhatsappkey"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yuurisan.ywhatsappkey"
        minSdk = 30
        targetSdk = 34
        versionCode = 5
        versionName = "1.0.4"
        resourceConfigurations += listOf("in", "en")
    }

    val keyStoreFile = file("keystore/ywk.jks")
    val hasKeyStore = keyStoreFile.exists()

    signingConfigs {
        if (hasKeyStore) {
            create("release") {
                storeFile = keyStoreFile
                storePassword = System.getenv("YWK_STORE_PASS") ?: "ywk-release"
                keyAlias = "ywk"
                keyPassword = System.getenv("YWK_KEY_PASS") ?: "ywk-release"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasKeyStore) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "DebugProbesKt.bin")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
