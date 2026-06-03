package dev.sakashita.tateyokopdf.cli;

import java.io.IOException;
import java.nio.file.Path;

/**
 * An action over a filesystem path that may fail with {@link IOException}. Lets {@link
 * OutputTarget} and {@link StdinSource} own the temp-file lifecycle while the caller supplies only
 * the work to run against the concrete path.
 */
@FunctionalInterface
interface IoPathAction {
  void accept(Path path) throws IOException;
}
