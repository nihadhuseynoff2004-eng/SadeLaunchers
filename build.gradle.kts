plugins {
    id("com.android.application") version "8.5.2"
}

android {
    namespace = "com.sade.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sade.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.setSrcDirs(listOf("."))
            java.filter.include("MainActivity.java")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
