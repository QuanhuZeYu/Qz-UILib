package club.heiqi.uilib.ui.paint;

import java.util.ArrayList;
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
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;

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

    private static void assertDrawCall(DrawCall drawCall, int left, int top, int right, int bottom, int fillColor,
            int borderColor, int cornerRadius) {
        Assert.assertEquals(left, drawCall.left);
        Assert.assertEquals(top, drawCall.top);
        Assert.assertEquals(right, drawCall.right);
        Assert.assertEquals(bottom, drawCall.bottom);
        Assert.assertEquals(fillColor, drawCall.surfaceStyle.fillColor);
        Assert.assertEquals(borderColor, drawCall.surfaceStyle.borderColor);
        Assert.assertEquals(cornerRadius, drawCall.surfaceStyle.cornerRadius);
    }

    private static void assertClipCall(ClipCall clipCall, int left, int top, int right, int bottom, int cornerRadius) {
        Assert.assertEquals(left, clipCall.left);
        Assert.assertEquals(top, clipCall.top);
        Assert.assertEquals(right, clipCall.right);
        Assert.assertEquals(bottom, clipCall.bottom);
        Assert.assertEquals(cornerRadius, clipCall.cornerRadius);
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

    /**
     * 记录 drawSurface 调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<DrawCall> drawCalls = new ArrayList<DrawCall>();
        private final List<ClipCall> clipCalls = new ArrayList<ClipCall>();
        private final List<TextCall> textCalls = new ArrayList<TextCall>();
        private int popClipCount;

        private RecordingUiRenderContext() {
            super(320, 240, 0, 0, 0.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            drawCalls.add(new DrawCall(left, top, right, bottom, surfaceStyle));
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {
            clipCalls.add(new ClipCall(left, top, right, bottom, cornerRadius));
        }

        @Override
        public void popClip() {
            popClipCount++;
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            textCalls.add(new TextCall(text, x, y, color, shadow));
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
        private final int cornerRadius;

        private ClipCall(int left, int top, int right, int bottom, int cornerRadius) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.cornerRadius = cornerRadius;
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

        private TextCall(String text, int x, int y, int color, boolean shadow) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.shadow = shadow;
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
}
