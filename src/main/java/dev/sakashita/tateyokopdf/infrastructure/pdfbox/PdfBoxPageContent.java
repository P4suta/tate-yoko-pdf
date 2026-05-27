package dev.sakashita.tateyokopdf.infrastructure.pdfbox;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import dev.sakashita.tateyokopdf.port.PageContent;
import java.io.IOException;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;

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
      throw SpreadException.withDetail(ErrorKind.PDF_WRITE_FAILED, "page=" + pageIndex, e);
    }
  }
}
