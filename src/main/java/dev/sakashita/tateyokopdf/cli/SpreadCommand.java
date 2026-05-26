package dev.sakashita.tateyokopdf.cli;

import dev.sakashita.tateyokopdf.application.*;
import dev.sakashita.tateyokopdf.domain.model.ReadingDirection;
import dev.sakashita.tateyokopdf.domain.service.SpreadLayoutCalculator;
import dev.sakashita.tateyokopdf.domain.strategy.*;
import dev.sakashita.tateyokopdf.infrastructure.pdfbox.PdfBoxDocumentFactory;
import dev.sakashita.tateyokopdf.port.exception.SpreadException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "tate-yoko-pdf",
    mixinStandardHelpOptions = true,
    version = "tate-yoko-pdf 1.0.0",
    description = "Convert scanned PDF pages into RTL spread layout for Japanese vertical text.")
public class SpreadCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Input PDF file path")
  private Path input;

  @Option(
      names = {"-o", "--output"},
      description = "Output PDF file path (default: <input>_spread.pdf)")
  private Path output;

  @Option(
      names = {"-d", "--direction"},
      defaultValue = "RTL",
      description = "Reading direction: RTL (default) or LTR")
  private ReadingDirection direction;

  @Option(
      names = {"--cover-single"},
      description = "Treat the first page as a standalone cover spread")
  private boolean coverSingle;

  @Option(
      names = {"-v", "--verbose"},
      description = "Enable verbose logging output (DEBUG level)")
  private boolean verbose;

  @Override
  public Integer call() {
    try {
      Path actualOutput =
          (output != null) ? output : SpreadOptions.withDefaults(input).outputPath();

      var options = new SpreadOptions(input, actualOutput, direction, coverSingle);

      if (verbose) {
        configureVerboseLogging();
      }

      var factory = new PdfBoxDocumentFactory();
      var calculator = new SpreadLayoutCalculator();
      PaginationStrategy strategy =
          coverSingle ? new CoverSinglePagination() : new StandardPagination();
      var listener = new ConsoleProgressListener();

      var service = new SpreadService(factory, calculator, strategy, listener);
      service.execute(options);

      return 0;

    } catch (SpreadException e) {
      System.err.println("Error: " + e.getMessage());
      return 1;
    } catch (Exception e) {
      System.err.println("Unexpected error: " + e.getMessage());
      e.printStackTrace(System.err);
      return 2;
    }
  }

  private static void configureVerboseLogging() {
    var context =
        (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
    context
        .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        .setLevel(ch.qos.logback.classic.Level.DEBUG);
  }

  public static void runCli(String[] args) {
    int exitCode = new CommandLine(new SpreadCommand()).execute(args);
    System.exit(exitCode);
  }

  public static void main(String[] args) {
    runCli(args);
  }
}
