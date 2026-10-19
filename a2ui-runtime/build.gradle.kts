plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ashish.gharbazaar.a2ui.runtime"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    defaultConfig {
        minSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":a2ui-catalog"))
    implementation(libs.androidx.a2ui.model)
    implementation(libs.kotlinx.coroutines.android)
}
