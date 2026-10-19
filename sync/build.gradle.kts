plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ashish.gharbazaar.sync"
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
    implementation(project(":data-local"))
    implementation(project(":data-remote"))
    implementation(libs.androidx.work)
    implementation(libs.kotlinx.coroutines.android)
}
