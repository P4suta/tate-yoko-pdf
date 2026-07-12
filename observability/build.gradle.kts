plugins {
    id("tateyokopdf.java-conventions")
    id("tateyokopdf.test-conventions")
    id("tateyokopdf.quality-conventions")
}

dependencies {
    // Maps domain exceptions to exit codes / log levels; sanitises paths. Knows only the domain.
    implementation(project(":domain"))
    implementation("ch.qos.logback:logback-classic:1.5.38")
}
