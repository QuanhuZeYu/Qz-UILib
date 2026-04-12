package club.heiqi.uilib.ui.theme;

import java.util.Objects;

import club.heiqi.uilib.ui.control.UiControlTheme;

/**
 * 文档页默认视觉主题。
 *
 * <p>结构由页面自行组合，视觉常量集中收口在主题层。</p>
 */
public final class UiDocumentTheme {

    private static final UiDocumentTheme DEFAULT_THEME = createDefaultTheme();

    private final UiSurfaceStyle shellSurface;
    private final UiSurfaceStyle cardSurface;
    private final UiControlTheme.LabelStyle titleLabelStyle;
    private final UiControlTheme.LabelStyle bodyLabelStyle;
    private final UiControlTheme.LabelStyle emphasisLabelStyle;
    private final UiControlTheme.LabelStyle secondaryLabelStyle;
    private final UiControlTheme.ButtonStyle buttonStyle;
    private final UiControlTheme.ToggleSwitchStyle toggleSwitchStyle;
    private final UiControlTheme.SegmentedSelectorStyle segmentedSelectorStyle;
    private final UiControlTheme.TextInputStyle textInputStyle;
    private final UiControlTheme.ScrollbarStyle scrollbarStyle;
    private final UiControlTheme.InventorySlotGridStyle inventorySlotGridStyle;
    private final int documentGap;
    private final int cardPadding;
    private final int cardGap;
    private final int sectionGap;
    private final int flowColumnGap;
    private final int flowRowGap;
    private final int toolbarGap;
    private final int formRowGap;
    private final int formLabelWidth;

    public UiDocumentTheme(UiSurfaceStyle shellSurface, UiSurfaceStyle cardSurface,
            UiControlTheme.LabelStyle titleLabelStyle, UiControlTheme.LabelStyle bodyLabelStyle,
            UiControlTheme.LabelStyle emphasisLabelStyle, UiControlTheme.LabelStyle secondaryLabelStyle,
            UiControlTheme.ButtonStyle buttonStyle, UiControlTheme.ToggleSwitchStyle toggleSwitchStyle,
            UiControlTheme.SegmentedSelectorStyle segmentedSelectorStyle, UiControlTheme.TextInputStyle textInputStyle,
            UiControlTheme.ScrollbarStyle scrollbarStyle, UiControlTheme.InventorySlotGridStyle inventorySlotGridStyle,
            int documentGap, int cardPadding, int cardGap, int sectionGap,
            int flowColumnGap, int flowRowGap, int toolbarGap, int formRowGap, int formLabelWidth) {
        this.shellSurface = Objects.requireNonNull(shellSurface, "shellSurface");
        this.cardSurface = Objects.requireNonNull(cardSurface, "cardSurface");
        this.titleLabelStyle = Objects.requireNonNull(titleLabelStyle, "titleLabelStyle");
        this.bodyLabelStyle = Objects.requireNonNull(bodyLabelStyle, "bodyLabelStyle");
        this.emphasisLabelStyle = Objects.requireNonNull(emphasisLabelStyle, "emphasisLabelStyle");
        this.secondaryLabelStyle = Objects.requireNonNull(secondaryLabelStyle, "secondaryLabelStyle");
        this.buttonStyle = Objects.requireNonNull(buttonStyle, "buttonStyle");
        this.toggleSwitchStyle = Objects.requireNonNull(toggleSwitchStyle, "toggleSwitchStyle");
        this.segmentedSelectorStyle = Objects.requireNonNull(segmentedSelectorStyle, "segmentedSelectorStyle");
        this.textInputStyle = Objects.requireNonNull(textInputStyle, "textInputStyle");
        this.scrollbarStyle = Objects.requireNonNull(scrollbarStyle, "scrollbarStyle");
        this.inventorySlotGridStyle = Objects.requireNonNull(inventorySlotGridStyle, "inventorySlotGridStyle");
        this.documentGap = Math.max(0, documentGap);
        this.cardPadding = Math.max(0, cardPadding);
        this.cardGap = Math.max(0, cardGap);
        this.sectionGap = Math.max(0, sectionGap);
        this.flowColumnGap = Math.max(0, flowColumnGap);
        this.flowRowGap = Math.max(0, flowRowGap);
        this.toolbarGap = Math.max(0, toolbarGap);
        this.formRowGap = Math.max(0, formRowGap);
        this.formLabelWidth = Math.max(0, formLabelWidth);
    }

    private static UiDocumentTheme createDefaultTheme() {
        return new UiDocumentTheme(
                new UiSurfaceStyle(0xD0151C25, 0xFF86A8F0),
                new UiSurfaceStyle(0xAA111721, 0xFF6E8FCB),
                new UiControlTheme.LabelStyle(0xFFFFFFFF, false),
                new UiControlTheme.LabelStyle(0xFFD7E3FF, false),
                new UiControlTheme.LabelStyle(0xFFF6D78E, false),
                new UiControlTheme.LabelStyle(0xFFB5D0FF, false),
                UiControlTheme.defaultButtonStyle(),
                UiControlTheme.defaultToggleSwitchStyle(),
                UiControlTheme.defaultSegmentedSelectorStyle(),
                UiControlTheme.defaultTextInputStyle(),
                UiControlTheme.defaultScrollbarStyle(),
                UiControlTheme.defaultInventorySlotGridStyle(),
                16,
                20,
                12,
                12,
                16,
                20,
                12,
                16,
                156
        );
    }

    /**
     * 获取默认文档主题。
     *
     * @return 默认文档主题
     */
    public static UiDocumentTheme defaultTheme() {
        return DEFAULT_THEME;
    }

    /**
     * 获取文档壳表面样式。
     *
     * @return 文档壳表面样式
     */
    public UiSurfaceStyle getShellSurface() {
        return shellSurface;
    }

    /**
     * 获取卡片表面样式。
     *
     * @return 卡片表面样式
     */
    public UiSurfaceStyle getCardSurface() {
        return cardSurface;
    }

    /**
     * 获取页面标题文字样式。
     *
     * @return 页面标题文字样式
     */
    public UiControlTheme.LabelStyle getTitleLabelStyle() {
        return titleLabelStyle;
    }

    /**
     * 获取正文文字样式。
     *
     * @return 正文文字样式
     */
    public UiControlTheme.LabelStyle getBodyLabelStyle() {
        return bodyLabelStyle;
    }

    /**
     * 获取强调文字样式。
     *
     * @return 强调文字样式
     */
    public UiControlTheme.LabelStyle getEmphasisLabelStyle() {
        return emphasisLabelStyle;
    }

    /**
     * 获取次级说明文字样式。
     *
     * @return 次级说明文字样式
     */
    public UiControlTheme.LabelStyle getSecondaryLabelStyle() {
        return secondaryLabelStyle;
    }

    /**
     * 获取按钮样式。
     *
     * @return 按钮样式
     */
    public UiControlTheme.ButtonStyle getButtonStyle() {
        return buttonStyle;
    }

    /**
     * 获取开关样式。
     *
     * @return 开关样式
     */
    public UiControlTheme.ToggleSwitchStyle getToggleSwitchStyle() {
        return toggleSwitchStyle;
    }

    /**
     * 获取分段选择器样式。
     *
     * @return 分段选择器样式
     */
    public UiControlTheme.SegmentedSelectorStyle getSegmentedSelectorStyle() {
        return segmentedSelectorStyle;
    }

    /**
     * 获取文本输入框样式。
     *
     * @return 文本输入框样式
     */
    public UiControlTheme.TextInputStyle getTextInputStyle() {
        return textInputStyle;
    }

    /**
     * 获取滚动条样式。
     *
     * @return 滚动条样式
     */
    public UiControlTheme.ScrollbarStyle getScrollbarStyle() {
        return scrollbarStyle;
    }

    /**
     * 获取背包格子网格样式。
     *
     * @return 背包格子网格样式
     */
    public UiControlTheme.InventorySlotGridStyle getInventorySlotGridStyle() {
        return inventorySlotGridStyle;
    }

    /**
     * 获取文档主轴间距。
     *
     * @return 文档主轴间距
     */
    public int getDocumentGap() {
        return documentGap;
    }

    /**
     * 获取卡片内边距。
     *
     * @return 卡片内边距
     */
    public int getCardPadding() {
        return cardPadding;
    }

    /**
     * 获取卡片内部间距。
     *
     * @return 卡片内部间距
     */
    public int getCardGap() {
        return cardGap;
    }

    /**
     * 获取段落内部间距。
     *
     * @return 段落内部间距
     */
    public int getSectionGap() {
        return sectionGap;
    }

    /**
     * 获取响应式卡片流列间距。
     *
     * @return 列间距
     */
    public int getFlowColumnGap() {
        return flowColumnGap;
    }

    /**
     * 获取响应式卡片流行间距。
     *
     * @return 行间距
     */
    public int getFlowRowGap() {
        return flowRowGap;
    }

    /**
     * 获取工具栏间距。
     *
     * @return 工具栏间距
     */
    public int getToolbarGap() {
        return toolbarGap;
    }

    /**
     * 获取表单行间距。
     *
     * @return 表单行间距
     */
    public int getFormRowGap() {
        return formRowGap;
    }

    /**
     * 获取表单标签参考宽度。
     *
     * @return 标签宽度
     */
    public int getFormLabelWidth() {
        return formLabelWidth;
    }
}
