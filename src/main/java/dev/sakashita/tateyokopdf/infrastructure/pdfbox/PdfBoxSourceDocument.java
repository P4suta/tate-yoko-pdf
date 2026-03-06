package dev.sakashita.tateyokopdf.infrastructure.pdfbox;

import dev.sakashita.tateyokopdf.domain.model.PageDimension;
import dev.sakashita.tateyokopdf.port.PageContent;
import dev.sakashita.tateyokopdf.port.SourceDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfBoxSourceDocument implements SourceDocument {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxSourceDocument.class);
    private final PDDocument document;

    PdfBoxSourceDocument(PDDocument document) {
        this.document = document;
    }

    @Override
    public int pageCount() {
        return document.getNumberOfPages();
    }

    @Override
    public PageDimension pageDimension(int index) {
        PDPage page = document.getPage(index);
        PDRectangle cropBox = page.getCropBox();
        int rotation = page.getRotation();

        float width = cropBox.getWidth();
        float height = cropBox.getHeight();

        if (rotation == 90 || rotation == 270) {
            log.debug("Page {} has rotation={}, swapping dimensions", index, rotation);
            return new PageDimension(height, width);
        }

        return new PageDimension(width, height);
    }

    @Override
    public PageContent pageContent(int index) {
        return new PdfBoxPageContent(document, index);
    }

    @Override
    public void close() {
        try {
            document.close();
        } catch (Exception e) {
            log.warn("Failed to close source document", e);
        }
    }
}
