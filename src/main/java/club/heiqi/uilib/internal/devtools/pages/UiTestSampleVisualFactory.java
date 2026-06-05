package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.cascade.UiStyleSheet;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiBoxSizing;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPointerEvents;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.props.UiVisibility;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.values.UiTransform;

/**
 * `/qzuilib test` 单张样例的真实视觉舞台工厂。
 */
final class UiTestSampleVisualFactory {

    private boolean styleSheetAttached;

    /**
     * 追加指定样例的视觉舞台。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 样例规格
     */
    void appendCaseDemo(UiDocument document, ElementNode parent, UiTestCaseSpec testCase) {
        ensureStyleSheet(document);
        ElementNode stage = createStage(document, "视觉样例：" + testCase.getVisualSample());
        String id = testCase.getId();
        if ("VIS-CSS-001".equals(id)) {
            appendCssSpecificityDemo(document, stage);
        } else if ("VIS-CSS-002".equals(id)) {
            appendCssBoxSizingDemo(document, stage);
        } else if ("VIS-CSS-003".equals(id)) {
            appendCssVisibilityDemo(document, stage);
        } else if ("VIS-LAYOUT-001".equals(id)) {
            appendLayoutBlockFlowDemo(document, stage);
        } else if ("VIS-LAYOUT-002".equals(id)) {
            appendLayoutFlexDemo(document, stage);
        } else if ("VIS-LAYOUT-003".equals(id)) {
            appendLayoutTableDemo(document, stage);
        } else if ("VIS-PAINT-001".equals(id)) {
            appendPaintStackingDemo(document, stage);
        } else if ("VIS-PAINT-002".equals(id)) {
            appendPaintClipDemo(document, stage);
        } else if ("VIS-PAINT-003".equals(id)) {
            appendPaintTransformDemo(document, stage);
        } else {
            appendMutedText(document, stage, "该样例暂无视觉舞台。");
        }
        parent.append(stage);
    }

    /**
     * 挂载样例专用样式表。
     *
     * @param document 文档实例
     */
    private void ensureStyleSheet(UiDocument document) {
        if (styleSheetAttached) {
            return;
        }
        document.addStyleSheet(UiStyleSheet.create()
                .addRule("sample", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF334155)
                        .setTextColor(0xFFEAF1FF)
                        .setBorderColor(0xFF64748B))
                .addRule(".specificity-class", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF2563EB)
                        .setBorderColor(0xFF93C5FD))
                .addRule("#specificity-id", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF059669)
                        .setBorderColor(0xFF6EE7B7)));
        styleSheetAttached = true;
    }

    /**
     * 追加 CSS specificity 色块演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendCssSpecificityDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createDemoRow(document);
        row.append(createSpecificitySample(document, "sample 标签", null, null));
        row.append(createSpecificitySample(document, ".specificity-class", "specificity-class", null));
        row.append(createSpecificitySample(document, "#specificity-id", "specificity-class", "specificity-id"));
        stage.append(row);
        appendMutedText(document, stage, "选择器命中：sample < .specificity-class < #specificity-id");
    }

    /**
     * 创建 specificity 样例色块。
     *
     * @param document 文档实例
     * @param label 色块文本
     * @param className class 名
     * @param id id 值
     * @return specificity 样例色块
     */
    private ElementNode createSpecificitySample(UiDocument document, String label, String className, String id) {
        ElementNode sample = document.element("sample");
        sample.style()
                .setPadding(UiStyleLength.px(8))
                .setWidth(UiStyleLength.px(138))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8))
                .setFontWeight(UiFontWeight.BOLD);
        if (className != null) {
            sample.setClassName(className);
        }
        if (id != null) {
            sample.setId(id);
        }
        sample.appendText(label);
        return sample;
    }

    /**
     * 追加 box-sizing 盒模型演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendCssBoxSizingDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createDemoRow(document);
        row.append(createBoxSizingPanel(document, "content-box", UiBoxSizing.CONTENT_BOX, 0xFFDC2626));
        row.append(createBoxSizingPanel(document, "border-box", UiBoxSizing.BORDER_BOX, 0xFF059669));
        stage.append(row);
        appendMutedText(document, stage, "同为 width=118px / padding=12px / border=4px，border-box 不外扩。");
    }

    /**
     * 创建 box-sizing 对比面板。
     *
     * @param document 文档实例
     * @param label 面板文本
     * @param boxSizing box-sizing 值
     * @param color 背景色
     * @return box-sizing 面板
     */
    private ElementNode createBoxSizingPanel(UiDocument document, String label, UiBoxSizing boxSizing, int color) {
        ElementNode panel = createDemoPanel(document, label, color);
        panel.style()
                .setWidth(UiStyleLength.px(118))
                .setPadding(UiStyleLength.px(12))
                .setBorderWidth(UiStyleLength.px(4))
                .setBorderColor(0xFFFFFFFF)
                .setBoxSizing(boxSizing);
        return panel;
    }

    /**
     * 追加 visibility 与 pointer-events 状态演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendCssVisibilityDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createDemoRow(document);
        row.append(createDemoPanel(document, "visible", 0xFF059669));
        ElementNode hidden = createDemoPanel(document, "hidden 占位", 0xFF7C2D12);
        hidden.style().setVisibility(UiVisibility.HIDDEN);
        row.append(hidden);
        ElementNode pointerNone = createDemoPanel(document, "pointer-events:none", 0xFF475569);
        pointerNone.style().setPointerEvents(UiPointerEvents.NONE);
        row.append(pointerNone);
        stage.append(row);
        appendMutedText(document, stage, "hidden 节点保留 DOM 文本，pointer-events:none 节点显示但不应命中。");
    }

    /**
     * 追加 block flow 与 margin collapse 标尺演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendLayoutBlockFlowDemo(UiDocument document, ElementNode stage) {
        ElementNode stack = document.div();
        stack.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setWidth(UiStyleLength.px(260))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF0F172A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF475569)
                .setBorderRadius(UiStyleLength.px(8));
        ElementNode first = createDemoPanel(document, "Block A margin-bottom=18", 0xFF2563EB);
        first.style().setMarginBottom(UiStyleLength.px(18));
        stack.append(first);
        stack.append(createRuler(document, "collapse 标尺：相邻 margin 以最大值呈现"));
        ElementNode second = createDemoPanel(document, "Block B margin-top=24", 0xFF7C3AED);
        second.style().setMarginTop(UiStyleLength.px(24));
        stack.append(second);
        stage.append(stack);
    }

    /**
     * 追加 flex min-content 收缩轨道演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendLayoutFlexDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createDemoRow(document);
        row.style()
                .setWidth(UiStyleLength.px(330))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF0F172A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF475569);
        ElementNode minContent = createDemoPanel(document, "min-content item keeps long label", 0xFF2563EB);
        minContent.style().setFlexShrink(1.0F).setWhiteSpace(UiWhiteSpace.NOWRAP);
        ElementNode shrinkable = createDemoPanel(document, "min-width:0 item", 0xFF059669);
        shrinkable.style()
                .setFlexShrink(1.0F)
                .setMinWidth(UiStyleLength.px(0))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setWhiteSpace(UiWhiteSpace.NOWRAP);
        row.append(minContent).append(shrinkable);
        stage.append(row);
        appendMutedText(document, stage, "左侧保留 min-content，右侧显式 min-width:0 可压缩。");
    }

    /**
     * 追加 table auto 列宽演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendLayoutTableDemo(UiDocument document, ElementNode stage) {
        ElementNode table = document.div();
        table.style()
                .setDisplay(UiDisplay.TABLE)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF64748B);
        table.append(createTableRow(document, "Key", "auto column chooses wider content"));
        table.append(createTableRow(document, "A", "short"));
        table.append(createTableRow(document, "Long label", "content driven width"));
        stage.append(table);
        appendMutedText(document, stage, "长内容列应获得更宽空间，行列边框保持对齐。");
    }

    /**
     * 创建表格演示行。
     *
     * @param document 文档实例
     * @param first 第一列文本
     * @param second 第二列文本
     * @return 表格行元素
     */
    private ElementNode createTableRow(UiDocument document, String first, String second) {
        ElementNode row = document.div();
        row.style().setDisplay(UiDisplay.TABLE_ROW);
        row.append(createTableCell(document, first, 0xFF1E293B));
        row.append(createTableCell(document, second, 0xFF0F172A));
        return row;
    }

    /**
     * 创建表格演示单元格。
     *
     * @param document 文档实例
     * @param text 单元格文本
     * @param color 背景色
     * @return 表格单元格元素
     */
    private ElementNode createTableCell(UiDocument document, String text, int color) {
        ElementNode cell = document.div();
        cell.style()
                .setDisplay(UiDisplay.TABLE_CELL)
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(color)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF475569)
                .setTextColor(0xFFEAF1FF);
        cell.appendText(text);
        return cell;
    }

    /**
     * 追加 stacking 与 opacity 重叠演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendPaintStackingDemo(UiDocument document, ElementNode stage) {
        ElementNode canvas = createPaintCanvas(document);
        canvas.append(createPaintLayer(document, "red z=1", 18, 20, 0xFFDC2626, 1, 1.0F));
        canvas.append(createPaintLayer(document, "blue opacity=.72 z=2", 58, 36, 0xFF2563EB, 2, 0.72F));
        canvas.append(createPaintLayer(document, "green z=3", 98, 52, 0xFF059669, 3, 1.0F));
        stage.append(canvas);
    }

    /**
     * 追加 overflow clip 裁剪演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendPaintClipDemo(UiDocument document, ElementNode stage) {
        ElementNode clip = document.div();
        clip.style()
                .setPosition(UiPosition.RELATIVE)
                .setWidth(UiStyleLength.px(220))
                .setHeight(UiStyleLength.px(82))
                .setPadding(UiStyleLength.px(8))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setBackgroundColor(0xFF0F172A)
                .setBorderWidth(UiStyleLength.px(2))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFFF59E0B)
                .setBorderRadius(UiStyleLength.px(10));
        ElementNode bar = createDemoPanel(document, "wide child clipped by parent", 0xFF22C55E);
        bar.style().setWidth(UiStyleLength.px(320)).setHeight(UiStyleLength.px(34));
        clip.append(bar);
        appendMutedText(document, clip, "clip boundary");
        stage.append(clip);
    }

    /**
     * 追加 transform 视觉命中舞台演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendPaintTransformDemo(UiDocument document, ElementNode stage) {
        ElementNode canvas = createPaintCanvas(document);
        ElementNode placeholder = createPaintLayer(document, "layout box", 34, 28, 0x5538BDF8, 1, 1.0F);
        placeholder.style().setBorderColor(0xFF38BDF8);
        ElementNode transformed = createPaintLayer(document, "rotate(12deg) translate(28,8)", 34, 28, 0xFF8B5CF6, 2, 1.0F);
        transformed.style().setTransform(UiTransform.of(28.0F, 8.0F, 1.0F, 1.0F, 12.0F));
        canvas.append(placeholder).append(transformed);
        stage.append(canvas);
        appendMutedText(document, stage, "半透明占位代表原布局盒，亮色卡片只在绘制/命中阶段变换。");
    }

    /**
     * 创建绘制样例画布。
     *
     * @param document 文档实例
     * @return 绘制样例画布
     */
    private ElementNode createPaintCanvas(UiDocument document) {
        ElementNode canvas = document.div();
        canvas.style()
                .setPosition(UiPosition.RELATIVE)
                .setWidth(UiStyleLength.px(260))
                .setHeight(UiStyleLength.px(128))
                .setBackgroundColor(0xFF020617)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF475569)
                .setBorderRadius(UiStyleLength.px(10));
        return canvas;
    }

    /**
     * 创建绝对定位绘制层。
     *
     * @param document 文档实例
     * @param label 层文本
     * @param left left 偏移
     * @param top top 偏移
     * @param color 背景色
     * @param zIndex z-index 值
     * @param opacity opacity 值
     * @return 绘制层元素
     */
    private ElementNode createPaintLayer(UiDocument document, String label, int left, int top, int color, int zIndex,
            float opacity) {
        ElementNode layer = createDemoPanel(document, label, color);
        layer.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(left))
                .setTop(UiStyleLength.px(top))
                .setWidth(UiStyleLength.px(122))
                .setHeight(UiStyleLength.px(44))
                .setZIndex(zIndex)
                .setOpacity(opacity);
        return layer;
    }

    /**
     * 创建通用视觉样例舞台。
     *
     * @param document 文档实例
     * @param title 舞台标题
     * @return 通用视觉样例舞台
     */
    private ElementNode createStage(UiDocument document, String title) {
        ElementNode stage = document.div();
        stage.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF0D1728)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF2F4D87)
                .setBorderRadius(UiStyleLength.px(10));
        appendMutedText(document, stage, title);
        return stage;
    }

    /**
     * 创建横向演示行。
     *
     * @param document 文档实例
     * @return 横向演示行
     */
    private ElementNode createDemoRow(UiDocument document) {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8))
                .setRowGap(UiStyleLength.px(8));
        return row;
    }

    /**
     * 创建通用演示面板。
     *
     * @param document 文档实例
     * @param label 面板文本
     * @param color 背景色
     * @return 通用演示面板
     */
    private ElementNode createDemoPanel(UiDocument document, String label, int color) {
        ElementNode panel = document.div();
        panel.style()
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(color)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF64748B)
                .setBorderRadius(UiStyleLength.px(8))
                .setTextColor(0xFFFFFFFF)
                .setFontWeight(UiFontWeight.BOLD);
        panel.appendText(label);
        return panel;
    }

    /**
     * 创建布局观察标尺。
     *
     * @param document 文档实例
     * @param label 标尺文本
     * @return 布局观察标尺
     */
    private ElementNode createRuler(UiDocument document, String label) {
        ElementNode ruler = document.div();
        ruler.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF94A3B8)
                .setTextColor(0xFFEAF1FF);
        ruler.appendText(label);
        return ruler;
    }

    /**
     * 追加弱化说明文本。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param text 文本
     */
    private void appendMutedText(UiDocument document, ElementNode parent, String text) {
        ElementNode line = document.div();
        line.style().setTextColor(0xFFC9D8F8);
        line.appendText(text);
        parent.append(line);
    }
}
