plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.brightnesscontrol"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.brightnesscontrol"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "VERSION_URL", "\"https://raw.githubusercontent.com/your-name/brightness-control/main/version.json\"")
        buildConfigField("String", "APK_URL", "\"https://github.com/your-name/brightness-control/raw/main/apk/BrightnessControl.apk\"")
        buildConfigField("String", "GITHUB_URL", "\"https://github.com/your-name/brightness-control\"")
        buildConfigField("String", "ISSUES_URL", "\"https://github.com/your-name/brightness-control/issues\"")
        buildConfigField("String", "AUTHOR_EMAIL", "\"author@example.com\"")
    }

    signingConfigs {
        create("release") {
            val storeFileValue = rootProject.file("keystore/brightness-release.jks")
            storeFile = storeFileValue
            storePassword = providers.gradleProperty("SIGNING_STORE_PASSWORD").orNull ?: "brightness-dev"
            keyAlias = "brightness"
            keyPassword = providers.gradleProperty("SIGNING_KEY_PASSWORD").orNull ?: "brightness-dev"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
