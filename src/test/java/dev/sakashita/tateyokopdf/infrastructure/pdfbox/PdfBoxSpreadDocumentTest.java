package dev.sakashita.tateyokopdf.infrastructure.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.sakashita.tateyokopdf.domain.model.LayoutPosition;
import dev.sakashita.tateyokopdf.domain.model.SpreadSpec;
import dev.sakashita.tateyokopdf.port.PagePlacement;
import dev.sakashita.tateyokopdf.port.SourceDocument;
import dev.sakashita.tateyokopdf.port.SpreadDocument;
import dev.sakashita.tateyokopdf.port.exception.ErrorKind;
import dev.sakashita.tateyokopdf.port.exception.SpreadException;
import dev.sakashita.tateyokopdf.testfixtures.PdfFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
}
