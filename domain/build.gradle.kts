import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("tateyokopdf.java-conventions")
    id("tateyokopdf.test-conventions")
    id("tateyokopdf.quality-conventions")
    id("info.solidsoft.pitest")
}

// The pure core: no project dependencies and no third-party runtime libraries.

// Mutation testing (warning-only thresholds today; read the kill rate, then tighten).
pitest {
    pitestVersion = "1.20.2"
    junit5PluginVersion = "1.2.3"
    targetClasses = listOf("dev.sakashita.tateyokopdf.domain.*")
    testStrengthThreshold = 0
    mutationThreshold = 0
    coverageThreshold = 0
    failWhenNoMutations = false
    timestampedReports = false
    outputFormats = listOf("HTML", "XML")
    jvmArgs =
        listOf(
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "-Xshare:off",
        )
}

// Domain is the most-tested layer: the strictest coverage floor.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.95".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
