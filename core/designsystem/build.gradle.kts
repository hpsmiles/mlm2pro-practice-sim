// core/designsystem/build.gradle.kts
plugins {
    alias(libs.plugins.golf.android.library)
}

android {
    namespace = "com.hpsmiles.golfsim.core.designsystem"
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
}
