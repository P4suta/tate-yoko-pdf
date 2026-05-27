package dev.sakashita.tateyokopdf.web.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Two threads racing to remove the same job: the download-completion path ({@link
 * JobRegistry#remove(UUID)}) and the {@code TempFileGc} sweep (which delegates to {@link
 * JobRegistry#removeOlderThan(java.time.Instant)}). The registry's per-entry compare-and-remove
 * must make this idempotent — only one of the two should "win" the eviction, neither should NPE,
 * and the eventual state must be empty.
 */
final class TempFileGcRaceTest {

  @Test
  void concurrentDownloadRemoveAndSweepConvergeWithoutErrors(@TempDir Path tmp) throws Exception {
    int rounds = 200;
    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      for (int round = 0; round < rounds; round++) {
        var registry = new JobRegistry();
        Path workDir = Files.createDirectories(tmp.resolve("round-" + round));
        var job =
            registry.register(workDir, workDir.resolve("in"), workDir.resolve("out"), "x.pdf");

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        var winners = ConcurrentHashMap.<String>newKeySet();

        var _ =
            pool.submit(
                () -> {
                  try {
                    start.await();
                    var removed = registry.remove(job.id());
                    if (removed.isPresent()) {
                      winners.add("download");
                    }
                  } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                  } finally {
                    done.countDown();
                  }
                });

        var _ =
            pool.submit(
                () -> {
                  try {
                    start.await();
                    var expired = registry.removeOlderThan(Instant.MAX);
                    if (!expired.isEmpty()) {
                      winners.add("sweep");
                    }
                  } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                  } finally {
                    done.countDown();
                  }
                });

        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS))
            .as("round %d did not converge in time", round)
            .isTrue();

        // Exactly one path observed the entry (compare-and-remove semantics). No NPE, no double
        // claim, registry empty regardless.
        assertThat(winners).as("round %d winners", round).hasSize(1);
        assertThat(registry.find(job.id())).as("round %d registry residue", round).isEmpty();
        assertThat(registry.listener(job.id())).as("round %d listener residue", round).isEmpty();
      }
    } finally {
      pool.shutdownNow();
    }
  }
}
