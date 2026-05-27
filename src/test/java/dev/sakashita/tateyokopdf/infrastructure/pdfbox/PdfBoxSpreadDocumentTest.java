package dev.sakashita.tateyokopdf.infrastructure.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.sakashita.tateyokopdf.application.PdfOutputPolicy;
import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import dev.sakashita.tateyokopdf.domain.model.DocumentMetadata;
import dev.sakashita.tateyokopdf.domain.model.LayoutPosition;
import dev.sakashita.tateyokopdf.domain.model.SpreadSpec;
import dev.sakashita.tateyokopdf.port.PagePlacement;
import dev.sakashita.tateyokopdf.port.SourceDocument;
import dev.sakashita.tateyokopdf.port.SpreadDocument;
import dev.sakashita.tateyokopdf.testfixtures.PdfFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PdfBoxSpreadDocumentTest {

  private final PdfBoxDocumentFactory factory = new PdfBoxDocumentFactory();

  @Test
  void addSpreadAndSaveProducesParsablePdf(@TempDir Path tmp) throws Exception {
    Path inputPdf = PdfFixtures.multiPageA4(tmp, "in.pdf", 2);
    Path output = tmp.resolve("out.pdf");
    try (SourceDocument src = factory.openSource(inputPdf);
        SpreadDocument out = factory.createOutput()) {
      var spec = new SpreadSpec(1190f, 842f);
      var placements =
          List.of(
              new PagePlacement(src.pageContent(0), new LayoutPosition(595f, 0f)),
              new PagePlacement(src.pageContent(1), new LayoutPosition(0f, 0f)));
      out.addSpread(spec, placements);
      out.save(output);
    }

    assertThat(Files.size(output)).isPositive();
    try (var doc = Loader.loadPDF(output.toFile())) {
      assertThat(doc.getNumberOfPages()).isOne();
      var mediaBox = doc.getPage(0).getMediaBox();
      assertThat(mediaBox.getWidth()).isEqualTo(1190f, within(0.5f));
      assertThat(mediaBox.getHeight()).isEqualTo(842f, within(0.5f));
      // PDFBox 3.0.7 setVersion(>=1.4) only updates the catalog /Version entry; getVersion()
      // returns the higher of (header byte, catalog /Version), so the catalog bump alone is
      // enough to surface here. The %PDF-x.x header byte assertion lives in QpdfLinearizerTest
      // because only qpdf can rewrite that byte.
      assertThat(doc.getVersion()).isEqualTo(PdfOutputPolicy.TARGET.headerValue());
    }
  }

  @Test
  void saveFailsWithWriteFailedKindForUnwritablePath(@TempDir Path tmp) throws Exception {
    Path inputPdf = PdfFixtures.multiPageA4(tmp, "in.pdf", 1);
    Path badPath = tmp.resolve("nope").resolve("nested").resolve("out.pdf");
    try (SourceDocument src = factory.openSource(inputPdf);
        SpreadDocument out = factory.createOutput()) {
      out.addSpread(
          new SpreadSpec(595f, 842f),
          List.of(new PagePlacement(src.pageContent(0), new LayoutPosition(0f, 0f))));
      assertThatThrownBy(() -> out.save(badPath))
          .isInstanceOfSatisfying(
              SpreadException.class,
              ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.PDF_WRITE_FAILED));
    }
  }

  @Test
  void closeIsIdempotent() {
    SpreadDocument out = factory.createOutput();
    out.close();
    assertThatNoException().isThrownBy(out::close);
  }

  @Test
  void applyMetadataCopiesInfoFieldsAndOverridesProducerAndModDate(@TempDir Path tmp)
      throws Exception {
    Instant sourceCreated = Instant.parse("2024-04-01T12:00:00Z");
    Instant modDate = Instant.parse("2026-05-27T09:00:00Z");
    var source =
        new DocumentMetadata(
            Optional.of("見開き化テスト"),
            Optional.of("テスト著者"),
            Optional.of("subject"),
            Optional.of("a, b, c"),
            Optional.of("Microsoft Word"),
            Optional.of(sourceCreated),
            Optional.of("ja-JP"));

    Path inputPdf = PdfFixtures.multiPageA4(tmp, "in.pdf", 1);
    Path output = tmp.resolve("out.pdf");
    try (SourceDocument src = factory.openSource(inputPdf);
        SpreadDocument out = factory.createOutput()) {
      out.addSpread(
          new SpreadSpec(595f, 842f),
          List.of(new PagePlacement(src.pageContent(0), new LayoutPosition(0f, 0f))));
      out.applyMetadata(source, modDate, "tate-yoko-pdf");
      out.save(output);
    }

    try (var doc = Loader.loadPDF(output.toFile())) {
      var info = doc.getDocumentInformation();
      assertThat(info.getTitle()).isEqualTo("見開き化テスト");
      assertThat(info.getAuthor()).isEqualTo("テスト著者");
      assertThat(info.getSubject()).isEqualTo("subject");
      assertThat(info.getKeywords()).isEqualTo("a, b, c");
      assertThat(info.getCreator()).isEqualTo("Microsoft Word");
      // CreationDate is inherited from source — the content's birth date.
      assertThat(info.getCreationDate().toInstant()).isEqualTo(sourceCreated);
      // ModDate and Producer are *override*, not pass-through — this assertion is the test
      // for that design decision.
      assertThat(info.getModificationDate().toInstant()).isEqualTo(modDate);
      assertThat(info.getProducer()).isEqualTo("tate-yoko-pdf");
      assertThat(doc.getDocumentCatalog().getLanguage()).isEqualTo("ja-JP");
    }
  }

  @Test
  void applyMetadataLeavesAbsentFieldsUntouched(@TempDir Path tmp) throws Exception {
    Instant modDate = Instant.parse("2026-05-27T09:00:00Z");
    Path inputPdf = PdfFixtures.multiPageA4(tmp, "in.pdf", 1);
    Path output = tmp.resolve("out.pdf");
    try (SourceDocument src = factory.openSource(inputPdf);
        SpreadDocument out = factory.createOutput()) {
      out.addSpread(
          new SpreadSpec(595f, 842f),
          List.of(new PagePlacement(src.pageContent(0), new LayoutPosition(0f, 0f))));
      // Empty source metadata — Producer/ModDate are still stamped, but no Title etc.
      out.applyMetadata(DocumentMetadata.empty(), modDate, "tate-yoko-pdf");
      out.save(output);
    }

    try (var doc = Loader.loadPDF(output.toFile())) {
      var info = doc.getDocumentInformation();
      assertThat(info.getTitle()).isNull();
      assertThat(info.getAuthor()).isNull();
      assertThat(info.getCreator()).isNull();
      assertThat(doc.getDocumentCatalog().getLanguage()).isNull();
      // Producer/ModDate must still be set even when source contributes nothing.
      assertThat(info.getProducer()).isEqualTo("tate-yoko-pdf");
      assertThat(info.getModificationDate().toInstant()).isEqualTo(modDate);
    }
  }
}
