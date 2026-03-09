plugins {
    alias(libs.plugins.android.application)
}

base {
    archivesName = "sysproxy"
}

android {
    namespace = "com.github.kr328.sysproxy"

    defaultConfig {
        applicationId = "com.github.kr328.sysproxy"
        versionCode = 10200
        versionName = "1.2.0"
    }

    buildFeatures {
        aidl = true
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

    packaging {
        dex {
            useLegacyPackaging = true
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    compileOnly(project(":hideapi"))

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}