package club.heiqi.uilib.ui.host;

import java.util.Objects;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.base.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.base.layout.UiLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * 文档宿主通用组件工厂。
 */
public final class DocumentHostWidgetFactory {

    private DocumentHostWidgetFactory() {}

    /**
     * 创建填满宿主视口的文档组件。
     *
     * @param document 文档
     * @param preferredWidth 首选宽度
     * @param preferredHeight 首选高度
     * @param textMeasureService 文本测量服务
     * @param viewportRootScrollingEnabled 是否启用根视口滚动
     * @return 已完成宿主级默认配置的文档组件
     */
    public static HtmlLikeDocumentWidget createViewportDocumentWidget(UiDocument document, int preferredWidth,
            int preferredHeight, TextMeasureService textMeasureService, boolean viewportRootScrollingEnabled) {
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(Objects.requireNonNull(document, "document"),
                preferredWidth, preferredHeight, Objects.requireNonNull(textMeasureService, "textMeasureService"));
        widget.setViewportRootScrollingEnabled(viewportRootScrollingEnabled);
        widget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));
        return widget;
    }
}
