plugins {
    id("com.android.application")
}

android {
    namespace = "me.damon.glasshome"
    compileSdk = 36

    defaultConfig {
        applicationId = "me.damon.glasshome"
        minSdk = 26
        // 31 keeps Play Protect quiet on Android 12 TVs; package visibility is declared in the
        // manifest so the launcher can still enumerate every installed app.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.1.0")
}
