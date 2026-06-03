import org.gradle.testing.jacoco.tasks.JacocoReport

// Shared test stack and execution settings: JUnit 5 + AssertJ + Mockito + jqwik + ArchUnit, the
// reflective-access JVM args system-stubs/jacoco need, and JaCoCo report wiring. Per-module
// coverage thresholds and class excludes live in each module's own build script.
plugins {
    java
    jacoco
}

dependencies {
    "testImplementation"("org.junit.jupiter:junit-jupiter:6.1.0")
    "testImplementation"("org.assertj:assertj-core:3.27.7")
    "testImplementation"("org.mockito:mockito-core:5.23.0")
    "testImplementation"("org.mockito:mockito-junit-jupiter:5.23.0")
    "testImplementation"("nl.jqno.equalsverifier:equalsverifier:4.5")
    "testImplementation"("org.awaitility:awaitility:4.3.0")
    "testImplementation"("uk.org.webcompere:system-stubs-jupiter:2.1.8")
    "testImplementation"("net.jqwik:jqwik:1.10.1")
    "testImplementation"("com.tngtech.archunit:archunit-junit5:1.4.2")
    "testCompileOnly"("org.jspecify:jspecify:1.0.0")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik", "archunit")
    }
    finalizedBy(tasks.named("jacocoTestReport"))
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    // system-stubs needs reflective access to mutate env vars on JDK 17+. -Xshare:off silences
    // the JVM CDS warning that fires when jacoco's javaagent appends to the bootstrap classpath.
    jvmArgs(
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "-Xshare:off",
    )
    testLogging {
        events("failed")
        showStackTraces = true
    }
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required = true
        html.required = true
    }
}
