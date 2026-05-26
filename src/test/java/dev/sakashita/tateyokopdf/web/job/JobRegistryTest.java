package dev.sakashita.tateyokopdf.web.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class JobRegistryTest {

  @Test
  void registerCreatesJobAndListener() {
    var reg = new JobRegistry();
    var job = reg.register(Path.of("/w"), Path.of("/in"), Path.of("/out"), "x.pdf");
    assertThat(reg.find(job.id())).contains(job);
    assertThat(reg.listener(job.id())).isPresent();
  }

  @Test
  void findUnknownReturnsEmpty() {
    var reg = new JobRegistry();
    assertThat(reg.find(UUID.randomUUID())).isEmpty();
    assertThat(reg.listener(UUID.randomUUID())).isEmpty();
  }

  @Test
  void removeReturnsTheJobAndClearsListener() {
    var reg = new JobRegistry();
    var job = reg.register(Path.of("/w"), Path.of("/in"), Path.of("/out"), "x.pdf");
    assertThat(reg.remove(job.id())).contains(job);
    assertThat(reg.find(job.id())).isEmpty();
    assertThat(reg.listener(job.id())).isEmpty();
  }

  @Test
  void removeOlderThanFiltersByCreatedAt() throws Exception {
    var reg = new JobRegistry();
    var old = reg.register(Path.of("/w"), Path.of("/in"), Path.of("/out"), "old.pdf");
    Thread.sleep(20);
    var fresh = reg.register(Path.of("/w"), Path.of("/in"), Path.of("/out"), "fresh.pdf");
    Instant cutoff = fresh.createdAt().minus(Duration.ofMillis(10));
    var removed = reg.removeOlderThan(cutoff);
    assertThat(removed).extracting(Job::id).containsExactly(old.id());
    assertThat(reg.find(old.id())).isEmpty();
    assertThat(reg.find(fresh.id())).isPresent();
  }

  @Test
  void drainAllReturnsSnapshotAndClears() {
    var reg = new JobRegistry();
    reg.register(Path.of("/w"), Path.of("/in"), Path.of("/out"), "a.pdf");
    reg.register(Path.of("/w"), Path.of("/in"), Path.of("/out"), "b.pdf");
    var snap = reg.drainAll();
    assertThat(snap).hasSize(2);
    assertThat(reg.find(snap.iterator().next().id())).isEmpty();
  }

  @Test
  void concurrentRegisterRemoveIsThreadSafe() throws Exception {
    var reg = new JobRegistry();
    int threads = 8;
    int perThread = 250;
    var pool = Executors.newFixedThreadPool(threads);
    var start = new CountDownLatch(1);
    var done = new CountDownLatch(threads);
    var jobs = ConcurrentHashMap.<UUID>newKeySet();
    try {
      for (int t = 0; t < threads; t++) {
        pool.submit(
            () -> {
              try {
                start.await();
                for (int i = 0; i < perThread; i++) {
                  var j = reg.register(Path.of("/w"), Path.of("/in"), Path.of("/out"), "x.pdf");
                  jobs.add(j.id());
                }
              } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
              } finally {
                done.countDown();
              }
            });
      }
      start.countDown();
      assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }
    assertThat(jobs).hasSize(threads * perThread);
    for (UUID id : jobs) {
      assertThat(reg.find(id)).isPresent();
    }
  }
}
