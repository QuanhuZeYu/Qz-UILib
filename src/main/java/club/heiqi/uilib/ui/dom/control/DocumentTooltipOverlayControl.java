package club.heiqi.uilib.ui.dom.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiJustifyContent;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleInsets;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * 页面级鼠标跟随 tooltip 浮层控件。
 */
public final class DocumentTooltipOverlayControl {

    /**
     * 视口与指针位置提供器。
     */
    public interface ViewportPointerProvider {

        /**
         * 返回当前 tooltip 视口宽度。
         *
         * @return 视口宽度
         */
        int getViewportWidth();

        /**
         * 返回当前 tooltip 视口高度。
         *
         * @return 视口高度
         */
        int getViewportHeight();

        /**
         * 返回当前视口内指针 X。
         *
         * @return 指针 X
         */
        int getPointerX();

        /**
         * 返回当前视口内指针 Y。
         *
         * @return 指针 Y
         */
        int getPointerY();
    }

    private final DocumentOverlayLayerControl overlayLayer;
    private final ElementNode element;
    private final TextMeasureService textMeasureService;
    private final ViewportPointerProvider viewportPointerProvider;
    private boolean requestedVisible;
    private boolean suppressed;
    private List<String> requestedLines = Collections.emptyList();
    private int maxWidth = 360;
    private float maxWidthRatio = 0.4F;
    private int minWidth = 120;
    private int titleTextColor = 0xFFFDFEFF;
    private int bodyTextColor = 0xFFD8E4FF;
    private int backgroundColor = 0xB8182033;
    private int borderColor = 0xCC8B5CF6;
    private int cornerRadius = 16;
    private int lineSpacing = 4;
    private int verticalPadding = 12;
    private int horizontalPadding = 14;
    private int backdropBlurRadius = 12;
    private float backdropSaturation = 1.2F;
    private int zIndex = 1000;

    /**
     * 创建页面级 tooltip 浮层控件。
     *
     * @param document 所属文档
     * @param textMeasureService 文本测量服务
     * @param viewportPointerProvider 视口与指针位置提供器
     */
    public DocumentTooltipOverlayControl(UiDocument document, TextMeasureService textMeasureService,
            ViewportPointerProvider viewportPointerProvider) {
        this.overlayLayer = new DocumentOverlayLayerControl(Objects.requireNonNull(document, "document"), "aside")
                .setHitTestHidden(true)
                .setZIndex(zIndex);
        this.element = overlayLayer.getElement();
        this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");
        this.viewportPointerProvider = Objects.requireNonNull(viewportPointerProvider, "viewportPointerProvider");
        configureElement();
        applySurfaceStyle();
        hideTooltipElement();
    }

    /**
     * 返回 tooltip 根元素。
     *
     * @return 根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 设置 tooltip 宽度策略。
     *
     * @param minWidth 最小宽度
     * @param maxWidth 最大宽度
     * @param maxWidthRatio 最大宽度占视口比例
     * @return 当前控件
     */
    public DocumentTooltipOverlayControl setWidthPolicy(int minWidth, int maxWidth, float maxWidthRatio) {
        this.minWidth = Math.max(1, minWidth);
        this.maxWidth = Math.max(this.minWidth, maxWidth);
        this.maxWidthRatio = Math.max(0.05F, maxWidthRatio);
        return this;
    }

    /**
     * 设置 tooltip 文本颜色。
     *
     * @param titleTextColor 标题行颜色
     * @param bodyTextColor 正文行颜色
     * @return 当前控件
     */
    public DocumentTooltipOverlayControl setTextColors(int titleTextColor, int bodyTextColor) {
        this.titleTextColor = titleTextColor;
        this.bodyTextColor = bodyTextColor;
        refresh();
        return this;
    }

    /**
     * 设置 tooltip 外壳样式。
     *
     * @param backgroundColor 背景色
     * @param borderColor 边框色
     * @param cornerRadius 圆角半径
     * @param lineSpacing 行间距
     * @param verticalPadding 垂直内边距
     * @param horizontalPadding 水平内边距
     * @return 当前控件
     */
    public DocumentTooltipOverlayControl setSurfaceStyle(int backgroundColor, int borderColor, int cornerRadius,
            int lineSpacing, int verticalPadding, int horizontalPadding) {
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        this.cornerRadius = Math.max(0, cornerRadius);
        this.lineSpacing = Math.max(0, lineSpacing);
        this.verticalPadding = Math.max(0, verticalPadding);
        this.horizontalPadding = Math.max(0, horizontalPadding);
        applySurfaceStyle();
        refresh();
        return this;
    }

    /**
     * 设置 tooltip 背景特效。
     *
     * @param backdropBlurRadius 背景模糊半径
     * @param backdropSaturation 背景饱和度
     * @return 当前控件
     */
    public DocumentTooltipOverlayControl setBackdropStyle(int backdropBlurRadius, float backdropSaturation) {
        this.backdropBlurRadius = Math.max(0, backdropBlurRadius);
        this.backdropSaturation = Math.max(0.0F, backdropSaturation);
        applySurfaceStyle();
        refresh();
        return this;
    }

    /**
     * 设置浮层层级。
     *
     * @param zIndex 层级
     * @return 当前控件
     */
    public DocumentTooltipOverlayControl setZIndex(int zIndex) {
        this.zIndex = zIndex;
        overlayLayer.setZIndex(zIndex);
        applySurfaceStyle();
        refresh();
        return this;
    }

    /**
     * 设置当前 tooltip 显示意图与文本。
     *
     * @param requestedVisible 是否请求显示
     * @param lines tooltip 文本行
     * @return 当前控件
     */
    public DocumentTooltipOverlayControl setRequestedTooltip(boolean requestedVisible, List<String> lines) {
        this.requestedVisible = requestedVisible;
        this.requestedLines = copyLines(lines);
        return this;
    }

    /**
     * 设置当前是否被页面全局状态抑制。
     *
     * @param suppressed 是否抑制
     * @return 当前控件
     */
    public DocumentTooltipOverlayControl setSuppressed(boolean suppressed) {
        this.suppressed = suppressed;
        return this;
    }

    /**
     * 按当前请求状态刷新 tooltip 可见性、内容和定位。
     *
     * @return 当前控件
     */
    public DocumentTooltipOverlayControl refresh() {
        element.clearChildren();
        if (!shouldRender()) {
            element.setAttribute("aria-hidden", "true");
            hideTooltipElement();
            return this;
        }
        element.setAttribute("aria-hidden", "false");
        appendLines();
        DocumentTooltipLayoutResolver.TooltipPlacement placement = DocumentTooltipLayoutResolver.resolve(
                resolveViewportWidth(), resolveViewportHeight(), resolvePointerX(), resolvePointerY(),
                resolvePreferredTooltipWidth(), minWidth, new DocumentTooltipLayoutResolver.TooltipHeightEstimator() {
                    @Override
                    public int estimate(int tooltipWidth) {
                        return estimateTooltipHeight(tooltipWidth);
                    }
                });
        element.style()
                .setWidth(UiStyleLength.px(placement.getWidth()))
                .setHeight(UiStyleLength.auto());
        overlayLayer.setOverlayPosition(placement.getLeft(), placement.getTop());
        return this;
    }

    private void configureElement() {
        element.setAttribute("role", "tooltip")
                .setAttribute("aria-hidden", "true");
        element.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.STRETCH)
                .setJustifyContent(UiJustifyContent.START)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
    }

    private void applySurfaceStyle() {
        element.style()
                .setWidth(UiStyleLength.px(minWidth))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(verticalPadding), UiStyleLength.px(horizontalPadding),
                        UiStyleLength.px(verticalPadding), UiStyleLength.px(horizontalPadding)))
                .setBackgroundColor(backgroundColor)
                .setBorderColor(borderColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(cornerRadius))
                .setTextColor(bodyTextColor)
                .setBackdropBlurRadius(UiStyleLength.px(backdropBlurRadius))
                .setBackdropSaturation(backdropSaturation);
    }

    private boolean shouldRender() {
        return requestedVisible && !suppressed && requestedLines != null && !requestedLines.isEmpty();
    }

    private void appendLines() {
        for (int lineIndex = 0; lineIndex < requestedLines.size(); lineIndex++) {
            ElementNode line = element.getOwnerDocument().element("p");
            line.appendText(requestedLines.get(lineIndex));
            line.style()
                    .setDisplay(UiDisplay.BLOCK)
                    .setWidth(UiStyleLength.auto())
                    .setTextColor(lineIndex == 0 ? titleTextColor : bodyTextColor)
                    .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(0),
                            UiStyleLength.px(lineIndex == requestedLines.size() - 1 ? 0 : lineSpacing),
                            UiStyleLength.px(0)));
            element.append(line);
        }
    }

    private void hideTooltipElement() {
        overlayLayer.collapseOffscreen();
    }

    private int resolvePreferredTooltipWidth() {
        int maxAllowedWidth = Math.max(minWidth,
                Math.min(maxWidth, Math.round(resolveViewportWidth() * maxWidthRatio)));
        int maxLineWidth = 0;
        for (String line : requestedLines) {
            maxLineWidth = Math.max(maxLineWidth, textMeasureService.getStringWidth(line));
        }
        int paddedWidth = maxLineWidth + horizontalPadding * 2;
        return Math.max(minWidth, Math.min(maxAllowedWidth, paddedWidth));
    }

    private int estimateTooltipHeight(int tooltipWidth) {
        if (requestedLines == null || requestedLines.isEmpty()) {
            return verticalPadding * 2;
        }
        int wrapWidth = Math.max(1, tooltipWidth - horizontalPadding * 2);
        int lineHeight = textMeasureService.getLineHeight();
        int totalTextLines = 0;
        for (String line : requestedLines) {
            List<String> wrappedLines = textMeasureService.listFormattedStringToWidth(line, wrapWidth);
            totalTextLines += Math.max(1, wrappedLines == null ? 0 : wrappedLines.size());
        }
        int gapCount = Math.max(0, requestedLines.size() - 1);
        return verticalPadding * 2 + totalTextLines * lineHeight + gapCount * lineSpacing;
    }

    private int resolveViewportWidth() {
        return Math.max(1, viewportPointerProvider.getViewportWidth());
    }

    private int resolveViewportHeight() {
        return Math.max(1, viewportPointerProvider.getViewportHeight());
    }

    private int resolvePointerX() {
        return viewportPointerProvider.getPointerX();
    }

    private int resolvePointerY() {
        return viewportPointerProvider.getPointerY();
    }

    private static List<String> copyLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> copied = new ArrayList<String>(lines.size());
        for (String line : lines) {
            copied.add(line == null ? "" : line);
        }
        return Collections.unmodifiableList(copied);
    }
}
