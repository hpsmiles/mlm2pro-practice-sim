// core/connect/build.gradle.kts
plugins {
    alias(libs.plugins.golf.android.library)
}

android {
    namespace = "com.hpsmiles.golfsim.core.connect"
}

dependencies {
    implementation(project(":core:ble"))
    // Env fix (disclosed deviation): the golf-android-library convention
    // applies the Compose compiler plugin to every consumer; :core:connect is
    // non-Compose, so the runtime must still reach the compiler. This mirrors
    // :core:designsystem's proven implementation-scoped BOM+ui pattern.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    testImplementation(libs.junit)
}
