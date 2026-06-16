package club.heiqi.uilib.ui.paint;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ResourceLocation;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.layout.DocumentEffectType;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.layout.DocumentScrollState;
import club.heiqi.uilib.ui.render.ClipSnapshot;
import club.heiqi.uilib.ui.render.RoundedClipRegion;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.values.UiBorderRadius;
import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.values.UiBoxShadow;
import club.heiqi.uilib.ui.style.values.UiBackgroundImage;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.values.UiOutline;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.values.UiTextShadow;
import club.heiqi.uilib.ui.style.values.UiSurfaceStyle;
import club.heiqi.uilib.ui.style.values.UiTransform;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * `DocumentPaintRenderer` 的渲染投影契约测试。
 */
public class DocumentPaintRendererTest {

    /**
     * 验证背景和多像素边框会按 paint command 顺序投影到 `UiRenderContext`。
     */
    @Test
    public void shouldRenderPaintCommandsToUiRenderContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xAA101820)
                .setBorderColor(0xFF86A8F0)
                .setBorderWidth(UiStyleLength.px(2))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 80, 0)));

        Assert.assertEquals(3, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 0, 0, 44, 24, 0xAA101820, 0, 8);
        assertDrawCall(renderContext.drawCalls.get(1), 0, 0, 44, 24, 0, 0xFF86A8F0, 8);
        assertDrawCall(renderContext.drawCalls.get(2), 1, 1, 43, 23, 0, 0xFF86A8F0, 7);
    }

    /**
     * 验证 border-style:none 不会被 renderer 强制当作 solid 绘制。
     */
    @Test
    public void shouldSkipBorderRenderingWhenBorderStyleIsNone() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style().setBorderStyle(UiBorderStyle.NONE);
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BORDER, root, 0, 0, 40, 20,
                0xFF86A8F0, 2, UiBorderRadiusResolver.ResolvedCornerRadii.uniform(8)));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, commands);

        Assert.assertTrue(renderContext.drawCalls.isEmpty());
    }

    /**
     * 验证背景命令会把左上为 0 的分角圆角完整传给底层 surface。
     */
    @Test
    public void shouldRenderBackgroundCornerRadiiToUiRenderContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF223344)
                .setBorderRadiusCorners(UiBorderRadius.of(UiStyleLength.px(0), UiStyleLength.px(9),
                        UiStyleLength.px(4), UiStyleLength.px(0)));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 80, 0)), 7, 11);

        Assert.assertEquals(1, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 7, 11, 47, 31, 0xFF223344, 0, 0, 9, 4, 0);
    }

    /**
     * 验证 outline 会按 offset、width、color 和外扩圆角绘制逐层圆角轮廓。
     */
    @Test
    public void shouldRenderRoundedOutlineWithOffsetWidthColor() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBorderRadius(UiStyleLength.px(8))
                .setOutline(UiOutline.of(2, 0xFF67E8F9, UiBorderStyle.SOLID, 1));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 80, 0)));

        Assert.assertEquals(2, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), -3, -3, 43, 23, 0, 0xFF67E8F9, 11);
        assertDrawCall(renderContext.drawCalls.get(1), -2, -2, 42, 22, 0, 0xFF67E8F9, 10);
    }

    /**
     * 验证 outline 命令会把分角圆角按外扩轮廓传给 renderer。
     */
    @Test
    public void shouldRenderOutlineCornerRadiiToUiRenderContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBorderRadiusCorners(UiBorderRadius.of(UiStyleLength.px(0), UiStyleLength.px(9),
                        UiStyleLength.px(4), UiStyleLength.px(0)))
                .setOutline(UiOutline.of(1, 0xFF67E8F9, UiBorderStyle.SOLID, 0));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 80, 0)));

        Assert.assertEquals(1, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), -1, -1, 41, 21, 0, 0xFF67E8F9, 1, 10, 5, 1);
    }

    /**
     * 验证 BORDER 命令携带的局部圆角掩码不会在 renderer 回放时丢失。
     */
    @Test
    public void shouldRenderBorderCornerMaskToUiRenderContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style().setBorderStyle(UiBorderStyle.SOLID);
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BORDER, root, 0, 0, 30, 14,
                0xFF86A8F0, 1, UiBorderRadiusResolver.ResolvedCornerRadii.uniform(6),
                UiSurfaceStyle.CORNER_TOP_LEFT | UiSurfaceStyle.CORNER_BOTTOM_LEFT,
                null, null, 0, 1.0F, 1.0F, null));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, commands, 7, 11);

        Assert.assertEquals(1, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 7, 11, 37, 25, 0, 0xFF86A8F0, 6,
                UiSurfaceStyle.CORNER_TOP_LEFT | UiSurfaceStyle.CORNER_BOTTOM_LEFT);
    }

    /**
     * 验证 border-collapse 下表头和表体交界只保留最后一行语义，不会把 section 边界当成独立终止行。
     */
    @Test
    public void shouldTreatTableSectionBoundaryAsSharedLastRowOnlyForFinalRow() throws Exception {
        UiDocument document = UiDocument.create();
        ElementNode table = document.table();
        ElementNode thead = document.thead();
        ElementNode tbody = document.tbody();
        ElementNode headRow = document.tr();
        ElementNode bodyRow = document.tr();
        ElementNode headCell = document.th();
        ElementNode bodyCell = document.td();

        table.style().setBorderCollapse(club.heiqi.uilib.ui.style.props.UiBorderCollapse.COLLAPSE);
        headCell.appendText("H");
        bodyCell.appendText("B");
        headRow.append(headCell);
        bodyRow.append(bodyCell);
        thead.append(headRow);
        tbody.append(bodyRow);
        table.append(thead).append(tbody);

        Method isLastTableRow = DocumentPaintRenderer.class.getDeclaredMethod("isLastTableRow", ElementNode.class);
        isLastTableRow.setAccessible(true);

        Assert.assertFalse((Boolean) isLastTableRow.invoke(null, headCell));
        Assert.assertTrue((Boolean) isLastTableRow.invoke(null, bodyCell));
    }

    /**
     * 验证 box-shadow 与 outline 命令会投影出精确的颜色、位置与圆角。
     */
    @Test
    public void shouldRenderBoxShadowAndOutlineCommands() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF223344)
                .setBorderRadius(UiStyleLength.px(8))
                .setBoxShadow(UiBoxShadow.of(4, 4, 2, 1, 0x6638BDF8))
                .setOutline(UiOutline.of(2, 0xFF67E8F9, UiBorderStyle.SOLID, 1));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 80, 0)));

        Assert.assertEquals(6, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 1, 1, 47, 27, 0x6638BDF8, 0, 11);
        assertDrawCall(renderContext.drawCalls.get(1), 2, 2, 46, 26, 0x4438BDF8, 0, 10);
        assertDrawCall(renderContext.drawCalls.get(2), 3, 3, 45, 25, 0x2238BDF8, 0, 9);
        assertDrawCall(renderContext.drawCalls.get(3), 0, 0, 40, 20, 0xFF223344, 0, 8);
        assertDrawCall(renderContext.drawCalls.get(4), -3, -3, 43, 23, 0, 0xFF67E8F9, 11);
        assertDrawCall(renderContext.drawCalls.get(5), -2, -2, 42, 22, 0, 0xFF67E8F9, 10);
    }

    /**
     * 验证 box-shadow 使用 paint engine 已经应用 opacity 后的命令颜色。
     */
    @Test
    public void shouldRenderBoxShadowWithCommandOpacityColor() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10))
                .setOpacity(0.5F)
                .setBoxShadow(UiBoxShadow.of(0, 0, 1, 0, 0x8038BDF8));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 80, 0)));

        Assert.assertEquals(2, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), -1, -1, 21, 11, 0x4038BDF8, 0, 1);
        assertDrawCall(renderContext.drawCalls.get(1), 0, 0, 20, 10, 0x2038BDF8, 0, 0);
    }

    /**
     * 验证 renderer 会优先使用命令携带的 box-shadow 值，而不是重新读取 style。
     */
    @Test
    public void shouldRenderBoxShadowFromCommandPayload() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));

        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BOX_SHADOW, root, 0, 0, 40, 20,
                0xFF112233, 0, UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0),
                UiBoxShadow.of(5, 6, 0, 0xFF112233)));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, commands);

        Assert.assertEquals(2, renderContext.drawCalls.size());
        Assert.assertEquals(5, renderContext.drawCalls.get(1).left);
        Assert.assertEquals(6, renderContext.drawCalls.get(1).top);
        Assert.assertEquals(45, renderContext.drawCalls.get(1).right);
        Assert.assertEquals(26, renderContext.drawCalls.get(1).bottom);
    }

    /**
     * 验证 inset box-shadow 使用分角圆角计算内层轮廓，而不是退回旧单值半径。
     */
    @Test
    public void shouldRenderInsetBoxShadowWithCornerRadii() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20))
                .setBorderRadiusCorners(UiBorderRadius.of(UiStyleLength.px(0), UiStyleLength.px(9),
                        UiStyleLength.px(4), UiStyleLength.px(0)))
                .setBoxShadow(UiBoxShadow.inset(0, 0, 1, 0, 0x8038BDF8));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 80, 0)));

        Assert.assertEquals(1, renderContext.clipCalls.size());
        assertClipCall(renderContext.clipCalls.get(0), 0, 0, 20, 20, 0, 9, 4, 0);
        Assert.assertEquals(1, renderContext.popClipCount);
        Assert.assertEquals(2, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 0, 0, 20, 20, 0, 0x8038BDF8, 0, 9, 4, 0);
        assertDrawCall(renderContext.drawCalls.get(1), 1, 1, 19, 19, 0, 0x4038BDF8, 0, 8, 3, 0);
    }

    /**
     * 验证空命令列表不会触发底层绘制。
     */
    @Test
    public void shouldIgnoreEmptyCommands() {
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();

        DocumentPaintRenderer.render(renderContext, null);
        DocumentPaintRenderer.render(renderContext, new ArrayList<DocumentPaintCommand>());

        Assert.assertTrue(renderContext.drawCalls.isEmpty());
    }

    /**
     * 验证绘制命令能按宿主 widget 的绝对位置整体偏移。
     */
    @Test
    public void shouldApplyRenderOffset() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(18))
                .setHeight(UiStyleLength.px(12))
                .setBackgroundColor(0xFF223344);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 40, 0)), 7, 11);

        Assert.assertEquals(1, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 7, 11, 25, 23, 0xFF223344, 0, 0);
    }

    /**
     * 验证文本命令会把文本解析模式传给渲染上下文。
     */
    @Test
    public void shouldReplayTextContentModeToRenderContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT, root, 0, 0, 24, 12,
                0xFFE2E8F0, 0, 0, "§aRaw?", TextContentMode.MINECRAFT_FORMATTED, null, 0, 1.0F, 1.0F));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, commands, 7, 11);

        Assert.assertEquals(1, renderContext.textCalls.size());
        Assert.assertEquals(TextContentMode.MINECRAFT_FORMATTED, renderContext.textCalls.get(0).textContentMode);
    }

    /**
     * 验证 text-shadow 会在实际文本之前按自定义偏移和颜色绘制。
     */
    @Test
    public void shouldRenderTextShadowBeforeText() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(40))
                .setTextShadow(UiTextShadow.of(2, 1, 0, 0xAA000000));
        root.appendText("Hi");

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 80, 0)), 7, 11);

        Assert.assertEquals(2, renderContext.textCalls.size());
        Assert.assertEquals("Hi", renderContext.textCalls.get(0).text);
        Assert.assertEquals(9, renderContext.textCalls.get(0).x);
        Assert.assertEquals(12, renderContext.textCalls.get(0).y);
        Assert.assertEquals(0xAA000000, renderContext.textCalls.get(0).color);
        Assert.assertEquals("Hi", renderContext.textCalls.get(1).text);
        Assert.assertEquals(7, renderContext.textCalls.get(1).x);
        Assert.assertEquals(11, renderContext.textCalls.get(1).y);
        Assert.assertEquals(0xFFFFFFFF, renderContext.textCalls.get(1).color);
    }

    /**
     * 验证 background-image 样式会在元素圆角裁剪内绘制宿主图片。
     */
    @Test
    public void shouldRenderBackgroundImageCommandToHostImage() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HostImageSource source = HostImageSource.texture(new ResourceLocation("qz_uilib", "textures/test/card.png"),
                64, 64);

        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBorderRadius(UiStyleLength.px(6))
                .setBackgroundImage(UiBackgroundImage.of(source));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 80, 0)), 7, 11);

        Assert.assertEquals(1, renderContext.clipCalls.size());
        assertClipCall(renderContext.clipCalls.get(0), 7, 11, 47, 31, 6);
        Assert.assertEquals(1, renderContext.hostImageCalls.size());
        HostImageCall hostImageCall = renderContext.hostImageCalls.get(0);
        Assert.assertSame(source, hostImageCall.source);
        Assert.assertEquals(7, hostImageCall.left);
        Assert.assertEquals(11, hostImageCall.top);
        Assert.assertEquals(47, hostImageCall.right);
        Assert.assertEquals(31, hostImageCall.bottom);
        Assert.assertEquals(1, renderContext.popClipCount);
    }

    /**
     * 验证普通字体样式也经由最终绘制入口处理，不会在 `drawText` 重载之间递归。
     */
    @Test
    public void shouldReplayNormalFontStyleWithoutRecursiveTextOverload() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT, root, 0, 0, 24, 12,
                0xFFE2E8F0, 0, 0, "Normal", TextContentMode.UILIB_RAW,
                UiFontWeight.NORMAL, UiFontStyle.NORMAL, null, 0, 1.0F, 1.0F));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, commands, 7, 11);

        Assert.assertEquals(1, renderContext.textCalls.size());
        Assert.assertEquals("Normal", renderContext.textCalls.get(0).text);
        Assert.assertEquals(UiFontWeight.NORMAL, renderContext.textCalls.get(0).fontWeight);
        Assert.assertEquals(UiFontStyle.NORMAL, renderContext.textCalls.get(0).fontStyle);
    }

    /**
     * 验证 surface 命令会把局部圆角掩码投影到底层表面样式。
     */
    @Test
    public void shouldRenderSurfaceCornerMaskToUiRenderContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKGROUND, root, 0, 0, 30, 14,
                0xFF223344, 0, 6, UiSurfaceStyle.CORNER_TOP_LEFT | UiSurfaceStyle.CORNER_BOTTOM_LEFT,
                null, null, 0, 1.0F, 1.0F, null));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, commands, 7, 11);

        Assert.assertEquals(1, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 7, 11, 37, 25, 0xFF223344, 0, 6,
                UiSurfaceStyle.CORNER_TOP_LEFT | UiSurfaceStyle.CORNER_BOTTOM_LEFT);
    }

    /**
     * 验证 BACKDROP_FILTER 命令会按宿主偏移投影到渲染上下文的效果入口。
     */
    @Test
    public void shouldRenderBackdropFilterCommandsToUiRenderContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(48))
                .setHeight(UiStyleLength.px(18))
                .setBorderRadius(UiStyleLength.px(7))
                .setBackdropBlurRadius(UiStyleLength.px(12))
                .setBackdropSaturation(1.25F);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 80, 0)), 7, 11);

        Assert.assertEquals(1, renderContext.backdropCalls.size());
        assertBackdropCall(renderContext.backdropCalls.get(0), 7, 11, 55, 29, 12, 1.25F, 7);
    }

    /**
     * 验证 backdrop-filter 会完整携带分角圆角。
     */
    @Test
    public void shouldRenderBackdropFilterCornerRadiiToUiRenderContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(48))
                .setHeight(UiStyleLength.px(18))
                .setBorderRadiusCorners(UiBorderRadius.of(UiStyleLength.px(0), UiStyleLength.px(9),
                        UiStyleLength.px(4), UiStyleLength.px(0)))
                .setBackdropBlurRadius(UiStyleLength.px(12))
                .setBackdropSaturation(1.25F);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 80, 0)), 7, 11);

        Assert.assertEquals(1, renderContext.backdropCalls.size());
        assertBackdropCall(renderContext.backdropCalls.get(0), 7, 11, 55, 29, 12, 1.25F, 0, 9, 4, 0);
    }

    /**
     * 验证连续 backdrop 之间如果发生绘制写入，后一个 backdrop 会看到新的内容版本。
     */
    @Test
    public void shouldAdvanceContentRevisionBetweenBackdropCommands() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKGROUND, root, 0, 0, 40, 20,
                0xFF223344, 0, 0));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKDROP_FILTER, root, 0, 0, 40, 20,
                0, 0, 0, "", null, 12, 1.1F));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKGROUND, root, 0, 20, 40, 40,
                0xFF556677, 0, 0));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKDROP_FILTER, root, 0, 20, 40, 40,
                0, 0, 0, "", null, 12, 1.1F));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, commands);

        Assert.assertEquals(2, renderContext.backdropCalls.size());
        Assert.assertEquals(1, renderContext.backdropCalls.get(0).contentRevision);
        Assert.assertEquals(3, renderContext.backdropCalls.get(1).contentRevision);
    }

    /**
     * 验证 effect command 会携带可供 renderer 运行时 pass 使用的显式效果类型。
     */
    @Test
    public void shouldExposeEffectTypesForRuntimePasses() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        DocumentPaintCommand paintContextStart = new DocumentPaintCommand(
                DocumentPaintCommandType.PAINT_CONTEXT_START, root, 0, 0, 10, 10, 0, 0, 0,
                null, null, 0, 1.0F, 0.5F, DocumentEffectType.PAINT_CONTEXT);
        DocumentPaintCommand backdrop = new DocumentPaintCommand(DocumentPaintCommandType.BACKDROP_FILTER, root,
                0, 0, 10, 10, 0, 0, 0, "", null, 8, 1.2F);
        DocumentPaintCommand clipStart = new DocumentPaintCommand(DocumentPaintCommandType.CLIP_START, root,
                0, 0, 10, 10, 0, 0, 0);
        DocumentPaintCommand clipEnd = new DocumentPaintCommand(DocumentPaintCommandType.CLIP_END, root,
                0, 0, 10, 10, 0, 0, 0);

        Assert.assertEquals(DocumentEffectType.PAINT_CONTEXT, paintContextStart.getEffectType());
        Assert.assertEquals(DocumentEffectType.BACKDROP_FILTER, backdrop.getEffectType());
        Assert.assertEquals(DocumentEffectType.OVERFLOW_CLIP, clipStart.getEffectType());
        Assert.assertEquals(DocumentEffectType.OVERFLOW_CLIP, clipEnd.getEffectType());
    }

    /**
     * 验证 renderer 会按显式 effect 类型清理未闭合的运行时 pass。
     */
    @Test
    public void shouldCleanupOpenRuntimeEffectPasses() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.PAINT_CONTEXT_START, root,
                0, 0, 30, 20, 0, 0, 0, null, null, 0, 1.0F, 0.5F, DocumentEffectType.PAINT_CONTEXT));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.CLIP_START, root,
                0, 0, 30, 20, 0, 0, 0));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, commands);

        Assert.assertEquals(1, renderContext.popClipCount);
        Assert.assertEquals(1, renderContext.popPaintContextCount);
    }

    /**
     * 验证 overflow clip 命令会按宿主偏移投影到 `UiRenderContext` 的裁剪栈。
     */
    @Test
    public void shouldReplayOverflowClipCommands() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(30))
                .setHeight(UiStyleLength.px(12))
                .setBorderWidth(UiStyleLength.px(2))
                .setBorderRadius(UiStyleLength.px(5))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        child.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(8))
                .setBackgroundColor(0xFF556677);
        root.append(child);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 60, 0)), 10, 20);

        Assert.assertEquals(1, renderContext.clipCalls.size());
        assertClipCall(renderContext.clipCalls.get(0), 12, 22, 42, 34, 5);
        Assert.assertEquals(1, renderContext.popClipCount);
        Assert.assertEquals(1, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 12, 22, 92, 30, 0xFF556677, 0, 0);
    }

    /**
     * 验证左上角为 0、其他角有值时 overflow clip 不会被误判为无圆角。
     */
    @Test
    public void shouldReplayOverflowClipCornerRadiiToUiRenderContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(30))
                .setHeight(UiStyleLength.px(20))
                .setBorderRadiusCorners(UiBorderRadius.of(UiStyleLength.px(0), UiStyleLength.px(9),
                        UiStyleLength.px(4), UiStyleLength.px(0)))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        child.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(8))
                .setBackgroundColor(0xFF556677);
        root.append(child);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 60, 0)), 10, 20);

        Assert.assertEquals(1, renderContext.clipCalls.size());
        assertClipCall(renderContext.clipCalls.get(0), 10, 20, 40, 40, 0, 9, 4, 0);
        Assert.assertEquals(1, renderContext.popClipCount);
        Assert.assertEquals(1, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 10, 20, 90, 28, 0xFF556677, 0, 0);
    }

    /**
     * 验证 clip 快照检查四个角，而不是只看左上角统一半径。
     */
    @Test
    public void shouldKeepRoundedClipSnapshotWhenTopLeftRadiusIsZero() throws Exception {
        UiRenderContext context = new UiRenderContext(320, 240, 0, 0, 0.0F);
        setClipStackForTest(context, 0, 0, 40, 20,
                UiBorderRadiusResolver.ResolvedCornerRadii.of(0, 9, 4, 0));

        Method copyCurrentClipSnapshot = UiRenderContext.class.getDeclaredMethod("copyCurrentClipSnapshot");
        copyCurrentClipSnapshot.setAccessible(true);
        ClipSnapshot clipSnapshot = (ClipSnapshot) copyCurrentClipSnapshot.invoke(context);

        Assert.assertNotNull(clipSnapshot);
        Assert.assertEquals(1, clipSnapshot.getRoundedClipRegions().size());
        RoundedClipRegion clipRegion = clipSnapshot.getRoundedClipRegions().get(0);
        Assert.assertEquals(0, clipRegion.getLeft());
        Assert.assertEquals(0, clipRegion.getTop());
        Assert.assertEquals(40, clipRegion.getRight());
        Assert.assertEquals(20, clipRegion.getBottom());
        assertCornerRadii(clipRegion.getCornerRadii(), 0, 9, 4, 0);
    }

    /**
     * 验证 TEXT 命令会按宿主偏移投影到 `UiRenderContext.drawText`。
     */
    @Test
    public void shouldRenderTextCommandsToUiRenderContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(100))
                .setPadding(UiStyleLength.px(3))
                .setTextColor(0xFFEFF6FF);
        root.appendText("Text");

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 120, 0)), 7, 11);

        Assert.assertEquals(1, renderContext.textCalls.size());
        assertTextCall(renderContext.textCalls.get(0), "Text", 10, 14, 0xFFEFF6FF, false);
    }

    /**
     * 验证 TEXT_DECORATION 命令会投影为一条普通 surface。
     */
    @Test
    public void shouldRenderTextDecorationCommandToUiRenderContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT_DECORATION, root, 5, 9, 29, 10,
                0xFFEFF6FF, 0, 0));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, commands, 7, 11);

        Assert.assertEquals(1, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 12, 20, 36, 21, 0xFFEFF6FF, 0, 0);
    }

    /**
     * 验证 CUSTOM 命令会按宿主偏移投影到自定义绘制回调。
     */
    @Test
    public void shouldRenderCustomCommandsToUiRenderContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<CustomCall> customCalls = new ArrayList<CustomCall>();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setPadding(UiStyleLength.px(3));
        root.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                customCalls.add(new CustomCall(contentLeft, contentTop, contentRight, contentBottom));
            }
        });

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 120, 0)), 7, 11);

        Assert.assertEquals(1, customCalls.size());
        assertCustomCall(customCalls.get(0), 10, 14, 50, 34);
        Assert.assertEquals(1, renderContext.getMainLayerContentRevisionForDiagnostics());
    }

    /**
     * 验证 CUSTOM 命令嵌在 overflow clip 内时：固化边界经 surface 可读并对齐布局盒几何，且回放后 clip 栈成对平衡。
     *
     * <p>治本层把视口/内容/图层文档坐标边界在构建期固化进 CUSTOM 命令，回放期渲染器经
     * {@code DocumentCustomRenderSurface.boundsOf()} 读取，不再调 {@code element.getDocumentBounds()}
     * 推进动画时间线。本测试守护「固化边界对齐布局盒几何」与「固化不破坏 clip/transform 栈配对」；
     * 「固化与挂载运行时实时查询等价」由 DocumentTextAreaControlTest / DocumentCodeEditorControlTest
     * 基于真实 widget 的 caret/选区像素断言守护。</p>
     */
    @Test
    public void shouldExposeBakedBoundsToCustomSurfaceWithBalancedClipStack() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode viewport = document.div();
        ElementNode layer = document.div();
        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(80));
        viewport.style()
                .setWidth(UiStyleLength.px(60))
                .setHeight(UiStyleLength.px(40))
                .setPadding(UiStyleLength.px(4))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        layer.style().setWidth(UiStyleLength.px(200)).setHeight(UiStyleLength.px(200));
        viewport.append(layer);
        root.append(viewport);

        final List<DocumentElementBounds> viewportBoundsSeen = new ArrayList<DocumentElementBounds>();
        layer.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                throw new AssertionError("回放期应走 surface 入口，而非 5 参 render");
            }

            @Override
            public void render(DocumentCustomRenderSurface surface) {
                viewportBoundsSeen.add(surface.boundsOf(viewport));
            }
        });

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 160, 0);
        DocumentLayoutBox viewportBox = rootBox.getChildren().get(0);
        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(rootBox);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, commands);

        Assert.assertEquals(1, viewportBoundsSeen.size());
        DocumentElementBounds baked = viewportBoundsSeen.get(0);
        Assert.assertTrue("固化边界应可用", baked.isAvailable());
        // 固化边界与布局盒几何一致（根级 offset 为 0，文档坐标即盒局部坐标）。
        Assert.assertEquals(viewportBox.getContentLeft(), baked.getContentLeft());
        Assert.assertEquals(viewportBox.getContentTop(), baked.getContentTop());
        Assert.assertEquals(viewportBox.getContentWidth(), baked.getContentWidth());
        Assert.assertEquals(viewportBox.getContentHeight(), baked.getContentHeight());
        // overflow clip 包住 CUSTOM 命令，回放后 clip 栈成对平衡。
        Assert.assertTrue("应至少压入一次 clip", renderContext.clipCalls.size() >= 1);
        Assert.assertEquals(renderContext.clipCalls.size(), renderContext.popClipCount);
    }

    /**
     * 验证字体延迟提交边界只包住连续文本命令，不跨越自定义渲染器。
     */
    @Test
    public void shouldScopeDeferredFontFlushToTextRunsOnly() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<Boolean> customBatchStates = new ArrayList<Boolean>();
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT, root, 0, 0, 24, 12,
                0xFFE2E8F0, 0, 0, "First", TextContentMode.UILIB_RAW, null, 0, 1.0F, 1.0F));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT, root, 0, 12, 24, 24,
                0xFFE2E8F0, 0, 0, "Second", TextContentMode.UILIB_RAW, null, 0, 1.0F, 1.0F));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.CUSTOM, root, 0, 24, 24, 36,
                0, 0, 0, null, new DocumentCustomRenderer() {
                    @Override
                    public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                            int contentBottom) {
                        customBatchStates.add(Boolean.valueOf(((BatchingRecordingUiRenderContext) context)
                                .isDeferredTextBatchActiveForTest()));
                    }
                }));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT, root, 0, 36, 24, 48,
                0xFFE2E8F0, 0, 0, "Third", TextContentMode.UILIB_RAW, null, 0, 1.0F, 1.0F));

        BatchingRecordingUiRenderContext renderContext = new BatchingRecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, commands);

        Assert.assertEquals(3, renderContext.textCalls.size());
        Assert.assertEquals("First", renderContext.textCalls.get(0).text);
        Assert.assertEquals("Second", renderContext.textCalls.get(1).text);
        Assert.assertEquals("Third", renderContext.textCalls.get(2).text);
        Assert.assertEquals(2, renderContext.beginDeferredTextBatchCount);
        Assert.assertEquals(2, renderContext.flushDeferredTextBatchCount);
        Assert.assertEquals(2, renderContext.endDeferredTextBatchCount);
        Assert.assertEquals(java.util.Collections.singletonList(Boolean.FALSE), customBatchStates);
    }

    /**
     * 必修 BUG 守护：{@code SCROLL_OFFSET_START} 命令的 left/top 必须携带「构建期 scroll 快照」。
     *
     * <p>delta 模型要求回放期 {@code delta = 构建期scroll - 当前scroll}。若 START 命令把 left/top 写成
     * 0/0（缺失构建期 scroll 项），delta 会退化为 {@code -当前scroll}，滚动后内容整体错位。本测试在构建期
     * scroll=12 时直接断言 START 命令携带的快照为 (0, 12)。</p>
     */
    @Test
    public void shouldCarryBuildScrollSnapshotInScrollOffsetStartCommand() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setOverflowY(UiOverflow.AUTO);
        child.style()
                .setHeight(UiStyleLength.px(80))
                .setBackgroundColor(0xFFAA5500);
        root.append(child);
        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);
        scrollState.setScrollOffset(root, 0, 12);

        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(rootBox, scrollState);

        DocumentPaintCommand startCommand = null;
        for (DocumentPaintCommand command : commands) {
            if (command.getType() == DocumentPaintCommandType.SCROLL_OFFSET_START) {
                startCommand = command;
                break;
            }
        }
        Assert.assertNotNull("应为可免重建滚动容器 emit SCROLL_OFFSET_START", startCommand);
        Assert.assertEquals(0, startCommand.getLeft());
        Assert.assertEquals(12, startCommand.getTop());
        Assert.assertSame(root, startCommand.getElement());
    }

    /**
     * 必修 BUG 守护：滚动偏移推迟到回放期叠加，结果应与「构建期直接烘焙到该滚动位置」逐像素一致。
     *
     * <p>用例 A：构建期 scroll=0，回放期 provider 报告当前 scroll=12（delta=-12）。用例 B：构建期直接
     * scroll=12，NONE provider（现状行为）。两者的 flow content 屏幕坐标必须完全相同，证明 delta 模型
     * 在「免重建滚动」下逐像素等价现状。scrollbar thumb 跟手由阶段4单独守护，本测试只比对内容背景。</p>
     */
    @Test
    public void shouldDeferScrollOffsetToReplayPixelIdentical() {
        DocumentPaintRenderer.ScrollOffsetProvider scrollTo12 = new DocumentPaintRenderer.ScrollOffsetProvider() {
            @Override
            public int getScrollLeft(ElementNode element) {
                return 0;
            }

            @Override
            public int getScrollTop(ElementNode element) {
                return 12;
            }
        };

        DrawCall groundTruth = renderScrollChildBackground(12, DocumentPaintRenderer.ScrollOffsetProvider.NONE);
        DrawCall deferred = renderScrollChildBackground(0, scrollTo12);

        Assert.assertEquals(groundTruth.left, deferred.left);
        Assert.assertEquals(groundTruth.top, deferred.top);
        Assert.assertEquals(groundTruth.right, deferred.right);
        Assert.assertEquals(groundTruth.bottom, deferred.bottom);
    }

    /**
     * 构建一个 overflow-y:auto 滚动容器并取其子内容背景的回放绘制记录。
     *
     * @param buildScrollTop 构建期纵向滚动偏移（写入 scrollState）
     * @param provider 回放期滚动偏移源
     * @return 子内容背景的绘制记录
     */
    private static DrawCall renderScrollChildBackground(int buildScrollTop,
            DocumentPaintRenderer.ScrollOffsetProvider provider) {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setOverflowY(UiOverflow.AUTO);
        child.style()
                .setHeight(UiStyleLength.px(80))
                .setBackgroundColor(0xFFAA5500);
        root.append(child);
        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);
        scrollState.setScrollOffset(root, 0, buildScrollTop);
        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(rootBox, scrollState);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, commands, 7, 11, provider);
        for (DrawCall drawCall : renderContext.drawCalls) {
            if (drawCall.surfaceStyle.fillColor == 0xFFAA5500) {
                return drawCall;
            }
        }
        throw new AssertionError("未找到子内容背景绘制记录");
    }

    /**
     * 阶段4 守护：scrollbar thumb 回放期跟手。免重建滚动启用时，构建期 scroll=0 的 thumb 命令应按回放期实时
     * 滚动偏移在主轴上重算位置，结果与「构建期直接烘焙到该滚动位置」逐像素一致；track 是视口框、不随滚动移动。
     *
     * <p>用例 A：构建期 scroll=0，provider 报告当前滚到底（scrollTop=maxScrollTop=60，delta 模型下 thumb 跟手到
     * 轨道底部）。用例 B：构建期直接 scroll=60，NONE provider（现状）。两者 thumb 绘制矩形必须完全相同。</p>
     */
    @Test
    public void shouldReplayScrollbarThumbFollowingLiveScroll() {
        DocumentPaintRenderer.ScrollOffsetProvider scrollToBottom = new DocumentPaintRenderer.ScrollOffsetProvider() {
            @Override
            public int getScrollLeft(ElementNode element) {
                return 0;
            }

            @Override
            public int getScrollTop(ElementNode element) {
                return 600;
            }
        };

        DrawCall groundTruthThumb = renderScrollThumb(600, DocumentPaintRenderer.ScrollOffsetProvider.NONE);
        DrawCall followedThumb = renderScrollThumb(0, scrollToBottom);

        Assert.assertEquals(groundTruthThumb.left, followedThumb.left);
        Assert.assertEquals(groundTruthThumb.top, followedThumb.top);
        Assert.assertEquals(groundTruthThumb.right, followedThumb.right);
        Assert.assertEquals(groundTruthThumb.bottom, followedThumb.bottom);
        // 守护跟手确实改变了 thumb 主轴位置：滚到底时 thumb 顶部应明显低于构建期 scroll=0 的顶部。
        DrawCall topThumb = renderScrollThumb(0, DocumentPaintRenderer.ScrollOffsetProvider.NONE);
        Assert.assertTrue("滚到底的 thumb 应位于 scroll=0 thumb 下方", followedThumb.top > topThumb.top);
    }

    /**
     * 构建一个 overflow-y:auto 滚动容器并取其 scrollbar thumb 的回放绘制记录。
     *
     * @param buildScrollTop 构建期纵向滚动偏移（写入 scrollState）
     * @param provider 回放期滚动偏移源
     * @return thumb 的绘制记录
     */
    private static DrawCall renderScrollThumb(int buildScrollTop,
            DocumentPaintRenderer.ScrollOffsetProvider provider) {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(200))
                .setOverflowY(UiOverflow.AUTO);
        child.style()
                .setHeight(UiStyleLength.px(800));
        root.append(child);
        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);
        scrollState.setScrollOffset(root, 0, buildScrollTop);
        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(rootBox, scrollState);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, commands, 7, 11, provider);
        // thumb 是最后一条滚动条 surface（thumbColor），track 在其之前。
        DrawCall thumb = null;
        for (DrawCall drawCall : renderContext.drawCalls) {
            if (drawCall.surfaceStyle.fillColor == 0xDDBCD7FF) {
                thumb = drawCall;
            }
        }
        if (thumb == null) {
            throw new AssertionError("未找到 scrollbar thumb 绘制记录");
        }
        return thumb;
    }

    /**
     * 验证 HTML-like 滚动条命令会投影为普通 surface 绘制。
     */
    @Test
    public void shouldRenderScrollbarCommandsToUiRenderContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setOverflowY(UiOverflow.AUTO);
        child.style()
                .setHeight(UiStyleLength.px(80));
        root.append(child);
        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);
        scrollState.setScrollOffset(root, 0, 12);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(rootBox, scrollState), 7,
                11);

        Assert.assertEquals(2, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 49, 13, 55, 29, 0x663B4A66, 0, 3);
        assertDrawCall(renderContext.drawCalls.get(1), 49, 13, 55, 29, 0xDDBCD7FF, 0, 3);
    }

    /**
     * 验证绘制上下文边界会按宿主偏移投影到 `UiRenderContext`。
     */
    @Test
    public void shouldReplayPaintContextCommands() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style().setWidth(UiStyleLength.px(80));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(0.5F)
                .setBackgroundColor(0xFF223344);
        root.append(child);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 100, 0)), 7, 11);

        Assert.assertEquals(1, renderContext.paintContextCalls.size());
        assertPaintContextCall(renderContext.paintContextCalls.get(0), 7, 11, 47, 31, 0.5F);
        Assert.assertEquals(1, renderContext.popPaintContextCount);
        Assert.assertEquals(1, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 7, 11, 47, 31, 0x80223344, 0, 0);
    }

    /**
     * 验证 transform 命令会按宿主偏移投影到 `UiRenderContext` 矩阵栈。
     */
    @Test
    public void shouldReplayTransformCommands() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TRANSFORM_START, root, 2, 3, 42, 23,
                UiTransform.of(8.0F, 4.0F, 1.5F, 0.75F, 12.0F)));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKGROUND, root, 2, 3, 42, 23,
                0xFF223344, 0, 0));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TRANSFORM_END, root, 2, 3, 42, 23,
                UiTransform.of(8.0F, 4.0F, 1.5F, 0.75F, 12.0F)));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, commands, 7, 11);

        Assert.assertEquals(1, renderContext.transformCalls.size());
        TransformCall transformCall = renderContext.transformCalls.get(0);
        Assert.assertEquals(9, transformCall.left);
        Assert.assertEquals(14, transformCall.top);
        Assert.assertEquals(49, transformCall.right);
        Assert.assertEquals(34, transformCall.bottom);
        Assert.assertEquals(8.0F, transformCall.transform.getTranslateX(), 0.0F);
        Assert.assertEquals(1, renderContext.popTransformCount);
        Assert.assertEquals(1, renderContext.drawCalls.size());
    }

    /**
     * 验证 transform 内文本不进入延迟字体批处理，避免批次 flush 时绕过父元素矩阵。
     */
    @Test
    public void shouldRenderTransformedTextWithoutDeferredBatching() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TRANSFORM_START, root, 2, 3, 42, 23,
                UiTransform.of(8.0F, 4.0F, 1.5F, 0.75F, 12.0F)));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT, root, 4, 5, 28, 17,
                0xFFE2E8F0, 0, 0, "Inner", TextContentMode.UILIB_RAW, null, 0, 1.0F, 1.0F));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT, root, 4, 17, 28, 29,
                0xFFE2E8F0, 0, 0, "Text", TextContentMode.UILIB_RAW, null, 0, 1.0F, 1.0F));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TRANSFORM_END, root, 2, 3, 42, 23,
                UiTransform.of(8.0F, 4.0F, 1.5F, 0.75F, 12.0F)));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT, root, 0, 30, 24, 42,
                0xFFE2E8F0, 0, 0, "Outer", TextContentMode.UILIB_RAW, null, 0, 1.0F, 1.0F));

        BatchingRecordingUiRenderContext renderContext = new BatchingRecordingUiRenderContext();
        DocumentPaintRenderer.render(renderContext, commands, 7, 11);

        Assert.assertEquals(3, renderContext.textCalls.size());
        assertTextCall(renderContext.textCalls.get(0), "Inner", 11, 16, 0xFFE2E8F0, false);
        assertTextCall(renderContext.textCalls.get(1), "Text", 11, 28, 0xFFE2E8F0, false);
        assertTextCall(renderContext.textCalls.get(2), "Outer", 7, 41, 0xFFE2E8F0, false);
        Assert.assertEquals(1, renderContext.beginDeferredTextBatchCount);
        Assert.assertEquals(1, renderContext.flushDeferredTextBatchCount);
        Assert.assertEquals(1, renderContext.endDeferredTextBatchCount);
        Assert.assertEquals(1, ((RecordingUiRenderContext) renderContext).transformCalls.size());
        Assert.assertEquals(1, ((RecordingUiRenderContext) renderContext).popTransformCount);
    }

    /**
     * 验证真实离屏 paint context 激活时，标准颜色命令不再被 renderer 额外乘以 context opacity。
     */
    @Test
    public void shouldKeepPaintColorsUnchangedWhenPaintContextLayerIsActive() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style().setWidth(UiStyleLength.px(80));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(0.5F)
                .setBackgroundColor(0xFF223344);
        root.append(child);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        renderContext.simulatePaintContextLayer = true;
        DocumentPaintRenderer.render(renderContext, DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 100, 0)), 7, 11);

        Assert.assertEquals(1, renderContext.paintContextCalls.size());
        Assert.assertEquals(1, renderContext.popPaintContextCount);
        Assert.assertEquals(1, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 7, 11, 47, 31, 0xFF223344, 0, 0);
    }

    private static void assertDrawCall(DrawCall drawCall, int left, int top, int right, int bottom, int fillColor,
            int borderColor, int cornerRadius) {
        assertDrawCall(drawCall, left, top, right, bottom, fillColor, borderColor, cornerRadius, cornerRadius,
                cornerRadius, cornerRadius,
                UiSurfaceStyle.CORNER_ALL);
    }

    private static void assertDrawCall(DrawCall drawCall, int left, int top, int right, int bottom, int fillColor,
            int borderColor, int cornerRadius, int cornerMask) {
        assertDrawCall(drawCall, left, top, right, bottom, fillColor, borderColor, cornerRadius, cornerRadius,
                cornerRadius, cornerRadius, cornerMask);
    }

    private static void assertDrawCall(DrawCall drawCall, int left, int top, int right, int bottom, int fillColor,
            int borderColor, int topLeft, int topRight, int bottomRight, int bottomLeft) {
        assertDrawCall(drawCall, left, top, right, bottom, fillColor, borderColor, topLeft, topRight, bottomRight,
                bottomLeft, UiSurfaceStyle.CORNER_ALL);
    }

    private static void assertDrawCall(DrawCall drawCall, int left, int top, int right, int bottom, int fillColor,
            int borderColor, int topLeft, int topRight, int bottomRight, int bottomLeft, int cornerMask) {
        Assert.assertEquals(left, drawCall.left);
        Assert.assertEquals(top, drawCall.top);
        Assert.assertEquals(right, drawCall.right);
        Assert.assertEquals(bottom, drawCall.bottom);
        Assert.assertEquals(fillColor, drawCall.surfaceStyle.fillColor);
        Assert.assertEquals(borderColor, drawCall.surfaceStyle.borderColor);
        assertCornerRadii(drawCall.surfaceStyle.cornerRadii, topLeft, topRight, bottomRight, bottomLeft);
        Assert.assertEquals(cornerMask, drawCall.surfaceStyle.cornerMask);
    }

    private static void assertClipCall(ClipCall clipCall, int left, int top, int right, int bottom, int cornerRadius) {
        assertClipCall(clipCall, left, top, right, bottom, cornerRadius, cornerRadius, cornerRadius, cornerRadius);
    }

    private static void assertClipCall(ClipCall clipCall, int left, int top, int right, int bottom, int topLeft,
            int topRight, int bottomRight, int bottomLeft) {
        Assert.assertEquals(left, clipCall.left);
        Assert.assertEquals(top, clipCall.top);
        Assert.assertEquals(right, clipCall.right);
        Assert.assertEquals(bottom, clipCall.bottom);
        assertCornerRadii(clipCall.cornerRadii, topLeft, topRight, bottomRight, bottomLeft);
    }

    private static void assertTextCall(TextCall textCall, String text, int x, int y, int color, boolean shadow) {
        Assert.assertEquals(text, textCall.text);
        Assert.assertEquals(x, textCall.x);
        Assert.assertEquals(y, textCall.y);
        Assert.assertEquals(color, textCall.color);
        Assert.assertEquals(shadow, textCall.shadow);
    }

    private static void assertCustomCall(CustomCall customCall, int left, int top, int right, int bottom) {
        Assert.assertEquals(left, customCall.left);
        Assert.assertEquals(top, customCall.top);
        Assert.assertEquals(right, customCall.right);
        Assert.assertEquals(bottom, customCall.bottom);
    }

    private static void assertBackdropCall(BackdropCall backdropCall, int left, int top, int right, int bottom,
            int blurRadius, float saturation, int cornerRadius) {
        assertBackdropCall(backdropCall, left, top, right, bottom, blurRadius, saturation, cornerRadius, cornerRadius,
                cornerRadius, cornerRadius);
    }

    private static void assertBackdropCall(BackdropCall backdropCall, int left, int top, int right, int bottom,
            int blurRadius, float saturation, int topLeft, int topRight, int bottomRight, int bottomLeft) {
        Assert.assertEquals(left, backdropCall.left);
        Assert.assertEquals(top, backdropCall.top);
        Assert.assertEquals(right, backdropCall.right);
        Assert.assertEquals(bottom, backdropCall.bottom);
        Assert.assertEquals(blurRadius, backdropCall.blurRadius);
        Assert.assertEquals(saturation, backdropCall.saturation, 0.0F);
        assertCornerRadii(backdropCall.cornerRadii, topLeft, topRight, bottomRight, bottomLeft);
    }

    private static void assertCornerRadii(UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, int topLeft,
            int topRight, int bottomRight, int bottomLeft) {
        Assert.assertNotNull(cornerRadii);
        Assert.assertEquals(topLeft, cornerRadii.getTopLeft());
        Assert.assertEquals(topRight, cornerRadii.getTopRight());
        Assert.assertEquals(bottomRight, cornerRadii.getBottomRight());
        Assert.assertEquals(bottomLeft, cornerRadii.getBottomLeft());
    }

    private static void assertPaintContextCall(PaintContextCall paintContextCall, int left, int top, int right,
            int bottom, float opacity) {
        Assert.assertEquals(left, paintContextCall.left);
        Assert.assertEquals(top, paintContextCall.top);
        Assert.assertEquals(right, paintContextCall.right);
        Assert.assertEquals(bottom, paintContextCall.bottom);
        Assert.assertEquals(opacity, paintContextCall.opacity, 0.0F);
    }

    @SuppressWarnings("unchecked")
    private static void setClipStackForTest(UiRenderContext context, int left, int top, int right, int bottom,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) throws Exception {
        Class<?> clipStateClass = Class.forName("club.heiqi.uilib.ui.render.ClipStack$ClipState");
        Constructor<?> constructor = clipStateClass.getDeclaredConstructor(int[].class,
                UiBorderRadiusResolver.ResolvedCornerRadii.class);
        constructor.setAccessible(true);
        Object clipState = constructor.newInstance(new int[] { left, top, right, bottom }, cornerRadii);
        Field clipStackField = UiRenderContext.class.getDeclaredField("clipStack");
        clipStackField.setAccessible(true);
        Object clipStack = clipStackField.get(context);
        Field entriesField = clipStack.getClass().getDeclaredField("entries");
        entriesField.setAccessible(true);
        Deque<Object> entries = (Deque<Object>) entriesField.get(clipStack);
        entries.clear();
        entries.push(clipState);
    }

    /**
     * 记录 drawSurface 调用的渲染上下文。
     */
    private static class RecordingUiRenderContext extends UiRenderContext {

        private final List<DrawCall> drawCalls = new ArrayList<DrawCall>();
        private final List<ClipCall> clipCalls = new ArrayList<ClipCall>();
        private final List<TextCall> textCalls = new ArrayList<TextCall>();
        private final List<BackdropCall> backdropCalls = new ArrayList<BackdropCall>();
        private final List<HostImageCall> hostImageCalls = new ArrayList<HostImageCall>();
        private final List<PaintContextCall> paintContextCalls = new ArrayList<PaintContextCall>();
        private final List<TransformCall> transformCalls = new ArrayList<TransformCall>();
        private boolean simulatePaintContextLayer;
        private boolean paintContextLayerActive;
        private int popClipCount;
        private int popPaintContextCount;
        private int popTransformCount;

        private RecordingUiRenderContext() {
            super(320, 240, 0, 0, 0.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            drawCalls.add(new DrawCall(left, top, right, bottom, surfaceStyle));
            notifyMainLayerContentChanged();
        }

        @Override
        public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
                int cornerRadius) {
            drawBackdropFilter(left, top, right, bottom, blurRadius, saturation,
                    UiBorderRadiusResolver.ResolvedCornerRadii.uniform(cornerRadius));
        }

        @Override
        public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
            backdropCalls.add(new BackdropCall(left, top, right, bottom, blurRadius, saturation, cornerRadii,
                    getMainLayerContentRevisionForDiagnostics()));
            notifyMainLayerContentChanged();
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {
            pushClip(left, top, right, bottom, UiBorderRadiusResolver.ResolvedCornerRadii.uniform(cornerRadius));
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
            clipCalls.add(new ClipCall(left, top, right, bottom, cornerRadii));
        }

        @Override
        public void popClip() {
            popClipCount++;
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            drawText(text, x, y, color, shadow, TextContentMode.UILIB_RAW);
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode) {
            drawText(text, x, y, color, shadow, textContentMode, UiFontWeight.NORMAL, UiFontStyle.NORMAL);
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode,
                UiFontWeight fontWeight, UiFontStyle fontStyle) {
            textCalls.add(new TextCall(text, x, y, color, shadow, textContentMode, fontWeight, fontStyle));
            notifyMainLayerContentChanged();
        }

        @Override
        public void drawHostImage(HostImageSource source, int left, int top, int right, int bottom) {
            hostImageCalls.add(new HostImageCall(source, left, top, right, bottom));
            notifyMainLayerContentChanged();
        }

        @Override
        public boolean supportsDeferredTextBatching() {
            return false;
        }

        @Override
        public void pushPaintContext(int left, int top, int right, int bottom, float opacity) {
            paintContextCalls.add(new PaintContextCall(left, top, right, bottom, opacity));
            paintContextLayerActive = simulatePaintContextLayer;
        }

        @Override
        public boolean isCurrentPaintContextLayerActive() {
            return paintContextLayerActive;
        }

        @Override
        public void popPaintContext() {
            if (paintContextLayerActive) {
                notifyMainLayerContentChanged();
            }
            paintContextLayerActive = false;
            popPaintContextCount++;
        }

        @Override
        public void pushTransform(UiTransform transform, int left, int top, int right, int bottom) {
            transformCalls.add(new TransformCall(transform, left, top, right, bottom));
        }

        @Override
        public void popTransform() {
            popTransformCount++;
        }
    }

    /**
     * 单次 surface 绘制记录。
     */
    private static final class DrawCall {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final UiSurfaceStyle surfaceStyle;

        private DrawCall(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.surfaceStyle = surfaceStyle;
        }
    }

    /**
     * 单次 clip 绘制状态记录。
     */
    private static final class ClipCall {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii;

        private ClipCall(int left, int top, int right, int bottom,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.cornerRadii = cornerRadii == null ? UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0)
                    : cornerRadii;
        }
    }

    /**
     * 单次文本绘制记录。
     */
    private static final class TextCall {

        private final String text;
        private final int x;
        private final int y;
        private final int color;
        private final boolean shadow;
        private final TextContentMode textContentMode;
        private final UiFontWeight fontWeight;
        private final UiFontStyle fontStyle;

        private TextCall(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode) {
            this(text, x, y, color, shadow, textContentMode, UiFontWeight.NORMAL, UiFontStyle.NORMAL);
        }

        private TextCall(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode,
                UiFontWeight fontWeight, UiFontStyle fontStyle) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.shadow = shadow;
            this.textContentMode = textContentMode;
            this.fontWeight = fontWeight == null ? UiFontWeight.NORMAL : fontWeight;
            this.fontStyle = fontStyle == null ? UiFontStyle.NORMAL : fontStyle;
        }
    }

    /**
     * 单次宿主图片绘制记录。
     */
    private static final class HostImageCall {

        private final HostImageSource source;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private HostImageCall(HostImageSource source, int left, int top, int right, int bottom) {
            this.source = source;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    /**
     * 记录延迟文本批处理边界的渲染上下文。
     */
    private static final class BatchingRecordingUiRenderContext extends RecordingUiRenderContext {

        private final List<TextCall> textCalls = new ArrayList<TextCall>();
        private int beginDeferredTextBatchCount;
        private int flushDeferredTextBatchCount;
        private int endDeferredTextBatchCount;
        private boolean deferredTextBatchActive;

        private BatchingRecordingUiRenderContext() {
            super();
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            drawText(text, x, y, color, shadow, TextContentMode.UILIB_RAW);
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode) {
            drawText(text, x, y, color, shadow, textContentMode, UiFontWeight.NORMAL, UiFontStyle.NORMAL);
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode,
                UiFontWeight fontWeight, UiFontStyle fontStyle) {
            textCalls.add(new TextCall(text, x, y, color, shadow, textContentMode, fontWeight, fontStyle));
            notifyMainLayerContentChanged();
        }

        @Override
        public boolean supportsDeferredTextBatching() {
            return true;
        }

        @Override
        public void beginDeferredTextBatch(int targetWidth, int targetHeight) {
            beginDeferredTextBatchCount++;
            deferredTextBatchActive = true;
        }

        @Override
        public void flushDeferredTextBatch() {
            flushDeferredTextBatchCount++;
        }

        @Override
        public void endDeferredTextBatch() {
            endDeferredTextBatchCount++;
            deferredTextBatchActive = false;
        }

        private boolean isDeferredTextBatchActiveForTest() {
            return deferredTextBatchActive;
        }
    }

    /**
     * 单次 custom 绘制记录。
     */
    private static final class CustomCall {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private CustomCall(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    /**
     * 单次 backdrop filter 绘制记录。
     */
    private static final class BackdropCall {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int blurRadius;
        private final float saturation;
        private final UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii;
        private final int contentRevision;

        private BackdropCall(int left, int top, int right, int bottom, int blurRadius, float saturation,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, int contentRevision) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.blurRadius = blurRadius;
            this.saturation = saturation;
            this.cornerRadii = cornerRadii == null ? UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0)
                    : cornerRadii;
            this.contentRevision = contentRevision;
        }
    }

    /**
     * 单次绘制上下文边界记录。
     */
    private static final class PaintContextCall {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final float opacity;

        private PaintContextCall(int left, int top, int right, int bottom, float opacity) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.opacity = opacity;
        }
    }

    /**
     * 单次 transform 压栈记录。
     */
    private static final class TransformCall {

        private final UiTransform transform;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private TransformCall(UiTransform transform, int left, int top, int right, int bottom) {
            this.transform = transform;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
