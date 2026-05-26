import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

// Apply security patches to the buildscript (plugin) classpath so Dependabot
// alerts on transitive deps like plexus-utils / log4j-core / jackson-core
// are resolved even though they only appear via Gradle plugins.
buildscript {
    val patches =
        mapOf(
            "com.fasterxml.jackson.core:jackson-core" to "2.18.6",
            "org.codehaus.plexus:plexus-utils" to "4.0.3",
            "org.apache.logging.log4j:log4j-core" to "2.25.4",
        )
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            patches["${requested.group}:${requested.name}"]?.let { useVersion(it) }
        }
    }
}

plugins {
    java
    application
    jacoco
    `java-test-fixtures`
    id("com.gradleup.shadow") version "9.4.1"
    id("org.graalvm.buildtools.native") version "1.1.0"
    id("com.diffplug.spotless") version "8.5.1"
    id("net.ltgt.errorprone") version "5.1.0"
    id("com.github.ben-manes.versions") version "0.54.0"
    id("gg.jte.gradle") version "3.2.4"
}

group = "dev.sakashita"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

application {
    mainClass = "dev.sakashita.tateyokopdf.Main"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.pdfbox:pdfbox:3.0.7")
    implementation("info.picocli:picocli:4.7.7")
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("io.javalin:javalin:7.2.2")
    implementation("gg.jte:jte:3.2.4")
    implementation("gg.jte:jte-runtime:3.2.4")
    jteGenerate("gg.jte:jte-native-resources:3.2.4")

    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    compileOnly("org.jspecify:jspecify:1.0.0")

    annotationProcessor("info.picocli:picocli-codegen:4.7.7")

    errorprone("com.google.errorprone:error_prone_core:2.49.0")
    errorprone("com.uber.nullaway:nullaway:0.13.4")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("io.javalin:javalin-testtools:7.2.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("nl.jqno.equalsverifier:equalsverifier:3.17.5")
    testImplementation("org.awaitility:awaitility:4.2.2")
    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.1.7")
    testImplementation("net.jqwik:jqwik:1.9.2")
    testCompileOnly("org.jspecify:jspecify:1.0.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testFixturesImplementation("org.apache.pdfbox:pdfbox:3.0.7")
    testFixturesImplementation("org.jspecify:jspecify:1.0.0")
    testFixturesImplementation("io.javalin:javalin:7.2.2")
    testFixturesImplementation("gg.jte:jte:3.2.4")
    testFixturesImplementation("gg.jte:jte-runtime:3.2.4")
}

jte {
    generate()
    binaryStaticContent = true
    contentType = gg.jte.ContentType.Html
    jteExtension("gg.jte.nativeimage.NativeResourcesExtension")
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

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.errorprone {
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*/build/generated/.*"
        check("NullAway", CheckSeverity.WARN)
        option("NullAway:AnnotatedPackages", "dev.sakashita.tateyokopdf")
        option("NullAway:JSpecifyMode", "true")
        option("NullAway:ExternalInitAnnotations", "picocli.CommandLine.Command")
    }
}

jacoco {
    toolVersion = "0.8.13"
}

// Dependabot security alerts: pin transitive deps on every runtime/test
// configuration. (The buildscript classpath is patched in the top-level
// `buildscript {}` block.)
val securityPatches =
    mapOf(
        "com.fasterxml.jackson.core:jackson-core" to "2.18.6",
        "org.codehaus.plexus:plexus-utils" to "4.0.3",
        "org.apache.logging.log4j:log4j-core" to "2.25.4",
    )

configurations.all {
    resolutionStrategy.eachDependency {
        securityPatches["${requested.group}:${requested.name}"]?.let { useVersion(it) }
    }
}

tasks.test {
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik")
    }
    finalizedBy(tasks.jacocoTestReport)
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    forkEvery = 0
    // system-stubs needs reflective access to mutate env vars on JDK 17+.
    jvmArgs(
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    )
    testLogging {
        events("failed")
        showStackTraces = true
    }
}

val jacocoClassExcludes =
    listOf(
        "dev/sakashita/tateyokopdf/tools/**",
        "dev/sakashita/tateyokopdf/Main.class",
        "dev/sakashita/tateyokopdf/web/WebLauncher.class",
        "dev/sakashita/tateyokopdf/web/WebLauncher\$*.class",
        "dev/sakashita/tateyokopdf/web/BrowserLauncher.class",
    )

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) { exclude(jacocoClassExcludes) }
            },
        ),
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) { exclude(jacocoClassExcludes) }
            },
        ),
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.78".toBigDecimal()
            }
        }
        rule {
            element = "PACKAGE"
            includes = listOf("dev.sakashita.tateyokopdf.domain.*")
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
        rule {
            element = "PACKAGE"
            includes = listOf("dev.sakashita.tateyokopdf.application")
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
        rule {
            element = "PACKAGE"
            includes = listOf("dev.sakashita.tateyokopdf.infrastructure.*")
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
        rule {
            // WS pump (`onProgressWs` thread) and download streaming are exercised in M4
            // end-to-end smoke tests; this baseline guards against regressions in submit/lookup.
            element = "PACKAGE"
            includes = listOf("dev.sakashita.tateyokopdf.web.routes")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.50".toBigDecimal()
            }
        }
        rule {
            element = "PACKAGE"
            includes = listOf("dev.sakashita.tateyokopdf.web.job")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
        rule {
            element = "PACKAGE"
            includes = listOf("dev.sakashita.tateyokopdf.web.lifecycle")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
        rule {
            element = "PACKAGE"
            includes = listOf("dev.sakashita.tateyokopdf.observability")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }

tasks.shadowJar {
    archiveBaseName = "tate-yoko-pdf"
    archiveClassifier = "all"
    archiveVersion = ""
    mergeServiceFiles()
}

tasks.register<JavaExec>("createSamplePdf") {
    group = "verification"
    description = "Generate a sample multi-page PDF for manual / smoke testing"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.sakashita.tateyokopdf.tools.SamplePdfGenerator"
    args = listOf("build/test-data/sample.pdf", "4")
}

graalvmNative {
    binaries {
        named("main") {
            mainClass = application.mainClass
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            // Windows-1252 and other charsets used by PDFBox's BaseParser static init.
            buildArgs.add("-H:+UnlockExperimentalVMOptions")
            buildArgs.add("-H:+AddAllCharsets")
            // Force AWT into headless mode at build time so PDFBox' Raster/ColorModel
            // <clinit> does not try to load X11-only graphics during the image build.
            buildArgs.add("-J-Djava.awt.headless=true")
            resources.includedPatterns.add(".*\\.xml")
            resources.includedPatterns.add(".*\\.properties")
        }
    }
}
