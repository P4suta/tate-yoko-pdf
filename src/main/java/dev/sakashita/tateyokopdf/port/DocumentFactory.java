package dev.sakashita.tateyokopdf.port;

import java.nio.file.Path;

public interface DocumentFactory {

  SourceDocument openSource(Path path);

  SpreadDocument createOutput();
}
