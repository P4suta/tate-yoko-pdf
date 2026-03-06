package dev.sakashita.tateyokopdf.infrastructure.pdfbox;

import dev.sakashita.tateyokopdf.port.DocumentFactory;
import dev.sakashita.tateyokopdf.port.SourceDocument;
import dev.sakashita.tateyokopdf.port.SpreadDocument;
import dev.sakashita.tateyokopdf.port.exception.DocumentReadException;
import dev.sakashita.tateyokopdf.port.exception.PasswordProtectedException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class PdfBoxDocumentFactory implements DocumentFactory {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxDocumentFactory.class);

    @Override
    public SourceDocument openSource(Path path) {
        log.info("Opening source PDF: {}", path);
        try {
            var doc = Loader.loadPDF(path.toFile());
            return new PdfBoxSourceDocument(doc);
        } catch (InvalidPasswordException e) {
            throw new PasswordProtectedException(
                "The PDF is password-protected and cannot be processed: " + path, e);
        } catch (IOException e) {
            throw new DocumentReadException(
                "Failed to open PDF: " + path, e);
        }
    }

    @Override
    public SpreadDocument createOutput() {
        return new PdfBoxSpreadDocument();
    }
}
