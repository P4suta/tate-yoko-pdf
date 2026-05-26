package dev.sakashita.tateyokopdf.web.job;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class JobRegistry {

  private final ConcurrentMap<UUID, Job> jobs = new ConcurrentHashMap<>();

  public Job register(Path workDir, Path inputPath, Path outputPath, String originalName) {
    UUID id = UUID.randomUUID();
    Job job = new Job(id, workDir, inputPath, outputPath, originalName, Instant.now());
    jobs.put(id, job);
    return job;
  }

  public Optional<Job> find(UUID id) {
    return Optional.ofNullable(jobs.get(id));
  }

  public Optional<Job> remove(UUID id) {
    return Optional.ofNullable(jobs.remove(id));
  }

  public List<Job> removeOlderThan(Instant cutoff) {
    List<Job> expired = new ArrayList<>();
    for (Map.Entry<UUID, Job> entry : jobs.entrySet()) {
      if (entry.getValue().createdAt().isBefore(cutoff)) {
        if (jobs.remove(entry.getKey(), entry.getValue())) {
          expired.add(entry.getValue());
        }
      }
    }
    return expired;
  }

  public Collection<Job> drainAll() {
    List<Job> all = new ArrayList<>(jobs.values());
    jobs.clear();
    return all;
  }
}
