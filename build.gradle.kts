import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import java.net.HttpURLConnection
import java.net.URI

// Apply security patches to the buildscript (plugin) classpath so Dependabot alerts on transitive
// deps like plexus-utils / log4j-core are resolved even though they only appear via Gradle plugins.
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

// The root is an aggregator: no production code. Per-module Java/test/quality config lives in the
// build-logic convention plugins; the runnable artifact and distribution live in :app. The root
// keeps cross-cutting maintenance tooling (ben-manes + checkExtraVersions, OpenRewrite) and declares
// the app-only plugin versions for subprojects to apply.
plugins {
    base
    id("com.github.ben-manes.versions") version "0.54.0"
    id("org.openrewrite.rewrite") version "7.35.0"
    id("com.gradleup.shadow") version "9.4.2" apply false
    id("info.solidsoft.pitest") version "1.19.0" apply false
}

group = "dev.sakashita"
version = "2.0.0"

repositories {
    mavenCentral()
}

// Pin transitive deps on the root's own (rewrite / ben-manes) configurations too.
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

rewrite {
    // Demonstrator recipes — low-risk, deterministic, complementary to Spotless and Error Prone.
    activeRecipe(
        "org.openrewrite.staticanalysis.UnnecessaryThrows",
        "org.openrewrite.staticanalysis.UseDiamondOperator",
        "org.openrewrite.staticanalysis.LambdaBlockToExpression",
    )
    failOnDryRunResults = false
}

dependencies {
    rewrite("org.openrewrite.recipe:rewrite-static-analysis:2.37.0")
}

// ---- `just outdated` plumbing -------------------------------------------------
// 1. ben-manes.versions: only show stable upgrades (skip alpha/beta/rc/M*/SNAPSHOT)
// 2. checkExtraVersions: diff non-Gradle pins (Dockerfile, spotless, jacoco, security-patch coords)
//    against upstream stable releases via GitHub Releases + Maven Central. Those literals now live
//    in the build-logic convention plugins, so the regex reads point there.

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
    // ben-manes 0.54 uses Task.project / non-serializable lambdas, so `just outdated` is invoked
    // with --no-configuration-cache.
}

tasks.register("checkExtraVersions") {
    group = "help"
    description = "Diff non-Gradle pinned versions against upstream stable releases"
    // Capture files and properties at configuration time so doLast doesn't hold a Project reference.
    val dockerfile = rootProject.file("Dockerfile")
    val buildScript = rootProject.file("build.gradle.kts")
    val qualityConventions =
        rootProject.file("build-logic/src/main/kotlin/tateyokopdf.quality-conventions.gradle.kts")
    val testConventions =
        rootProject.file("build-logic/src/main/kotlin/tateyokopdf.test-conventions.gradle.kts")
    val javaConventions =
        rootProject.file("build-logic/src/main/kotlin/tateyokopdf.java-conventions.gradle.kts")
    val failOnUpdates =
        providers.gradleProperty("failOnUpdates").map { it.toBoolean() }.getOrElse(false)
    outputs.upToDateWhen { false }

    doLast {
        val dockerfileText = dockerfile.readText()
        val buildScriptText = buildScript.readText()
        val qualityText = qualityConventions.readText()
        val testText = testConventions.readText()
        val javaText = javaConventions.readText()
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

        // Search a given text for a version-shaped capture. Finds all occurrences so duplicated
        // maps (e.g. the buildscript pin map + the convention-plugin pin map) are detected when
        // they drift apart.
        fun extractFrom(
            text: String,
            pattern: String,
        ): String {
            val matches =
                Regex(pattern)
                    .findAll(text)
                    .map { it.groupValues[1] }
                    .filter { stableRe.matches(it) }
                    .toList()
            return when {
                matches.isEmpty() -> error("expected /$pattern/ not found")
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
            extractFrom(qualityText, """googleJavaFormat\("([^"]+)"\)"""),
            latestGitHub("google/google-java-format"),
        )
        // jacoco toolVersion now lives only in the test-conventions plugin (spotbugs' toolVersion is
        // in quality-conventions and intentionally not tracked here).
        report(
            "jacoco",
            extractFrom(testText, """toolVersion\s*=\s*"([^"]+)""""),
            latestMaven("org.jacoco", "jacoco"),
        )

        println("--- security-patch pins (manual floors; bump when upstream catches up) ---")
        // Pins live in the root buildscript/runtime maps and the java-conventions plugin; search all.
        val pinText = buildScriptText + "\n" + javaText
        val securityPins =
            listOf(
                Triple("plexus-utils", "org.codehaus.plexus", "plexus-utils"),
                Triple("log4j-core", "org.apache.logging.log4j", "log4j-core"),
            )
        for ((label, g, a) in securityPins) {
            val current = extractFrom(pinText, """"${Regex.escape("$g:$a")}"\s+to\s+"([^"]+)"""")
            report(label, current, latestMaven(g, a))
        }

        // --- GitHub Actions (.github/workflows/*) -----------------------------
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
