package dev.sakashita.tateyokopdf.domain.service;

import dev.sakashita.tateyokopdf.domain.model.*;
import java.util.Optional;

public class SpreadLayoutCalculator {

    public SpreadLayout calculate(
            ReadingDirection direction,
            PageDimension firstDim,
            PageDimension secondDim) {

        PageDimension bounds = (secondDim != null)
            ? PageDimension.max(firstDim, secondDim)
            : firstDim;

        float halfWidth = bounds.widthPt();
        float spreadWidth = halfWidth * 2;
        float spreadHeight = bounds.heightPt();
        SpreadSpec spec = new SpreadSpec(spreadWidth, spreadHeight);

        float firstCenterX = (halfWidth - firstDim.widthPt()) / 2;
        float firstCenterY = (spreadHeight - firstDim.heightPt()) / 2;

        float firstOffsetX = switch (direction) {
            case RTL -> halfWidth + firstCenterX;
            case LTR -> firstCenterX;
        };

        LayoutPosition firstPos = new LayoutPosition(firstOffsetX, firstCenterY);

        Optional<LayoutPosition> secondPos;
        if (secondDim != null) {
            float secondCenterX = (halfWidth - secondDim.widthPt()) / 2;
            float secondCenterY = (spreadHeight - secondDim.heightPt()) / 2;

            float secondOffsetX = switch (direction) {
                case RTL -> secondCenterX;
                case LTR -> halfWidth + secondCenterX;
            };

            secondPos = Optional.of(new LayoutPosition(secondOffsetX, secondCenterY));
        } else {
            secondPos = Optional.empty();
        }

        return new SpreadLayout(spec, firstPos, secondPos);
    }
}
