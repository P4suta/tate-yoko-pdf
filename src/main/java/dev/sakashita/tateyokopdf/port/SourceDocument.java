package dev.sakashita.tateyokopdf.port;

import dev.sakashita.tateyokopdf.domain.model.DocumentMetadata;
import dev.sakashita.tateyokopdf.domain.model.PageDimension;

public interface SourceDocument extends AutoCloseable {

  int pageCount();

  PageDimension pageDimension(int index);

  PageContent pageContent(int index);

  DocumentMetadata metadata();

  @Override
  void close();
}
