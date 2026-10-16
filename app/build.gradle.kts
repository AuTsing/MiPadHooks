plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.at.mipadhooks"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.at.mipadhooks"
        minSdk = 26
        targetSdk = 36
        versionCode = 200
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
//    implementation("com.github.kyuubiran:EzXHelper:2.2.1")
    compileOnly("de.robv.android.xposed:api:82")
}