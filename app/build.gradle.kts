import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing: read from a gitignored keystore.properties at the repo root
// (see README > Releasing). Absent the file (e.g. CI, fresh clones) the release
// build falls back to unsigned, keeping `./gradlew build` green everywhere.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.hpsmiles.golfsim"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hpsmiles.golfsim"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:ble"))
    implementation(project(":core:physics"))
    // M4b Task 4 (mechanical prerequisite): the connect flow in :app imports
    // Mlm2proGattClient/Mlm2proScanner/ConnectionState from :core:connect.
    implementation(project(":core:connect"))
    // M5: session/shot persistence + bag records.
    implementation(project(":core:data"))
    // M8: strokes-gained scoring (SkillBand/Lie/ExpectedStrokes/ShotInput).
    implementation(project(":core:scoring"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
