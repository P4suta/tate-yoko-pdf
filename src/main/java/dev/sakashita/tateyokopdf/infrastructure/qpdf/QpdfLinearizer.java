package dev.sakashita.tateyokopdf.infrastructure.qpdf;

import dev.sakashita.tateyokopdf.application.PdfOutputPolicy;
import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import dev.sakashita.tateyokopdf.domain.model.PdfVersion;
import dev.sakashita.tateyokopdf.port.PdfPostProcessor;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calls the {@code qpdf} binary as an out-of-process step that performs two modernisations in one
 * pass:
 *
 * <ul>
 *   <li>{@code --linearize} — reorders bytes so the PDF can be streamed (Fast Web View / HTTP Range
 *       requests). qpdf packs the linearized output into object streams as part of this step, so we
 *       do not need {@code --object-streams=generate} on top (verified empirically — adding it
 *       produces a byte-identical file).
 *   <li>{@code --min-version=X.Y} — rewrites the {@code %PDF-x.x} header byte to match {@link
 *       PdfOutputPolicy#TARGET}. PDFBox's {@code setVersion} updates only the catalog {@code
 *       /Version} entry for any value &ge; 1.4, so the header bump must happen here.
 * </ul>
 *
 * <p>The binary is resolved in this order:
 *
 * <ol>
 *   <li>The in-bundle copy staged by {@code stageJpackageInput} from the upstream release zip.
 *       jpackage drops the input next to the shadow JAR under {@code app/}; the zip's native layout
 *       puts the executable at {@code bin/qpdf} (or {@code bin\qpdf.exe}). {@link
 *       #resolveBundledQpdf()} therefore probes the {@code bin/} subdirectory first, then falls
 *       back to a flat sibling layout for legacy dev-tree runs.
 *   <li>{@code which qpdf} / {@code where qpdf} on {@code PATH} — for dev runs from the source tree
 *       on a machine that has qpdf installed.
 *   <li>Falls back to {@link PdfPostProcessor#noOp()} and logs a single warning so a missing binary
 *       in a bundling failure surfaces audibly without breaking the whole pipeline. In that
 *       fallback the output PDF still has its catalog {@code /Version} set to the target (most
 *       conformant readers honour it) and is not linearized; the header byte stays at PDFBox's
 *       internal default.
 * </ol>
 */
public final class QpdfLinearizer implements PdfPostProcessor {

  private static final Logger log = LoggerFactory.getLogger(QpdfLinearizer.class);
  private static final long TIMEOUT_SECONDS = 60L;
  private static final Pattern PATH_SEPARATOR = Pattern.compile(Pattern.quote(File.pathSeparator));

  private final Path qpdfBinary;
  private final PdfVersion targetVersion;

  // Package-private so tests can pin the binary to a controlled failure mode
  // (e.g. /bin/false) without going through the production resolution chain.
  QpdfLinearizer(Path qpdfBinary, PdfVersion targetVersion) {
    this.qpdfBinary = qpdfBinary;
    this.targetVersion = targetVersion;
  }

  // Convenience overload for tests that don't care about the target version.
  QpdfLinearizer(Path qpdfBinary) {
    this(qpdfBinary, PdfOutputPolicy.TARGET);
  }

  /** Build the most capable {@link PdfPostProcessor} we can for the current environment. */
  public static PdfPostProcessor create() {
    Optional<Path> bundled = resolveBundledQpdf();
    if (bundled.isPresent()) {
      log.info("qpdf binary resolved from bundle: {}", bundled.get());
      return new QpdfLinearizer(bundled.get(), PdfOutputPolicy.TARGET);
    }
    Optional<Path> onPath = resolveOnPath();
    if (onPath.isPresent()) {
      log.info("qpdf binary resolved from PATH: {}", onPath.get());
      return new QpdfLinearizer(onPath.get(), PdfOutputPolicy.TARGET);
    }
    log.warn(
        "qpdf binary not found. The following PDF modernisations are SKIPPED: "
            + "(a) Fast Web View (linearisation), "
            + "(b) header byte rewrite to %PDF-{}. "
            + "Catalog /Version is still {} — most conformant readers honour it, "
            + "but bundle a qpdf binary or add one to PATH for full conformance.",
        PdfOutputPolicy.TARGET.label(), PdfOutputPolicy.TARGET.label());
    return PdfPostProcessor.noOp();
  }

  @Override
  public void process(Path path) {
    if (!Files.isRegularFile(path)) {
      throw SpreadException.withDetail(
          ErrorKind.PDF_WRITE_FAILED, "qpdf input missing: " + path, null);
    }
    ProcessBuilder pb =
        new ProcessBuilder(
                qpdfBinary.toString(),
                "--linearize",
                "--min-version=" + targetVersion.label(),
                "--replace-input",
                path.toString())
            .redirectErrorStream(true);
    try {
      Process process = pb.start();
      if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw SpreadException.withDetail(
            ErrorKind.PDF_WRITE_FAILED, "qpdf timed out after " + TIMEOUT_SECONDS + "s", null);
      }
      int code = process.exitValue();
      // qpdf exits with 0 on success, 3 on warnings (which we accept), anything else is a failure.
      if (code != 0 && code != 3) {
        String tail = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        throw SpreadException.withDetail(
            ErrorKind.PDF_WRITE_FAILED, "qpdf exit=" + code + " out=" + tail, null);
      }
      log.debug("Linearised {} via qpdf exit={}", path.getFileName(), code);
    } catch (IOException e) {
      throw SpreadException.withDetail(ErrorKind.PDF_WRITE_FAILED, "qpdf invocation failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw SpreadException.withDetail(ErrorKind.INTERNAL, "qpdf interrupted", e);
    }
  }

  static Optional<Path> resolveBundledQpdf() {
    try {
      var codeSource = QpdfLinearizer.class.getProtectionDomain().getCodeSource();
      if (codeSource == null) {
        return Optional.empty();
      }
      Path jarPath = Path.of(codeSource.getLocation().toURI());
      Path jarDir = jarPath.getParent();
      if (jarDir == null) {
        return Optional.empty();
      }
      return resolveBundledQpdfIn(jarDir);
    } catch (URISyntaxException | RuntimeException e) {
      log.debug("Could not derive bundled qpdf path from class location: {}", e.getMessage());
      return Optional.empty();
    }
  }

  // Package-private so tests can drive the lookup with a synthetic directory
  // without going through the class-loader/CodeSource probe.
  static Optional<Path> resolveBundledQpdfIn(Path jarDir) {
    String executableName = osIsWindows() ? "qpdf.exe" : "qpdf";
    Path[] candidates = {
      jarDir.resolve("bin").resolve(executableName), // upstream zip layout
      jarDir.resolve(executableName), // legacy / flat dev-tree layout
    };
    for (Path candidate : candidates) {
      if (Files.isExecutable(candidate)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  // Error Prone's StringSplitter check wants Guava's Splitter; pulling Guava in for one PATH
  // walk is not worth it. `Pattern.compile(quote(File.pathSeparator)).split(...)` would still
  // trip the check, and the surprising trailing-empty semantics that StringSplitter warns about
  // don't matter here (we explicitly skip empties below).
  @SuppressWarnings("StringSplitter")
  static Optional<Path> resolveOnPath() {
    String executableName = osIsWindows() ? "qpdf.exe" : "qpdf";
    String pathEnv = System.getenv("PATH");
    if (pathEnv == null) {
      return Optional.empty();
    }
    for (String entry : PATH_SEPARATOR.split(pathEnv)) {
      if (entry.isEmpty()) {
        continue;
      }
      Path candidate = Path.of(entry, executableName);
      if (Files.isExecutable(candidate)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  private static boolean osIsWindows() {
    @Nullable String os = System.getProperty("os.name");
    return os != null && os.toLowerCase(Locale.ROOT).contains("win");
  }
}
