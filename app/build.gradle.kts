plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.glassbrowser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.glassbrowser"
        minSdk = 26
        targetSdk = 34
        versionCode = 44
        versionName = "44.0"
    }

    // Saját, rögzített aláíró kulcs (a projektben van): így a GitHubon és az Android Studióban
    // készült APK ugyanazzal a kulccsal van aláírva, és az új verzió a régire frissíthető.
    signingConfigs {
        create("glass") {
            storeFile = file("glass.keystore")
            storePassword = "glassbrowser"
            keyAlias = "glass"
            keyPassword = "glassbrowser"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("glass")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("glass")
        }
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.webkit:webkit:1.11.0")
}
