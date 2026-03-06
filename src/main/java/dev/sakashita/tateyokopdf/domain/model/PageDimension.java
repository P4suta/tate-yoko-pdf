package dev.sakashita.tateyokopdf.domain.model;

public record PageDimension(float widthPt, float heightPt) {

    public PageDimension {
        if (widthPt <= 0 || heightPt <= 0) {
            throw new IllegalArgumentException(
                "Page dimensions must be positive: width=%f, height=%f"
                    .formatted(widthPt, heightPt));
        }
    }

    public static PageDimension max(PageDimension a, PageDimension b) {
        return new PageDimension(
            Math.max(a.widthPt, b.widthPt),
            Math.max(a.heightPt, b.heightPt)
        );
    }
}
