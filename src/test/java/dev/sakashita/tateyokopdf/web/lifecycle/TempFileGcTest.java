package dev.sakashita.tateyokopdf.web.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.sakashita.tateyokopdf.testfixtures.TestClock;
import dev.sakashita.tateyokopdf.web.job.Job;
import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TempFileGcTest {

  @Test
  void sweepDeletesExpiredJobsAndWorkDirs(@TempDir Path tmp) throws Exception {
    var registry = new JobRegistry();
    Path freshWork = Files.createDirectory(tmp.resolve("fresh"));
    Path expiredWork = Files.createDirectory(tmp.resolve("expired"));
    Files.writeString(expiredWork.resolve("file.txt"), "x");
    Files.writeString(freshWork.resolve("file.txt"), "y");

    Job expiredJob = registry.register(expiredWork, Path.of("/in"), Path.of("/out"), "old.pdf");
    // make expiredJob look old by re-registering with a doctored clock isn't trivial; instead
    // rely on the fact that the TTL we choose is shorter than the sleep below.
    Thread.sleep(15);
    Job freshJob = registry.register(freshWork, Path.of("/in"), Path.of("/out"), "new.pdf");

    var clock = TestClock.at(Instant.now());
    var gc = new TempFileGc(registry, Duration.ofMillis(10), Duration.ofMillis(20), clock);
    gc.start();
    try {
      await()
          .atMost(2, TimeUnit.SECONDS)
          .untilAsserted(
              () -> {
                assertThat(registry.find(expiredJob.id())).isEmpty();
                assertThat(Files.exists(expiredWork)).isFalse();
              });
      assertThat(registry.find(freshJob.id())).isPresent();
      assertThat(Files.exists(freshWork)).isTrue();
    } finally {
      gc.stop();
    }
  }

  @Test
  void stopCanBeCalledWithoutStart() {
    var gc =
        new TempFileGc(
            new JobRegistry(), Duration.ofSeconds(1), Duration.ofSeconds(1), Instant::now);
    gc.stop();
  }
}
