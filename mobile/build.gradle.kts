plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.sequi.mobile"
    compileSdk = 37

    defaultConfig {
        // Must equal the watch app's applicationId, or phone <-> watch sync will not connect.
        applicationId = "app.sequi"
        minSdk = 33
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // Signed with the debug key so it installs like the debug build (phone and watch must share a key),
            // but optimised: much smoother than a debug build.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.foundation:foundation:1.12.1")
    implementation("androidx.compose.ui:ui:1.12.1")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
}
