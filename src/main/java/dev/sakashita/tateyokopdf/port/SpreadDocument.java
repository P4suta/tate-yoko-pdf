package dev.sakashita.tateyokopdf.port;

import dev.sakashita.tateyokopdf.domain.model.SpreadSpec;
import java.nio.file.Path;
import java.util.List;

public interface SpreadDocument extends AutoCloseable {

  void addSpread(SpreadSpec spec, List<PagePlacement> placements);

  void save(Path destination);

  @Override
  void close();
}
