package club.heiqi.uilib.ui.render;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * `UiRenderContext` 文本绘制入口的回归测试。
 */
public class UiRenderContextTest {

    /**
     * 验证普通字体样式不会在 `drawText` 重载之间递归。
     */
    @Test
    public void shouldDrawNormalFontStyleWithoutRecursiveOverload() {
        RecordingUiRenderContext context = new RecordingUiRenderContext();

        context.drawText("Normal", 3, 5, 0xFFE2E8F0, false, TextContentMode.UILIB_RAW,
                UiFontWeight.NORMAL, UiFontStyle.NORMAL);

        Assert.assertEquals(1, context.resolvedTextCount);
        Assert.assertEquals("Normal", context.lastText);
        Assert.assertEquals(UiFontWeight.NORMAL, context.lastFontWeight);
        Assert.assertEquals(UiFontStyle.NORMAL, context.lastFontStyle);
    }

    /**
     * 记录最终文本绘制入口的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private int resolvedTextCount;
        private String lastText;
        private UiFontWeight lastFontWeight;
        private UiFontStyle lastFontStyle;

        private RecordingUiRenderContext() {
            super(320, 240, 0, 0, 0.0F);
        }

        @Override
        protected void drawTextResolved(String text, int x, int y, int color, boolean shadow,
                TextContentMode textContentMode, UiFontWeight resolvedFontWeight, UiFontStyle resolvedFontStyle) {
            resolvedTextCount++;
            lastText = text;
            lastFontWeight = resolvedFontWeight;
            lastFontStyle = resolvedFontStyle;
        }
    }
}
