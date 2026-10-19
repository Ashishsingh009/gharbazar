plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ashish.gharbazaar.shell"
    compileSdk { version = release(37) {
            minorApiLevel = 2
        } }
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":localization"))
    implementation(project(":data-local"))
    implementation(project(":data-remote"))
    implementation(project(":sync"))
    implementation(project(":a2ui-catalog"))
    implementation(project(":a2ui-runtime"))
    implementation(project(":feature-onboarding"))
    implementation(project(":feature-mealplan"))
    implementation(project(":feature-grocery"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.a2ui.compose.runtime)
    implementation(libs.androidx.a2ui.compose.ui)
    implementation(libs.androidx.material3.a2ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
