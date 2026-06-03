import java.nio.file.Files
import javax.inject.Inject

plugins {
    id("tateyokopdf.java-conventions")
    id("tateyokopdf.test-conventions")
    id("tateyokopdf.quality-conventions")
    application
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":port"))
    implementation(project(":application"))
    implementation(project(":infrastructure"))
    implementation(project(":observability"))
    implementation("commons-cli:commons-cli:1.11.0")
    implementation("ch.qos.logback:logback-classic:1.5.34")

    // veraPDF greenfield validator — independently confirms the emitted file is genuinely PDF/A-2b,
    // not merely tagged as such. Test-only; brings its own (non-PDFBox) parser.
    testImplementation("org.verapdf:validation-model:1.30.1")
    testImplementation(testFixtures(project(":infrastructure")))
    testImplementation(testFixtures(project(":application")))
}

application {
    mainClass = "dev.sakashita.tateyokopdf.Main"
}

repositories {
    // qpdf official GitHub releases — Ivy URL repository for fetching the Fast Web View
    // post-processor binary as a regular Gradle dependency. Bundled by stageJpackageInput below.
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

// Per-host classifier: jpackage only emits images for the host OS. macOS is intentionally absent —
// upstream qpdf has no Darwin binary, so QpdfLinearizer's noOp/PATH fallback applies.
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
}

tasks.shadowJar {
    archiveBaseName = "tate-yoko-pdf"
    archiveClassifier = "all"
    archiveVersion = ""
    mergeServiceFiles()
}

tasks.register<JavaExec>("createSamplePdf") {
    group = "verification"
    description = "Generate a sample multi-page PDF for manual / smoke testing"
    // SamplePdfGenerator lives in :infrastructure, which is on app's runtime classpath.
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "dev.sakashita.tateyokopdf.infrastructure.pdfbox.tools.SamplePdfGenerator"
    args = listOf("build/test-data/sample.pdf", "4")
}

// ---- Distribution: jlink + jpackage app-image ------------------------------
// Produces build/dist-jpackage/tate-yoko-pdf/ with a launcher, a trimmed JRE (jlink), and the
// shadow jar. jlink/jpackage are invoked directly (Beryx 1.13.x is incompatible with Gradle 9.x).

val jpackageAppName = "tate-yoko-pdf"
val bundledModules =
    listOf(
        // java.base + everything PDFBox / Logback / HttpClient need at runtime.
        // - java.desktop: PDFBox' PDDocument <clinit> touches java.awt.image.Raster / ColorModel.
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

// jlink/jpackage refuse to write into a pre-existing directory; Gradle eagerly creates declared
// output dirs. So we declare each tool's *parent* as the output and have the tool write a fixed
// child inside it, with separate Delete tasks to reset state config-cache-safely.
val jreOutputParent = layout.buildDirectory.dir("dist-jre")
val jreImageDir = jreOutputParent.map { it.dir("runtime") }
val jpackageOutputParent = layout.buildDirectory.dir("dist-jpackage")
val jpackageInputDir = layout.buildDirectory.dir("jpackage-input")

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
        description = "Stage shadow jar and (on Linux/Windows) qpdf into jpackage-input/"
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

        // On Windows, --win-console makes the launcher a console app so stdout/stderr show in the
        // terminal. Other OSes need no flag. Logging is stderr-only (logback.xml); no file log.
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
                // MaxRAMPercentage adapts heap to the host (and container cgroups) instead of a
                // fixed value. --low-memory additionally spills page streams to disk.
                "--java-options",
                "-XX:MaxRAMPercentage=75.0",
            )
        commandLine = if (hostOs.isWindows) jpackageArgs + "--win-console" else jpackageArgs

        inputs.dir(jreImageDir)
        inputs.dir(jpackageInputDir)
        outputs.dir(jpackageOutputParent)
    }
