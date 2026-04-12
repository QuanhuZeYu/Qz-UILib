package club.heiqi.uilib.ui.document;

import java.util.Objects;

import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;

/**
 * 文档段落容器。
 */
public class DocumentSectionWidget extends DivWidget {

    public DocumentSectionWidget(UiDocumentTheme theme) {
        UiDocumentTheme resolvedTheme = Objects.requireNonNull(theme, "theme");
        setDirection(Direction.COLUMN)
                .setAlignItems(AlignItems.STRETCH)
                .setJustifyContent(JustifyContent.START)
                .setWrap(Wrap.NOWRAP)
                .setOverflowX(Overflow.VISIBLE)
                .setOverflowY(Overflow.VISIBLE)
                .setGap(resolvedTheme.getSectionGap());
    }
}
