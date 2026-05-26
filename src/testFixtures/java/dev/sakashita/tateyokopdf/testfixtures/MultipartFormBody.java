package dev.sakashita.tateyokopdf.testfixtures;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;

/** Minimal multipart/form-data builder for {@code javalin-testtools} HTTP client. */
public final class MultipartFormBody {

  private final String boundary = "----tate-yoko-test-" + System.nanoTime();
  private final ByteArrayOutputStream out = new ByteArrayOutputStream();

  public MultipartFormBody addField(String name, String value) {
    write("--" + boundary + "\r\n");
    write("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
    write(value);
    write("\r\n");
    return this;
  }

  public MultipartFormBody addFile(
      String name, String filename, String contentType, byte[] content) {
    write("--" + boundary + "\r\n");
    write(
        "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n");
    write("Content-Type: " + contentType + "\r\n\r\n");
    out.writeBytes(content);
    write("\r\n");
    return this;
  }

  public HttpRequest.BodyPublisher publisher() {
    write("--" + boundary + "--\r\n");
    return HttpRequest.BodyPublishers.ofByteArray(out.toByteArray());
  }

  public String contentType() {
    return "multipart/form-data; boundary=" + boundary;
  }

  private void write(String s) {
    out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
  }
}
