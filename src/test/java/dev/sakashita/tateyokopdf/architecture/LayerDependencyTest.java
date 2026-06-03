package dev.sakashita.tateyokopdf.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Boundary rules — the single source of truth for what is allowed to depend on what.
 *
 * <p>Each rule encodes one specific concern. Together they enforce the hexagonal layout: domain at
 * the centre, ports as pure abstractions, infrastructure adapters confined to their package, and
 * web/cli only assembling — never reaching past application into infrastructure or directly
 * instantiating domain strategies.
 */
@AnalyzeClasses(
    packages = "dev.sakashita.tateyokopdf",
    importOptions = {
      ImportOption.DoNotIncludeTests.class,
      LayerDependencyTest.NoTestFixtures.class
    })
final class LayerDependencyTest {

  /**
   * Excludes the {@code testFixtures} sourceSet — fixtures may use PDFBox directly to build PDFs.
   */
  public static final class NoTestFixtures implements ImportOption {
    @Override
    public boolean includes(Location location) {
      return !location.contains("testFixtures") && !location.contains("test-fixtures");
    }
  }

  /** PDFBox types are an implementation detail — they must not leak out of the adapter. */
  @ArchTest
  static final ArchRule pdfBoxConfined =
      noClasses()
          .that()
          .resideOutsideOfPackage("dev.sakashita.tateyokopdf.infrastructure.pdfbox..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.apache.pdfbox..");

  /**
   * Strategy selection is an application-layer concern. Web/CLI must obtain a {@code
   * PaginationStrategy} through the application factory, never by instantiating the concrete type.
   */
  @ArchTest
  static final ArchRule strategyInstantiatedOnlyByApplication =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "dev.sakashita.tateyokopdf.application..",
              "dev.sakashita.tateyokopdf.domain.strategy..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("dev.sakashita.tateyokopdf.domain.strategy..");

  /** Domain knows nothing about HTTP, CLI, infrastructure, or cross-cutting plumbing. */
  @ArchTest
  static final ArchRule domainIsPure =
      noClasses()
          .that()
          .resideInAPackage("dev.sakashita.tateyokopdf.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "dev.sakashita.tateyokopdf.application..",
              "dev.sakashita.tateyokopdf.infrastructure..",
              "dev.sakashita.tateyokopdf.cli..",
              "dev.sakashita.tateyokopdf.observability..",
              "org.apache.pdfbox..",
              "org.apache.commons.cli..");

  /** Ports may speak the domain vocabulary but otherwise stay abstract. */
  @ArchTest
  static final ArchRule portIsPure =
      noClasses()
          .that()
          .resideInAPackage("dev.sakashita.tateyokopdf.port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "dev.sakashita.tateyokopdf.application..",
              "dev.sakashita.tateyokopdf.infrastructure..",
              "dev.sakashita.tateyokopdf.cli..",
              "dev.sakashita.tateyokopdf.observability..",
              "org.apache.pdfbox..",
              "org.apache.commons.cli..");

  /** The CLI is the outermost shell — application must not depend on it. */
  @ArchTest
  static final ArchRule applicationDoesNotDependOnCli =
      noClasses()
          .that()
          .resideInAPackage("dev.sakashita.tateyokopdf.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.sakashita.tateyokopdf.cli..", "org.apache.commons.cli..");

  /** Observability is cross-cutting — must not couple to the CLI surface. */
  @ArchTest
  static final ArchRule observabilityIndependentOfUi =
      noClasses()
          .that()
          .resideInAPackage("dev.sakashita.tateyokopdf.observability..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.sakashita.tateyokopdf.cli..", "org.apache.commons.cli..");

  /** No package-level cycles. */
  @ArchTest
  static final ArchRule noPackageCycles =
      slices().matching("dev.sakashita.tateyokopdf.(*)..").should().beFreeOfCycles();
}
