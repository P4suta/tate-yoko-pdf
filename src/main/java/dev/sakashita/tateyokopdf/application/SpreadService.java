package dev.sakashita.tateyokopdf.application;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.Validators;
import dev.sakashita.tateyokopdf.domain.model.*;
import dev.sakashita.tateyokopdf.domain.service.SpreadLayoutCalculator;
import dev.sakashita.tateyokopdf.port.*;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpreadService {

  private static final Logger log = LoggerFactory.getLogger(SpreadService.class);

  private final DocumentFactory documentFactory;
  private final SpreadLayoutCalculator calculator;
  private final PdfPostProcessor pdfPostProcessor;
  private final ProgressListener progressListener;

  public SpreadService(
      DocumentFactory documentFactory,
      SpreadLayoutCalculator calculator,
      PdfPostProcessor pdfPostProcessor,
      ProgressListener progressListener) {
    this.documentFactory = documentFactory;
    this.calculator = calculator;
    this.pdfPostProcessor = pdfPostProcessor;
    this.progressListener = progressListener;
  }

  public void execute(SpreadOptions options) {
    Validators.requireExists(options.sourcePath(), ErrorKind.PDF_NOT_FOUND);
    long startTime = System.currentTimeMillis();

    try (var source = documentFactory.openSource(options.sourcePath());
        var output = documentFactory.createOutput()) {

      int totalPages = source.pageCount();
      log.info("Source PDF: {} pages", totalPages);

      List<PagePairSpec> pairs =
          PaginationStrategyFactory.from(options.coverSingle()).paginate(totalPages);
      progressListener.onStart(pairs.size());

      for (int i = 0; i < pairs.size(); i++) {
        processSpread(source, output, pairs.get(i), options.direction());
        progressListener.onSpreadComplete(i + 1, pairs.size());
      }

      output.applyMetadata(source.metadata(), Instant.now(), Producer.NAME);
      if (options.pdfA()) {
        // After applyMetadata so the PDF/A XMP packet can mirror the info dictionary.
        output.finalizePdfA();
      }
      output.save(options.outputPath());
    }
    // Post-processing runs *after* the SpreadDocument is closed so the file
    // handle is released before qpdf opens it in --replace-input mode.
    pdfPostProcessor.process(options.outputPath());
    progressListener.onComplete(System.currentTimeMillis() - startTime);
  }

  private void processSpread(
      SourceDocument source,
      SpreadDocument output,
      PagePairSpec pairSpec,
      ReadingDirection direction) {

    switch (pairSpec) {
      case PagePairSpec.Pair(var first, var second) -> {
        PageDimension firstDim = source.pageDimension(first);
        PageDimension secondDim = source.pageDimension(second);

        SpreadLayout layout = calculator.calculate(direction, firstDim, secondDim);

        List<PagePlacement> placements =
            List.of(
                new PagePlacement(source.pageContent(first), layout.firstPosition()),
                new PagePlacement(
                    source.pageContent(second), layout.secondPosition().orElseThrow()));

        output.addSpread(layout.spec(), placements);
      }

      case PagePairSpec.Single(var pageIndex) -> {
        PageDimension dim = source.pageDimension(pageIndex);

        SpreadLayout layout = calculator.calculate(direction, dim, null);

        List<PagePlacement> placements =
            List.of(new PagePlacement(source.pageContent(pageIndex), layout.firstPosition()));

        output.addSpread(layout.spec(), placements);
      }
    }
  }
}
