package dev.sakashita.tateyokopdf.infrastructure.pdfbox;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import dev.sakashita.tateyokopdf.port.DocumentFactory;
import dev.sakashita.tateyokopdf.port.SourceDocument;
import dev.sakashita.tateyokopdf.port.SpreadDocument;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfBoxDocumentFactory implements DocumentFactory {

  private static final Logger log = LoggerFactory.getLogger(PdfBoxDocumentFactory.class);

  @Override
  public SourceDocument openSource(Path path) {
    log.info("Opening source PDF: {}", path.getFileName());
    try {
      var doc = Loader.loadPDF(path.toFile());
      return new PdfBoxSourceDocument(doc);
    } catch (InvalidPasswordException e) {
      throw SpreadException.withDetail(ErrorKind.PDF_PASSWORD_PROTECTED, "path=" + path, e);
    } catch (IOException e) {
      throw SpreadException.withDetail(ErrorKind.PDF_CORRUPTED, "path=" + path, e);
    }
  }

  @Override
  public SpreadDocument createOutput() {
    return new PdfBoxSpreadDocument();
  }
}
