package dev.sakashita.tateyokopdf.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Where a single conversion should write: a concrete file, or stdout.
 *
 * <p>The PDF writer needs a real, seekable path, so stdout cannot be written to directly. {@link
 * #write} bridges that: it hands the supplied action a real path — the destination file, or a fresh
 * temp file for stdout — then, for stdout, streams the result to {@code System.out} and removes the
 * temp (always, even on failure).
 */
record OutputTarget(boolean toStdout, @Nullable Path file) {

  static OutputTarget stdout() {
    return new OutputTarget(true, null);
  }

  static OutputTarget file(Path path) {
    return new OutputTarget(false, path);
  }

  /**
   * Runs {@code action} against the concrete path to write to, then completes the write: for
   * stdout, streams the produced PDF to {@code System.out} and deletes the temp file; for a file
   * target the path is the destination and there is nothing else to do.
   */
  void write(IoPathAction action) throws IOException {
    Path realOut = toStdout ? Files.createTempFile("tate-yoko-out", ".pdf") : requireFile();
    try {
      action.accept(realOut);
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

  private Path requireFile() {
    return Objects.requireNonNull(file, "file target must have a path");
  }
}
