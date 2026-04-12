package club.heiqi.uilib.ui.document;

import java.util.Objects;

import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;

/**
 * 文档卡片容器。
 */
public class DocumentCardWidget extends DivWidget {

    public DocumentCardWidget(UiDocumentTheme theme) {
        UiDocumentTheme resolvedTheme = Objects.requireNonNull(theme, "theme");
        setDirection(Direction.COLUMN)
                .setAlignItems(AlignItems.STRETCH)
                .setJustifyContent(JustifyContent.START)
                .setWrap(Wrap.NOWRAP)
                .setOverflowX(Overflow.VISIBLE)
                .setOverflowY(Overflow.VISIBLE)
                .setPadding(resolvedTheme.getCardPadding())
                .setGap(resolvedTheme.getCardGap())
                .setSurfaceStyle(resolvedTheme.getCardSurface());
    }
}
