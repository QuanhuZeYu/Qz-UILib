package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.control.ButtonWidget;
import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.control.InventorySlotGridItemRenderer;
import club.heiqi.uilib.ui.control.InventorySlotGridWidget;
import club.heiqi.uilib.ui.control.SegmentedSelectorWidget;
import club.heiqi.uilib.ui.control.TextInputWidget;
import club.heiqi.uilib.ui.control.ToggleSwitchWidget;
import club.heiqi.uilib.ui.control.UiControlRuntimeAdapters;
import club.heiqi.uilib.ui.document.DocumentCardWidget;
import club.heiqi.uilib.ui.document.DocumentFlowRowWidget;
import club.heiqi.uilib.ui.document.DocumentFormRowWidget;
import club.heiqi.uilib.ui.document.DocumentSectionWidget;
import club.heiqi.uilib.ui.document.DocumentTextWidget;
import club.heiqi.uilib.ui.document.DocumentToolbarWidget;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 文档屏幕作者侧的高频组件工厂。
 *
 * <p>该作用域只负责把 `UiDocumentTheme` 投影成常用组件，不承载页面布局策略。</p>
 */
public final class DocumentUiScope {

    private final UiDocumentTheme documentTheme;
    private final TextMeasureService textMeasureService;
    private final UiControlRuntimeAdapters runtimeAdapters;

    public DocumentUiScope(UiDocumentTheme documentTheme) {
        this(documentTheme, DefaultTextMeasureService.getInstance(), UiControlRuntimeAdapters.minecraftDefaults());
    }

    public DocumentUiScope(UiDocumentTheme documentTheme, TextMeasureService textMeasureService) {
        this(documentTheme, textMeasureService, UiControlRuntimeAdapters.minecraftDefaults());
    }

    public DocumentUiScope(UiDocumentTheme documentTheme, TextMeasureService textMeasureService,
            UiControlRuntimeAdapters runtimeAdapters) {
        this.documentTheme = Objects.requireNonNull(documentTheme, "documentTheme");
        this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");
        this.runtimeAdapters = Objects.requireNonNull(runtimeAdapters, "runtimeAdapters");
    }

    /**
     * 获取当前作用域主题。
     *
     * @return 主题
     */
    public UiDocumentTheme theme() {
        return documentTheme;
    }

    /**
     * 创建文档文本。
     *
     * @param role 文本角色
     * @param text 文本内容
     * @param maxLines 最大行数
     * @return 文本控件
     */
    public DocumentTextWidget text(DocumentTextWidget.Role role, String text, int maxLines) {
        return new DocumentTextWidget(documentTheme, role, text, maxLines, textMeasureService);
    }

    /**
     * 创建文档卡片。
     *
     * @return 卡片控件
     */
    public DocumentCardWidget card() {
        return new DocumentCardWidget(documentTheme, textMeasureService);
    }

    /**
     * 创建文档段落容器。
     *
     * @return 段落控件
     */
    public DocumentSectionWidget section() {
        return new DocumentSectionWidget(documentTheme, textMeasureService);
    }

    /**
     * 创建文档工具栏。
     *
     * @return 工具栏控件
     */
    public DocumentToolbarWidget toolbar() {
        return new DocumentToolbarWidget(documentTheme, textMeasureService);
    }

    /**
     * 创建响应式流式行容器。
     *
     * @return 流式容器
     */
    public DocumentFlowRowWidget flowRow() {
        return new DocumentFlowRowWidget(documentTheme, textMeasureService);
    }

    /**
     * 创建文档表单行。
     *
     * @param labelText 标签文本
     * @param field 字段控件
     * @return 表单行控件
     */
    public DocumentFormRowWidget formRow(String labelText, Widget field) {
        return new DocumentFormRowWidget(documentTheme, labelText, field, textMeasureService);
    }

    /**
     * 创建按钮。
     *
     * @param text 按钮文本
     * @return 按钮控件
     */
    public ButtonWidget button(String text) {
        return new ButtonWidget(text, documentTheme.getButtonStyle(), textMeasureService);
    }

    /**
     * 创建开关。
     *
     * @param text 开关文本
     * @return 开关控件
     */
    public ToggleSwitchWidget toggle(String text) {
        return new ToggleSwitchWidget(text, documentTheme.getToggleSwitchStyle(), textMeasureService);
    }

    /**
     * 创建分段选择器。
     *
     * @param options 选项列表
     * @return 分段选择器
     */
    public SegmentedSelectorWidget segmented(String... options) {
        return new SegmentedSelectorWidget(textMeasureService, documentTheme.getSegmentedSelectorStyle(), options);
    }

    /**
     * 创建文本输入框。
     *
     * @return 文本输入框
     */
    public TextInputWidget textInput() {
        return new TextInputWidget(documentTheme.getTextInputStyle(), textMeasureService);
    }

    /**
     * 创建背包网格。
     *
     * @param slotCount 槽位数量
     * @param preferredColumns 期望列数
     * @param slotContentProvider 内容提供器
     * @return 背包网格控件
     */
    public InventorySlotGridWidget inventoryGrid(int slotCount, int preferredColumns,
            InventorySlotGridWidget.SlotContentProvider slotContentProvider) {
        InventorySlotGridItemRenderer itemRenderer = runtimeAdapters.getInventorySlotGridItemRenderer();
        return new InventorySlotGridWidget(slotCount, preferredColumns, documentTheme.getInventorySlotGridStyle(),
                slotContentProvider, itemRenderer);
    }

    /**
     * 创建只注入滚动条样式的滚动 Div。
     *
     * @return 带滚动条样式的 Div
     */
    public DivWidget scrollDiv() {
        return new ScrollStyledDivWidget(documentTheme, textMeasureService);
    }

    /**
     * 只负责注入滚动条样式的轻量 Div。
     */
    private static final class ScrollStyledDivWidget extends DivWidget {

        private ScrollStyledDivWidget(UiDocumentTheme documentTheme, TextMeasureService textMeasureService) {
            super(textMeasureService);
            applyScrollbarStyle(Objects.requireNonNull(documentTheme, "documentTheme").getScrollbarStyle());
        }
    }
}
