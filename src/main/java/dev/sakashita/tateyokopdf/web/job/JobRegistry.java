package dev.sakashita.tateyokopdf.web.job;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class JobRegistry {

  private final ConcurrentMap<UUID, Job> jobs = new ConcurrentHashMap<>();

  public Job register(Path workDir, Path inputPath, Path outputPath, String originalName) {
    UUID id = UUID.randomUUID();
    Job job = new Job(id, workDir, inputPath, outputPath, originalName);
    jobs.put(id, job);
    return job;
  }

  public Optional<Job> find(UUID id) {
    return Optional.ofNullable(jobs.get(id));
  }

  public Optional<Job> remove(UUID id) {
    return Optional.ofNullable(jobs.remove(id));
  }
}
