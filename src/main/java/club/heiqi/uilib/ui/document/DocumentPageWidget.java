package club.heiqi.uilib.ui.document;

import java.util.Objects;

import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.control.ScrollViewportWidget;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 语义化文档页面容器。
 */
public class DocumentPageWidget extends ScrollViewportWidget {

    private final UiDocumentTheme theme;

    public DocumentPageWidget(UiDocumentTheme theme) {
        this(theme, DefaultTextMeasureService.getInstance());
    }

    public DocumentPageWidget(UiDocumentTheme theme, TextMeasureService textMeasureService) {
        super(textMeasureService);
        this.theme = Objects.requireNonNull(theme, "theme");
        setViewportFrameAlignment(FrameAlign.CENTER, FrameAlign.START);
        applyViewportSurfaceStyle(this.theme.getShellSurface());
        applyScrollbarStyle(this.theme.getScrollbarStyle());

        DivWidget content = getContent();
        content.setDirection(DivWidget.Direction.COLUMN)
                .setAlignItems(DivWidget.AlignItems.STRETCH)
                .setJustifyContent(DivWidget.JustifyContent.START)
                .setWrap(DivWidget.Wrap.NOWRAP)
                .setOverflowX(DivWidget.Overflow.VISIBLE)
                .setOverflowY(DivWidget.Overflow.VISIBLE)
                .setGap(this.theme.getDocumentGap());
    }

    /**
     * 追加文档块级内容。
     *
     * @param child 文档块
     * @return 当前页面
     */
    public DocumentPageWidget addBlock(Widget child) {
        getContent().addChild(child);
        return this;
    }

    /**
     * 设置页面壳留白。
     *
     * @param padding 统一留白
     * @return 当前页面
     */
    public DocumentPageWidget setShellPadding(int padding) {
        applyViewportPadding(padding);
        return this;
    }

    /**
     * 设置页面壳留白。
     *
     * @param left 左侧留白
     * @param top 上侧留白
     * @param right 右侧留白
     * @param bottom 下侧留白
     * @return 当前页面
     */
    public DocumentPageWidget setShellPadding(int left, int top, int right, int bottom) {
        applyViewportPadding(left, top, right, bottom);
        return this;
    }

    /**
     * 设置内容宽度区间。
     *
     * @param minContentWidth 最小宽度
     * @param maxContentWidth 最大宽度
     * @return 当前页面
     */
    public DocumentPageWidget setContentWidthRange(int minContentWidth, int maxContentWidth) {
        setViewportFrameWidthRange(minContentWidth, maxContentWidth);
        return this;
    }

    /**
     * 设置最小内容高度。
     *
     * @param minContentHeight 最小内容高度
     * @return 当前页面
     */
    public DocumentPageWidget setMinContentHeight(int minContentHeight) {
        setViewportFrameMinHeight(minContentHeight);
        return this;
    }

    /**
     * 设置相对父视口的填充比例。
     *
     * @param maxViewportFillWidth 最大宽度占比
     * @param maxViewportFillHeight 最大高度占比
     * @return 当前页面
     */
    public DocumentPageWidget setViewportFillRatio(float maxViewportFillWidth, float maxViewportFillHeight) {
        setViewportFrameFillRatio(maxViewportFillWidth, maxViewportFillHeight);
        return this;
    }

}
