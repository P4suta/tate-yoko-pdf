package dev.sakashita.tateyokopdf.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.apache.commons.cli.ParseException;

/**
 * Expands the raw positional arguments into the concrete list of source PDFs to convert.
 *
 * <ul>
 *   <li>{@code -} means "read a single PDF from stdin" and must be the only input.
 *   <li>a directory expands to its direct {@code *.pdf} children (case-insensitive), sorted by
 *       name, non-recursively.
 *   <li>any other path is taken verbatim — existence is validated later by the conversion pipeline
 *       so a missing file surfaces as {@code PDF_NOT_FOUND} rather than a generic resolver error.
 * </ul>
 */
final class InputResolver {

  /** Outcome of resolving the positional arguments. */
  record Resolved(boolean stdin, List<Path> files) {}

  private InputResolver() {}

  static Resolved resolve(List<String> rawInputs) throws ParseException {
    if (rawInputs.stream().anyMatch(InputResolver::isStdin)) {
      if (rawInputs.size() != 1) {
        throw new ParseException("'-' (stdin) cannot be combined with other inputs");
      }
      return new Resolved(true, List.of());
    }

    List<Path> files = new ArrayList<>();
    for (String raw : rawInputs) {
      Path path = Path.of(raw);
      if (Files.isDirectory(path)) {
        files.addAll(listPdfs(path));
      } else {
        files.add(path);
      }
    }
    return new Resolved(false, List.copyOf(files));
  }

  private static List<Path> listPdfs(Path dir) throws ParseException {
    try (Stream<Path> entries = Files.list(dir)) {
      return entries
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
          .sorted(Comparator.comparing(p -> p.getFileName().toString()))
          .toList();
    } catch (IOException e) {
      throw new ParseException("cannot read directory '" + dir + "': " + e.getMessage());
    }
  }

  private static boolean isStdin(String raw) {
    return "-".equals(raw);
  }
}
