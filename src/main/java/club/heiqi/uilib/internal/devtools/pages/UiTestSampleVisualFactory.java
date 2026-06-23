package club.heiqi.uilib.internal.devtools.pages;

import java.util.List;

import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.DocumentNode;
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
import club.heiqi.uilib.ui.style.values.UiBackgroundImage;

/**
 * `/qzuilib test` 单张样例的真实视觉舞台工厂。
 */
final class UiTestSampleVisualFactory {

    private static final String DEFERRED_TOP_LAYER_ATTRIBUTE = "data-ui-test-deferred-top-layer";

    private boolean styleSheetAttached;
    private final UiTestDomVisualFactory domVisualFactory = new UiTestDomVisualFactory();
    private final UiTestInputVisualFactory inputVisualFactory = new UiTestInputVisualFactory();
    private final UiTestControlsVisualFactory controlsVisualFactory = new UiTestControlsVisualFactory();
    private final UiTestTextVisualFactory textVisualFactory = new UiTestTextVisualFactory();
    private final UiTestAnimationVisualFactory animationVisualFactory = new UiTestAnimationVisualFactory();
    private final UiTestRuntimeHostVisualFactory runtimeHostVisualFactory = new UiTestRuntimeHostVisualFactory();

    /**
     * 追加指定样例的视觉舞台。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 样例规格
     */
    void appendCaseDemo(UiDocument document, ElementNode parent, UiTestCaseSpec testCase) {
        ensureStyleSheet(document);
        ElementNode stage = createStage(document, "样例");
        stage.setAttribute("data-ui-test-case", testCase.getId());
        String id = testCase.getId();
        if (domVisualFactory.supports(id)) {
            domVisualFactory.appendCaseDemo(document, stage, testCase);
        } else if ("VIS-CSS-001".equals(id)) {
            appendCssSpecificityDemo(document, stage);
        } else if ("VIS-CSS-002".equals(id)) {
            appendCssBoxSizingDemo(document, stage);
        } else if ("VIS-CSS-003".equals(id)) {
            appendCssVisibilityDemo(document, stage);
        } else if ("VIS-CSS-004".equals(id)) {
            appendCssInheritanceDemo(document, stage);
        } else if ("VIS-CSS-005".equals(id)) {
            appendCssBackgroundDemo(document, stage);
        } else if ("VIS-CSS-006".equals(id)) {
            appendCssOverflowDemo(document, stage);
        } else if ("VIS-LAYOUT-001".equals(id)) {
            appendLayoutBlockFlowDemo(document, stage);
        } else if ("VIS-LAYOUT-002".equals(id)) {
            appendLayoutFlexDemo(document, stage);
        } else if ("VIS-LAYOUT-003".equals(id)) {
            appendLayoutTableDemo(document, stage);
        } else if ("VIS-LAYOUT-004".equals(id)) {
            appendLayoutInlineDemo(document, stage);
        } else if ("VIS-LAYOUT-005".equals(id)) {
            appendLayoutInlineBlockBaselineDemo(document, stage);
        } else if ("VIS-LAYOUT-006".equals(id)) {
            appendLayoutFixedStickyDemo(document, stage);
        } else if ("VIS-PAINT-001".equals(id)) {
            appendPaintStackingDemo(document, stage);
        } else if ("VIS-PAINT-002".equals(id)) {
            appendPaintClipDemo(document, stage);
        } else if ("VIS-PAINT-003".equals(id)) {
            appendPaintTransformDemo(document, stage);
        } else if ("VIS-PAINT-004".equals(id)) {
            appendPaintTransformHitDemo(document, stage);
        } else if ("VIS-PAINT-005".equals(id)) {
            appendPaintTopLayerDemo(document, stage);
        } else if ("VIS-PAINT-006".equals(id)) {
            appendPaintScrollbarDemo(document, stage);
        } else if ("VIS-PAINT-007".equals(id)) {
            appendPaintHostImageDemo(document, stage);
        } else if (inputVisualFactory.supports(id)) {
            inputVisualFactory.appendCaseDemo(document, stage, testCase);
        } else if (controlsVisualFactory.supports(id)) {
            controlsVisualFactory.appendCaseDemo(document, stage, testCase);
        } else if (textVisualFactory.supports(id)) {
            textVisualFactory.appendCaseDemo(document, stage, testCase);
        } else if (animationVisualFactory.supports(id)) {
            animationVisualFactory.appendCaseDemo(document, stage, testCase);
        } else if (runtimeHostVisualFactory.supports(id)) {
            runtimeHostVisualFactory.appendCaseDemo(document, stage, testCase);
        } else if ("VIS-MODCFG-001".equals(id)) {
            appendModernConfigDemoStage(document, stage);
        } else if ("VIS-REACTIVE-001".equals(id)) {
            appendReactiveTriadDemoStage(document, stage);
        } else if ("VIS-SCENE-001".equals(id)) {
            appendSceneDemoStage(document, stage);
        } else if ("VIS-SCENE-002".equals(id)) {
            appendSceneControlsDemoStage(document, stage);
        } else if ("VIS-SCENE-003".equals(id)) {
            appendSceneScrollDemoStage(document, stage);
        } else if ("VIS-SCENE-004".equals(id)) {
            appendSceneTableDemoStage(document, stage);
        } else if ("VIS-SCENE-005".equals(id)) {
            appendSceneLayoutDemoStage(document, stage);
        } else if ("VIS-SCENE-006".equals(id)) {
            appendSceneFormDemoStage(document, stage);
        } else if ("VIS-SCENE-007".equals(id)) {
            appendSceneSelectDemoStage(document, stage);
        } else {
            appendMutedText(document, stage, "该样例暂无视觉舞台。");
        }
        parent.append(stage);
    }

    /**
     * 激活已挂载样例中的延迟 top-layer 元素。
     *
     * @param document 文档实例
     * @param root 查找根节点
     */
    void activateDeferredTopLayerDemos(UiDocument document, ElementNode root) {
        List<ElementNode> elements = new java.util.ArrayList<ElementNode>();
        collectDeferredTopLayerElements(root, elements);
        for (ElementNode element : elements) {
            if (document.__isTopLayerElement(element)) {
                continue;
            }
            DocumentElementBounds bounds = element.getDocumentBounds();
            if (!bounds.isAvailable()) {
                continue;
            }
            element.style()
                    .setPosition(UiPosition.FIXED)
                    .setLeft(UiStyleLength.px(bounds.getLeft()))
                    .setTop(UiStyleLength.px(bounds.getTop()))
                    .setWidth(UiStyleLength.px(bounds.getWidth()))
                    .setHeight(UiStyleLength.px(bounds.getHeight()))
                    .clearZIndex();
            document.__showTopLayerElement(element);
        }
    }

    private void collectDeferredTopLayerElements(ElementNode current, List<ElementNode> elements) {
        if (current == null) {
            return;
        }
        if ("true".equals(current.getAttribute(DEFERRED_TOP_LAYER_ATTRIBUTE))) {
            elements.add(current);
        }
        for (DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                collectDeferredTopLayerElements((ElementNode) child, elements);
            }
        }
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
        ElementNode second = createDemoPanel(document, "Block B margin-top=24", 0xFF7C3AED);
        second.style().setMarginTop(UiStyleLength.px(24));
        stack.append(second);
        stack.append(createRuler(document, "collapse 标尺：A/B 相邻 gap 应接近 24px，而不是 42px"));
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
     * 追加 CSS 继承演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendCssInheritanceDemo(UiDocument document, ElementNode stage) {
        ElementNode parent = document.div();
        parent.style()
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF1E293B)
                .setTextColor(0xFF38BDF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF475569);
        ElementNode inherited = createDemoPanel(document, "继承 color (应为蓝色)", 0xFF334155);
        ElementNode sized = createDemoPanel(document, "width=80px (非继承)", 0xFF059669);
        inherited.style().clearTextColor();
        sized.style().setWidth(UiStyleLength.px(80)).clearTextColor();
        parent.append(inherited).append(sized);
        stage.append(parent);
        appendMutedText(document, stage, "子元素颜色继承父，宽度不继承。");
    }

    /**
     * 追加 CSS background/url/none 演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendCssBackgroundDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createDemoRow(document);
        ElementNode colorOnly = createDemoPanel(document, "纯 background-color", 0xFFDC2626);
        ElementNode withUrl = createDemoPanel(document, "background + url", 0xFF1E40AF);
        withUrl.style().setBackgroundImage(UiBackgroundImage.texture(
                "minecraft:textures/gui/options_background.png", 16, 16));
        ElementNode noneLike = createDemoPanel(document, "background:none 效果", 0xFF059669);
        row.append(colorOnly).append(withUrl).append(noneLike);
        stage.append(row);
        appendMutedText(document, stage, "url 面板尝试加载贴图，none 仅留底色。");
    }

    /**
     * 追加 CSS overflow 行为演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendCssOverflowDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createDemoRow(document);
        ElementNode hidden = document.div();
        hidden.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setBackgroundColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFFF59E0B);
        ElementNode hiddenWide = createDemoPanel(document, "hidden 裁剪", 0xFFDC2626);
        hiddenWide.style().setWidth(UiStyleLength.px(140)).setHeight(UiStyleLength.px(20));
        hidden.append(hiddenWide);
        ElementNode autoBox = document.div();
        autoBox.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setOverflowX(UiOverflow.AUTO)
                .setOverflowY(UiOverflow.AUTO)
                .setBackgroundColor(0xFF1E293B)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF38BDF8);
        ElementNode wide = createDemoPanel(document, "宽内容触发滚动", 0xFF22C55E);
        wide.style().setWidth(UiStyleLength.px(140)).setHeight(UiStyleLength.px(20));
        autoBox.append(wide);
        ElementNode visible = document.div();
        visible.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE)
                .setBackgroundColor(0xFF0F172A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF64748B);
        ElementNode visibleWide = createDemoPanel(document, "visible 越界可见", 0xFF7C3AED);
        visibleWide.style().setWidth(UiStyleLength.px(140)).setHeight(UiStyleLength.px(20));
        visible.append(visibleWide);
        row.append(hidden).append(autoBox).append(visible);
        stage.append(row);
        appendMutedText(document, stage, "hidden 裁剪；auto 滚动；visible 溢出可见。");
    }

    /**
     * 追加 inline/inline-block 排列演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendLayoutInlineDemo(UiDocument document, ElementNode stage) {
        ElementNode container = document.div();
        container.style()
                .setWidth(UiStyleLength.px(260))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF0F172A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF475569);
        container.appendText("文本前 ");
        ElementNode inline = document.div();
        inline.style()
                .setDisplay(UiDisplay.INLINE)
                .setBackgroundColor(0xFF38BDF8)
                .setPadding(UiStyleLength.px(2));
        inline.appendText("inline");
        container.append(inline);
        container.appendText(" 文本中 ");
        ElementNode ib = createDemoPanel(document, "inline-block", 0xFF059669);
        ib.style()
                .setDisplay(UiDisplay.INLINE_BLOCK)
                .setWidth(UiStyleLength.px(72))
                .setHeight(UiStyleLength.px(28));
        container.append(ib);
        container.appendText(" 文本后");
        stage.append(container);
        appendMutedText(document, stage, "inline 流式，inline-block 独立盒仍同行。");
    }

    /**
     * 追加 inline-block baseline 演示（人工）。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendLayoutInlineBlockBaselineDemo(UiDocument document, ElementNode stage) {
        ElementNode container = document.div();
        container.style()
                .setWidth(UiStyleLength.px(280))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF0F172A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF475569);
        container.appendText("基线对齐前 ");
        ElementNode ib = document.div();
        ib.style()
                .setDisplay(UiDisplay.INLINE_BLOCK)
                .setBackgroundColor(0xFF7C3AED)
                .setPadding(UiStyleLength.px(4))
                .setWidth(UiStyleLength.px(90))
                .setHeight(UiStyleLength.px(36));
        ib.appendText("ib baseline tall");
        container.append(ib);
        container.appendText(" 相邻文本基线");
        stage.append(container);
        appendMutedText(document, stage, "观察 inline-block 底部与文本基线是否对齐（人工确认）。");
    }

    /**
     * 追加 fixed/sticky 参考框与滚动演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendLayoutFixedStickyDemo(UiDocument document, ElementNode stage) {
        ElementNode scroller = document.div();
        scroller.style()
                .setPosition(UiPosition.RELATIVE)
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(90))
                .setOverflowY(UiOverflow.AUTO)
                .setBackgroundColor(0xFF020617)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF475569);
        ElementNode sticky = createDemoPanel(document, "sticky 头", 0xFFF59E0B);
        sticky.style()
                .setPosition(UiPosition.STICKY)
                .setTop(UiStyleLength.px(0))
                .setWidth(UiStyleLength.px(200));
        scroller.append(sticky);
        for (int i = 0; i < 3; i++) {
            ElementNode block = createDemoPanel(document, "内容块 " + i, 0xFF334155);
            block.style().setMarginBottom(UiStyleLength.px(12));
            scroller.append(block);
        }
        ElementNode fixed = createDemoPanel(document, "fixed 按钮", 0xFF22C55E);
        fixed.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(10))
                .setTop(UiStyleLength.px(10))
                .setWidth(UiStyleLength.px(60))
                .setHeight(UiStyleLength.px(20));
        stage.append(scroller).append(fixed);
        appendMutedText(document, stage, "sticky 吸附，fixed 相对视口固定（滚动容器内观察）。");
    }

    /**
     * 追加 transform 命中演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendPaintTransformHitDemo(UiDocument document, ElementNode stage) {
        ElementNode canvas = createPaintCanvas(document);
        ElementNode placeholder = createPaintLayer(document, "layout占位", 30, 30, 0x554F46E5, 1, 1.0F);
        placeholder.style().setBorderColor(0xFF8B5CF6);
        ElementNode hit = createPaintLayer(document, "transform 命中区", 30, 30, 0xFFEC4899, 2, 1.0F);
        hit.style().setTransform(UiTransform.of(20.0F, 12.0F, 1.0F, 1.0F, -15.0F));
        canvas.append(placeholder).append(hit);
        stage.append(canvas);
        appendMutedText(document, stage, "点击变换后视觉位置应命中（人工+诊断）。");
    }

    /**
     * 追加 top-layer 绘制演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendPaintTopLayerDemo(UiDocument document, ElementNode stage) {
        ElementNode canvas = createPaintCanvas(document);
        canvas.append(createPaintLayer(document, "普通层 z=999", 20, 20, 0xFFDC2626, 999, 1.0F));
        ElementNode top = createDemoPanel(document, "top-layer 弹层", 0xFF7C3AED);
        top.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(50))
                .setTop(UiStyleLength.px(35))
                .setWidth(UiStyleLength.px(140))
                .setHeight(UiStyleLength.px(50))
                .setOpacity(0.95F);
        top.setAttribute(DEFERRED_TOP_LAYER_ATTRIBUTE, "true");
        canvas.append(top);
        stage.append(canvas);
        appendMutedText(document, stage, "紫色弹层已注册到文档 top-layer，应覆盖普通高 z-index 层。");
    }

    /**
     * 追加 scrollbar 几何演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendPaintScrollbarDemo(UiDocument document, ElementNode stage) {
        ElementNode scroller = document.div();
        scroller.style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(70))
                .setOverflowY(UiOverflow.AUTO)
                .setBackgroundColor(0xFF0F172A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF64748B);
        for (int i = 0; i < 4; i++) {
            ElementNode item = createDemoPanel(document, "scroll item " + i, 0xFF334155);
            item.style().setMarginBottom(UiStyleLength.px(6));
            scroller.append(item);
        }
        stage.append(scroller);
        appendMutedText(document, stage, "auto 溢出时显示 scrollbar track/thumb。");
    }

    /**
     * 追加 host image fallback 演示。
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendPaintHostImageDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createDemoRow(document);
        ElementNode ok = createDemoPanel(document, "有效 host image", 0xFF1E40AF);
        ok.style().setBackgroundImage(UiBackgroundImage.texture(
                "minecraft:textures/gui/options_background.png", 16, 16));
        ElementNode fb = createDemoPanel(document, "缺失 fallback", 0xFF7F1D1D);
        fb.style().setBackgroundImage(UiBackgroundImage.texture("missing:nonexistent.png", 16, 16));
        row.append(ok).append(fb);
        stage.append(row);
        appendMutedText(document, stage, "有效显示图片，缺失保留底色，不使用 Minecraft 默认紫黑 missing texture。");
    }

    /**
     * 追加现代配置模板完整 demo 舞台。
     *
     * <p>渲染「打开完整 demo 页」按钮、config 模块可用性状态牌与 12 入口预览卡片。
     * 按钮点击经 {@link UiTestModernConfigDemoLauncher} 检测后跳转到
     * {@code ModernConfigTemplateScreen}，模块不可用时按钮禁用并显示降级说明。</p>
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendModernConfigDemoStage(UiDocument document, ElementNode stage) {
        boolean available = UiTestModernConfigDemoLauncher.isModernConfigModuleAvailable();
        DocumentButtonControl button = new DocumentButtonControl(document, "打开完整现代配置模板 demo 页");
        button.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                UiTestModernConfigDemoLauncher.openDemo();
            }
        });
        button.setEnabled(available);
        button.setBackgroundColors(0xFF059669, 0xFF047857, 0xFF334155);
        button.setTextColors(0xFFFFFFFF, 0xFFA0AEC0);
        button.getElement().style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setMinWidth(UiStyleLength.px(220))
                .setPadding(UiStyleLength.px(10));
        stage.append(button.getElement());
        appendMutedText(document, stage, available
                ? "已检测到 club.heiqi.config 模块，点击按钮进入完整 demo 页（ESC 返回）。"
                : "未检测到 club.heiqi.config 模块，按钮已禁用，无法展示现代配置模板 demo。");
        ElementNode grid = createDemoRow(document);
        String[] entries = {
                "STRING", "NUMBER", "BOOLEAN", "CHOICE",
                "LONG_TEXT", "SIMPLE_LIST", "TABLE", "OBJECT",
                "KEY_VALUE_MAP", "PRESET_SELECTOR", "RAW_EDITOR", "ENHANCED_PICKER"
        };
        int[] colors = {
                0xFF1E3A8A, 0xFF1E40AF, 0xFF0E7490, 0xFF155E75,
                0xFF065F46, 0xFF064E3B, 0xFF7C2D12, 0xFF9A3412,
                0xFF581C87, 0xFF6B21A8, 0xFF831843, 0xFF9D174D
        };
        for (int i = 0; i < entries.length; i++) {
            grid.append(createDemoPanel(document, entries[i], colors[i]));
        }
        stage.append(grid);
        appendMutedText(document, stage, "12 个模板入口将在完整 demo 页中以真实控件展示，支持搜索、草稿、保存与恢复。");
    }

    /**
     * 追加声明式三基石 demo 舞台。
     *
     * <p>渲染「打开声明式三基石 demo 页」按钮与三基石说明卡片。按钮点击跳转到
     * {@link ReactiveTriadDemoScreen}（纯 signal 驱动的任务清单，演示 show/forEach/bindText）。
     * 该 demo 不依赖任何可选模块（响应式运行时随框架常驻），故无需可用性检测。</p>
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendReactiveTriadDemoStage(UiDocument document, ElementNode stage) {
        DocumentButtonControl button = new DocumentButtonControl(document, "打开声明式三基石 demo 页");
        button.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                ReactiveTriadDemoScreen.openDemo();
            }
        });
        button.setBackgroundColors(0xFF2563EB, 0xFF1D4ED8, 0xFF334155);
        button.setTextColors(0xFFFFFFFF, 0xFFA0AEC0);
        button.getElement().style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setMinWidth(UiStyleLength.px(220))
                .setPadding(UiStyleLength.px(10));
        stage.append(button.getElement());
        appendMutedText(document, stage,
                "点击按钮进入纯 signal 驱动的任务清单 demo（ESC 返回）：增删/打乱任务、切换完成、显隐说明区块。");
        ElementNode grid = createDemoRow(document);
        String[] pillars = {"show 条件渲染", "forEach keyed 列表", "bindText 文本绑定"};
        int[] colors = {0xFF065F46, 0xFF1E3A8A, 0xFF6B21A8};
        for (int i = 0; i < pillars.length; i++) {
            grid.append(createDemoPanel(document, pillars[i], colors[i]));
        }
        stage.append(grid);
        appendMutedText(document, stage,
                "三基石齐备后，组件层可纯声明式表达含条件 + 列表 + 文本的完整界面（信条一 / I1）。");
    }

    /**
     * 追加新栈 ui.scene demo 舞台。
     *
     * <p>渲染「打开 Scene demo 页」按钮与场景说明卡片。按钮点击跳转到 {@link SceneDemoScreen}
     * （纯 signal 驱动的背景 + 文本 demo，演示端到端 pipeline：signal→SceneNode→layout→paint→PaintPlan→UiRenderContext）。
     * 该 demo 不依赖任何可选模块（响应式运行时随框架常驻），故无需可用性检测。</p>
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendSceneDemoStage(UiDocument document, ElementNode stage) {
        DocumentButtonControl button = new DocumentButtonControl(document, "打开 Scene demo 页");
        button.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                SceneDemoScreen.openDemo();
            }
        });
        button.setBackgroundColors(0xFF059669, 0xFF047857, 0xFF334155);
        button.setTextColors(0xFFFFFFFF, 0xFFA0AEC0);
        button.getElement().style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setMinWidth(UiStyleLength.px(220))
                .setPadding(UiStyleLength.px(10));
        stage.append(button.getElement());
        appendMutedText(document, stage,
                "点击按钮进入纯 signal 驱动的新栈 demo（ESC 返回）：signal → SceneNode → layout → paint → UiRenderContext。");
        ElementNode grid = createDemoRow(document);
        String[] steps = {"signal 驱动", "SceneNode 强类型属性槽", "layout + paint 增量"};
        int[] colors = {0xFF065F46, 0xFF1E3A8A, 0xFF6B21A8};
        for (int i = 0; i < steps.length; i++) {
            grid.append(createDemoPanel(document, steps[i], colors[i]));
        }
        stage.append(grid);
        appendMutedText(document, stage,
                "新栈 pipeline 贯通后，可逐步迁移 Phase 2 forEach/Phase 3 composite 动画/Phase 4 控件层。");
    }

    /**
     * 追加新栈 ui.scene 控件 demo 舞台（Phase 4 批 1：Checkbox + Toggle）。
     *
     * <p>渲染「打开 Scene 控件 demo 页」按钮与受控双向说明卡片。按钮点击跳转到
     * {@link SceneControlsDemoScreen}（首批真实迁移控件 SceneCheckbox + SceneToggle，
     * 演示受控双向闭环：控件零内部状态，当前值由外部 signal 驱动，交互经 onChange 交还期望新值）。
     * 该 demo 不依赖任何可选模块（响应式运行时随框架常驻），故无需可用性检测。</p>
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendSceneControlsDemoStage(UiDocument document, ElementNode stage) {
        DocumentButtonControl button = new DocumentButtonControl(document, "打开 Scene 控件 demo 页");
        button.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                SceneControlsDemoScreen.openDemo();
            }
        });
        button.setBackgroundColors(0xFF059669, 0xFF047857, 0xFF334155);
        button.setTextColors(0xFFFFFFFF, 0xFFA0AEC0);
        button.getElement().style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setMinWidth(UiStyleLength.px(220))
                .setPadding(UiStyleLength.px(10));
        stage.append(button.getElement());
        appendMutedText(document, stage,
                "点击按钮进入新栈控件 demo（ESC 返回）：Checkbox + Toggle 受控双向，控件零内部状态，"
                        + "当前值由外部 signal 驱动，点击经 onChange 交还期望新值后由外部 set 回。");
        ElementNode grid = createDemoRow(document);
        String[] steps = {"受控双向（零内部状态）", "四态背景 bind 派生", "命中穿透到交互根"};
        int[] colors = {0xFF065F46, 0xFF1E3A8A, 0xFF6B21A8};
        for (int i = 0; i < steps.length; i++) {
            grid.append(createDemoPanel(document, steps[i], colors[i]));
        }
        stage.append(grid);
        appendMutedText(document, stage,
                "受控范式只保留外部 signal 唯一状态源，控件退化为「读外部值渲染 + 上抛期望新值」纯函数式视图（契约 R7）。");
    }

    /**
     * 追加新栈 ui.scene 滚动 demo 舞台（Phase 4 批 4 步骤 B：滚动/视口基础设施地基）。
     *
     * <p>渲染「打开 Scene 滚动 demo 页」按钮与滚动地基说明卡片。按钮点击跳转到
     * {@link SceneScrollDemoScreen}（长列表视口形态，演示纵向滚轮滚动 + 视口裁剪：
     * scrollable + preferredHeight 钉死视口高、内容超出被 CLIP 裁剪、滚轮经 signal-first
     * 路径驱动 geometry 级偏移、layout 零重排守 I7）。该 demo 不依赖任何可选模块
     * （响应式运行时随框架常驻），故无需可用性检测。</p>
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendSceneScrollDemoStage(UiDocument document, ElementNode stage) {
        DocumentButtonControl button = new DocumentButtonControl(document, "打开 Scene 滚动 demo 页");
        button.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                SceneScrollDemoScreen.openDemo();
            }
        });
        button.setBackgroundColors(0xFF059669, 0xFF047857, 0xFF334155);
        button.setTextColors(0xFFFFFFFF, 0xFFA0AEC0);
        button.getElement().style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setMinWidth(UiStyleLength.px(220))
                .setPadding(UiStyleLength.px(10));
        stage.append(button.getElement());
        appendMutedText(document, stage,
                "点击按钮进入新栈滚动 demo（ESC 返回）：长列表视口形态，滚轮滚动内容、超出被裁剪、"
                        + "视口窗口固定不动、clamp 到 [0, maxScroll]。");
        ElementNode grid = createDemoRow(document);
        String[] steps = {"scrollable 钉死视口高", "SCROLL signal-first", "geometry 级零重排"};
        int[] colors = {0xFF065F46, 0xFF1E3A8A, 0xFF6B21A8};
        for (int i = 0; i < steps.length; i++) {
            grid.append(createDemoPanel(document, steps[i], colors[i]));
        }
        stage.append(grid);
        appendMutedText(document, stage,
                "滚动地基组装完成后，可逐步叠加滚动条、横向滚动、嵌套滚动等能力（本期 scrollOffsetY + viewport 裁剪为地基）。");
    }

    /**
     * 追加新栈 ui.scene Table demo 舞台。
     *
     * <p>渲染「打开 Scene Table demo 页」按钮与表格验收说明卡片。按钮点击跳转到
     * {@link SceneTableDemoScreen}，独立屏幕直接挂载 {@code SceneTable} 组件本体，验证固定列宽、
     * 长文本裁剪和纵向滚动，不塞入现有 controls demo。</p>
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendSceneTableDemoStage(UiDocument document, ElementNode stage) {
        DocumentButtonControl button = new DocumentButtonControl(document, "打开 Scene Table demo 页");
        button.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                SceneTableDemoScreen.openDemo();
            }
        });
        button.setBackgroundColors(0xFF059669, 0xFF047857, 0xFF334155);
        button.setTextColors(0xFFFFFFFF, 0xFFA0AEC0);
        button.getElement().style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setMinWidth(UiStyleLength.px(220))
                .setPadding(UiStyleLength.px(10));
        stage.append(button.getElement());
        appendMutedText(document, stage,
                "点击按钮进入新栈 Table demo（ESC 返回）：固定列宽、固定行高、表头与数据行列对齐。");
        ElementNode grid = createDemoRow(document);
        String[] steps = {"固定列宽", "长文本裁剪", "纵向滚动"};
        int[] colors = {0xFF065F46, 0xFF1E3A8A, 0xFF6B21A8};
        for (int i = 0; i < steps.length; i++) {
            grid.append(createDemoPanel(document, steps[i], colors[i]));
        }
        stage.append(grid);
        appendMutedText(document, stage,
                "独立页面直接 runtime.mount(SceneTable.create(...))，滚动逻辑由 Table 组件内部 handler 承担。零异常需真机日志确认。");
    }

    /**
     * 追加新栈 ui.scene Layout demo 舞台。
     *
     * <p>渲染「打开 Scene Layout demo 页」按钮与排版地基说明卡片。按钮点击跳转到
     * {@link SceneLayoutDemoScreen}，旧 visual matrix 仅作预览与跳转入口，不在文档页挂载
     * {@code SceneNode}。</p>
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendSceneLayoutDemoStage(UiDocument document, ElementNode stage) {
        DocumentButtonControl button = new DocumentButtonControl(document, "打开 Scene Layout demo 页");
        button.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                SceneLayoutDemoScreen.openDemo();
            }
        });
        button.setBackgroundColors(0xFF059669, 0xFF047857, 0xFF334155);
        button.setTextColors(0xFFFFFFFF, 0xFFA0AEC0);
        button.getElement().style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setMinWidth(UiStyleLength.px(220))
                .setPadding(UiStyleLength.px(10));
        stage.append(button.getElement());
        appendMutedText(document, stage,
                "点击按钮进入新栈 Layout demo（ESC 返回）：固定标题条 + fillParentHeight 视口吃满剩余高并滚动，集中展示六项排版地基能力。");
        ElementNode grid = createDemoRow(document);
        String[] panels = {"SHRINK 内容宽", "ROW/COLUMN + 间距", "Breadcrumb + 视口填高"};
        int[] colors = {0xFF065F46, 0xFF1E3A8A, 0xFF6B21A8};
        for (int i = 0; i < panels.length; i++) {
            grid.append(createDemoPanel(document, panels[i], colors[i]));
        }
        stage.append(grid);
        appendMutedText(document, stage,
                "独立页面用 SceneLayoutHostWidget 组装：root COLUMN 固定标题 + 唯一 fillParentHeight 视口（P1-a），各卡片演示 P0 SHRINK 与 ROW/COLUMN/padding/gap/preferredWidth/Breadcrumb。");
    }

    /**
     * 追加新栈 ui.scene 配置表单 demo 舞台。
     *
     * <p>渲染「打开 Scene 配置表单 demo 页」按钮与表单机制说明卡片。按钮点击跳转到
     * {@link SceneFormDemoScreen}，旧 visual matrix 仅作预览与跳转入口，不在文档页挂载
     * {@code SceneNode}。</p>
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendSceneFormDemoStage(UiDocument document, ElementNode stage) {
        DocumentButtonControl button = new DocumentButtonControl(document, "打开 Scene 配置表单 demo 页");
        button.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                SceneFormDemoScreen.openDemo();
            }
        });
        button.setBackgroundColors(0xFF059669, 0xFF047857, 0xFF334155);
        button.setTextColors(0xFFFFFFFF, 0xFFA0AEC0);
        button.getElement().style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setMinWidth(UiStyleLength.px(220))
                .setPadding(UiStyleLength.px(10));
        stage.append(button.getElement());
        appendMutedText(document, stage,
                "点击按钮进入新栈配置表单 demo（ESC 返回）：硬编码 draft/current 双副本、dirty 标记、字段校验与保存恢复。 ");
        ElementNode grid = createDemoRow(document);
        String[] panels = {"双副本 + 脏标记", "字段校验 + 错误提示", "保存写回 / 取消回滚"};
        int[] colors = {0xFF065F46, 0xFF1E3A8A, 0xFF6B21A8};
        for (int i = 0; i < panels.length; i++) {
            grid.append(createDemoPanel(document, panels[i], colors[i]));
        }
        stage.append(grid);
        appendMutedText(document, stage,
                "表单状态全由 Signal/Computed 表达：canSave=isDirty&&!hasError，按钮 enabled 与卡片边框/错误文案均经 bind 消费，交互 handler 只写 signal。 ");
    }

    /**
     * 追加新栈 ui.scene Select demo 舞台。
     *
     * <p>渲染「打开 Scene Select demo 页」按钮与 Select 验收说明卡片。按钮点击跳转到
     * {@link SceneSelectDemoScreen}，旧 visual matrix 仅作预览与跳转入口，不在文档页挂载
     * {@code SceneNode}。</p>
     *
     * @param document 文档实例
     * @param stage 演示舞台
     */
    private void appendSceneSelectDemoStage(UiDocument document, ElementNode stage) {
        DocumentButtonControl button = new DocumentButtonControl(document, "打开 Scene Select demo 页");
        button.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                SceneSelectDemoScreen.openDemo();
            }
        });
        button.setBackgroundColors(0xFF059669, 0xFF047857, 0xFF334155);
        button.setTextColors(0xFFFFFFFF, 0xFFA0AEC0);
        button.getElement().style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setMinWidth(UiStyleLength.px(220))
                .setPadding(UiStyleLength.px(10));
        stage.append(button.getElement());
        appendMutedText(document, stage,
                "点击按钮进入新栈 Select demo（ESC 返回）：基础、长列表、禁用态与并排双 Select。 ");
        ElementNode grid = createDemoRow(document);
        String[] panels = {"top-layer 下拉", "anchor 定位 + 滚动", "外部点击/ESC 关闭", "键盘导航"};
        int[] colors = {0xFF065F46, 0xFF1E3A8A, 0xFF6B21A8, 0xFF9A3412};
        for (int i = 0; i < panels.length; i++) {
            grid.append(createDemoPanel(document, panels[i], colors[i]));
        }
        stage.append(grid);
        appendMutedText(document, stage,
                "SceneSelect 保持受控：selectedIndex 为唯一外部状态源，trigger 常驻主树，listbox 由 portalAnchored 提升到 overlay root。 ");
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
