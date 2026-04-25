package club.heiqi.uilib.ui.paint;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.render.UiRenderContext;
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

    /**
     * 记录 drawSurface 调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<DrawCall> drawCalls = new ArrayList<DrawCall>();

        private RecordingUiRenderContext() {
            super(320, 240, 0, 0, 0.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            drawCalls.add(new DrawCall(left, top, right, bottom, surfaceStyle));
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
}
