package dev.sakashita.tateyokopdf.infrastructure.pdfbox;

import dev.sakashita.tateyokopdf.domain.model.SpreadSpec;
import dev.sakashita.tateyokopdf.port.PagePlacement;
import dev.sakashita.tateyokopdf.port.SpreadDocument;
import dev.sakashita.tateyokopdf.port.exception.DocumentWriteException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class PdfBoxSpreadDocument implements SpreadDocument {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxSpreadDocument.class);
    private final PDDocument document;

    PdfBoxSpreadDocument() {
        this.document = new PDDocument();
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
                cs.transform(Matrix.getTranslateInstance(
                    placement.position().offsetXPt(),
                    placement.position().offsetYPt()
                ));
                cs.drawForm(form);
                cs.restoreGraphicsState();
            }
        } catch (IOException e) {
            throw new DocumentWriteException("Failed to create spread page", e);
        }

        log.debug("Added spread: {}x{} pt with {} placements",
            spec.widthPt(), spec.heightPt(), placements.size());
    }

    @Override
    public void save(Path destination) {
        try {
            document.save(destination.toFile());
            log.info("Saved output to {}", destination);
        } catch (IOException e) {
            throw new DocumentWriteException("Failed to save output PDF: " + destination, e);
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
