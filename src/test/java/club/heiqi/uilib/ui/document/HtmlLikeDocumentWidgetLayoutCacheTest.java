package club.heiqi.uilib.ui.document;

import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertContainsDrawCall;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertTextCall;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.findLayoutBox;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.CountingTextMeasureService;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.RecordingUiRenderContext;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.cascade.UiStyleSheet;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.values.UiTransform;

/**
 * `HtmlLikeDocumentWidget` 的静态布局缓存回归测试。
 */
public class HtmlLikeDocumentWidgetLayoutCacheTest {

    /**
     * 验证静态长文本二次渲染复用布局缓存，不因绘制裁剪触发重排。
     */
    @Test
    public void shouldReuseStaticLayoutForLongTextAcrossRenders() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 400; index++) {
            if (index > 0) {
                text.append('\n');
            }
            text.append("line-").append(index);
        }
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(54))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setWhiteSpace(UiWhiteSpace.PRE);
        root.appendText(text.toString());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 54,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 54);

        RecordingUiRenderContext firstContext = new RecordingUiRenderContext();
        widget.render(firstContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot firstSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();
        RecordingUiRenderContext secondContext = new RecordingUiRenderContext();
        widget.render(secondContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot secondSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertTrue(firstContext.textCalls.size() <= 5);
        Assert.assertEquals(firstSnapshot.getStaticLayoutGeneration(), secondSnapshot.getStaticLayoutGeneration());
        Assert.assertEquals(firstSnapshot.getRuntimeLayoutGeneration(), secondSnapshot.getRuntimeLayoutGeneration());
        Assert.assertEquals(firstSnapshot.getPaintCacheGeneration(), secondSnapshot.getPaintCacheGeneration());
    }

    /**
     * 验证局部文本变更后的静态重排会复用未脏 block 子树。
     */
    @Test
    public void shouldReuseCleanBlockSubtreesDuringDirtyRelayout() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode changed = document.div();
        ElementNode last = document.div();
        first.style().setHeight(UiStyleLength.px(18));
        changed.style().setHeight(UiStyleLength.px(18));
        last.style().setHeight(UiStyleLength.px(18));
        first.appendText("first");
        TextNode changedText = document.text("middle");
        changed.appendChild(changedText);
        last.appendText("last");
        root.append(first).append(changed).append(last);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80, textMeasureService);
        widget.applyLayoutBounds(0, 0, 120, 80);

        widget.render(new RecordingUiRenderContext());
        int firstMeasureCount = textMeasureService.getMeasureCount();
        changedText.setText("center");
        RecordingUiRenderContext secondContext = new RecordingUiRenderContext();
        widget.render(secondContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot secondSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertTrue(secondSnapshot.getLastLayoutReusedSubtreeCount() >= 2);
        Assert.assertTrue(textMeasureService.getMeasureCount() - firstMeasureCount <= 2);
        assertTextCall(secondContext.textCalls.get(0), "first", 0, 0, 0xFFFFFFFF, false);
        assertTextCall(secondContext.textCalls.get(1), "center", 0, 18, 0xFFFFFFFF, false);
        assertTextCall(secondContext.textCalls.get(2), "last", 0, 36, 0xFFFFFFFF, false);
    }

    /**
     * 验证上方兄弟高度变化后，未脏 block 子树可以通过纵向平移复用。
     */
    @Test
    public void shouldTranslateCleanBlockSubtreeWhenFlowTopChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode clean = document.div();
        changed.style().setHeight(UiStyleLength.px(18));
        clean.appendText("clean");
        root.append(changed).append(clean);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80, textMeasureService);
        widget.applyLayoutBounds(0, 0, 120, 80);

        widget.render(new RecordingUiRenderContext());
        int measureCountAfterInitialRender = textMeasureService.getMeasureCount();
        changed.style().setHeight(UiStyleLength.px(36));
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        Assert.assertEquals(measureCountAfterInitialRender, textMeasureService.getMeasureCount());
        Assert.assertEquals(1, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "clean", 0, 36, 0xFFFFFFFF, false);
    }

    /**
     * 验证可平移复用仍保留子树内首子级 margin collapse 的相对几何。
     */
    @Test
    public void shouldTranslateCleanBlockSubtreeWithCollapsedMargins() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode clean = document.div();
        ElementNode collapsedChild = document.div();
        changed.style().setHeight(UiStyleLength.px(10));
        collapsedChild.style().setMarginTop(UiStyleLength.px(20));
        collapsedChild.appendText("collapsed");
        clean.append(collapsedChild);
        root.append(changed).append(clean);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100, textMeasureService);
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.render(new RecordingUiRenderContext());
        int measureCountAfterInitialRender = textMeasureService.getMeasureCount();
        changed.style().setHeight(UiStyleLength.px(25));
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        Assert.assertEquals(measureCountAfterInitialRender, textMeasureService.getMeasureCount());
        Assert.assertEquals(1, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "collapsed", 0, 45, 0xFFFFFFFF, false);
    }

    /**
     * 验证可平移复用不会破坏横向 auto margin 求解结果。
     */
    @Test
    public void shouldTranslateCleanBlockSubtreeWithAutoMargin() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode centered = document.div();
        root.style().setWidth(UiStyleLength.px(120));
        changed.style().setHeight(UiStyleLength.px(18));
        centered.style()
                .setWidth(UiStyleLength.px(40))
                .setMargin(UiStyleInsets.horizontal(UiStyleLength.auto()));
        centered.appendText("center");
        root.append(changed).append(centered);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100, textMeasureService);
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.render(new RecordingUiRenderContext());
        int measureCountAfterInitialRender = textMeasureService.getMeasureCount();
        changed.style().setHeight(UiStyleLength.px(36));
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertEquals(1, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertEquals(measureCountAfterInitialRender, textMeasureService.getMeasureCount());
        Assert.assertEquals(1, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "center", 40, 36, 0xFFFFFFFF, false);
    }

    /**
     * 验证可平移复用保留 relative 视觉偏移。
     */
    @Test
    public void shouldTranslateCleanRelativeBlockSubtree() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode relative = document.div();
        changed.style().setHeight(UiStyleLength.px(10));
        relative.style()
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(5));
        relative.appendText("relative");
        root.append(changed).append(relative);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100, textMeasureService);
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.render(new RecordingUiRenderContext());
        int measureCountAfterInitialRender = textMeasureService.getMeasureCount();
        changed.style().setHeight(UiStyleLength.px(20));
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertEquals(1, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertEquals(measureCountAfterInitialRender, textMeasureService.getMeasureCount());
        Assert.assertEquals(1, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "relative", 0, 25, 0xFFFFFFFF, false);
    }

    /**
     * 验证可平移复用保留 sticky 在滚动视口中的视觉偏移。
     */
    @Test
    public void shouldTranslateCleanStickyBlockSubtree() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode sticky = document.div();
        ElementNode tail = document.div();
        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(60))
                .setOverflowY(UiOverflow.AUTO);
        changed.style().setHeight(UiStyleLength.px(30));
        sticky.style()
                .setPosition(UiPosition.STICKY)
                .setTop(UiStyleLength.px(0))
                .setHeight(UiStyleLength.px(10))
                .setBackgroundColor(0xFF224466);
        tail.style().setHeight(UiStyleLength.px(120));
        root.append(changed).append(sticky).append(tail);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 100, 60,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 100, 60);

        widget.render(new RecordingUiRenderContext());
        Assert.assertTrue(widget.requestScrollTo(root, 0, 40));
        changed.style().setHeight(UiStyleLength.px(50));
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        assertContainsDrawCall(changedContext, 0, 10, 100, 20, 0xFF224466, 0, 0);
    }

    /**
     * 验证包含 absolute 后代的子树仍保守重排，避免外部 containing block 坐标误平移。
     */
    @Test
    public void shouldNotTranslateSubtreeContainingAbsolutePositionedBox() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode container = document.div();
        ElementNode absolute = document.div();
        changed.style().setHeight(UiStyleLength.px(10));
        container.style()
                .setPosition(UiPosition.RELATIVE)
                .setHeight(UiStyleLength.px(40));
        absolute.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(7))
                .setTop(UiStyleLength.px(4))
                .setWidth(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(8));
        container.append(absolute);
        root.append(changed).append(container);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(30));
        widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        DocumentLayoutBox containerBox = findLayoutBox(rootBox, container);
        DocumentLayoutBox absoluteBox = findLayoutBox(rootBox, absolute);

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertNotNull(containerBox);
        Assert.assertNotNull(absoluteBox);
        Assert.assertEquals(30, containerBox.getTop());
        Assert.assertEquals(7, absoluteBox.getLeft());
        Assert.assertEquals(34, absoluteBox.getTop());
    }

    /**
     * 验证包含 viewport fixed 后代的子树仍保守重排，fixed 坐标不随父级普通流位置平移。
     */
    @Test
    public void shouldNotTranslateSubtreeContainingViewportFixedBox() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode container = document.div();
        ElementNode fixed = document.div();
        changed.style().setHeight(UiStyleLength.px(10));
        container.style().setHeight(UiStyleLength.px(40));
        fixed.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(7))
                .setTop(UiStyleLength.px(4))
                .setWidth(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(8));
        container.append(fixed);
        root.append(changed).append(container);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(30));
        widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        DocumentLayoutBox containerBox = findLayoutBox(rootBox, container);
        DocumentLayoutBox fixedBox = findLayoutBox(rootBox, fixed);

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertNotNull(containerBox);
        Assert.assertNotNull(fixedBox);
        Assert.assertEquals(30, containerBox.getTop());
        Assert.assertEquals(7, fixedBox.getLeft());
        Assert.assertEquals(4, fixedBox.getTop());
    }

    /**
     * 验证 transform fixed containing block 子树仍保守重排，fixed 后代继续相对 transform 祖先定位。
     */
    @Test
    public void shouldNotTranslateTransformFixedContainingBlockSubtree() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode transformed = document.div();
        ElementNode fixed = document.div();
        changed.style().setHeight(UiStyleLength.px(10));
        transformed.style()
                .setHeight(UiStyleLength.px(40))
                .setTransform(UiTransform.translate(1.0F, 0.0F));
        fixed.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(7))
                .setTop(UiStyleLength.px(4))
                .setWidth(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(8));
        transformed.append(fixed);
        root.append(changed).append(transformed);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(30));
        widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        DocumentLayoutBox transformedBox = findLayoutBox(rootBox, transformed);
        DocumentLayoutBox fixedBox = findLayoutBox(rootBox, fixed);

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertNotNull(transformedBox);
        Assert.assertNotNull(fixedBox);
        Assert.assertEquals(30, transformedBox.getTop());
        Assert.assertEquals(7, fixedBox.getLeft());
        Assert.assertEquals(34, fixedBox.getTop());
    }

    /**
     * 验证可平移复用保留 row flex 主轴 grow 分配与交叉轴居中几何。
     */
    @Test
    public void shouldTranslateCleanRowFlexSubtreeWithMainAndCrossAxisDistribution() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode row = document.div();
        ElementNode grow = document.div();
        ElementNode largerGrow = document.div();
        root.style().setWidth(UiStyleLength.px(100));
        changed.style().setHeight(UiStyleLength.px(10));
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(40));
        grow.style()
                .setFlexGrow(1.0F)
                .setFlexBasis(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10));
        largerGrow.style()
                .setFlexGrow(2.0F)
                .setFlexBasis(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20));
        row.append(grow).append(largerGrow).appendText("note");
        root.append(changed).append(row);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 100, 100, textMeasureService);
        widget.applyLayoutBounds(0, 0, 100, 100);

        widget.resolveLayoutBoxForTest();
        int measureCountAfterInitialLayout = textMeasureService.getMeasureCount();
        changed.style().setHeight(UiStyleLength.px(26));
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox rowBox = findLayoutBox(rootBox, row);
        DocumentLayoutBox growBox = findLayoutBox(rootBox, grow);
        DocumentLayoutBox largerGrowBox = findLayoutBox(rootBox, largerGrow);

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        Assert.assertNotNull(rowBox);
        Assert.assertNotNull(growBox);
        Assert.assertNotNull(largerGrowBox);
        Assert.assertEquals(measureCountAfterInitialLayout, textMeasureService.getMeasureCount());
        Assert.assertEquals(26, rowBox.getTop());
        Assert.assertEquals(29, growBox.getWidth());
        Assert.assertEquals(10, growBox.getHeight());
        Assert.assertEquals(0, growBox.getLeft());
        Assert.assertEquals(41, growBox.getTop());
        Assert.assertEquals(39, largerGrowBox.getWidth());
        Assert.assertEquals(20, largerGrowBox.getHeight());
        Assert.assertEquals(29, largerGrowBox.getLeft());
        Assert.assertEquals(36, largerGrowBox.getTop());
    }

    /**
     * 验证可平移复用保留 column flex item 主轴尺寸分配。
     */
    @Test
    public void shouldTranslateCleanColumnFlexSubtreeWithFlexItemSizeDistribution() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode column = document.div();
        ElementNode head = document.div();
        ElementNode grow = document.div();
        ElementNode tail = document.div();
        root.style().setWidth(UiStyleLength.px(80));
        changed.style().setHeight(UiStyleLength.px(12));
        column.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.STRETCH)
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(60));
        head.style().setHeight(UiStyleLength.px(10));
        grow.style().setFlexGrow(1.0F);
        tail.style().setHeight(UiStyleLength.px(20));
        column.append(head).append(grow).append(tail);
        root.append(changed).append(column);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 100,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 100);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(22));
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox columnBox = findLayoutBox(rootBox, column);
        DocumentLayoutBox headBox = findLayoutBox(rootBox, head);
        DocumentLayoutBox growBox = findLayoutBox(rootBox, grow);
        DocumentLayoutBox tailBox = findLayoutBox(rootBox, tail);

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        Assert.assertNotNull(columnBox);
        Assert.assertNotNull(headBox);
        Assert.assertNotNull(growBox);
        Assert.assertNotNull(tailBox);
        Assert.assertEquals(22, columnBox.getTop());
        Assert.assertEquals(60, columnBox.getHeight());
        Assert.assertEquals(22, headBox.getTop());
        Assert.assertEquals(10, headBox.getHeight());
        Assert.assertEquals(32, growBox.getTop());
        Assert.assertEquals(30, growBox.getHeight());
        Assert.assertEquals(62, tailBox.getTop());
        Assert.assertEquals(20, tailBox.getHeight());
    }

    /**
     * 验证可平移复用不会破坏 flex item 主轴与交叉轴 auto margin 求解结果。
     */
    @Test
    public void shouldTranslateCleanFlexSubtreeWithMainAndCrossAxisAutoMargin() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode row = document.div();
        ElementNode centered = document.div();
        root.style().setWidth(UiStyleLength.px(100));
        changed.style().setHeight(UiStyleLength.px(8));
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(40));
        centered.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10))
                .setMargin(UiStyleInsets.of(UiStyleLength.auto(), UiStyleLength.auto(), UiStyleLength.auto(),
                        UiStyleLength.auto()));
        row.append(centered);
        root.append(changed).append(row);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 100, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 100, 80);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(18));
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox centeredBox = findLayoutBox(rootBox, centered);

        Assert.assertEquals(1, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertNotNull(centeredBox);
        Assert.assertEquals(40, centeredBox.getLeft());
        Assert.assertEquals(33, centeredBox.getTop());
        Assert.assertEquals(20, centeredBox.getWidth());
    }

    /**
     * 验证包含 absolute/fixed 后代的 flex 子树仍保守重排。
     */
    @Test
    public void shouldNotTranslateFlexSubtreeContainingOutOfFlowPositionedDescendants() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode row = document.div();
        ElementNode absolute = document.div();
        ElementNode fixed = document.div();
        root.style().setWidth(UiStyleLength.px(120));
        changed.style().setHeight(UiStyleLength.px(10));
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setPosition(UiPosition.RELATIVE)
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(40));
        absolute.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(7))
                .setTop(UiStyleLength.px(4))
                .setWidth(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(8));
        fixed.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(9))
                .setTop(UiStyleLength.px(6))
                .setWidth(UiStyleLength.px(10))
                .setHeight(UiStyleLength.px(6));
        row.append(absolute).append(fixed);
        root.append(changed).append(row);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(30));
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox rowBox = findLayoutBox(rootBox, row);
        DocumentLayoutBox absoluteBox = findLayoutBox(rootBox, absolute);
        DocumentLayoutBox fixedBox = findLayoutBox(rootBox, fixed);

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertNotNull(rowBox);
        Assert.assertNotNull(absoluteBox);
        Assert.assertNotNull(fixedBox);
        Assert.assertEquals(30, rowBox.getTop());
        Assert.assertEquals(7, absoluteBox.getLeft());
        Assert.assertEquals(34, absoluteBox.getTop());
        Assert.assertEquals(9, fixedBox.getLeft());
        Assert.assertEquals(6, fixedBox.getTop());
    }

    /**
     * 验证可平移复用保留 table auto 列宽分配结果。
     */
    @Test
    public void shouldTranslateCleanTableSubtreeWithColumnWidths() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode table = document.table();
        ElementNode row = document.div();
        ElementNode shortCell = document.div();
        ElementNode longCell = document.div();
        root.style().setWidth(UiStyleLength.px(160));
        changed.style().setHeight(UiStyleLength.px(10));
        table.style()
                .setDisplay(UiDisplay.TABLE)
                .setWidth(UiStyleLength.px(160));
        row.style().setDisplay(UiDisplay.TABLE_ROW);
        shortCell.style().setDisplay(UiDisplay.TABLE_CELL);
        longCell.style().setDisplay(UiDisplay.TABLE_CELL);
        shortCell.appendText("A");
        longCell.appendText("ABCDEFGHIJ");
        row.append(shortCell).append(longCell);
        table.append(row);
        root.append(changed).append(table);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 100, textMeasureService);
        widget.applyLayoutBounds(0, 0, 160, 100);

        widget.resolveLayoutBoxForTest();
        int measureCountAfterInitialLayout = textMeasureService.getMeasureCount();
        changed.style().setHeight(UiStyleLength.px(26));
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox tableBox = findLayoutBox(rootBox, table);
        DocumentLayoutBox rowBox = findLayoutBox(rootBox, row);
        DocumentLayoutBox shortBox = findLayoutBox(rootBox, shortCell);
        DocumentLayoutBox longBox = findLayoutBox(rootBox, longCell);

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        Assert.assertEquals(measureCountAfterInitialLayout, textMeasureService.getMeasureCount());
        Assert.assertNotNull(tableBox);
        Assert.assertNotNull(rowBox);
        Assert.assertNotNull(shortBox);
        Assert.assertNotNull(longBox);
        Assert.assertEquals(26, tableBox.getTop());
        Assert.assertEquals(26, rowBox.getTop());
        Assert.assertEquals(26, shortBox.getTop());
        Assert.assertEquals(shortBox.getLeft() + shortBox.getWidth(), longBox.getLeft());
        Assert.assertEquals(160, shortBox.getWidth() + longBox.getWidth());
        Assert.assertTrue(longBox.getWidth() > shortBox.getWidth());
    }

    /**
     * 验证可平移复用保留 table 行高测量与行落位结果。
     */
    @Test
    public void shouldTranslateCleanTableSubtreeWithRowHeights() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode table = document.table();
        ElementNode firstRow = document.div();
        ElementNode secondRow = document.div();
        ElementNode tallCell = document.div();
        ElementNode shortCell = document.div();
        root.style().setWidth(UiStyleLength.px(100));
        changed.style().setHeight(UiStyleLength.px(8));
        table.style()
                .setDisplay(UiDisplay.TABLE)
                .setWidth(UiStyleLength.px(100));
        firstRow.style().setDisplay(UiDisplay.TABLE_ROW);
        secondRow.style().setDisplay(UiDisplay.TABLE_ROW);
        tallCell.style()
                .setDisplay(UiDisplay.TABLE_CELL)
                .setHeight(UiStyleLength.px(24));
        shortCell.style()
                .setDisplay(UiDisplay.TABLE_CELL)
                .setHeight(UiStyleLength.px(12));
        firstRow.append(tallCell);
        secondRow.append(shortCell);
        table.append(firstRow).append(secondRow);
        root.append(changed).append(table);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 100, 100,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 100, 100);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(18));
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox tableBox = findLayoutBox(rootBox, table);
        DocumentLayoutBox firstRowBox = findLayoutBox(rootBox, firstRow);
        DocumentLayoutBox secondRowBox = findLayoutBox(rootBox, secondRow);
        DocumentLayoutBox tallBox = findLayoutBox(rootBox, tallCell);
        DocumentLayoutBox shortBox = findLayoutBox(rootBox, shortCell);

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        Assert.assertNotNull(tableBox);
        Assert.assertNotNull(firstRowBox);
        Assert.assertNotNull(secondRowBox);
        Assert.assertNotNull(tallBox);
        Assert.assertNotNull(shortBox);
        Assert.assertEquals(18, tableBox.getTop());
        Assert.assertEquals(36, tableBox.getHeight());
        Assert.assertEquals(18, firstRowBox.getTop());
        Assert.assertEquals(24, firstRowBox.getHeight());
        Assert.assertEquals(42, secondRowBox.getTop());
        Assert.assertEquals(12, secondRowBox.getHeight());
        Assert.assertEquals(24, tallBox.getHeight());
        Assert.assertEquals(12, shortBox.getHeight());
    }

    /**
     * 验证 table 单元格内容变化会让 table 子树重新布局，而不是复用旧列宽。
     */
    @Test
    public void shouldInvalidateTableSubtreeReuseWhenCellContentChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode table = document.table();
        ElementNode row = document.div();
        ElementNode stableCell = document.div();
        ElementNode changedCell = document.div();
        root.style().setWidth(UiStyleLength.px(120));
        table.style()
                .setDisplay(UiDisplay.TABLE)
                .setWidth(UiStyleLength.px(120));
        row.style().setDisplay(UiDisplay.TABLE_ROW);
        stableCell.style().setDisplay(UiDisplay.TABLE_CELL);
        changedCell.style().setDisplay(UiDisplay.TABLE_CELL);
        stableCell.appendText("A");
        TextNode changedText = changedCell.appendText("BB");
        row.append(stableCell).append(changedCell);
        table.append(row);
        root.append(table);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80, textMeasureService);
        widget.applyLayoutBounds(0, 0, 120, 80);

        widget.resolveLayoutBoxForTest();
        int measureCountAfterInitialLayout = textMeasureService.getMeasureCount();
        changedText.setText("BBBBBBBBBBBB");
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox stableBox = findLayoutBox(rootBox, stableCell);
        DocumentLayoutBox changedBox = findLayoutBox(rootBox, changedCell);

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertTrue(textMeasureService.getMeasureCount() > measureCountAfterInitialLayout);
        Assert.assertNotNull(stableBox);
        Assert.assertNotNull(changedBox);
        Assert.assertTrue(changedBox.getWidth() > stableBox.getWidth());
    }

    /**
     * 验证包含 absolute/fixed 后代的 table 子树仍保守重排。
     */
    @Test
    public void shouldNotTranslateTableSubtreeContainingOutOfFlowPositionedDescendants() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode table = document.table();
        ElementNode row = document.div();
        ElementNode cell = document.div();
        ElementNode absolute = document.div();
        ElementNode fixed = document.div();
        root.style().setWidth(UiStyleLength.px(120));
        changed.style().setHeight(UiStyleLength.px(10));
        table.style()
                .setDisplay(UiDisplay.TABLE)
                .setPosition(UiPosition.RELATIVE)
                .setWidth(UiStyleLength.px(120));
        row.style().setDisplay(UiDisplay.TABLE_ROW);
        cell.style()
                .setDisplay(UiDisplay.TABLE_CELL)
                .setHeight(UiStyleLength.px(40));
        absolute.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(7))
                .setTop(UiStyleLength.px(4))
                .setWidth(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(8));
        fixed.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(9))
                .setTop(UiStyleLength.px(6))
                .setWidth(UiStyleLength.px(10))
                .setHeight(UiStyleLength.px(6));
        cell.append(absolute).append(fixed);
        row.append(cell);
        table.append(row);
        root.append(changed).append(table);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(30));
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox tableBox = findLayoutBox(rootBox, table);
        DocumentLayoutBox absoluteBox = findLayoutBox(rootBox, absolute);
        DocumentLayoutBox fixedBox = findLayoutBox(rootBox, fixed);

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertNotNull(tableBox);
        Assert.assertNotNull(absoluteBox);
        Assert.assertNotNull(fixedBox);
        Assert.assertEquals(30, tableBox.getTop());
        Assert.assertEquals(7, absoluteBox.getLeft());
        Assert.assertEquals(34, absoluteBox.getTop());
        Assert.assertEquals(9, fixedBox.getLeft());
        Assert.assertEquals(6, fixedBox.getTop());
    }

    /**
     * 验证同一父级内移动 DOM 子节点会让布局顺序重新计算。
     */
    @Test
    public void shouldRelayoutWhenChildMovesWithinSameParent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode moved = document.div();
        ElementNode last = document.div();
        first.style().setHeight(UiStyleLength.px(18));
        moved.style().setHeight(UiStyleLength.px(18));
        last.style().setHeight(UiStyleLength.px(18));
        first.appendText("first");
        moved.appendText("moved");
        last.appendText("last");
        root.append(first).append(moved).append(last);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80,
                new CountingTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 80);

        widget.render(new RecordingUiRenderContext());
        root.insertBefore(last, first);
        RecordingUiRenderContext movedContext = new RecordingUiRenderContext();
        widget.render(movedContext);

        Assert.assertEquals(3, movedContext.textCalls.size());
        assertTextCall(movedContext.textCalls.get(0), "last", 0, 0, 0xFFFFFFFF, false);
        assertTextCall(movedContext.textCalls.get(1), "first", 0, 18, 0xFFFFFFFF, false);
        assertTextCall(movedContext.textCalls.get(2), "moved", 0, 36, 0xFFFFFFFF, false);
    }

    /**
     * 验证跨父级移动 DOM 子节点时旧父级也会标脏并收缩布局高度。
     */
    @Test
    public void shouldInvalidatePreviousParentWhenChildMovesAcrossParents() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode source = document.div();
        ElementNode target = document.div();
        ElementNode moved = document.div();
        ElementNode sourceTail = document.div();
        ElementNode targetHead = document.div();
        moved.style().setHeight(UiStyleLength.px(18));
        sourceTail.style().setHeight(UiStyleLength.px(18));
        targetHead.style().setHeight(UiStyleLength.px(18));
        moved.appendText("moved");
        sourceTail.appendText("source-tail");
        targetHead.appendText("target-head");
        source.append(moved).append(sourceTail);
        target.append(targetHead);
        root.append(source).append(target);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new CountingTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.render(new RecordingUiRenderContext());
        target.append(moved);
        RecordingUiRenderContext movedContext = new RecordingUiRenderContext();
        widget.render(movedContext);

        Assert.assertEquals(3, movedContext.textCalls.size());
        assertTextCall(movedContext.textCalls.get(0), "source-tail", 0, 0, 0xFFFFFFFF, false);
        assertTextCall(movedContext.textCalls.get(1), "target-head", 0, 18, 0xFFFFFFFF, false);
        assertTextCall(movedContext.textCalls.get(2), "moved", 0, 36, 0xFFFFFFFF, false);
    }

    /**
     * 验证继承性 layout 样式变化会让后代子树重新布局，而不是复用旧行高。
     */
    @Test
    public void shouldInvalidateDescendantsWhenInheritedLayoutStyleChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode container = document.div();
        ElementNode inherited = document.div();
        ElementNode after = document.div();
        inherited.appendText("inherited");
        after.appendText("after");
        container.append(inherited).append(after);
        root.append(container);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new CountingTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.render(new RecordingUiRenderContext());
        container.style().setLineHeight(UiStyleLength.px(30));
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);

        Assert.assertEquals(2, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "inherited", 0, 0, 0xFFFFFFFF, false);
        assertTextCall(changedContext.textCalls.get(1), "after", 0, 30, 0xFFFFFFFF, false);
    }

    /**
     * 验证文档级样式表变化会全局失效布局缓存并应用新规则。
     */
    @Test
    public void shouldInvalidateLayoutCacheWhenStyleSheetChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode target = document.div();
        ElementNode after = document.div();
        target.setClassName("sheet-target");
        target.appendText("sheet");
        after.appendText("after");
        root.append(target).append(after);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new CountingTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.render(new RecordingUiRenderContext());
        document.addStyleSheet(UiStyleSheet.create().addRule(".sheet-target",
                new UiStyleDeclaration().setLineHeight(UiStyleLength.px(30))));
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertEquals(2, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "sheet", 0, 0, 0xFFFFFFFF, false);
        assertTextCall(changedContext.textCalls.get(1), "after", 0, 30, 0xFFFFFFFF, false);
    }

    /**
     * 验证已挂载样式表新增布局规则后，下一次布局会立即应用。
     */
    @Test
    public void shouldApplyMountedStyleSheetAddedLayoutRule() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode target = document.div();
        ElementNode after = document.div();
        UiStyleSheet sheet = UiStyleSheet.create();
        target.setClassName("mounted-sheet-target");
        target.appendText("sheet");
        after.appendText("after");
        root.append(target).append(after);
        document.addStyleSheet(sheet);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new CountingTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.render(new RecordingUiRenderContext());
        sheet.addRule(".mounted-sheet-target", new UiStyleDeclaration().setLineHeight(UiStyleLength.px(30)));
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);

        Assert.assertEquals(2, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "sheet", 0, 0, 0xFFFFFFFF, false);
        assertTextCall(changedContext.textCalls.get(1), "after", 0, 30, 0xFFFFFFFF, false);
    }

    /**
     * 验证已挂载样式表规则声明变更 paint-only 属性后，下一次绘制会立即应用。
     */
    @Test
    public void shouldApplyMountedStyleSheetPaintOnlyDeclarationChange() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode target = document.div();
        UiStyleDeclaration declaration = new UiStyleDeclaration().setTextColor(0xFFFF0000);
        UiStyleSheet sheet = UiStyleSheet.create().addRule(".paint-sheet-target", declaration);
        target.setClassName("paint-sheet-target");
        target.appendText("paint");
        root.append(target);
        document.addStyleSheet(sheet);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new CountingTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.render(new RecordingUiRenderContext());
        int layoutGeneration = widget.getPerformanceDiagnosticsSnapshot().getStaticLayoutGeneration();
        declaration.setTextColor(0xFF00FF00);
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);

        Assert.assertEquals(layoutGeneration,
                widget.getPerformanceDiagnosticsSnapshot().getStaticLayoutGeneration());
        Assert.assertEquals(1, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "paint", 0, 0, 0xFF00FF00, false);
    }

    /**
     * 验证文本测量 epoch 推进后不会复用旧文本布局盒。
     */
    @Test
    public void shouldInvalidateLayoutReuseWhenTextMeasureEpochChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();
        first.appendText("first");
        second.appendText("second");
        root.append(first).append(second);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80, textMeasureService);
        widget.applyLayoutBounds(0, 0, 120, 80);

        widget.render(new RecordingUiRenderContext());
        int measureCountAfterInitialRender = textMeasureService.getMeasureCount();
        textMeasureService.advanceEpoch();
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertTrue(textMeasureService.getMeasureCount() > measureCountAfterInitialRender);
        Assert.assertEquals(2, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "first", 0, 0, 0xFFFFFFFF, false);
        assertTextCall(changedContext.textCalls.get(1), "second", 0, 18, 0xFFFFFFFF, false);
    }
}
