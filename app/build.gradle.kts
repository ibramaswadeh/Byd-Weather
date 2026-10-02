plugins {
    id("com.android.application")
}

val releaseKeystore = providers.environmentVariable("BYD_WEATHER_KEYSTORE").orNull
val releaseStorePassword = providers.environmentVariable("BYD_WEATHER_STORE_PASSWORD").orNull
val releaseKeyPassword = providers.environmentVariable("BYD_WEATHER_KEY_PASSWORD").orNull

android {
    namespace = "com.ibramaswadeh.bydweather"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ibramaswadeh.bydweather"
        minSdk = 26
        // Target Android 10 to support starting the location foreground service at boot.
        targetSdk = 29
        versionCode = 4
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    if (releaseKeystore != null && releaseStorePassword != null
            && releaseKeyPassword != null) {
        signingConfigs {
            create("weatherRelease") {
                storeFile = file(releaseKeystore)
                storePassword = releaseStorePassword
                keyAlias = "byd-weather"
                keyPassword = releaseKeyPassword
            }
        }
        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs.getByName("weatherRelease")
            }
        }
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
