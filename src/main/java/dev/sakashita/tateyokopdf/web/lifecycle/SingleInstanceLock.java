package dev.sakashita.tateyokopdf.web.lifecycle;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores PID + port in a lock file under ~/.tate-yoko-pdf/. Lets a second startup detect a still
 * running instance and just open its URL in the browser instead of binding the port itself.
 */
public final class SingleInstanceLock {

  private static final Logger log = LoggerFactory.getLogger(SingleInstanceLock.class);

  private final Path lockFile;

  public SingleInstanceLock() {
    String home = System.getProperty("user.home", ".");
    this.lockFile = Path.of(home, ".tate-yoko-pdf", "app.lock");
  }

  /**
   * Returns the URL of an already running instance if its PID is alive and its /health endpoint
   * responds. Otherwise returns empty (caller should claim the lock and start the server).
   */
  public Optional<URI> findLiveInstance() {
    if (!Files.isRegularFile(lockFile)) {
      return Optional.empty();
    }
    try {
      List<String> lines = Files.readAllLines(lockFile);
      if (lines.size() < 2) {
        return Optional.empty();
      }
      long pid = Long.parseLong(lines.get(0).trim());
      int port = Integer.parseInt(lines.get(1).trim());
      if (!pidAlive(pid)) {
        return Optional.empty();
      }
      URI url = URI.create("http://127.0.0.1:" + port + "/");
      if (!healthOk(url)) {
        return Optional.empty();
      }
      return Optional.of(url);
    } catch (IOException | NumberFormatException e) {
      log.debug("Could not read lock file {}: {}", lockFile, e.getMessage());
      return Optional.empty();
    }
  }

  public void claim(int port) {
    try {
      Path parent = lockFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      long pid = ProcessHandle.current().pid();
      Files.writeString(
          lockFile,
          pid + System.lineSeparator() + port + System.lineSeparator(),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
    } catch (IOException e) {
      log.warn("Failed to write lock file {}: {}", lockFile, e.getMessage());
    }
  }

  public void release() {
    try {
      Files.deleteIfExists(lockFile);
    } catch (IOException e) {
      log.debug("Failed to delete lock file {}: {}", lockFile, e.getMessage());
    }
  }

  private static boolean pidAlive(long pid) {
    return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
  }

  private static boolean healthOk(URI base) {
    try {
      HttpURLConnection conn =
          (HttpURLConnection) URI.create(base + "health").toURL().openConnection();
      conn.setConnectTimeout((int) Duration.ofSeconds(1).toMillis());
      conn.setReadTimeout((int) Duration.ofSeconds(1).toMillis());
      conn.setRequestMethod("GET");
      int code = conn.getResponseCode();
      conn.disconnect();
      return code == 200;
    } catch (IOException e) {
      return false;
    }
  }
}
