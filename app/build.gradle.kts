plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.github.kr328.sysproxy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.github.kr328.sysproxy"
        versionCode = 1
        versionName = "1.0"

        setProperty("archivesBaseName", "sysproxy")
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
    implementation(libs.lspass.hiddenapibypass)
}