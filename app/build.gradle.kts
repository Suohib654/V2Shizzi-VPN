plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath = System.getenv("V2SHIZZI_KEYSTORE")
val releaseStorePassword = System.getenv("V2SHIZZI_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("V2SHIZZI_KEY_ALIAS")
val releaseKeyPassword = System.getenv("V2SHIZZI_KEY_PASSWORD")
val hasPrivateReleaseKey = !releaseKeystorePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() && !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.fastnode.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fastnode.app"
        minSdk = 29
        targetSdk = 33
        versionCode = 13
        versionName = "1.2.1"
    }

    signingConfigs {
        if (hasPrivateReleaseKey) {
            create("privateRelease") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasPrivateReleaseKey) signingConfigs.getByName("privateRelease") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    implementation("androidx.core:core:1.13.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
