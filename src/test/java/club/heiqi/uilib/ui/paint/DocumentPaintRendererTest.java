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
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.layout.DocumentEffectType;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.layout.DocumentScrollState;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiBorderRadius;
import club.heiqi.uilib.ui.style.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.UiBorderStyle;
import club.heiqi.uilib.ui.style.UiBoxShadow;
import club.heiqi.uilib.ui.style.UiBackgroundImage;
import club.heiqi.uilib.ui.style.UiFontStyle;
import club.heiqi.uilib.ui.style.UiFontWeight;
import club.heiqi.uilib.ui.style.UiOutline;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.style.UiTextShadow;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;
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

        table.style().setBorderCollapse(club.heiqi.uilib.ui.style.UiBorderCollapse.COLLAPSE);
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
        UiRenderContext.ClipSnapshot clipSnapshot = (UiRenderContext.ClipSnapshot) copyCurrentClipSnapshot.invoke(
                context);

        Assert.assertNotNull(clipSnapshot);
        Assert.assertEquals(1, clipSnapshot.getRoundedClipRegions().size());
        UiRenderContext.RoundedClipRegion clipRegion = clipSnapshot.getRoundedClipRegions().get(0);
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
        Class<?> clipStateClass = Class.forName("club.heiqi.uilib.ui.render.UiRenderContext$ClipState");
        Constructor<?> constructor = clipStateClass.getDeclaredConstructor(int[].class,
                UiBorderRadiusResolver.ResolvedCornerRadii.class);
        constructor.setAccessible(true);
        Object clipState = constructor.newInstance(new int[] { left, top, right, bottom }, cornerRadii);
        Field clipStackField = UiRenderContext.class.getDeclaredField("clipStack");
        clipStackField.setAccessible(true);
        Deque<Object> clipStack = (Deque<Object>) clipStackField.get(context);
        clipStack.clear();
        clipStack.push(clipState);
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
        private boolean simulatePaintContextLayer;
        private boolean paintContextLayerActive;
        private int popClipCount;
        private int popPaintContextCount;

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
}
