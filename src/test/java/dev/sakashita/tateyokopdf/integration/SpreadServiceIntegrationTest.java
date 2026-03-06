package dev.sakashita.tateyokopdf.integration;

import dev.sakashita.tateyokopdf.application.ProgressListener;
import dev.sakashita.tateyokopdf.application.SpreadOptions;
import dev.sakashita.tateyokopdf.application.SpreadService;
import dev.sakashita.tateyokopdf.domain.model.ReadingDirection;
import dev.sakashita.tateyokopdf.domain.service.SpreadLayoutCalculator;
import dev.sakashita.tateyokopdf.domain.strategy.CoverSinglePagination;
import dev.sakashita.tateyokopdf.domain.strategy.StandardPagination;
import dev.sakashita.tateyokopdf.infrastructure.pdfbox.PdfBoxDocumentFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpreadServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private Path fourPagePdf;
    private Path fivePagePdf;
    private Path singlePagePdf;

    private static final ProgressListener NOOP_LISTENER = new ProgressListener() {
        @Override public void onStart(int totalSpreads) {}
        @Override public void onSpreadComplete(int currentSpread, int totalSpreads) {}
        @Override public void onComplete(long elapsedMillis) {}
    };

    @BeforeEach
    void createFixtures() throws IOException {
        fourPagePdf = createTestPdf(tempDir.resolve("4pages.pdf"), 4,
            PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
        fivePagePdf = createTestPdf(tempDir.resolve("5pages.pdf"), 5,
            PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
        singlePagePdf = createTestPdf(tempDir.resolve("1page.pdf"), 1,
            PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
    }

    private Path createTestPdf(Path path, int pageCount, float width, float height) throws IOException {
        try (var doc = new PDDocument()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int i = 0; i < pageCount; i++) {
                var page = new PDPage(new PDRectangle(width, height));
                doc.addPage(page);
                try (var cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, 24);
                    cs.newLineAtOffset(100, height / 2);
                    cs.showText("Page " + (i + 1));
                    cs.endText();
                }
            }
            doc.save(path.toFile());
        }
        return path;
    }

    @Test
    void fourPages_producesTwoSpreads() throws IOException {
        Path output = tempDir.resolve("output_4.pdf");
        var options = new SpreadOptions(fourPagePdf, output, ReadingDirection.RTL, false);

        createService(false).execute(options);

        try (var result = Loader.loadPDF(output.toFile())) {
            assertThat(result.getNumberOfPages()).isEqualTo(2);
            for (int i = 0; i < result.getNumberOfPages(); i++) {
                PDRectangle mediaBox = result.getPage(i).getMediaBox();
                assertThat(mediaBox.getWidth()).isGreaterThan(mediaBox.getHeight());
            }
        }
    }

    @Test
    void fivePages_producesThreeSpreadsWithLastSingle() throws IOException {
        Path output = tempDir.resolve("output_5.pdf");
        var options = new SpreadOptions(fivePagePdf, output, ReadingDirection.RTL, false);

        createService(false).execute(options);

        try (var result = Loader.loadPDF(output.toFile())) {
            assertThat(result.getNumberOfPages()).isEqualTo(3);
        }
    }

    @Test
    void singlePage_producesOneSingleSpread() throws IOException {
        Path output = tempDir.resolve("output_1.pdf");
        var options = new SpreadOptions(singlePagePdf, output, ReadingDirection.RTL, false);

        createService(false).execute(options);

        try (var result = Loader.loadPDF(output.toFile())) {
            assertThat(result.getNumberOfPages()).isEqualTo(1);
            PDRectangle mediaBox = result.getPage(0).getMediaBox();
            assertThat(mediaBox.getWidth()).isGreaterThan(mediaBox.getHeight());
        }
    }

    @Test
    void coverSingle_fourPages_producesThreeSpreads() throws IOException {
        Path output = tempDir.resolve("output_cover.pdf");
        var options = new SpreadOptions(fourPagePdf, output, ReadingDirection.RTL, true);

        createService(true).execute(options);

        try (var result = Loader.loadPDF(output.toFile())) {
            // 4 pages with cover single: Single(0), Pair(1,2), Single(3)
            assertThat(result.getNumberOfPages()).isEqualTo(3);
        }
    }

    @Test
    void ltrDirection_producesCorrectSpreads() throws IOException {
        Path output = tempDir.resolve("output_ltr.pdf");
        var options = new SpreadOptions(fourPagePdf, output, ReadingDirection.LTR, false);

        createService(false).execute(options);

        try (var result = Loader.loadPDF(output.toFile())) {
            assertThat(result.getNumberOfPages()).isEqualTo(2);
        }
    }

    private SpreadService createService(boolean coverSingle) {
        return new SpreadService(
            new PdfBoxDocumentFactory(),
            new SpreadLayoutCalculator(),
            coverSingle ? new CoverSinglePagination() : new StandardPagination(),
            NOOP_LISTENER
        );
    }
}
