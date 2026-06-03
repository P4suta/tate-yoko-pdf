import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import javax.inject.Inject

// Apply security patches to the buildscript (plugin) classpath so Dependabot
// alerts on transitive deps like plexus-utils / log4j-core are resolved even
// though they only appear via Gradle plugins.
buildscript {
    val patches =
        mapOf(
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
    id("com.gradleup.shadow") version "9.4.2"
    id("com.diffplug.spotless") version "8.6.0"
    id("net.ltgt.errorprone") version "5.1.0"
    id("com.github.spotbugs") version "6.5.5"
    id("com.github.ben-manes.versions") version "0.54.0"
    id("org.openrewrite.rewrite") version "7.33.0"
    id("info.solidsoft.pitest") version "1.19.0"
}

rewrite {
    // Demonstrator recipes — low-risk, deterministic, complementary to Spotless and Error Prone.
    // Run `just rewrite-check` to preview, `just rewrite` to apply.
    activeRecipe(
        "org.openrewrite.staticanalysis.UnnecessaryThrows",
        "org.openrewrite.staticanalysis.UseDiamondOperator",
        "org.openrewrite.staticanalysis.LambdaBlockToExpression",
    )
    // Dry-run reports diffs without failing CI; the harness is a tool, not a gate.
    failOnDryRunResults = false
}

dependencies {
    rewrite("org.openrewrite.recipe:rewrite-static-analysis:2.35.0")
}

// ---- Pitest (mutation testing) ----------------------------------------------
// Layered on top of JaCoCo coverage: where JaCoCo says "this line ran", Pitest
// says "and a meaningful test actually distinguished its behaviour from a
// trivial mutation of it". Run with `just mutation`.
//
// Warning-only at introduction: threshold=0 keeps the build green so the next
// PR can read the actual kill rate per package and pick a tightening target.
pitest {
    pitestVersion = "1.20.2"
    junit5PluginVersion = "1.2.3"
    targetClasses =
        listOf(
            "dev.sakashita.tateyokopdf.domain.*",
            "dev.sakashita.tateyokopdf.application.*",
        )
    excludedClasses =
        listOf(
            "dev.sakashita.tateyokopdf.tools.*",
            "dev.sakashita.tateyokopdf.infrastructure.pdfbox.tools.*",
        )
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

group = "dev.sakashita"
version = "2.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

application {
    mainClass = "dev.sakashita.tateyokopdf.Main"
}

repositories {
    mavenCentral()
    // qpdf official GitHub releases — Ivy URL repository for fetching the
    // Fast Web View post-processor binary as a regular Gradle dependency.
    // Bundled into the jpackage app-image by `stageQpdf` below.
    ivy {
        name = "qpdf-releases"
        url = uri("https://github.com/qpdf/qpdf/releases/download/")
        patternLayout {
            artifact("v[revision]/qpdf-[revision]-[classifier].[ext]")
        }
        metadataSources { artifact() }
        content { includeGroup("com.github.qpdf") }
    }
}

val qpdfVersion = "12.3.2"

// Per-host classifier: jpackage only emits images for the host OS, so we only
// need the artifact matching the build machine. macOS is intentionally absent
// — upstream qpdf has no Darwin binary, so the noOp fallback in QpdfLinearizer
// kicks in (or the user installs via Homebrew and we resolve from PATH).
val qpdfBinary by configurations.creating { isCanBeConsumed = false }

dependencies {
    val hostOs =
        org.gradle.internal.os.OperatingSystem
            .current()
    val qpdfCoords: String? =
        when {
            hostOs.isLinux -> "com.github.qpdf:qpdf:$qpdfVersion:bin-linux-x86_64@zip"
            hostOs.isWindows -> "com.github.qpdf:qpdf:$qpdfVersion:mingw64@zip"
            else -> null
        }
    qpdfCoords?.let { qpdfBinary(it) }

    implementation("org.apache.pdfbox:pdfbox:3.0.7")
    // xmpbox ships with PDFBox and shares its version — it builds the pdfaid /
    // Dublin Core / Adobe PDF XMP packet required for PDF/A conformance.
    implementation("org.apache.pdfbox:xmpbox:3.0.7")
    implementation("commons-cli:commons-cli:1.11.0")
    implementation("ch.qos.logback:logback-classic:1.5.34")

    compileOnly("org.jspecify:jspecify:1.0.0")

    errorprone("com.google.errorprone:error_prone_core:2.49.0")
    errorprone("com.uber.nullaway:nullaway:0.13.4")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testImplementation("nl.jqno.equalsverifier:equalsverifier:4.5")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.1.8")
    testImplementation("net.jqwik:jqwik:1.10.1")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
    // veraPDF greenfield validator — independently confirms the emitted file is
    // genuinely PDF/A-2b compliant, not merely tagged as such. Test-only: never
    // ships in the application classpath. Brings its own PDF parser (no PDFBox
    // coupling), so it cannot drift from how a real archival validator reads us.
    testImplementation("org.verapdf:validation-model:1.30.1")
    testCompileOnly("org.jspecify:jspecify:1.0.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testFixturesImplementation("org.apache.pdfbox:pdfbox:3.0.7")
    testFixturesImplementation("org.jspecify:jspecify:1.0.0")
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
    options.release = 25
    options.errorprone {
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*/build/generated/.*"
        // Zero-warning policy: NullAway findings break the build instead of being whispered as warnings.
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "dev.sakashita.tateyokopdf")
        option("NullAway:JSpecifyMode", "true")
    }
}

jacoco {
    toolVersion = "0.8.13"
}

// SpotBugs operates on compiled bytecode, complementing source-level Error Prone
// and JSpecify/NullAway analysis. Strict tuning: MAX effort runs every detector
// (slowest, most thorough) and MEDIUM confidence reports both definite and
// likely bugs while filtering speculative noise (LOW confidence flooded with
// EI/EI2 reports on immutable Path/Instant fields, plus DM_DEFAULT_ENCODING
// hits in third-party output paths). The build fails on any finding.
spotbugs {
    toolVersion = "4.9.6"
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
    ignoreFailures = false
    showStackTraces = true
    excludeFilter = file("config/spotbugs/exclude.xml")
}

// Limit SpotBugs to production code only. Test code uses Mockito / assertion
// patterns that generate noisy false positives (DM_DEFAULT_ENCODING, etc.).
tasks.named("spotbugsTest").configure { enabled = false }
tasks.named("spotbugsTestFixtures").configure { enabled = false }

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports {
        create("html") { required.set(true) }
        create("xml") { required.set(true) }
    }
}

// Dependabot security alerts: pin transitive deps on every runtime/test
// configuration. (The buildscript classpath is patched in the top-level
// `buildscript {}` block.)
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

tasks.test {
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik", "archunit")
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
        "dev/sakashita/tateyokopdf/infrastructure/pdfbox/tools/**",
        "dev/sakashita/tateyokopdf/Main.class",
        // QpdfLinearizer is a thin out-of-process CLI wrapper. Its defensive
        // branches (bundled-JAR resolution, ProcessBuilder timeout, thread
        // interruption during waitFor) cannot be unit-tested without an
        // unnatural amount of harness scaffolding. Same precedent as
        // BrowserLauncher above. Happy + non-zero-exit + missing-file +
        // missing-binary paths are still exercised by QpdfLinearizerTest.
        "dev/sakashita/tateyokopdf/infrastructure/qpdf/QpdfLinearizer.class",
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

        // search.maven.org is rate-limited and flaky enough that single-shot
        // requests routinely return 5xx; retry up to 3× with linear backoff so
        // a single transient blip doesn't surface as an `[ERR ] fetch failed`
        // in the report. GitHub's API is more reliable but reuses the same
        // wrapper for consistency.
        fun fetch(url: String): String? {
            repeat(3) { attempt ->
                val result =
                    runCatching {
                        (URI(url).toURL().openConnection() as HttpURLConnection).run {
                            connectTimeout = 10_000
                            readTimeout = 10_000
                            setRequestProperty("User-Agent", "tate-yoko-pdf-checkExtraVersions")
                            setRequestProperty("Accept", "application/json")
                            if (responseCode in 200..299) {
                                inputStream.use { it.bufferedReader().readText() }
                            } else {
                                null
                            }
                        }
                    }.getOrNull()
                if (result != null) return result
                if (attempt < 2) Thread.sleep(500L * (attempt + 1))
            }
            return null
        }

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
        // Scope the regex to the jacoco { ... } block — a bare `toolVersion =`
        // matcher also catches spotbugs' toolVersion and reports them as one
        // conflicted entry. SpotBugs' toolVersion is *not* tracked here: it
        // needs to stay ahead of Maven Central's indexer to support Java 25
        // bytecode scanning, and ben-manes already covers the plugin version.
        report(
            "jacoco",
            extractFromBuild("""jacoco\s*\{[^}]*toolVersion\s*=\s*"([^"]+)""""),
            latestMaven("org.jacoco", "jacoco"),
        )

        println("--- security-patch pins (manual floors; bump when upstream catches up) ---")
        val securityPins =
            listOf(
                Triple("plexus-utils", "org.codehaus.plexus", "plexus-utils"),
                Triple("log4j-core", "org.apache.logging.log4j", "log4j-core"),
            )
        for ((label, g, a) in securityPins) {
            val current = extractFromBuild(""""${Regex.escape("$g:$a")}"\s+to\s+"([^"]+)"""")
            report(label, current, latestMaven(g, a))
        }

        // --- GitHub Actions (.github/workflows/*) -----------------------------
        // The `uses: <repo>@<ref>` lines drift independently of Gradle deps and
        // were historically a blind spot (the project tracked Dockerfile pins
        // but not Actions). Scan each workflow file, dedupe `<owner>/<repo>`
        // references, and compare the major version against the latest release
        // tag. Branch pins (`@master`, `@main`) are reported informationally.
        println("--- GitHub Actions ---")
        val workflowFiles =
            rootProject
                .fileTree(".github/workflows") {
                    include("*.yml", "*.yaml")
                }.files
        val actionUses = mutableMapOf<String, String>()
        val branchPins = mutableSetOf<String>()
        for (yml in workflowFiles) {
            Regex("""uses:\s*([\w./-]+)@(\S+)""")
                .findAll(yml.readText())
                .forEach { m ->
                    val ref = m.groupValues[1]
                    val ver = m.groupValues[2]
                    if (ver == "master" || ver == "main") {
                        branchPins.add(ref)
                    } else {
                        actionUses[ref] = ver
                    }
                }
        }

        fun majorOf(v: String): Int =
            v
                .removePrefix("v")
                .split(".")
                .firstOrNull()
                ?.toIntOrNull() ?: 0

        for ((ref, currentVer) in actionUses.toSortedMap()) {
            // Action repo = first two path segments (handles subpath actions
            // like `gradle/actions/setup-gradle` where the release tag lives
            // on the parent `gradle/actions` repo).
            val parts = ref.split("/")
            val repo = if (parts.size >= 2) "${parts[0]}/${parts[1]}" else ref
            val latestTag =
                fetch("https://api.github.com/repos/$repo/releases/latest")
                    ?.let {
                        Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
                            .find(it)
                            ?.groupValues
                            ?.get(1)
                    }
            val currentMajor = majorOf(currentVer)
            val latestMajor = latestTag?.let { majorOf(it) }
            val tag =
                when {
                    latestTag == null -> {
                        "ERR "
                    }

                    latestMajor == currentMajor -> {
                        "OK  "
                    }

                    latestMajor!! > currentMajor -> {
                        updates++
                        "UPD "
                    }

                    else -> {
                        headCount++
                        "HEAD"
                    }
                }
            println("[%s] %-36s current=%-12s latest=%s".format(tag, ref, currentVer, latestTag ?: "(fetch failed)"))
        }
        for (ref in branchPins.toSortedSet()) {
            println("[INFO] %-36s pinned to branch (not version-tracked)".format(ref))
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
    mainClass = "dev.sakashita.tateyokopdf.infrastructure.pdfbox.tools.SamplePdfGenerator"
    args = listOf("build/test-data/sample.pdf", "4")
}

// ---- Distribution: jlink + jpackage app-image ------------------------------
// Replaces the GraalVM native-image path. Produces a directory layout under
// build/jpackage/tate-yoko-pdf/ containing a launcher, a trimmed JRE (jlink),
// and the application's shadow jar. The directory is zip-distributable as-is.
//
// We invoke jlink and jpackage directly (instead of via the Beryx plugin)
// because Beryx 1.13.x is incompatible with Gradle 9.x — it relies on the
// removed `Project.exec(...)` API and trips the configuration cache.

val jpackageAppName = "tate-yoko-pdf"
val bundledModules =
    listOf(
        // java.base + everything else needed for PDFBox / Javalin / Logback at runtime.
        // - java.desktop is mandatory: PDFBox' PDDocument <clinit> touches
        //   java.awt.image.Raster / ColorModel.
        // - jdk.crypto.ec: TLS cipher suites used by Java HttpClient.
        // - jdk.unsupported: sun.misc.Unsafe used by some transitive deps.
        // - jdk.zipfs: PDFBox uses zip-style stream filters.
        "java.base",
        "java.desktop",
        "java.naming",
        "java.management",
        "java.logging",
        "java.net.http",
        "java.sql",
        "java.xml",
        "jdk.crypto.ec",
        "jdk.unsupported",
        "jdk.zipfs",
    )

val javaHomeProvider: Provider<String> =
    providers.systemProperty("java.home").orElse(providers.environmentVariable("JAVA_HOME"))

fun toolPath(tool: String): Provider<String> =
    javaHomeProvider.map { home ->
        val exe =
            if (org.gradle.internal.os.OperatingSystem
                    .current()
                    .isWindows
            ) {
                "$tool.exe"
            } else {
                tool
            }
        "$home/bin/$exe"
    }

// jlink and jpackage both refuse to write into a pre-existing directory.
// Gradle eagerly creates declared `outputs.dir(...)` locations, so we declare
// each tool's *parent* directory as the output and have the tool write into a
// fixed-name child dir inside it. Gradle creates the parent; the tool creates
// the child. Separate Delete tasks reset state between runs in a way that's
// configuration-cache safe (project.delete inside doFirst is not).
val jreOutputParent = layout.buildDirectory.dir("dist-jre")
val jreImageDir = jreOutputParent.map { it.dir("runtime") }
val jpackageOutputParent = layout.buildDirectory.dir("dist-jpackage")
val jpackageInputDir = layout.buildDirectory.dir("jpackage-input")

// Single configuration-cache-safe task that stages the shadow jar + qpdf zip
// (Linux/Windows hosts only) into jpackage-input/. Using a custom task with
// injected ArchiveOperations + FileSystemOperations because Gradle 9's
// configuration cache forbids `zipTree(...)` inside closures that capture the
// script object.
//
// Layout after sync:
//   jpackage-input/
//     tate-yoko-pdf-<ver>-all.jar       (shadow jar)
//     bin/qpdf[.exe] + helpers          (from upstream qpdf release zip)
//     bin/*.dll                         (Windows mingw64 only)
//     lib/libqpdf.so.30 → 30.3.2 + deps (Linux only)
//
// The Linux zip is already flat (bin/+lib/ at top), the mingw64 zip nests
// everything under qpdf-12.3.2-mingw64/. `eachFile` strips the latter prefix.
//
// `libqpdf.so.30` is a SONAME symlink to `libqpdf.so.30.3.2`. Gradle's archive
// extraction dereferences it, so a post-sync NIO step recreates it; the qpdf
// binary's RUNPATH=../lib then resolves at runtime.
val hostIsLinux =
    org.gradle.internal.os.OperatingSystem
        .current()
        .isLinux
val hostIsWindows =
    org.gradle.internal.os.OperatingSystem
        .current()
        .isWindows

abstract class StageJpackageInput : DefaultTask() {
    @get:InputFiles abstract val shadowJar: ConfigurableFileCollection

    @get:InputFiles abstract val qpdfZip: ConfigurableFileCollection

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:Input abstract val fixLinuxSoSymlink: Property<Boolean>

    @get:Inject abstract val archives: ArchiveOperations

    @get:Inject abstract val files: FileSystemOperations

    @TaskAction
    fun run() {
        files.sync {
            from(shadowJar)
            if (!qpdfZip.isEmpty) {
                from(archives.zipTree(qpdfZip.singleFile)) {
                    eachFile {
                        val segs = relativePath.segments
                        if (segs.isNotEmpty() && segs[0].startsWith("qpdf-")) {
                            relativePath =
                                RelativePath(true, *segs.drop(1).toTypedArray())
                        }
                    }
                    includeEmptyDirs = false
                    // headers / docs not needed at runtime
                    exclude("**/include/**", "**/share/**", "**/doc/**")
                }
            }
            into(outputDir)
        }
        if (fixLinuxSoSymlink.get()) {
            val libDir =
                outputDir
                    .get()
                    .asFile
                    .toPath()
                    .resolve("lib")
            val link = libDir.resolve("libqpdf.so.30")
            val target = libDir.resolve("libqpdf.so.30.3.2")
            if (Files.isRegularFile(link) && Files.isRegularFile(target)) {
                Files.delete(link)
                Files.createSymbolicLink(link, libDir.relativize(target))
            }
        }
    }
}

val stageJpackageInput =
    tasks.register<StageJpackageInput>("stageJpackageInput") {
        group = "distribution"
        description =
            "Stage shadow jar and (on Linux/Windows) qpdf into jpackage-input/"
        shadowJar.from(tasks.shadowJar.flatMap { it.archiveFile })
        if (hostIsLinux || hostIsWindows) {
            qpdfZip.from(qpdfBinary)
        }
        outputDir.set(jpackageInputDir)
        fixLinuxSoSymlink.set(hostIsLinux)
    }

val cleanJreImage =
    tasks.register<Delete>("cleanJreImage") {
        delete(jreImageDir)
    }

val cleanJpackageImage =
    tasks.register<Delete>("cleanJpackageImage") {
        // Match the directory jpackage actually populates inside the parent.
        delete(jpackageOutputParent.map { it.dir(jpackageAppName) })
    }

val jreImage =
    tasks.register<Exec>("jreImage") {
        group = "distribution"
        description = "Run jlink to build a trimmed JRE under build/dist-jre/runtime/"
        dependsOn(cleanJreImage)

        commandLine =
            listOf(
                toolPath("jlink").get(),
                "--add-modules",
                bundledModules.joinToString(","),
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages",
                "--compress=zip-9",
                "--output",
                jreImageDir.get().asFile.absolutePath,
            )
        inputs.property("modules", bundledModules.joinToString(","))
        outputs.dir(jreOutputParent)
    }

val jpackageImage =
    tasks.register<Exec>("jpackageImage") {
        group = "distribution"
        description = "Run jpackage to build the app-image under build/dist-jpackage/"
        dependsOn(jreImage, stageJpackageInput, cleanJpackageImage)

        val mainJarName =
            tasks.shadowJar
                .get()
                .archiveFileName
                .get()

        // CLI tool: on Windows, `--win-console` makes the launcher a console-subsystem
        // app so stdout/stderr (help, progress, errors) show up in the terminal. Other
        // OSes need no flag. Logging is stderr-only (see logback.xml); there is no file log.
        val hostOs =
            org.gradle.internal.os.OperatingSystem
                .current()
        val jpackageArgs =
            listOf(
                toolPath("jpackage").get(),
                "--type",
                "app-image",
                "--name",
                jpackageAppName,
                "--input",
                jpackageInputDir.get().asFile.absolutePath,
                "--main-jar",
                mainJarName,
                "--main-class",
                application.mainClass.get(),
                "--runtime-image",
                jreImageDir.get().asFile.absolutePath,
                "--dest",
                jpackageOutputParent.get().asFile.absolutePath,
                "--app-version",
                project.version.toString(),
                // Size the heap to the host instead of a fixed 2g: MaxRAMPercentage adapts
                // to big and small machines alike (and respects container cgroup limits),
                // so large scans get headroom on capable hosts without over-committing on
                // small ones. For an enormous PDF on a constrained host, `--low-memory`
                // additionally spills page streams to disk to keep heap bounded.
                "--java-options",
                "-XX:MaxRAMPercentage=75.0",
            )
        commandLine = if (hostOs.isWindows) jpackageArgs + "--win-console" else jpackageArgs

        inputs.dir(jreImageDir)
        inputs.dir(jpackageInputDir)
        outputs.dir(jpackageOutputParent)
    }
