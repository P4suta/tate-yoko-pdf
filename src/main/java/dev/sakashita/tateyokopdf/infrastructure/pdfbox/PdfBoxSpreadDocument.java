package dev.sakashita.tateyokopdf.infrastructure.pdfbox;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import dev.sakashita.tateyokopdf.domain.model.DocumentMetadata;
import dev.sakashita.tateyokopdf.domain.model.PdfVersion;
import dev.sakashita.tateyokopdf.domain.model.SpreadSpec;
import dev.sakashita.tateyokopdf.port.PagePlacement;
import dev.sakashita.tateyokopdf.port.SpreadDocument;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.function.Consumer;
import javax.xml.transform.TransformerException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.AdobePDFSchema;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.type.BadFieldValueException;
import org.apache.xmpbox.xml.XmpSerializer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfBoxSpreadDocument implements SpreadDocument {

  private static final Logger log = LoggerFactory.getLogger(PdfBoxSpreadDocument.class);
  private final PDDocument document;
  private final PdfVersion version;

  PdfBoxSpreadDocument(PdfVersion version) {
    this.document = new PDDocument();
    this.version = version;
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
  public void finalizePdfA() {
    // PDF/A-2 is built on ISO 32000-1 (PDF 1.7). Refuse to stamp the pdfaid marker onto any other
    // version rather than emit a file that claims conformance it cannot meet (e.g. if TARGET is
    // ever flipped to 2.0, which is the basis for PDF/A-4, not PDF/A-2).
    if (version != PdfVersion.PDF_1_7) {
      throw SpreadException.withDetail(
          ErrorKind.INTERNAL, "PDF/A-2b requires PDF 1.7 output, but version is " + version, null);
    }
    try {
      addSrgbOutputIntent();
      addPdfAMetadata();
    } catch (IOException | TransformerException | BadFieldValueException e) {
      throw SpreadException.of(ErrorKind.PDF_WRITE_FAILED, e);
    }
    log.debug("Marked output as PDF/A-2b (sRGB output intent + pdfaid XMP)");
  }

  /**
   * PDF/A requires an output intent so device colour is reproducible. The JDK's built-in sRGB
   * profile avoids bundling an .icc resource (and the shadow-jar / jpackage plumbing that entails);
   * {@code java.desktop} is already a bundled module because PDFBox needs it.
   */
  private void addSrgbOutputIntent() throws IOException {
    ICC_Profile srgb = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
    PDOutputIntent intent = new PDOutputIntent(document, new ByteArrayInputStream(srgb.getData()));
    intent.setInfo("sRGB IEC61966-2.1");
    intent.setOutputCondition("sRGB IEC61966-2.1");
    intent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
    intent.setRegistryName("http://www.color.org");
    document.getDocumentCatalog().addOutputIntent(intent);
  }

  /**
   * Build the XMP packet by mirroring the document information dictionary that {@link
   * #applyMetadata} already populated. Reading the values back from the Info dictionary (rather
   * than from the original source) means both representations come from the same second-resolution
   * PDF date strings and string values, so PDF/A's Info/XMP consistency rule holds by construction.
   */
  private void addPdfAMetadata() throws IOException, TransformerException, BadFieldValueException {
    PDDocumentInformation info = document.getDocumentInformation();
    XMPMetadata xmp = XMPMetadata.createXMPMetadata();

    PDFAIdentificationSchema pdfaid = xmp.createAndAddPDFAIdentificationSchema();
    pdfaid.setPart(2);
    pdfaid.setConformance("B");

    DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
    ifPresent(info.getTitle(), dc::setTitle);
    ifPresent(info.getAuthor(), dc::addCreator);
    ifPresent(info.getSubject(), dc::setDescription);

    AdobePDFSchema pdf = xmp.createAndAddAdobePDFSchema();
    ifPresent(info.getProducer(), pdf::setProducer);
    ifPresent(info.getKeywords(), pdf::setKeywords);

    XMPBasicSchema basic = xmp.createAndAddXMPBasicSchema();
    ifPresent(info.getCreator(), basic::setCreatorTool);
    Calendar creationDate = info.getCreationDate();
    if (creationDate != null) {
      basic.setCreateDate(creationDate);
    }
    Calendar modificationDate = info.getModificationDate();
    if (modificationDate != null) {
      basic.setModifyDate(modificationDate);
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new XmpSerializer().serialize(xmp, out, true);
    PDMetadata metadata = new PDMetadata(document);
    metadata.importXMPMetadata(out.toByteArray());
    document.getDocumentCatalog().setMetadata(metadata);
  }

  private static void ifPresent(@Nullable String value, Consumer<String> setter) {
    if (value != null && !value.isEmpty()) {
      setter.accept(value);
    }
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
