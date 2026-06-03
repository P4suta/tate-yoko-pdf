plugins {
    `kotlin-dsl`
}

// Plugin marker artifacts so the convention scripts can `id(...)` these plugins.
// Versions are kept in sync with the root build's plugin declarations.
dependencies {
    implementation("com.diffplug.spotless:com.diffplug.spotless.gradle.plugin:8.6.0")
    implementation("net.ltgt.errorprone:net.ltgt.errorprone.gradle.plugin:5.1.0")
    implementation("com.github.spotbugs:com.github.spotbugs.gradle.plugin:6.5.5")
}
