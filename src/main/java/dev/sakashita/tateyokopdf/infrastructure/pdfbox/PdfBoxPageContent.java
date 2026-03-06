package dev.sakashita.tateyokopdf.infrastructure.pdfbox;

import dev.sakashita.tateyokopdf.port.PageContent;
import dev.sakashita.tateyokopdf.port.exception.DocumentWriteException;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;

import java.io.IOException;

public class PdfBoxPageContent implements PageContent {

    private final PDDocument sourceDocument;
    private final int pageIndex;

    PdfBoxPageContent(PDDocument sourceDocument, int pageIndex) {
        this.sourceDocument = sourceDocument;
        this.pageIndex = pageIndex;
    }

    PDFormXObject importInto(PDDocument targetDocument) {
        try {
            var layerUtility = new LayerUtility(targetDocument);
            return layerUtility.importPageAsForm(sourceDocument, pageIndex);
        } catch (IOException e) {
            throw new DocumentWriteException(
                "Failed to import page " + pageIndex + " as FormXObject", e);
        }
    }
}
