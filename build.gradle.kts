import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import java.net.HttpURLConnection
import java.net.URI

// Apply security patches to the buildscript (plugin) classpath so Dependabot
// alerts on transitive deps like plexus-utils / log4j-core / jackson-core
// are resolved even though they only appear via Gradle plugins.
buildscript {
    val patches =
        mapOf(
            "com.fasterxml.jackson.core:jackson-core" to "2.19.0",
            "org.codehaus.plexus:plexus-utils" to "4.0.2",
            "org.apache.logging.log4j:log4j-core" to "2.26.0",
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
    id("org.graalvm.buildtools.native") version "1.1.1"
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
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testImplementation("nl.jqno.equalsverifier:equalsverifier:4.5")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.1.8")
    testImplementation("net.jqwik:jqwik:1.10.0")
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
        // Zero-warning policy: NullAway findings break the build instead of being whispered as warnings.
        check("NullAway", CheckSeverity.ERROR)
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
        "com.fasterxml.jackson.core:jackson-core" to "2.19.0",
        "org.codehaus.plexus:plexus-utils" to "4.0.2",
        "org.apache.logging.log4j:log4j-core" to "2.26.0",
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
    // -Xshare:off silences the JVM CDS warning that fires when jacoco's javaagent
    // appends to the bootstrap classpath.
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

// ---- `just outdated` plumbing -------------------------------------------------
// 1. ben-manes.versions: only show stable upgrades (skip alpha/beta/rc/M*/SNAPSHOT)
// 2. checkExtraVersions: diff non-Gradle pins (Dockerfile, spotless, jacoco,
//    security-patch coords) against upstream stable releases via GitHub Releases
//    + Maven Central. Wired as a finalizer of dependencyUpdates so a single
//    `./gradlew dependencyUpdates` reports everything.
//
// `just outdated` is the entry point; this block only configures the tasks.

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    return !stableKeyword && !regex.matches(version)
}

tasks.withType<DependencyUpdatesTask>().configureEach {
    rejectVersionIf { isNonStable(candidate.version) }
    outputFormatter = "plain"
    checkForGradleUpdate = true
    finalizedBy("checkExtraVersions")
    // ben-manes 0.54 uses Task.project / non-serializable lambdas, so `just outdated`
    // is invoked with --no-configuration-cache.
}

tasks.register("checkExtraVersions") {
    group = "help"
    description = "Diff non-Gradle pinned versions against upstream stable releases"
    // Capture files and properties at configuration time so doLast doesn't hold
    // a Project reference (keeps the task config-cache compatible despite network IO).
    val dockerfile = rootProject.file("Dockerfile")
    val buildScript = rootProject.file("build.gradle.kts")
    val failOnUpdates =
        providers.gradleProperty("failOnUpdates").map { it.toBoolean() }.getOrElse(false)
    outputs.upToDateWhen { false }

    doLast {
        val dockerfileText = dockerfile.readText()
        val buildScriptText = buildScript.readText()
        val knownDockerArgs = setOf("TYPOS_VERSION", "JUST_VERSION")

        val stableRe = Regex("^[0-9]+(\\.[0-9]+)*$")

        fun parseVersion(v: String): List<Int> = v.split(".").map { it.toIntOrNull() ?: 0 }

        val versionComparator =
            Comparator<String> { a, b ->
                val pa = parseVersion(a)
                val pb = parseVersion(b)
                (0 until maxOf(pa.size, pb.size))
                    .map { pa.getOrElse(it) { 0 }.compareTo(pb.getOrElse(it) { 0 }) }
                    .firstOrNull { it != 0 } ?: 0
            }

        fun fetch(url: String): String? =
            runCatching {
                (URI(url).toURL().openConnection() as HttpURLConnection).run {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("User-Agent", "tate-yoko-pdf-checkExtraVersions")
                    setRequestProperty("Accept", "application/json")
                    inputStream.use { it.bufferedReader().readText() }
                }
            }.getOrNull()

        fun latestGitHub(repo: String): String? {
            val body = fetch("https://api.github.com/repos/$repo/releases/latest") ?: return null
            val tag =
                Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
                    .find(body)
                    ?.groupValues
                    ?.get(1) ?: return null
            return tag.removePrefix("v")
        }

        fun latestMaven(
            group: String,
            artifact: String,
        ): String? {
            val url = "https://search.maven.org/solrsearch/select?q=g:%22$group%22+AND+a:%22$artifact%22&core=gav&rows=200&wt=json"
            val body = fetch(url) ?: return null
            return Regex("\"v\"\\s*:\\s*\"([^\"]+)\"")
                .findAll(body)
                .map { it.groupValues[1] }
                .filter { stableRe.matches(it) }
                .maxWithOrNull(versionComparator)
        }

        fun dockerArg(name: String): String =
            Regex("^ARG ${Regex.escape(name)}=(\\S+)", RegexOption.MULTILINE)
                .find(dockerfileText)
                ?.groupValues
                ?.get(1)
                ?: error("Dockerfile is missing ARG $name=")

        // Find all occurrences so duplicated maps (e.g. the buildscript pin map +
        // the runtime configurations.all pin map) are detected when they drift apart.
        // Filter to version-shaped captures so this script's own regex literals
        // (which themselves contain the patterns) don't self-match.
        fun extractFromBuild(pattern: String): String {
            val matches =
                Regex(pattern)
                    .findAll(buildScriptText)
                    .map { it.groupValues[1] }
                    .filter { stableRe.matches(it) }
                    .toList()
            return when {
                matches.isEmpty() -> error("build.gradle.kts is missing /$pattern/")
                matches.distinct().size == 1 -> matches.first()
                else -> matches.joinToString("/") // visual mismatch flag in the report
            }
        }

        var updates = 0
        var headCount = 0

        fun report(
            name: String,
            current: String,
            latest: String?,
        ) {
            val tag =
                when {
                    latest == null -> {
                        "ERR "
                    }

                    current == latest -> {
                        "OK  "
                    }

                    versionComparator.compare(current, latest) > 0 -> {
                        headCount++
                        "HEAD"
                    }

                    else -> {
                        updates++
                        "UPD "
                    }
                }
            println("[%s] %-22s current=%-12s latest=%s".format(tag, name, current, latest ?: "(fetch failed)"))
        }

        println()
        println("=== Extra pinned versions (non-Gradle) ===")
        report("typos", dockerArg("TYPOS_VERSION"), latestGitHub("crate-ci/typos"))
        report("just", dockerArg("JUST_VERSION"), latestGitHub("casey/just"))
        report(
            "google-java-format",
            extractFromBuild("""googleJavaFormat\("([^"]+)"\)"""),
            latestGitHub("google/google-java-format"),
        )
        report(
            "jacoco",
            extractFromBuild("""toolVersion = "([^"]+)""""),
            latestMaven("org.jacoco", "jacoco"),
        )

        println("--- security-patch pins (manual floors; bump when upstream catches up) ---")
        val securityPins =
            listOf(
                Triple("jackson-core", "com.fasterxml.jackson.core", "jackson-core"),
                Triple("plexus-utils", "org.codehaus.plexus", "plexus-utils"),
                Triple("log4j-core", "org.apache.logging.log4j", "log4j-core"),
            )
        for ((label, g, a) in securityPins) {
            val current = extractFromBuild(""""${Regex.escape("$g:$a")}"\s+to\s+"([^"]+)"""")
            report(label, current, latestMaven(g, a))
        }

        val unknownDockerArgs =
            Regex("^ARG ([A-Z_]+_VERSION)=", RegexOption.MULTILINE)
                .findAll(dockerfileText)
                .map { it.groupValues[1] }
                .filter { it !in knownDockerArgs }
                .toList()
        if (unknownDockerArgs.isNotEmpty()) {
            println()
            for (arg in unknownDockerArgs) {
                println("WARN: unknown pinned version in Dockerfile: ARG $arg=… (add to knownDockerArgs in build.gradle.kts)")
            }
        }

        println()
        println("$updates update(s) available")

        val totalProblems = updates + headCount + unknownDockerArgs.size
        if (failOnUpdates && totalProblems > 0) {
            throw GradleException(
                "$totalProblems pin(s) need attention (updates=$updates, head=$headCount, unknown=${unknownDockerArgs.size}). " +
                    "Re-run without -PfailOnUpdates=true to see the report and resolve.",
            )
        }
    }
}
// ---- end `just outdated` plumbing --------------------------------------------

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
