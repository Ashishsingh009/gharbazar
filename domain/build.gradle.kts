plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(libs.org.json)
    testImplementation(libs.junit)
}

tasks.test {
    environment(
        "GHARBAZAAR_CONTRACTS",
        rootProject.projectDir.resolve("contracts").absolutePath,
    )
}
