package dev.sakashita.tateyokopdf.web.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import io.javalin.http.UploadedFile;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class UploadValidatorTest {

  @Mock UploadedFile file;

  private final UploadValidator validator = new UploadValidator();

  private static byte[] pdfBytes() {
    return "%PDF-1.7\n%mock-fixture\n".getBytes(StandardCharsets.UTF_8);
  }

  private void stubValidPdf(String filename) {
    when(file.size()).thenReturn((long) pdfBytes().length);
    when(file.filename()).thenReturn(filename);
    when(file.contentType()).thenReturn("application/pdf");
    when(file.content()).thenReturn((InputStream) new ByteArrayInputStream(pdfBytes()));
  }

  @Test
  void nullFileRejectedAsEmpty() {
    assertThatThrownBy(() -> validator.validate(null))
        .isInstanceOfSatisfying(
            SpreadException.class, ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.UPLOAD_EMPTY));
  }

  @Test
  void zeroSizeRejectedAsEmpty() {
    when(file.size()).thenReturn(0L);
    assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(SpreadException.class);
  }

  @Test
  void blankFilenameRejected() {
    when(file.size()).thenReturn(10L);
    when(file.filename()).thenReturn("   ");
    assertThatThrownBy(() -> validator.validate(file))
        .isInstanceOfSatisfying(
            SpreadException.class, ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.UPLOAD_INVALID));
  }

  @Test
  void wrongExtensionRejected() {
    when(file.size()).thenReturn(10L);
    when(file.filename()).thenReturn("doc.txt");
    assertThatThrownBy(() -> validator.validate(file))
        .isInstanceOfSatisfying(
            SpreadException.class,
            ex -> {
              assertThat(ex.kind()).isEqualTo(ErrorKind.UPLOAD_INVALID);
              assertThat(ex.technicalDetail()).contains(".pdf");
            });
  }

  @Test
  void wrongContentTypeRejected() {
    when(file.size()).thenReturn(10L);
    when(file.filename()).thenReturn("a.pdf");
    when(file.contentType()).thenReturn("text/html");
    assertThatThrownBy(() -> validator.validate(file))
        .isInstanceOfSatisfying(
            SpreadException.class, ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.UPLOAD_INVALID));
  }

  @Test
  void missingMagicBytesRejected() {
    when(file.size()).thenReturn(10L);
    when(file.filename()).thenReturn("a.pdf");
    when(file.contentType()).thenReturn("application/pdf");
    when(file.content())
        .thenReturn(
            (InputStream)
                new ByteArrayInputStream("NOPE-not-a-pdf".getBytes(StandardCharsets.UTF_8)));
    assertThatThrownBy(() -> validator.validate(file))
        .isInstanceOfSatisfying(
            SpreadException.class, ex -> assertThat(ex.technicalDetail()).contains("%PDF"));
  }

  @Test
  void validPdfReturnsCleanedName() {
    stubValidPdf("hello.pdf");
    assertThat(validator.validate(file)).isEqualTo("hello.pdf");
  }

  @Test
  void caseInsensitivePdfExtensionAccepted() {
    stubValidPdf("HELLO.PDF");
    assertThat(validator.validate(file)).isEqualTo("HELLO.PDF");
  }

  @Test
  void contentTypeWithCharsetParameterAccepted() {
    when(file.size()).thenReturn((long) pdfBytes().length);
    when(file.filename()).thenReturn("ok.pdf");
    when(file.contentType()).thenReturn("application/pdf; charset=binary");
    when(file.content()).thenReturn((InputStream) new ByteArrayInputStream(pdfBytes()));
    assertThat(validator.validate(file)).isEqualTo("ok.pdf");
  }

  @Test
  void unknownContentTypeIsAcceptedWhenOctetStream() {
    when(file.size()).thenReturn((long) pdfBytes().length);
    when(file.filename()).thenReturn("ok.pdf");
    when(file.contentType()).thenReturn("application/octet-stream");
    when(file.content()).thenReturn((InputStream) new ByteArrayInputStream(pdfBytes()));
    assertThat(validator.validate(file)).isEqualTo("ok.pdf");
  }

  @Test
  void nullContentTypeAccepted() {
    when(file.size()).thenReturn((long) pdfBytes().length);
    when(file.filename()).thenReturn("ok.pdf");
    when(file.contentType()).thenReturn(null);
    when(file.content()).thenReturn((InputStream) new ByteArrayInputStream(pdfBytes()));
    assertThat(validator.validate(file)).isEqualTo("ok.pdf");
  }

  @Test
  void pathTraversalStrippedToBasename() {
    when(file.size()).thenReturn((long) pdfBytes().length);
    when(file.filename()).thenReturn("../../etc/passwd.pdf");
    when(file.contentType()).thenReturn("application/pdf");
    when(file.content()).thenReturn((InputStream) new ByteArrayInputStream(pdfBytes()));
    assertThat(validator.validate(file)).isEqualTo("passwd.pdf");
  }

  @Test
  void windowsBackslashStrippedToBasename() {
    when(file.size()).thenReturn((long) pdfBytes().length);
    when(file.filename()).thenReturn("C:\\Users\\me\\doc.pdf");
    when(file.contentType()).thenReturn("application/pdf");
    when(file.content()).thenReturn((InputStream) new ByteArrayInputStream(pdfBytes()));
    assertThat(validator.validate(file)).isEqualTo("doc.pdf");
  }

  @Test
  void filenameOver255BytesRejected() {
    String longName = "a".repeat(252) + ".pdf"; // 256 bytes
    when(file.size()).thenReturn(10L);
    when(file.filename()).thenReturn(longName);
    assertThatThrownBy(() -> validator.validate(file))
        .isInstanceOfSatisfying(
            SpreadException.class, ex -> assertThat(ex.technicalDetail()).contains("255"));
  }

  @Test
  void unicodeFilenameNormalisedToNfc() {
    // U+0041 U+030A (LATIN LETTER A + COMBINING RING) → U+00C5 (Å) under NFC
    String decomposed = "Å.pdf";
    when(file.size()).thenReturn((long) pdfBytes().length);
    when(file.filename()).thenReturn(decomposed);
    when(file.contentType()).thenReturn("application/pdf");
    when(file.content()).thenReturn((InputStream) new ByteArrayInputStream(pdfBytes()));
    String returned = validator.validate(file);
    assertThat(returned).isEqualTo("Å.pdf");
  }
}
