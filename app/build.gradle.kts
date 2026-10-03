plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.novashield.vpn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.novashield.vpn"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.zaneschepke:amneziawg-android:2.3.7")
}
