package club.heiqi.uilib.ui.document;

import java.util.Objects;

import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 文档中的响应式卡片流容器。
 */
public class DocumentFlowRowWidget extends DivWidget {

    public DocumentFlowRowWidget(UiDocumentTheme theme) {
        UiDocumentTheme resolvedTheme = Objects.requireNonNull(theme, "theme");
        setDirection(Direction.ROW)
                .setAlignItems(AlignItems.STRETCH)
                .setJustifyContent(JustifyContent.START)
                .setWrap(Wrap.WRAP)
                .setOverflowX(Overflow.VISIBLE)
                .setOverflowY(Overflow.VISIBLE)
                .setColumnGap(resolvedTheme.getFlowColumnGap())
                .setRowGap(resolvedTheme.getFlowRowGap());
        setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()));
    }

    /**
     * 追加会按主轴拉伸的块。
     *
     * @param child 子块
     * @param growFactor 增长权重
     * @return 当前容器
     */
    public DocumentFlowRowWidget addFlexibleBlock(Widget child, float growFactor) {
        if (child != null) {
            UiLayoutSpec layoutSpec = child.getLayoutSpec();
            if (layoutSpec == null) {
                layoutSpec = new UiLayoutSpec();
            }
            layoutSpec.setGrow(Math.max(0.0F, growFactor)).setShrink(1.0F);
            child.setLayoutSpec(layoutSpec);
            addChild(child);
        }
        return this;
    }
}
