import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("tateyokopdf.java-conventions")
    id("tateyokopdf.test-conventions")
    id("tateyokopdf.quality-conventions")
    `java-test-fixtures`
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":port"))
    implementation("org.apache.pdfbox:pdfbox:3.0.7")
    // xmpbox ships with PDFBox and shares its version — it builds the pdfaid / Dublin Core / Adobe
    // PDF XMP packet required for PDF/A conformance.
    implementation("org.apache.pdfbox:xmpbox:3.0.7")
    implementation("ch.qos.logback:logback-classic:1.5.34")

    // PdfFixtures builds real PDFs with PDFBox for tests across modules.
    testFixturesImplementation("org.apache.pdfbox:pdfbox:3.0.7")
    testFixturesImplementation("org.jspecify:jspecify:1.0.0")
}

// QpdfLinearizer is a thin out-of-process wrapper whose defensive branches (bundle resolution,
// ProcessBuilder timeout, thread interruption) cannot be unit-tested without unnatural scaffolding;
// SamplePdfGenerator is a dev tool. Both are excluded from the coverage floor (their public paths
// are still exercised — see QpdfLinearizerTest / ProcessRunnerTest).
val coverageExcludes =
    listOf(
        "dev/sakashita/tateyokopdf/infrastructure/pdfbox/tools/**",
        "dev/sakashita/tateyokopdf/infrastructure/qpdf/QpdfLinearizer.class",
    )

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } }),
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
