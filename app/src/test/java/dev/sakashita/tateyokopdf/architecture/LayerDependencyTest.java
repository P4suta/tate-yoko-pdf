package dev.sakashita.tateyokopdf.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Boundary rules that the Gradle module graph cannot already enforce.
 *
 * <p>Since the split into {@code :domain}, {@code :port}, {@code :application}, {@code
 * :infrastructure}, {@code :observability}, and {@code :app}, most hexagonal rules are guaranteed
 * at compile time by the absence of a project dependency — PDFBox confinement, domain purity, port
 * purity, and application-not-depending-on-cli are simply not on the offending module's classpath.
 * What remains are the two intra-graph rules a missing dependency does not catch: that nothing but
 * the application layer reaches into {@code domain.strategy} (all of which is visible to {@code
 * :app}), and that there are no package cycles. Analysed from {@code :app}, whose test classpath
 * sees every module.
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

  /**
   * Strategy selection is an application-layer concern. The composition root (CLI) must obtain a
   * {@code PaginationStrategy} through the application factory, never by instantiating the concrete
   * type. Not compile-enforced: {@code :app} depends on {@code :domain}, so the strategies are on
   * its classpath.
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

  /** No package-level cycles. */
  @ArchTest
  static final ArchRule noPackageCycles =
      slices().matching("dev.sakashita.tateyokopdf.(*)..").should().beFreeOfCycles();
}
