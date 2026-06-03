package dev.sakashita.tateyokopdf.cli;

import dev.sakashita.tateyokopdf.application.SpreadOptions;
import dev.sakashita.tateyokopdf.domain.service.SpreadLayoutCalculator;
import dev.sakashita.tateyokopdf.infrastructure.pdfbox.PdfBoxDocumentFactory;
import dev.sakashita.tateyokopdf.infrastructure.qpdf.QpdfLinearizer;
import dev.sakashita.tateyokopdf.observability.ExceptionMapper;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jspecify.annotations.Nullable;

/**
 * Command-line front-end built on Apache Commons CLI.
 *
 * <p>Accepts one or more inputs (files, directories, or {@code -} for stdin), resolves them to
 * concrete PDFs via {@link InputResolver}, and runs {@link FileConversion} for each. Diagnostics
 * and progress go to stderr; with {@code -o -} the converted PDF is streamed to stdout as a clean
 * binary stream.
 */
public final class SpreadCommand {

  static final String NAME = "tate-yoko-pdf";
  static final String VERSION = "tate-yoko-pdf 2.0.0";

  private SpreadCommand() {}

  // ---- entry points -------------------------------------------------------

  public static void main(String[] args) {
    runCli(args);
  }

  public static void runCli(String[] args) {
    System.exit(run(args));
  }

  /** Parses {@code args}, runs the conversion(s), and returns the exit code (never calls exit). */
  public static int run(String[] args) {
    Options options = buildOptions();
    boolean verbose = false;
    try {
      CommandLine cmd = new DefaultParser().parse(options, args);
      verbose = cmd.hasOption("verbose");
      if (verbose) {
        configureVerboseLogging();
      }
      if (cmd.hasOption("help")) {
        printHelp(System.out);
        return CliExitCodes.OK;
      }
      if (cmd.hasOption("version")) {
        System.out.println(VERSION);
        return CliExitCodes.OK;
      }
      if (cmd.getArgList().isEmpty()) {
        printHelp(System.out);
        return CliExitCodes.OK;
      }
      return execute(CliArguments.from(cmd));
    } catch (ParseException e) {
      System.err.println("Error: " + e.getMessage());
      printHelp(System.err);
      return CliExitCodes.USAGE;
    } catch (Exception e) {
      boolean v = verbose;
      return new CliExceptionHandler(() -> v).handle(e);
    }
  }

  // ---- orchestration ------------------------------------------------------

  private static int execute(CliArguments args) throws IOException, ParseException {
    // Composition root: assemble the pipeline once, then dispatch per input.
    FileConversion conversion =
        new FileConversion(
            new PdfBoxDocumentFactory(args.memoryMode()),
            new SpreadLayoutCalculator(),
            QpdfLinearizer.create(),
            args);

    InputResolver.Resolved resolved = args.inputs();
    @Nullable String outputOpt = args.outputOpt();

    if (resolved.stdin()) {
      OutputTarget target =
          (outputOpt == null || "-".equals(outputOpt))
              ? OutputTarget.stdout()
              : OutputTarget.file(Path.of(outputOpt));
      StdinSource.withStdinPdf(in -> conversion.convert(in, target, null));
      return CliExitCodes.OK;
    }

    List<Path> files = resolved.files();
    if (files.isEmpty()) {
      throw new ParseException("no PDF files found in the given inputs");
    }

    // Single input: fail-fast — let the exception bubble up to run()'s mapper.
    if (files.size() == 1) {
      Path input = files.get(0);
      conversion.convert(input, singleOutput(input, outputOpt), null);
      return CliExitCodes.OK;
    }

    // Batch: continue-on-error, aggregate failures, non-zero exit if any failed.
    if ("-".equals(outputOpt)) {
      throw new ParseException("cannot write multiple inputs to stdout ('-o -')");
    }
    @Nullable Path outDir = batchOutputDir(outputOpt);
    int failures = 0;
    for (int i = 0; i < files.size(); i++) {
      Path input = files.get(i);
      String label = "[" + (i + 1) + "/" + files.size() + "] " + input.getFileName();
      try {
        conversion.convert(input, batchOutput(input, outDir), label);
      } catch (Exception e) {
        failures++;
        ExceptionMapper.Mapping m = ExceptionMapper.map(e);
        System.err.println("Error[" + m.kind() + "] " + input + ": " + m.userMessage());
      }
    }
    if (failures > 0) {
      System.err.printf("%d of %d files failed.%n", failures, files.size());
      return CliExitCodes.GENERIC_ERROR;
    }
    return CliExitCodes.OK;
  }

  // ---- output resolution --------------------------------------------------

  private static OutputTarget singleOutput(Path input, @Nullable String outputOpt) {
    if (outputOpt == null) {
      return OutputTarget.file(SpreadOptions.withDefaults(input).outputPath());
    }
    if ("-".equals(outputOpt)) {
      return OutputTarget.stdout();
    }
    return OutputTarget.file(Path.of(outputOpt));
  }

  private static @Nullable Path batchOutputDir(@Nullable String outputOpt)
      throws IOException, ParseException {
    if (outputOpt == null) {
      return null; // write each output next to its input
    }
    Path dir = Path.of(outputOpt);
    if (Files.exists(dir) && !Files.isDirectory(dir)) {
      throw new ParseException("-o must be a directory when multiple inputs are given: " + dir);
    }
    Files.createDirectories(dir);
    return dir;
  }

  private static OutputTarget batchOutput(Path input, @Nullable Path outDir) {
    Path sibling = SpreadOptions.withDefaults(input).outputPath();
    if (outDir == null) {
      return OutputTarget.file(sibling);
    }
    Path name = Objects.requireNonNull(sibling.getFileName());
    return OutputTarget.file(outDir.resolve(name));
  }

  // ---- options & help -----------------------------------------------------

  private static Options buildOptions() {
    Options options = new Options();
    options.addOption(
        Option.builder("o")
            .longOpt("output")
            .hasArg()
            .argName("FILE|DIR|-")
            .desc(
                "Output path; a directory for batch; '-' for stdout (default: <input>_spread.pdf)")
            .get());
    options.addOption(
        Option.builder("d")
            .longOpt("direction")
            .hasArg()
            .argName("RTL|LTR")
            .desc("Reading direction: RTL (default) or LTR")
            .get());
    options.addOption(
        Option.builder()
            .longOpt("first-page")
            .hasArg()
            .argName("right|left|cover")
            .desc(
                "Which side page 1 opens on: right, left, or a standalone cover"
                    + " (default: the reading direction's natural side)")
            .get());
    options.addOption(
        Option.builder()
            .longOpt("pdf-a")
            .desc(
                "Emit PDF/A-2b for archiving (best-effort: adds the conformance structure;"
                    + " full validity depends on the source PDF's content)")
            .get());
    options.addOption(
        Option.builder()
            .longOpt("low-memory")
            .desc(
                "Spill page streams to a temp file instead of the heap; bounds memory for very"
                    + " large scans on memory-constrained hosts (slightly slower; uses"
                    + " java.io.tmpdir)")
            .get());
    options.addOption(
        Option.builder("v")
            .longOpt("verbose")
            .desc("Enable verbose logging output (DEBUG level)")
            .get());
    options.addOption(Option.builder("h").longOpt("help").desc("Show this help and exit").get());
    options.addOption(Option.builder().longOpt("version").desc("Print version and exit").get());
    return options;
  }

  private static void printHelp(PrintStream out) {
    out.print(
        """
        Usage: tate-yoko-pdf [options] INPUT...

        Convert scanned PDF pages into a side-by-side spread layout for Japanese
        vertical text. INPUT may be one or more PDF files, a directory (its *.pdf
        children), or '-' to read a single PDF from stdin.

        Options:
          -o, --output <FILE|DIR|->   Output path; a directory for batch; '-' for stdout
                                      (default: <input>_spread.pdf)
          -d, --direction <RTL|LTR>   Reading direction (default: RTL)
              --first-page <right|left|cover>
                                      Side page 1 opens on (default: direction's natural side);
                                      'left' (RTL) leads with a blank, 'cover' stands page 1 alone
              --pdf-a                 Emit PDF/A-2b for archiving (best-effort; see docs)
              --low-memory            Spill page streams to a temp file to bound heap on huge scans
          -v, --verbose               Enable verbose (DEBUG) logging
          -h, --help                  Show this help and exit
              --version               Print version and exit

        Examples:
          tate-yoko-pdf novel.pdf                       -> novel_spread.pdf (RTL)
          tate-yoko-pdf novel.pdf --first-page left     page 1 on the left (leading blank)
          tate-yoko-pdf scans/ -o out/                  batch a directory
          cat in.pdf | tate-yoko-pdf - -o - > out.pdf   stdin -> stdout
        """);
  }

  private static void configureVerboseLogging() {
    var context =
        (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
    context
        .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        .setLevel(ch.qos.logback.classic.Level.DEBUG);
  }
}
