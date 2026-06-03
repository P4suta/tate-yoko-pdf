import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask

// Shared formatting and bytecode-analysis conventions: Spotless (google-java-format) and SpotBugs
// at MAX effort / MEDIUM confidence, sharing the one exclude filter at the repository root.
plugins {
    java
    id("com.diffplug.spotless")
    id("com.github.spotbugs")
}

spotless {
    java {
        googleJavaFormat("1.35.0")
        target("src/**/*.java")
        targetExclude("build/**", "**/generated/**")
        removeUnusedImports()
        formatAnnotations()
    }
    kotlinGradle {
        ktlint()
    }
}

spotbugs {
    toolVersion = "4.9.6"
    effort = Effort.MAX
    reportLevel = Confidence.MEDIUM
    ignoreFailures = false
    showStackTraces = true
    excludeFilter = rootProject.file("config/spotbugs/exclude.xml")
}

// Limit SpotBugs to production code: test / fixture code uses Mockito and assertion patterns that
// generate noisy false positives. The fixtures task only exists where java-test-fixtures applies,
// so match by name rather than named(...) to stay no-op where absent.
tasks.matching { it.name == "spotbugsTest" || it.name == "spotbugsTestFixtures" }
    .configureEach { enabled = false }

tasks.withType<SpotBugsTask>().configureEach {
    reports {
        create("html") { required.set(true) }
        create("xml") { required.set(true) }
    }
}
