plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hdrscreen"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hdrscreen"
        minSdk = 34
        targetSdk = 34
        versionCode = 9
        versionName = "1.3.6"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        // viewBinding不再需要（纯代码构建UI）
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 无AndroidX依赖，纯原生Android框架API（API34+完全支持）
}