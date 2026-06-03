import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

// Shared Java compilation conventions for every production module: Java 25 toolchain, the
// Error Prone + NullAway zero-warning null-safety gate, the security-patch resolution strategy,
// and the Javadoc doclint gate. Module-specific dependencies and coverage rules live in each
// module's own build script.
plugins {
    java
    id("net.ltgt.errorprone")
}

group = "dev.sakashita"
version = "2.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
}

dependencies {
    "compileOnly"("org.jspecify:jspecify:1.0.0")
    "errorprone"("com.google.errorprone:error_prone_core:2.49.0")
    "errorprone"("com.uber.nullaway:nullaway:0.13.4")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.errorprone {
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*/build/generated/.*"
        // Zero-warning policy: NullAway findings break the build instead of being whispered.
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "dev.sakashita.tateyokopdf")
        option("NullAway:JSpecifyMode", "true")
    }
}

// Javadoc doclint: validate cross-references, HTML, and syntax of the Javadoc we ship.
// `-missing` keeps the gate on correctness of written docs rather than exhaustive coverage.
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:all,-missing", "-quiet")
        addBooleanOption("Werror", true)
    }
}

tasks.named("check") { dependsOn(tasks.named("javadoc")) }

// Dependabot security alerts: pin transitive deps on every runtime/test configuration.
val securityPatches =
    mapOf(
        "org.codehaus.plexus:plexus-utils" to "4.0.2",
        "org.apache.logging.log4j:log4j-core" to "2.26.0",
    )

configurations.all {
    resolutionStrategy.eachDependency {
        securityPatches["${requested.group}:${requested.name}"]?.let { useVersion(it) }
    }
}
