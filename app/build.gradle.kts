import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
    id("io.github.takahirom.roborazzi") version "1.64.0"
}

// API keys are read from local.properties (gitignored) into BuildConfig — never committed to git.
val keyProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "dev.soupslurpr.transcribro"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "28.2.13676358"

    // Roborazzi renders Compose to PNG in a JVM (Robolectric) unit test — design iteration without an emulator.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    defaultConfig {
        // Fork identity. Namespace/package stay dev.soupslurpr.transcribro (internal, cosmetic);
        // only the installed applicationId + display name are rebranded. Rename to taste.
        applicationId = "dev.kerr.voicetranslate"
        minSdk = 29
        targetSdk = 36
        versionCode = 7
        versionName = versionCode.toString()

        // Personal API keys from local.properties (gitignored); empty string in a clean checkout.
        buildConfigField("String", "GROQ_API_KEY", "\"${keyProps.getProperty("GROQ_API_KEY", "")}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${keyProps.getProperty("GEMINI_API_KEY", "")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            // arm64-only: drops ~24MB of x86_64 emulator libs. Apple Silicon emulators are arm64 too,
            // so this still runs in the emulator on this Mac. Re-add "x86_64" only for Intel emulators.
            abiFilters.addAll(setOf("arm64-v8a"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Debug-signed so the personal build is installable without a release keystore.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

dependencies {

    implementation(project(":lib"))

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.activity:activity-ktx:1.10.1")

    implementation(platform("androidx.compose:compose-bom:2025.07.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("com.google.accompanist:accompanist-permissions:0.37.3")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")

    // Roborazzi — render Compose UI to PNG locally for design iteration (JVM, no emulator).
    testImplementation(platform("androidx.compose:compose-bom:2025.07.00"))
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.64.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.64.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}