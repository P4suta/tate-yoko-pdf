package dev.sakashita.tateyokopdf.port;

import dev.sakashita.tateyokopdf.domain.model.DocumentMetadata;
import dev.sakashita.tateyokopdf.domain.model.SpreadSpec;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public interface SpreadDocument extends AutoCloseable {

  void addSpread(SpreadSpec spec, List<PagePlacement> placements);

  /**
   * Copy preserved fields from {@code source} into this document and stamp the output-specific
   * values ({@code modDate}, {@code producer}). {@code Optional.empty()} fields on {@code source}
   * leave the corresponding entry untouched — no empty-string overwrite.
   */
  void applyMetadata(DocumentMetadata source, Instant modDate, String producer);

  void save(Path destination);

  @Override
  void close();
}
