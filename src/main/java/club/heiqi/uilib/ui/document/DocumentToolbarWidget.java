package club.heiqi.uilib.ui.document;

import java.util.Objects;

import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;

/**
 * 文档工具栏容器。
 */
public class DocumentToolbarWidget extends DivWidget {

    public DocumentToolbarWidget(UiDocumentTheme theme) {
        UiDocumentTheme resolvedTheme = Objects.requireNonNull(theme, "theme");
        setDirection(Direction.ROW)
                .setAlignItems(AlignItems.STRETCH)
                .setJustifyContent(JustifyContent.START)
                .setWrap(Wrap.WRAP)
                .setOverflowX(Overflow.VISIBLE)
                .setOverflowY(Overflow.VISIBLE)
                .setGap(resolvedTheme.getToolbarGap());
        setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()));
    }
}
