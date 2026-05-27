package dev.sakashita.tateyokopdf.infrastructure.pdfbox;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import dev.sakashita.tateyokopdf.domain.model.DocumentMetadata;
import dev.sakashita.tateyokopdf.domain.model.PdfVersion;
import dev.sakashita.tateyokopdf.domain.model.SpreadSpec;
import dev.sakashita.tateyokopdf.port.PagePlacement;
import dev.sakashita.tateyokopdf.port.SpreadDocument;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.GregorianCalendar;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfBoxSpreadDocument implements SpreadDocument {

  private static final Logger log = LoggerFactory.getLogger(PdfBoxSpreadDocument.class);
  private final PDDocument document;

  PdfBoxSpreadDocument(PdfVersion version) {
    this.document = new PDDocument();
    // PDFBox 3.0.7 quirk: for any value >= 1.4 this only updates the catalog /Version entry,
    // NOT the %PDF-x.x header byte. The header byte is rewritten downstream by qpdf
    // (--min-version=X.Y); together they yield a fully version-consistent output.
    this.document.setVersion(version.headerValue());
  }

  @Override
  public void addSpread(SpreadSpec spec, List<PagePlacement> placements) {
    var rect = new PDRectangle(spec.widthPt(), spec.heightPt());
    var page = new PDPage(rect);
    document.addPage(page);

    try (var cs = new PDPageContentStream(document, page)) {
      for (var placement : placements) {
        var pdfBoxContent = (PdfBoxPageContent) placement.content();
        PDFormXObject form = pdfBoxContent.importInto(document);

        cs.saveGraphicsState();
        cs.transform(
            Matrix.getTranslateInstance(
                placement.position().offsetXPt(), placement.position().offsetYPt()));
        cs.drawForm(form);
        cs.restoreGraphicsState();
      }
    } catch (IOException e) {
      throw SpreadException.of(ErrorKind.PDF_WRITE_FAILED, e);
    }

    log.debug(
        "Added spread: {}x{} pt with {} placements",
        spec.widthPt(),
        spec.heightPt(),
        placements.size());
  }

  @Override
  public void applyMetadata(DocumentMetadata source, Instant modDate, String producer) {
    PDDocumentInformation info = document.getDocumentInformation();
    source.title().ifPresent(info::setTitle);
    source.author().ifPresent(info::setAuthor);
    source.subject().ifPresent(info::setSubject);
    source.keywords().ifPresent(info::setKeywords);
    source.creator().ifPresent(info::setCreator);
    source
        .creationDate()
        .ifPresent(t -> info.setCreationDate(GregorianCalendar.from(t.atZone(ZoneOffset.UTC))));
    info.setModificationDate(GregorianCalendar.from(modDate.atZone(ZoneOffset.UTC)));
    info.setProducer(producer);
    source.language().ifPresent(document.getDocumentCatalog()::setLanguage);
  }

  @Override
  public void save(Path destination) {
    try {
      document.save(destination.toFile());
      log.info("Saved output to {}", destination.getFileName());
    } catch (IOException e) {
      throw SpreadException.withDetail(ErrorKind.PDF_WRITE_FAILED, "destination=" + destination, e);
    }
  }

  @Override
  public void close() {
    try {
      document.close();
    } catch (Exception e) {
      log.warn("Failed to close output document", e);
    }
  }
}
