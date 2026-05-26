package dev.sakashita.tateyokopdf.infrastructure.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dev.sakashita.tateyokopdf.port.SourceDocument;
import dev.sakashita.tateyokopdf.testfixtures.PdfFixtures;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PdfBoxSourceDocumentTest {

  private final PdfBoxDocumentFactory factory = new PdfBoxDocumentFactory();

  @Test
  void pageCountMatchesFixture(@TempDir Path tmp) throws Exception {
    try (SourceDocument src = factory.openSource(PdfFixtures.multiPageA4(tmp, "n.pdf", 5))) {
      assertThat(src.pageCount()).isEqualTo(5);
    }
  }

  @Test
  void pageDimensionForA4(@TempDir Path tmp) throws Exception {
    try (SourceDocument src = factory.openSource(PdfFixtures.multiPageA4(tmp, "a4.pdf", 1))) {
      var dim = src.pageDimension(0);
      // A4: 595 x 842 pt (approx)
      assertThat(dim.widthPt()).isEqualTo(595f, org.assertj.core.api.Assertions.within(0.5f));
      assertThat(dim.heightPt()).isEqualTo(842f, org.assertj.core.api.Assertions.within(0.5f));
    }
  }

  @Test
  void pageDimensionSwapsForRotation90(@TempDir Path tmp) throws Exception {
    try (SourceDocument src = factory.openSource(PdfFixtures.rotated(tmp, "r90.pdf", 90))) {
      var dim = src.pageDimension(0);
      // After swap, width should equal A4 height
      assertThat(dim.widthPt()).isEqualTo(842f, org.assertj.core.api.Assertions.within(0.5f));
      assertThat(dim.heightPt()).isEqualTo(595f, org.assertj.core.api.Assertions.within(0.5f));
    }
  }

  @Test
  void pageDimensionSwapsForRotation270(@TempDir Path tmp) throws Exception {
    try (SourceDocument src = factory.openSource(PdfFixtures.rotated(tmp, "r270.pdf", 270))) {
      var dim = src.pageDimension(0);
      assertThat(dim.widthPt()).isEqualTo(842f, org.assertj.core.api.Assertions.within(0.5f));
    }
  }

  @Test
  void pageContentReturnsPdfBoxPageContent(@TempDir Path tmp) throws Exception {
    try (SourceDocument src = factory.openSource(PdfFixtures.multiPageA4(tmp, "p.pdf", 1))) {
      assertThat(src.pageContent(0)).isInstanceOf(PdfBoxPageContent.class);
    }
  }

  @Test
  void closeIsIdempotent(@TempDir Path tmp) throws Exception {
    SourceDocument src = factory.openSource(PdfFixtures.multiPageA4(tmp, "p.pdf", 1));
    src.close();
    assertThatNoException().isThrownBy(src::close);
  }
}
