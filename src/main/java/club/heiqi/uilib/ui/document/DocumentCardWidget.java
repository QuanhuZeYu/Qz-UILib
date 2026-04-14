package club.heiqi.uilib.ui.document;

import java.util.Objects;

import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * 文档卡片容器。
 */
public class DocumentCardWidget extends DivWidget {

    public DocumentCardWidget(UiDocumentTheme theme) {
        this(theme, DefaultTextMeasureService.getInstance());
    }

    public DocumentCardWidget(UiDocumentTheme theme, TextMeasureService textMeasureService) {
        super(textMeasureService);
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
