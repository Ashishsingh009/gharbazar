plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ashish.gharbazaar.a2ui.catalog"
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.a2ui.model)
    implementation(libs.androidx.a2ui.engine)
    implementation(libs.androidx.a2ui.compose.runtime)
    implementation(libs.androidx.a2ui.compose.ui)
    implementation(libs.androidx.material3.a2ui)
}
