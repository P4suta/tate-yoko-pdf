package dev.sakashita.tateyokopdf.web.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SingleInstanceLockTest {

  @Test
  void findLiveInstanceEmptyWhenNoLockFile(@TempDir Path tmp) {
    var lock = new SingleInstanceLock(tmp.resolve("app.lock"));
    assertThat(lock.findLiveInstance()).isEmpty();
  }

  @Test
  void findLiveInstanceEmptyWhenLockFileMalformed(@TempDir Path tmp) throws Exception {
    Path lockPath = tmp.resolve("app.lock");
    Files.writeString(lockPath, "garbage\n");
    var lock = new SingleInstanceLock(lockPath);
    assertThat(lock.findLiveInstance()).isEmpty();
  }

  @Test
  void findLiveInstanceEmptyWhenPidIsDead(@TempDir Path tmp) throws Exception {
    Path lockPath = tmp.resolve("app.lock");
    // very large pid that is unlikely to be alive on the host
    Files.writeString(lockPath, "9999999\n8080\n");
    var lock = new SingleInstanceLock(lockPath);
    assertThat(lock.findLiveInstance()).isEmpty();
  }

  @Test
  void findLiveInstanceEmptyWhenHealthCheckFails(@TempDir Path tmp) throws Exception {
    Path lockPath = tmp.resolve("app.lock");
    long myPid = ProcessHandle.current().pid();
    // pid is alive (this JVM) but port 1 will refuse the health probe
    Files.writeString(lockPath, myPid + "\n1\n");
    var lock = new SingleInstanceLock(lockPath);
    assertThat(lock.findLiveInstance()).isEmpty();
  }

  @Test
  void claimWritesPidAndPort(@TempDir Path tmp) throws Exception {
    Path lockPath = tmp.resolve("sub/app.lock");
    var lock = new SingleInstanceLock(lockPath);
    lock.claim(45678);
    assertThat(Files.exists(lockPath)).isTrue();
    var lines = Files.readAllLines(lockPath);
    assertThat(lines).hasSize(2);
    assertThat(Long.parseLong(lines.get(0).trim())).isEqualTo(ProcessHandle.current().pid());
    assertThat(Integer.parseInt(lines.get(1).trim())).isEqualTo(45678);
  }

  @Test
  void claimOverwritesExisting(@TempDir Path tmp) throws Exception {
    Path lockPath = tmp.resolve("app.lock");
    Files.writeString(lockPath, "1\n1\n");
    var lock = new SingleInstanceLock(lockPath);
    lock.claim(12345);
    var lines = Files.readAllLines(lockPath);
    assertThat(Integer.parseInt(lines.get(1).trim())).isEqualTo(12345);
  }

  @Test
  void releaseDeletesFile(@TempDir Path tmp) throws Exception {
    Path lockPath = tmp.resolve("app.lock");
    Files.writeString(lockPath, "x");
    var lock = new SingleInstanceLock(lockPath);
    lock.release();
    assertThat(Files.exists(lockPath)).isFalse();
  }

  @Test
  void releaseSilentWhenMissing(@TempDir Path tmp) {
    var lock = new SingleInstanceLock(tmp.resolve("missing.lock"));
    lock.release(); // no exception
  }
}
