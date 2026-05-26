import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

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

    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

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

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
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
            resources.includedPatterns.add(".*\\.xml")
            resources.includedPatterns.add(".*\\.properties")
        }
    }
}
