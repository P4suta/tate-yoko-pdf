package dev.sakashita.tateyokopdf.infrastructure.pdfbox;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import dev.sakashita.tateyokopdf.domain.model.MemoryMode;
import dev.sakashita.tateyokopdf.domain.model.PdfOutputPolicy;
import dev.sakashita.tateyokopdf.domain.model.PdfVersion;
import dev.sakashita.tateyokopdf.port.DocumentFactory;
import dev.sakashita.tateyokopdf.port.SourceDocument;
import dev.sakashita.tateyokopdf.port.SpreadDocument;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessStreamCache.StreamCacheCreateFunction;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfBoxDocumentFactory implements DocumentFactory {

  private static final Logger log = LoggerFactory.getLogger(PdfBoxDocumentFactory.class);

  private final PdfVersion outputVersion;
  private final MemoryMode memoryMode;

  public PdfBoxDocumentFactory(PdfVersion outputVersion, MemoryMode memoryMode) {
    this.outputVersion = outputVersion;
    this.memoryMode = memoryMode;
  }

  public PdfBoxDocumentFactory(MemoryMode memoryMode) {
    this(PdfOutputPolicy.TARGET, memoryMode);
  }

  public PdfBoxDocumentFactory(PdfVersion outputVersion) {
    this(outputVersion, MemoryMode.IN_MEMORY);
  }

  public PdfBoxDocumentFactory() {
    this(PdfOutputPolicy.TARGET, MemoryMode.IN_MEMORY);
  }

  @Override
  public SourceDocument openSource(Path path) {
    log.info("Opening source PDF: {}", path.getFileName());
    try {
      var doc = Loader.loadPDF(path.toFile(), streamCache());
      return new PdfBoxSourceDocument(doc);
    } catch (InvalidPasswordException e) {
      throw SpreadException.withDetail(ErrorKind.PDF_PASSWORD_PROTECTED, "path=" + path, e);
    } catch (IOException e) {
      throw SpreadException.withDetail(ErrorKind.PDF_CORRUPTED, "path=" + path, e);
    }
  }

  @Override
  public SpreadDocument createOutput() {
    return new PdfBoxSpreadDocument(outputVersion, streamCache());
  }

  /**
   * Picks PDFBox's stream cache for the configured {@link MemoryMode}: memory-only (heap, fast) or
   * temp-file-only (disk, heap-bounded). A fresh function is returned per call because each
   * document owns its own cache. Temp files land in {@code java.io.tmpdir} and are removed on
   * document close.
   */
  private StreamCacheCreateFunction streamCache() {
    return memoryMode == MemoryMode.SCRATCH_FILE
        ? IOUtils.createTempFileOnlyStreamCache()
        : IOUtils.createMemoryOnlyStreamCache();
  }
}
