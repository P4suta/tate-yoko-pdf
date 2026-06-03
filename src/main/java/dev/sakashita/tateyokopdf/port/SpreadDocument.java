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

  /**
   * Add the structures that mark this document as PDF/A-2b: an sRGB output intent and an XMP packet
   * carrying the {@code pdfaid} identification plus the standard Dublin Core / Adobe PDF / XMP
   * Basic properties.
   *
   * <p>Call this <em>after</em> {@link #applyMetadata} — the XMP packet is built by mirroring the
   * document information dictionary, so the two stay byte-for-byte consistent as PDF/A requires.
   *
   * <p>This adds conformance <em>structure</em> only; it does not rewrite page content. Whether the
   * output actually validates depends on the embedded source pages (fonts must be embedded, colours
   * device-independent or covered by the output intent).
   */
  void finalizePdfA();

  void save(Path destination);

  @Override
  void close();
}
