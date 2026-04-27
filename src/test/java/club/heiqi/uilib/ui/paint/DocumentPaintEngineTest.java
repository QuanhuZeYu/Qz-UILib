package club.heiqi.uilib.ui.paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.layout.DocumentScrollState;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiOverflow;
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

    private static int countCommands(List<DocumentPaintCommand> commands, DocumentPaintCommandType type) {
        int count = 0;
        for (DocumentPaintCommand command : commands) {
            if (command.getType() == type) {
                count++;
            }
        }
        return count;
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
