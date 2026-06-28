import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("tateyokopdf.java-conventions")
    id("tateyokopdf.test-conventions")
    id("tateyokopdf.quality-conventions")
    id("info.solidsoft.pitest")
    `java-test-fixtures`
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":port"))
    // SLF4J via the logback binding, matching the single-module classpath. Domain stays log-free.
    implementation("ch.qos.logback:logback-classic:1.5.37")
}

pitest {
    pitestVersion = "1.20.2"
    junit5PluginVersion = "1.2.3"
    targetClasses = listOf("dev.sakashita.tateyokopdf.application.*")
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

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.65".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
