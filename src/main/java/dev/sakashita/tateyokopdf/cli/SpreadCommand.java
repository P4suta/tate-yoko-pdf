package dev.sakashita.tateyokopdf.cli;

import dev.sakashita.tateyokopdf.application.SpreadOptions;
import dev.sakashita.tateyokopdf.application.SpreadService;
import dev.sakashita.tateyokopdf.domain.model.FirstPageMode;
import dev.sakashita.tateyokopdf.domain.model.MemoryMode;
import dev.sakashita.tateyokopdf.domain.model.ReadingDirection;
import dev.sakashita.tateyokopdf.domain.service.SpreadLayoutCalculator;
import dev.sakashita.tateyokopdf.infrastructure.pdfbox.PdfBoxDocumentFactory;
import dev.sakashita.tateyokopdf.infrastructure.qpdf.QpdfLinearizer;
import dev.sakashita.tateyokopdf.observability.ExceptionMapper;
import dev.sakashita.tateyokopdf.port.DocumentFactory;
import dev.sakashita.tateyokopdf.port.PdfPostProcessor;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
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
 * concrete PDFs via {@link InputResolver}, and runs {@link SpreadService} for each. Diagnostics and
 * progress go to stderr; with {@code -o -} the converted PDF is streamed to stdout as a clean
 * binary stream.
 */
public final class SpreadCommand {

  static final String NAME = "tate-yoko-pdf";
  static final String VERSION = "tate-yoko-pdf 1.0.0";

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

      List<String> positionals = cmd.getArgList();
      if (positionals.isEmpty()) {
        printHelp(System.out);
        return CliExitCodes.OK;
      }

      @Nullable String directionValue = cmd.getOptionValue("direction");
      ReadingDirection direction = parseDirection(directionValue != null ? directionValue : "RTL");
      FirstPageMode firstPageMode =
          resolveFirstPage(
              cmd.getOptionValue("first-page"), cmd.hasOption("cover-single"), direction);
      boolean pdfA = cmd.hasOption("pdf-a");
      boolean lowMemory = cmd.hasOption("low-memory");
      @Nullable String outputOpt = cmd.getOptionValue("output");

      return execute(
          InputResolver.resolve(positionals), outputOpt, direction, firstPageMode, pdfA, lowMemory);
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

  private static int execute(
      InputResolver.Resolved resolved,
      @Nullable String outputOpt,
      ReadingDirection direction,
      FirstPageMode firstPageMode,
      boolean pdfA,
      boolean lowMemory)
      throws IOException, ParseException {

    MemoryMode memoryMode = lowMemory ? MemoryMode.SCRATCH_FILE : MemoryMode.IN_MEMORY;
    DocumentFactory factory = new PdfBoxDocumentFactory(memoryMode);
    SpreadLayoutCalculator calculator = new SpreadLayoutCalculator();
    PdfPostProcessor postProcessor = QpdfLinearizer.create();

    if (resolved.stdin()) {
      OutputTarget target =
          (outputOpt == null || "-".equals(outputOpt))
              ? OutputTarget.stdout()
              : OutputTarget.file(Path.of(outputOpt));
      convertStdin(factory, calculator, postProcessor, target, direction, firstPageMode, pdfA);
      return CliExitCodes.OK;
    }

    List<Path> files = resolved.files();
    if (files.isEmpty()) {
      throw new ParseException("no PDF files found in the given inputs");
    }

    // Single input: fail-fast — let the exception bubble up to run()'s mapper.
    if (files.size() == 1) {
      Path input = files.get(0);
      convertFile(
          factory,
          calculator,
          postProcessor,
          input,
          singleOutput(input, outputOpt),
          direction,
          firstPageMode,
          pdfA,
          null);
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
        convertFile(
            factory,
            calculator,
            postProcessor,
            input,
            batchOutput(input, outDir),
            direction,
            firstPageMode,
            pdfA,
            label);
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

  private static void convertStdin(
      DocumentFactory factory,
      SpreadLayoutCalculator calculator,
      PdfPostProcessor postProcessor,
      OutputTarget target,
      ReadingDirection direction,
      FirstPageMode firstPageMode,
      boolean pdfA)
      throws IOException {
    Path tmpIn = Files.createTempFile("tate-yoko-in", ".pdf");
    try {
      // Files.copy(InputStream, ...) does not close System.in.
      Files.copy(System.in, tmpIn, StandardCopyOption.REPLACE_EXISTING);
      convertFile(
          factory, calculator, postProcessor, tmpIn, target, direction, firstPageMode, pdfA, null);
    } finally {
      Files.deleteIfExists(tmpIn);
    }
  }

  private static void convertFile(
      DocumentFactory factory,
      SpreadLayoutCalculator calculator,
      PdfPostProcessor postProcessor,
      Path input,
      OutputTarget target,
      ReadingDirection direction,
      FirstPageMode firstPageMode,
      boolean pdfA,
      @Nullable String label)
      throws IOException {

    boolean toStdout = target.toStdout();
    Path realOut = toStdout ? Files.createTempFile("tate-yoko-out", ".pdf") : target.requireFile();
    try {
      var options = new SpreadOptions(input, realOut, direction, firstPageMode, pdfA);
      var service =
          new SpreadService(factory, calculator, postProcessor, new ConsoleProgressListener(label));
      service.execute(options);
      if (toStdout) {
        // Files.copy(Path, OutputStream) does not close System.out.
        Files.copy(realOut, System.out);
        System.out.flush();
      }
    } finally {
      if (toStdout) {
        Files.deleteIfExists(realOut);
      }
    }
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

  /** Where a single conversion should write: a concrete file, or stdout. */
  private record OutputTarget(boolean toStdout, @Nullable Path file) {
    static OutputTarget stdout() {
      return new OutputTarget(true, null);
    }

    static OutputTarget file(Path path) {
      return new OutputTarget(false, path);
    }

    Path requireFile() {
      return Objects.requireNonNull(file, "file target must have a path");
    }
  }

  // ---- parsing helpers ----------------------------------------------------

  private static ReadingDirection parseDirection(String value) throws ParseException {
    try {
      return ReadingDirection.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new ParseException("invalid direction '" + value + "' (expected RTL or LTR)");
    }
  }

  /**
   * Resolves the opening mode from {@code --first-page} (an absolute side or {@code cover}) plus
   * the deprecated {@code --cover-single} alias, given the reading direction. An absolute side
   * equals {@code STANDARD} when it falls on the direction's leading side (RTL→right, LTR→left);
   * the opposite side requests a leading blank. Nothing specified → {@code STANDARD}.
   */
  private static FirstPageMode resolveFirstPage(
      @Nullable String firstPage, boolean coverSingleAlias, ReadingDirection direction)
      throws ParseException {
    if (firstPage != null && coverSingleAlias) {
      throw new ParseException("--first-page and --cover-single cannot be combined");
    }
    if (coverSingleAlias) {
      return FirstPageMode.COVER; // deprecated alias for --first-page cover
    }
    if (firstPage == null) {
      return FirstPageMode.STANDARD;
    }
    return switch (firstPage.toLowerCase(Locale.ROOT)) {
      case "cover" -> FirstPageMode.COVER;
      case "right" ->
          direction == ReadingDirection.RTL ? FirstPageMode.STANDARD : FirstPageMode.LEADING_BLANK;
      case "left" ->
          direction == ReadingDirection.LTR ? FirstPageMode.STANDARD : FirstPageMode.LEADING_BLANK;
      default ->
          throw new ParseException(
              "invalid first-page '" + firstPage + "' (expected right, left, or cover)");
    };
  }

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
            .longOpt("cover-single")
            .desc("Deprecated alias for --first-page cover")
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
