package dev.sakashita.tateyokopdf;

import dev.sakashita.tateyokopdf.cli.SpreadCommand;
import dev.sakashita.tateyokopdf.observability.FatalUncaughtHandler;
import dev.sakashita.tateyokopdf.web.WebLauncher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class Main {

  private Main() {}

  public static void main(String[] args) {
    // Set LOG_DIR before any logger touches Logback — the RollingFileAppender in
    // logback.xml interpolates ${LOG_DIR} at context-init time, which runs lazily
    // on first LoggerFactory call inside Javalin / PDFBox / Picocli code.
    System.setProperty("LOG_DIR", resolveLogDir(System::getProperty, System::getenv).toString());
    Thread.setDefaultUncaughtExceptionHandler(new FatalUncaughtHandler());
    if (args.length == 0) {
      new WebLauncher().run();
      return;
    }
    SpreadCommand.runCli(args);
  }

  // sysProp/env are injected so tests can stub the platform without touching real System lookups.
  static Path resolveLogDir(
      Function<String, @Nullable String> sysProp, Function<String, @Nullable String> env) {
    Path candidate = platformLogDir(sysProp, env);
    try {
      Files.createDirectories(candidate);
      return candidate;
    } catch (IOException | SecurityException e) {
      // Filesystem refused the platform path (read-only fs, missing parent, locked-down sandbox).
      // Fall back to a temp-dir location so the FILE appender still has somewhere to write rather
      // than throwing during logger init and killing the app before it can report the problem.
      String tmp = sysProp.apply("java.io.tmpdir");
      Path fallback = Path.of(tmp != null ? tmp : ".").resolve("tate-yoko-pdf-logs");
      try {
        Files.createDirectories(fallback);
      } catch (IOException | SecurityException ignored) {
        // best-effort — if even tmp is unwritable, logback will surface the error itself
      }
      return fallback;
    }
  }

  private static Path platformLogDir(
      Function<String, @Nullable String> sysProp, Function<String, @Nullable String> env) {
    String osName = sysProp.apply("os.name");
    String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
    String userHome = sysProp.apply("user.home");
    Path home = Path.of(userHome != null ? userHome : ".");
    if (os.contains("mac")) {
      return home.resolve("Library/Logs/tate-yoko-pdf");
    }
    if (os.contains("win")) {
      String appData = env.apply("APPDATA");
      Path base = appData != null ? Path.of(appData) : home.resolve("AppData/Roaming");
      return base.resolve("tate-yoko-pdf").resolve("logs");
    }
    // Linux + other Unix: XDG Base Directory specification.
    String xdg = env.apply("XDG_DATA_HOME");
    Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : home.resolve(".local/share");
    return base.resolve("tate-yoko-pdf");
  }
}
