// core/data/build.gradle.kts
plugins {
    alias(libs.plugins.golf.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.hpsmiles.golfsim.core.data"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Robolectric on JDK 17 wants headroom; tasks.withType is DSL-stable across AGP versions.
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
}

dependencies {
    implementation(project(":core:ble"))
    implementation(project(":core:physics"))
    // Non-Compose module under golf-android-library (convention applies the
    // Compose compiler), so the runtime must still reach the compiler —
    // mirrors :core:connect's proven implementation-scoped BOM+ui pattern.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
