package club.heiqi.uilib.ui.paint;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.style.UiStyleLength;

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

    private static void assertCommand(DocumentPaintCommand command, DocumentPaintCommandType type,
            ElementNode element, int left, int top, int right, int bottom, int color, int borderWidth,
            int borderRadius) {
        Assert.assertEquals(type, command.getType());
        Assert.assertSame(element, command.getElement());
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
}
