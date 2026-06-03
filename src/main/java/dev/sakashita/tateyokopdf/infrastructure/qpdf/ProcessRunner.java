package dev.sakashita.tateyokopdf.infrastructure.qpdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs an external process to completion, merging stderr into stdout and bounding the wait by a
 * timeout. Keeps the raw {@link ProcessBuilder} plumbing — start, wait, drain, kill-on-timeout —
 * out of the qpdf adapter, which is then left with only qpdf-specific argument and exit-code
 * policy.
 */
final class ProcessRunner {

  /** The exit code and captured (stdout + stderr) output of a finished process. */
  record Result(int exitCode, String mergedOutput) {}

  /**
   * Starts {@code command}, waits up to {@code timeout} for it to finish, and returns its exit code
   * and merged output. The output is read after the process exits, so a command that floods its
   * pipe beyond the OS buffer could block — acceptable here because qpdf emits little.
   *
   * @throws TimeoutException if the process does not finish within {@code timeout} (it is killed)
   * @throws IOException if the process cannot be started or its output cannot be read
   * @throws InterruptedException if the calling thread is interrupted while waiting
   */
  Result run(List<String> command, Duration timeout)
      throws IOException, InterruptedException, TimeoutException {
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
      process.destroyForcibly();
      throw new TimeoutException("process timed out after " + timeout);
    }
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Result(process.exitValue(), output);
  }
}
