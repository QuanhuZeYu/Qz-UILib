package club.heiqi.uilib.ui.paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentEffectType;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.layout.DocumentScrollState;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `DocumentPaintEngine` 的绘制命令生成契约测试。
 */
public class DocumentPaintEngineTest {

    /**
     * 验证绘制命令按元素背景、边框、子树的顺序输出。
     */
    @Test
    public void shouldBuildBackgroundBorderAndDescendantCommandsInPaintOrder() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(40))
                .setBackgroundColor(0xAA101820)
                .setBorderColor(0xFF86A8F0)
                .setBorderWidth(UiStyleLength.px(2))
                .setBorderRadius(UiStyleLength.px(12));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(10))
                .setBackgroundColor(0xFF223344);
        root.append(child);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 200, 0);
        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(rootBox);

        Assert.assertEquals(3, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.BACKGROUND, root, 0, 0, 104, 44, 0xAA101820, 0,
                12);
        assertCommand(commands.get(1), DocumentPaintCommandType.BORDER, root, 0, 0, 104, 44, 0xFF86A8F0, 2,
                12);
        assertCommand(commands.get(2), DocumentPaintCommandType.BACKGROUND, child, 2, 2, 42, 12, 0xFF223344, 0,
                0);
    }

    /**
     * 验证 absolute 子元素按 inset 坐标绘制，并位于普通流内容之上。
     */
    @Test
    public void shouldPaintAbsolutePositionedChildAboveNormalFlow() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode absolute = document.div();
        ElementNode normal = document.div();

        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(30))
                .setBackgroundColor(0xFF101820);
        absolute.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(8))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(6))
                .setLeft(UiStyleLength.px(10))
                .setBackgroundColor(0xFFFF0000);
        normal.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(10))
                .setBackgroundColor(0xFF0000FF);
        root.append(absolute).append(normal);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 120, 0));

        Assert.assertEquals(3, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.BACKGROUND, root, 0, 0, 100, 30, 0xFF101820,
                0, 0);
        assertCommand(commands.get(1), DocumentPaintCommandType.BACKGROUND, normal, 0, 0, 40, 10, 0xFF0000FF,
                0, 0);
        assertCommand(commands.get(2), DocumentPaintCommandType.BACKGROUND, absolute, 10, 6, 30, 14, 0xFFFF0000,
                0, 0);
    }

    /**
     * 验证 absolute 子元素按最近 positioned ancestor 的 containing block 绘制。
     */
    @Test
    public void shouldPaintAbsolutePositionedChildAgainstNearestPositionedAncestor() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode positioned = document.div();
        ElementNode staticParent = document.div();
        ElementNode absolute = document.div();

        root.style().setWidth(UiStyleLength.px(180));
        positioned.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(60))
                .setPosition(UiPosition.RELATIVE)
                .setBorderWidth(UiStyleLength.px(2))
                .setPadding(UiStyleLength.px(10));
        staticParent.style()
                .setHeight(UiStyleLength.px(20))
                .setPadding(UiStyleLength.px(3));
        absolute.style()
                .setWidth(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(8))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(6))
                .setLeft(UiStyleLength.px(8))
                .setBackgroundColor(0xFFFF0000);
        staticParent.append(absolute);
        positioned.append(staticParent);
        root.append(positioned);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 220, 0));

        Assert.assertEquals(1, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.BACKGROUND, absolute, 20, 18, 32, 26, 0xFFFF0000,
                0, 0);
    }

    /**
     * 验证 fixed 子元素在根滚动后仍按视口固定位置绘制。
     */
    @Test
    public void shouldPaintFixedPositionedChildAtViewportPositionAfterRootScroll() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode spacer = document.div();
        ElementNode fixed = document.div();

        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(50))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setBackgroundColor(0xFF101820);
        spacer.style()
                .setHeight(UiStyleLength.px(140))
                .setBackgroundColor(0xFF0000FF);
        fixed.style()
                .setWidth(UiStyleLength.px(30))
                .setHeight(UiStyleLength.px(12))
                .setPosition(UiPosition.FIXED)
                .setTop(UiStyleLength.px(6))
                .setLeft(UiStyleLength.px(10))
                .setBackgroundColor(0xFFFF0000);
        root.append(spacer).append(fixed);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 100, 50);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);
        Assert.assertTrue(scrollState.setScrollOffset(root, 0, 36));
        List<DocumentPaintCommand> commands = withoutScrollbarCommands(DocumentPaintEngine.buildPaintCommands(rootBox,
                scrollState, 1L));

        Assert.assertEquals(5, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.BACKGROUND, root, 0, 0, 100, 50, 0xFF101820,
                0, 0);
        assertCommand(commands.get(1), DocumentPaintCommandType.CLIP_START, root, 0, 0, 100, 50, 0, 0, 0);
        assertCommand(commands.get(2), DocumentPaintCommandType.BACKGROUND, spacer, 0, -36, 100, 104, 0xFF0000FF,
                0, 0);
        assertCommand(commands.get(3), DocumentPaintCommandType.BACKGROUND, fixed, 10, 6, 40, 18, 0xFFFF0000,
                0, 0);
        assertCommand(commands.get(4), DocumentPaintCommandType.CLIP_END, root, 0, 0, 100, 50, 0, 0, 0);
    }

    /**
     * 验证非 context 祖先 opacity 会应用到标准绘制命令颜色，当前 context 的 opacity 由离屏合成处理。
     */
    @Test
    public void shouldApplyOpacityToStandardPaintColors() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(40))
                .setOpacity(0.5F)
                .setBackgroundColor(0xAA101820)
                .setBorderColor(0xFF86A8F0)
                .setBorderWidth(UiStyleLength.px(2))
                .setBorderRadius(UiStyleLength.px(12));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(10))
                .setOpacity(0.5F)
                .setBackgroundColor(0xFF223344);
        root.append(child);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 200, 0));
        List<DocumentPaintCommand> paintCommands = withoutPaintContextCommands(commands);

        Assert.assertEquals(5, commands.size());
        Assert.assertEquals(DocumentPaintCommandType.PAINT_CONTEXT_START, commands.get(2).getType());
        Assert.assertEquals(0.5F, commands.get(2).getPaintContextOpacity(), 0.0F);
        Assert.assertEquals(DocumentPaintCommandType.PAINT_CONTEXT_END, commands.get(4).getType());
        Assert.assertEquals(3, paintCommands.size());
        assertCommand(paintCommands.get(0), DocumentPaintCommandType.BACKGROUND, root, 0, 0, 104, 44, 0x55101820, 0,
                12);
        assertCommand(paintCommands.get(1), DocumentPaintCommandType.BORDER, root, 0, 0, 104, 44, 0x8086A8F0, 2,
                12);
        assertCommand(paintCommands.get(2), DocumentPaintCommandType.BACKGROUND, child, 2, 2, 42, 12, 0x80223344, 0,
                0);
    }

    /**
     * 验证非根 opacity 元素会输出显式绘制上下文边界。
     */
    @Test
    public void shouldWrapOpacityDescendantWithPaintContextCommands() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style().setWidth(UiStyleLength.px(100));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(10))
                .setOpacity(0.5F)
                .setBackgroundColor(0xFF223344);
        root.append(child);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 120, 0));

        Assert.assertEquals(3, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.PAINT_CONTEXT_START, child, 0, 0, 40, 10, 0, 0,
                0);
        Assert.assertEquals(0.5F, commands.get(0).getPaintContextOpacity(), 0.0F);
        assertCommand(commands.get(1), DocumentPaintCommandType.BACKGROUND, child, 0, 0, 40, 10, 0xFF223344, 0,
                0);
        assertCommand(commands.get(2), DocumentPaintCommandType.PAINT_CONTEXT_END, child, 0, 0, 40, 10, 0, 0,
                0);
    }

    /**
     * 验证 positioned z-index 元素会输出显式绘制上下文边界。
     */
    @Test
    public void shouldWrapPositionedZIndexDescendantWithPaintContextCommands() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style().setWidth(UiStyleLength.px(100));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.RELATIVE)
                .setZIndex(1)
                .setBackgroundColor(0xFF223344);
        root.append(child);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 120, 0));

        Assert.assertEquals(3, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.PAINT_CONTEXT_START, child, 0, 0, 40, 10, 0, 0,
                0);
        Assert.assertEquals(1.0F, commands.get(0).getPaintContextOpacity(), 0.0F);
        assertCommand(commands.get(1), DocumentPaintCommandType.BACKGROUND, child, 0, 0, 40, 10, 0xFF223344, 0,
                0);
        assertCommand(commands.get(2), DocumentPaintCommandType.PAINT_CONTEXT_END, child, 0, 0, 40, 10, 0, 0,
                0);
    }

    /**
     * 验证非根 backdrop-filter 元素会输出显式绘制上下文边界。
     */
    @Test
    public void shouldWrapBackdropDescendantWithPaintContextCommands() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style().setWidth(UiStyleLength.px(100));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(10))
                .setBackdropBlurRadius(UiStyleLength.px(6))
                .setBackdropSaturation(1.2F);
        root.append(child);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 120, 0));

        Assert.assertEquals(3, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.PAINT_CONTEXT_START, child, 0, 0, 40, 10, 0, 0,
                0);
        Assert.assertEquals(1.0F, commands.get(0).getPaintContextOpacity(), 0.0F);
        assertCommand(commands.get(1), DocumentPaintCommandType.BACKDROP_FILTER, child, 0, 0, 40, 10, 0, 0,
                0);
        Assert.assertEquals(6, commands.get(1).getBackdropBlurRadius());
        Assert.assertEquals(1.2F, commands.get(1).getBackdropSaturation(), 0.0F);
        assertCommand(commands.get(2), DocumentPaintCommandType.PAINT_CONTEXT_END, child, 0, 0, 40, 10, 0, 0,
                0);
    }

    /**
     * 验证动画中的 opacity 运行值会参与绘制命令颜色计算。
     */
    @Test
    public void shouldApplyAnimatedOpacityToPaintColors() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(1.0F)
                .setBackgroundColor(0xFF223344)
                .setTransition(DocumentAnimationProperty.OPACITY, 1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();
        DocumentLayoutBox firstLayout = DocumentLayoutEngine.layout(root, 80, 0);
        timeline.updateFromLayout(firstLayout, 0L);

        root.style().setOpacity(0.5F);
        DocumentLayoutBox secondLayout = DocumentLayoutEngine.layout(root, 80, 0);
        timeline.updateFromLayout(secondLayout, 0L);
        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(secondLayout, null,
                500_000_000L, timeline);

        Assert.assertEquals(1, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.BACKGROUND, root, 0, 0, 40, 20, 0xBF223344, 0,
                0);
    }

    /**
     * 验证动画中的 border-radius 运行值会参与表面、边框和裁剪命令。
     */
    @Test
    public void shouldApplyAnimatedBorderRadiusToPaintCommands() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setBackgroundColor(0xFF223344)
                .setBorderColor(0xFF88AADD)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(0))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTransition(DocumentAnimationProperty.BORDER_RADIUS, 1000L);
        child.style()
                .setWidth(UiStyleLength.px(10))
                .setHeight(UiStyleLength.px(10))
                .setBackgroundColor(0xFF0000FF);
        root.append(child);

        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();
        DocumentLayoutBox firstLayout = DocumentLayoutEngine.layout(root, 120, 0);
        timeline.updateFromLayout(firstLayout, 0L);
        root.style().setBorderRadius(UiStyleLength.px(12));
        DocumentLayoutBox secondLayout = DocumentLayoutEngine.layout(root, 120, 0);
        timeline.updateFromLayout(secondLayout, 0L);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(secondLayout, null,
                500_000_000L, timeline);

        Assert.assertEquals(5, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.BACKGROUND, root, 0, 0, 82, 42, 0xFF223344, 0,
                6);
        assertCommand(commands.get(1), DocumentPaintCommandType.BORDER, root, 0, 0, 82, 42, 0xFF88AADD, 1, 6);
        assertCommand(commands.get(2), DocumentPaintCommandType.CLIP_START, root, 1, 1, 81, 41, 0, 0, 6);
        assertCommand(commands.get(3), DocumentPaintCommandType.BACKGROUND, child, 1, 1, 11, 11, 0xFF0000FF, 0,
                0);
        assertCommand(commands.get(4), DocumentPaintCommandType.CLIP_END, root, 0, 0, 82, 42, 0, 0, 0);
    }

    /**
     * 验证 opacity 会应用到文本绘制颜色。
     */
    @Test
    public void shouldApplyOpacityToTextColors() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(120))
                .setPadding(UiStyleLength.px(4))
                .setOpacity(0.5F)
                .setTextColor(0xFFEFF6FF);
        root.appendText("Hello");

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(DocumentLayoutEngine.layout(root,
                160, 0, new DeterministicTextMeasureService()));

        Assert.assertEquals(1, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.TEXT, root, 4, 4, 44, 22, 0x80EFF6FF, 0, 0);
        Assert.assertEquals("Hello", commands.get(0).getText());
    }

    /**
     * 验证 opacity 会应用到滚动条绘制颜色。
     */
    @Test
    public void shouldApplyOpacityToScrollbarColors() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(0.5F)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        child.style().setHeight(UiStyleLength.px(50));
        root.append(child);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);
        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(rootBox, scrollState);

        Assert.assertEquals(4, commands.size());
        assertCommand(commands.get(2), DocumentPaintCommandType.SCROLLBAR_TRACK, root, 42, 2, 48, 18, 0x333B4A66,
                0, 3);
        assertCommand(commands.get(3), DocumentPaintCommandType.SCROLLBAR_THUMB, root, 42, 2, 48, 18, 0x6FBCD7FF,
                0, 3);
    }

    /**
     * 验证 backdrop filter 命令会在元素自身背景与边框之前输出。
     */
    @Test
    public void shouldBuildBackdropFilterBeforeOwnSurfaceCommands() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(28))
                .setBackgroundColor(0x44FFFFFF)
                .setBorderColor(0x99FFFFFF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(10))
                .setBackdropBlurRadius(UiStyleLength.px(14))
                .setBackdropSaturation(1.4F);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 120, 0));

        Assert.assertEquals(3, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.BACKDROP_FILTER, root, 0, 0, 82, 30, 0, 0,
                10);
        Assert.assertEquals(14, commands.get(0).getBackdropBlurRadius());
        Assert.assertEquals(1.4F, commands.get(0).getBackdropSaturation(), 0.0F);
        assertCommand(commands.get(1), DocumentPaintCommandType.BACKGROUND, root, 0, 0, 82, 30, 0x44FFFFFF, 0,
                10);
        assertCommand(commands.get(2), DocumentPaintCommandType.BORDER, root, 0, 0, 82, 30, 0x99FFFFFF, 1,
                10);
    }

    /**
     * 验证子元素按 CSS-like stacking phase 绘制，负 z-index 会落在父内容命令之前。
     */
    @Test
    public void shouldPaintChildrenByStackingPhases() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode autoPositioned = document.div();
        ElementNode positiveHigh = document.div();
        ElementNode normal = document.div();
        ElementNode negative = document.div();
        ElementNode zero = document.div();
        ElementNode positiveLow = document.div();

        root.style().setWidth(UiStyleLength.px(100));
        root.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {}
        });
        autoPositioned.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.RELATIVE)
                .setBackgroundColor(0xFF00AA00);
        positiveHigh.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.RELATIVE)
                .setZIndex(3)
                .setBackgroundColor(0xFFFF00FF);
        normal.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(10))
                .setBackgroundColor(0xFF0000FF);
        negative.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.RELATIVE)
                .setZIndex(-1)
                .setBackgroundColor(0xFFFF0000);
        zero.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.RELATIVE)
                .setZIndex(0)
                .setBackgroundColor(0xFFFFFF00);
        positiveLow.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.RELATIVE)
                .setZIndex(1)
                .setBackgroundColor(0xFFAA00AA);
        root.append(autoPositioned).append(positiveHigh).append(normal).append(negative).append(zero)
                .append(positiveLow);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 120, 0));
        List<DocumentPaintCommand> paintCommands = withoutPaintContextCommands(commands);

        Assert.assertEquals(7, paintCommands.size());
        assertCommand(paintCommands.get(0), DocumentPaintCommandType.BACKGROUND, negative, 0, 30, 50, 40, 0xFFFF0000,
                0, 0);
        assertCommand(paintCommands.get(1), DocumentPaintCommandType.CUSTOM, root, 0, 0, 100, 60, 0, 0, 0);
        assertCommand(paintCommands.get(2), DocumentPaintCommandType.BACKGROUND, normal, 0, 20, 50, 30, 0xFF0000FF,
                0, 0);
        assertCommand(paintCommands.get(3), DocumentPaintCommandType.BACKGROUND, autoPositioned, 0, 0, 50, 10,
                0xFF00AA00, 0, 0);
        assertCommand(paintCommands.get(4), DocumentPaintCommandType.BACKGROUND, zero, 0, 40, 50, 50, 0xFFFFFF00,
                0, 0);
        assertCommand(paintCommands.get(5), DocumentPaintCommandType.BACKGROUND, positiveLow, 0, 50, 50, 60,
                0xFFAA00AA, 0, 0);
        assertCommand(paintCommands.get(6), DocumentPaintCommandType.BACKGROUND, positiveHigh, 0, 10, 50, 20,
                0xFFFF00FF, 0, 0);
    }

    /**
     * 验证非 stacking context 祖先不会阻止 positioned 后代参与最近上下文排序。
     */
    @Test
    public void shouldPaintPositionedDescendantInNearestStackingContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode parent = document.div();
        ElementNode raisedDescendant = document.div();
        ElementNode normalCover = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        parent.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF111827);
        raisedDescendant.style()
                .setWidth(UiStyleLength.px(70))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(12))
                .setZIndex(5)
                .setBackgroundColor(0xFFFF3333);
        normalCover.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF2563EB);
        parent.append(raisedDescendant);
        root.append(parent).append(normalCover);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 140, 0));
        List<DocumentPaintCommand> paintCommands = withoutPaintContextCommands(commands);

        Assert.assertEquals(3, paintCommands.size());
        assertCommand(paintCommands.get(0), DocumentPaintCommandType.BACKGROUND, parent, 0, 0, 80, 20,
                0xFF111827, 0, 0);
        assertCommand(paintCommands.get(1), DocumentPaintCommandType.BACKGROUND, normalCover, 0, 20, 80, 40,
                0xFF2563EB, 0, 0);
        assertCommand(paintCommands.get(2), DocumentPaintCommandType.BACKGROUND, raisedDescendant, 0, 12, 70, 32,
                0xFFFF3333, 0, 0);
    }

    /**
     * 验证 stacking context 祖先会把高 z-index 后代隔离成一个整体。
     */
    @Test
    public void shouldKeepPositionedDescendantInsideParentStackingContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode isolatedParent = document.div();
        ElementNode raisedDescendant = document.div();
        ElementNode normalCover = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        isolatedParent.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(0.98F)
                .setBackgroundColor(0xFF111827);
        raisedDescendant.style()
                .setWidth(UiStyleLength.px(70))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(12))
                .setZIndex(99)
                .setBackgroundColor(0xFFFF3333);
        normalCover.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF2563EB);
        isolatedParent.append(raisedDescendant);
        root.append(isolatedParent).append(normalCover);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 140, 0));
        List<DocumentPaintCommand> paintCommands = withoutPaintContextCommands(commands);

        Assert.assertEquals(3, paintCommands.size());
        assertCommand(paintCommands.get(0), DocumentPaintCommandType.BACKGROUND, isolatedParent, 0, 0, 80, 20,
                0xFF111827, 0, 0);
        assertCommand(paintCommands.get(1), DocumentPaintCommandType.BACKGROUND, raisedDescendant, 0, 12, 70, 32,
                0xFFFF3333, 0, 0);
        assertCommand(paintCommands.get(2), DocumentPaintCommandType.BACKGROUND, normalCover, 0, 20, 80, 40,
                0xFF2563EB, 0, 0);
    }

    /**
     * 验证 overflow clip 作为 effect boundary 会隔离高 z-index 后代。
     */
    @Test
    public void shouldKeepPositionedDescendantInsideOverflowClipBoundary() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode clippedParent = document.div();
        ElementNode raisedDescendant = document.div();
        ElementNode normalCover = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        clippedParent.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setBackgroundColor(0xFF111827);
        raisedDescendant.style()
                .setWidth(UiStyleLength.px(70))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(12))
                .setZIndex(99)
                .setBackgroundColor(0xFFFF3333);
        normalCover.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF2563EB);
        clippedParent.append(raisedDescendant);
        root.append(clippedParent).append(normalCover);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 140, 0));
        List<DocumentPaintCommand> paintCommands = withoutPaintContextCommands(commands);

        Assert.assertEquals(5, paintCommands.size());
        assertCommand(paintCommands.get(0), DocumentPaintCommandType.BACKGROUND, clippedParent, 0, 0, 80, 20,
                0xFF111827, 0, 0);
        assertCommand(paintCommands.get(1), DocumentPaintCommandType.CLIP_START, clippedParent, 0, 0, 80, 20,
                0, 0, 0);
        assertCommand(paintCommands.get(2), DocumentPaintCommandType.BACKGROUND, raisedDescendant, 0, 12, 70, 32,
                0xFFFF3333, 0, 0);
        assertCommand(paintCommands.get(3), DocumentPaintCommandType.CLIP_END, clippedParent, 0, 0, 80, 20,
                0, 0, 0);
        assertCommand(paintCommands.get(4), DocumentPaintCommandType.BACKGROUND, normalCover, 0, 20, 80, 40,
                0xFF2563EB, 0, 0);
    }

    /**
     * 验证透明背景与零宽边框不会产生绘制命令。
     */
    @Test
    public void shouldSkipTransparentBackgroundAndZeroBorder() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0)
                .setBorderColor(0xFFFFFFFF)
                .setBorderWidth(UiStyleLength.px(0));

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 100, 0));

        Assert.assertTrue(commands.isEmpty());
    }

    /**
     * 验证 border radius 会被限制在当前 border box 可承受范围内。
     */
    @Test
    public void shouldClampPaintCommandBorderRadius() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(10))
                .setBackgroundColor(0xFF101820)
                .setBorderRadius(UiStyleLength.px(99));

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 100, 0));

        Assert.assertEquals(1, commands.size());
        Assert.assertEquals(5, commands.get(0).getBorderRadius());
    }

    /**
     * 验证非 visible overflow 会在子树前后输出结构裁剪命令。
     */
    @Test
    public void shouldWrapDescendantCommandsWithOverflowClip() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setPadding(UiStyleLength.px(4))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(6))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setBackgroundColor(0xFF101820);
        child.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(10))
                .setBackgroundColor(0xFFAA5500);
        root.append(child);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 100, 0));

        Assert.assertEquals(4, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.BACKGROUND, root, 0, 0, 60, 30, 0xFF101820, 0,
                6);
        assertCommand(commands.get(1), DocumentPaintCommandType.CLIP_START, root, 1, 1, 59, 29, 0, 0, 6);
        assertCommand(commands.get(2), DocumentPaintCommandType.BACKGROUND, child, 5, 5, 85, 15, 0xFFAA5500, 0,
                0);
        assertCommand(commands.get(3), DocumentPaintCommandType.CLIP_END, root, 0, 0, 60, 30, 0, 0, 0);
    }

    /**
     * 验证直接文本布局行会生成 TEXT 绘制命令并继承父元素文本颜色。
     */
    @Test
    public void shouldBuildTextCommandForDirectTextRun() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(120))
                .setPadding(UiStyleLength.px(4))
                .setTextColor(0xFFEFF6FF);
        root.appendText("Hello");

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 160, 0));

        Assert.assertEquals(1, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.TEXT, root, 4, 4, 44, 22, 0xFFEFF6FF, 0, 0);
        Assert.assertEquals("Hello", commands.get(0).getText());
    }

    /**
     * 验证换行后的文本布局行会各自生成 TEXT 绘制命令。
     */
    @Test
    public void shouldBuildTextCommandsForWrappedTextRuns() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(24))
                .setTextColor(0xFFEFF6FF);
        root.appendText("abcdefg");

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(DocumentLayoutEngine.layout(root,
                80, 0, new DeterministicTextMeasureService()));

        Assert.assertEquals(3, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.TEXT, root, 0, 0, 24, 18, 0xFFEFF6FF, 0, 0);
        Assert.assertEquals("abc", commands.get(0).getText());
        assertCommand(commands.get(1), DocumentPaintCommandType.TEXT, root, 0, 18, 24, 36, 0xFFEFF6FF, 0, 0);
        Assert.assertEquals("def", commands.get(1).getText());
        assertCommand(commands.get(2), DocumentPaintCommandType.TEXT, root, 0, 36, 8, 54, 0xFFEFF6FF, 0, 0);
        Assert.assertEquals("g", commands.get(2).getText());
    }

    /**
     * 验证 inline span 文本片段使用自身继承链解析出的文本颜色。
     */
    @Test
    public void shouldPaintInlineSpanTextWithOwnTextColor() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode span = document.span();

        root.style()
                .setWidth(UiStyleLength.px(48))
                .setTextColor(0xFFEFF6FF);
        span.style().setTextColor(0xFFFFD166);
        root.appendText("AA");
        span.appendText("BBBB");
        root.append(span);
        root.appendText("CC");

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(DocumentLayoutEngine.layout(root,
                80, 0, new DeterministicTextMeasureService()));

        Assert.assertEquals(3, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.TEXT, root, 0, 0, 16, 18, 0xFFEFF6FF, 0, 0);
        Assert.assertEquals("AA", commands.get(0).getText());
        assertCommand(commands.get(1), DocumentPaintCommandType.TEXT, span, 16, 0, 48, 18, 0xFFFFD166, 0, 0);
        Assert.assertEquals("BBBB", commands.get(1).getText());
        assertCommand(commands.get(2), DocumentPaintCommandType.TEXT, root, 0, 18, 16, 36, 0xFFEFF6FF, 0, 0);
        Assert.assertEquals("CC", commands.get(2).getText());
    }

    /**
     * 验证 inline span 文本片段会生成自身背景与边框 fragment 命令。
     */
    @Test
    public void shouldPaintInlineSpanFragmentSurfaceBeforeText() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode span = document.span();

        root.style()
                .setWidth(UiStyleLength.px(48))
                .setTextColor(0xFFEFF6FF);
        span.style()
                .setBackgroundColor(0x334F46E5)
                .setBorderColor(0xFFFFD166)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(5))
                .setTextColor(0xFFFFD166);
        root.appendText("AA");
        span.appendText("BBBB");
        root.append(span);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(DocumentLayoutEngine.layout(root,
                80, 0, new DeterministicTextMeasureService()));

        Assert.assertEquals(4, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.BACKGROUND, span, 16, 0, 48, 18, 0x334F46E5, 0,
                5);
        assertCommand(commands.get(1), DocumentPaintCommandType.BORDER, span, 16, 0, 48, 18, 0xFFFFD166, 1,
                5);
        assertCommand(commands.get(2), DocumentPaintCommandType.TEXT, root, 0, 0, 16, 18, 0xFFEFF6FF, 0, 0);
        assertCommand(commands.get(3), DocumentPaintCommandType.TEXT, span, 16, 0, 48, 18, 0xFFFFD166, 0, 0);
    }

    /**
     * 验证跨行 inline span 会按行绘制独立 fragment 表面。
     */
    @Test
    public void shouldPaintSplitInlineSpanFragmentsAcrossLines() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode span = document.span();

        root.style().setWidth(UiStyleLength.px(32));
        span.style()
                .setBackgroundColor(0x334F46E5)
                .setBorderColor(0xFFFFD166)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(5))
                .setTextColor(0xFFFFD166);
        span.appendText("AABBCC");
        root.append(span);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(DocumentLayoutEngine.layout(root,
                80, 0, new DeterministicTextMeasureService()));

        Assert.assertEquals(6, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.BACKGROUND, span, 0, 0, 32, 18, 0x334F46E5, 0, 5);
        assertCommand(commands.get(1), DocumentPaintCommandType.BORDER, span, 0, 0, 32, 18, 0xFFFFD166, 1, 5);
        assertCommand(commands.get(2), DocumentPaintCommandType.BACKGROUND, span, 0, 18, 16, 36, 0x334F46E5, 0,
                5);
        assertCommand(commands.get(3), DocumentPaintCommandType.BORDER, span, 0, 18, 16, 36, 0xFFFFD166, 1, 5);
        assertCommand(commands.get(4), DocumentPaintCommandType.TEXT, span, 0, 0, 32, 18, 0xFFFFD166, 0, 0);
        Assert.assertEquals("AABB", commands.get(4).getText());
        assertCommand(commands.get(5), DocumentPaintCommandType.TEXT, span, 0, 18, 16, 36, 0xFFFFD166, 0, 0);
        Assert.assertEquals("CC", commands.get(5).getText());
    }

    /**
     * 验证父 inline 背景 fragment 会覆盖嵌套 inline 子内容的整体范围。
     */
    @Test
    public void shouldPaintParentInlineFragmentAcrossNestedInlineDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode outerSpan = document.span();
        ElementNode innerSpan = document.span();

        root.style().setWidth(UiStyleLength.px(80));
        outerSpan.style().setBackgroundColor(0x5538BDF8).setTextColor(0xFFE0F2FE);
        innerSpan.style().setBackgroundColor(0xAA0EA5E9).setTextColor(0xFFFFFFFF);
        outerSpan.appendText("AA");
        innerSpan.appendText("BB");
        outerSpan.append(innerSpan);
        outerSpan.appendText("CC");
        root.append(outerSpan);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(DocumentLayoutEngine.layout(root,
                100, 0, new DeterministicTextMeasureService()));

        Assert.assertEquals(5, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.BACKGROUND, outerSpan, 0, 0, 48, 18, 0x5538BDF8,
                0, 0);
        assertCommand(commands.get(1), DocumentPaintCommandType.BACKGROUND, innerSpan, 16, 0, 32, 18, 0xAA0EA5E9,
                0, 0);
    }

    /**
     * 验证 CUSTOM 绘制命令使用元素内容盒，而不是 padding 盒。
     */
    @Test
    public void shouldBuildCustomCommandInContentBox() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBorderWidth(UiStyleLength.px(2))
                .setPadding(UiStyleLength.px(3));
        root.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {}
        });

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 120, 0));

        Assert.assertEquals(1, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.CUSTOM, root, 5, 5, 45, 25, 0, 0, 0);
        Assert.assertNotNull(commands.get(0).getCustomRenderer());
    }

    /**
     * 验证 overflow auto 的滚动偏移只移动内容命令，不移动自身背景与裁剪框。
     */
    @Test
    public void shouldOffsetScrollableContentCommands() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setBackgroundColor(0xFF101820);
        child.style()
                .setHeight(UiStyleLength.px(50))
                .setBackgroundColor(0xFFAA5500);
        root.append(child);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);
        Assert.assertEquals(30, scrollState.getMaxScrollTop(root));
        Assert.assertTrue(scrollState.setScrollOffset(root, 0, 12));
        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(rootBox, scrollState);

        Assert.assertEquals(6, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.BACKGROUND, root, 0, 0, 50, 20, 0xFF101820, 0,
                0);
        assertCommand(commands.get(1), DocumentPaintCommandType.CLIP_START, root, 0, 0, 50, 20, 0, 0, 0);
        assertCommand(commands.get(2), DocumentPaintCommandType.BACKGROUND, child, 0, -12, 50, 38, 0xFFAA5500, 0,
                0);
        assertCommand(commands.get(3), DocumentPaintCommandType.CLIP_END, root, 0, 0, 50, 20, 0, 0, 0);
        assertCommand(commands.get(4), DocumentPaintCommandType.SCROLLBAR_TRACK, root, 42, 2, 48, 18, 0x663B4A66,
                0, 3);
        assertCommand(commands.get(5), DocumentPaintCommandType.SCROLLBAR_THUMB, root, 42, 2, 48, 18, 0xDDBCD7FF,
                0, 3);
    }

    /**
     * 验证 CUSTOM 绘制命令作为内容命令，会随元素自身滚动偏移。
     */
    @Test
    public void shouldOffsetCustomCommandWithScrollableContent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setBorderWidth(UiStyleLength.px(1))
                .setPadding(UiStyleLength.px(4))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        root.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {}
        });
        child.style()
                .setHeight(UiStyleLength.px(50))
                .setBackgroundColor(0xFFAA5500);
        root.append(child);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);
        Assert.assertTrue(scrollState.setScrollOffset(root, 0, 12));
        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(rootBox, scrollState);

        Assert.assertEquals(6, commands.size());
        assertCommand(commands.get(0), DocumentPaintCommandType.CLIP_START, root, 1, 1, 59, 29, 0, 0, 0);
        assertCommand(commands.get(1), DocumentPaintCommandType.CUSTOM, root, 5, -7, 55, 13, 0, 0, 0);
        assertCommand(commands.get(2), DocumentPaintCommandType.BACKGROUND, child, 5, -7, 55, 43, 0xFFAA5500, 0,
                0);
        assertCommand(commands.get(3), DocumentPaintCommandType.CLIP_END, root, 0, 0, 60, 30, 0, 0, 0);
        assertCommand(commands.get(4), DocumentPaintCommandType.SCROLLBAR_TRACK, root, 47, 7, 53, 23, 0x663B4A66,
                0, 3);
        assertCommand(commands.get(5), DocumentPaintCommandType.SCROLLBAR_THUMB, root, 47, 7, 53, 23, 0xDDBCD7FF,
                0, 3);
    }

    /**
     * 验证嵌套滚动块的滚动条只在最近滚动后短暂显示。
     */
    @Test
    public void shouldHideNestedScrollbarAfterScrollBecomesIdle() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode scroller = document.div();
        ElementNode child = document.div();

        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        scroller.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        child.style()
                .setHeight(UiStyleLength.px(80))
                .setBackgroundColor(0xFFAA5500);
        scroller.append(child);
        root.append(scroller);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);

        List<DocumentPaintCommand> idleCommands = DocumentPaintEngine.buildPaintCommands(rootBox, scrollState,
                1_000_000_000L);
        Assert.assertEquals(0, countCommands(idleCommands, DocumentPaintCommandType.SCROLLBAR_TRACK));
        Assert.assertEquals(0, countCommands(idleCommands, DocumentPaintCommandType.SCROLLBAR_THUMB));

        Assert.assertTrue(scrollState.handleWheel(rootBox, 10, 10, -120, 1_000_000_000L));
        List<DocumentPaintCommand> activeCommands = DocumentPaintEngine.buildPaintCommands(rootBox, scrollState,
                1_500_000_000L);
        Assert.assertEquals(1, countCommands(activeCommands, DocumentPaintCommandType.SCROLLBAR_TRACK));
        Assert.assertEquals(1, countCommands(activeCommands, DocumentPaintCommandType.SCROLLBAR_THUMB));

        List<DocumentPaintCommand> expiredCommands = DocumentPaintEngine.buildPaintCommands(rootBox, scrollState,
                3_000_000_000L);
        Assert.assertEquals(0, countCommands(expiredCommands, DocumentPaintCommandType.SCROLLBAR_TRACK));
        Assert.assertEquals(0, countCommands(expiredCommands, DocumentPaintCommandType.SCROLLBAR_THUMB));
    }

    private static void assertCommand(DocumentPaintCommand command, DocumentPaintCommandType type,
            ElementNode element, int left, int top, int right, int bottom, int color, int borderWidth,
            int borderRadius) {
        Assert.assertEquals(type, command.getType());
        Assert.assertEquals(expectedEffectType(type), command.getEffectType());
        Assert.assertEquals(element.__getElementUid(), command.getElement().__getElementUid());
        Assert.assertEquals(left, command.getLeft());
        Assert.assertEquals(top, command.getTop());
        Assert.assertEquals(right, command.getRight());
        Assert.assertEquals(bottom, command.getBottom());
        Assert.assertEquals(right - left, command.getWidth());
        Assert.assertEquals(bottom - top, command.getHeight());
        Assert.assertEquals(color, command.getColor());
        Assert.assertEquals(borderWidth, command.getBorderWidth());
        Assert.assertEquals(borderRadius, command.getBorderRadius());
    }

    private static int countCommands(List<DocumentPaintCommand> commands, DocumentPaintCommandType type) {
        int count = 0;
        for (DocumentPaintCommand command : commands) {
            if (command.getType() == type) {
                count++;
            }
        }
        return count;
    }

    private static DocumentEffectType expectedEffectType(DocumentPaintCommandType type) {
        if (type == DocumentPaintCommandType.PAINT_CONTEXT_START
                || type == DocumentPaintCommandType.PAINT_CONTEXT_END) {
            return DocumentEffectType.PAINT_CONTEXT;
        }
        if (type == DocumentPaintCommandType.BACKDROP_FILTER) {
            return DocumentEffectType.BACKDROP_FILTER;
        }
        if (type == DocumentPaintCommandType.CLIP_START || type == DocumentPaintCommandType.CLIP_END) {
            return DocumentEffectType.OVERFLOW_CLIP;
        }
        return null;
    }

    private static List<DocumentPaintCommand> withoutPaintContextCommands(List<DocumentPaintCommand> commands) {
        List<DocumentPaintCommand> filteredCommands = new ArrayList<DocumentPaintCommand>();
        for (DocumentPaintCommand command : commands) {
            if (command.getType() == DocumentPaintCommandType.PAINT_CONTEXT_START
                    || command.getType() == DocumentPaintCommandType.PAINT_CONTEXT_END) {
                continue;
            }
            filteredCommands.add(command);
        }
        return filteredCommands;
    }

    private static List<DocumentPaintCommand> withoutScrollbarCommands(List<DocumentPaintCommand> commands) {
        List<DocumentPaintCommand> filteredCommands = new ArrayList<DocumentPaintCommand>();
        for (DocumentPaintCommand command : commands) {
            if (command.getType() == DocumentPaintCommandType.SCROLLBAR_TRACK
                    || command.getType() == DocumentPaintCommandType.SCROLLBAR_THUMB) {
                continue;
            }
            filteredCommands.add(command);
        }
        return filteredCommands;
    }

    /**
     * 供 paint 测试使用的确定性文本测量服务。
     */
    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 4;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int maxLength = Math.max(0, targetWidth / 4);
            return text.substring(0, Math.min(text.length(), maxLength));
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return Collections.emptyList();
            }
            List<String> lines = new ArrayList<String>();
            int maxCharsPerLine = Math.max(1, wrapWidth / 4);
            for (int index = 0; index < text.length(); index += maxCharsPerLine) {
                lines.add(text.substring(index, Math.min(text.length(), index + maxCharsPerLine)));
            }
            return lines;
        }
    }
}
