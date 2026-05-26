package dev.sakashita.tateyokopdf.web.job;

import java.nio.file.Path;
import java.util.UUID;

public record Job(UUID id, Path workDir, Path inputPath, Path outputPath, String originalName) {}
