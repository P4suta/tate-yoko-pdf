package dev.sakashita.tateyokopdf.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Bridges a single PDF arriving on {@code System.in} to the file-based conversion path. */
final class StdinSource {

  private StdinSource() {}

  /**
   * Drains {@code System.in} into a temp PDF, runs {@code action} against it, and deletes the temp
   * afterwards (always, even on failure). {@code Files.copy(InputStream, …)} does not close {@code
   * System.in}.
   */
  static void withStdinPdf(IoPathAction action) throws IOException {
    Path tmpIn = Files.createTempFile("tate-yoko-in", ".pdf");
    try {
      Files.copy(System.in, tmpIn, StandardCopyOption.REPLACE_EXISTING);
      action.accept(tmpIn);
    } finally {
      Files.deleteIfExists(tmpIn);
    }
  }
}
