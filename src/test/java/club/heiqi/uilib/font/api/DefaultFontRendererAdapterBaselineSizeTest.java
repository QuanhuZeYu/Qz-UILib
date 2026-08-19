package club.heiqi.uilib.font.api;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontRuntimeSettings;

/**
 * 混排字号渲染尺寸解析测试：基线按行内最大字号、glyph 按自身字号。
 */
public class DefaultFontRendererAdapterBaselineSizeTest {

    @Test
    public void shouldReturnCharSizeForBaselineWhenNoOversizedSpan() {
        Assert.assertEquals(16.0F, DefaultFontRendererAdapter.resolveBaselineCharSize(16.0F, 16, settings()),
                0.0001F);
        Assert.assertEquals(16.0F, DefaultFontRendererAdapter.resolveBaselineCharSize(16.0F, 0, settings()),
                0.0001F);
    }

    @Test
    public void shouldScaleBaselineByMaxFontSize() {
        // 基准 16px 渲染尺寸、行内最大 32px → 基线换算渲染尺寸 32
        Assert.assertEquals(32.0F, DefaultFontRendererAdapter.resolveBaselineCharSize(16.0F, 32, settings()),
                0.0001F);
        // scaled 路径：整段渲染尺寸 32（16×2 缩放）、行内最大 32px → 64
        Assert.assertEquals(64.0F, DefaultFontRendererAdapter.resolveBaselineCharSize(32.0F, 32, settings()),
                0.0001F);
    }

    @Test
    public void shouldScaleGlyphByOwnFontSize() {
        Assert.assertEquals(16.0F, DefaultFontRendererAdapter.resolveGlyphCharSize(16.0F, 16, settings()),
                0.0001F);
        Assert.assertEquals(32.0F, DefaultFontRendererAdapter.resolveGlyphCharSize(16.0F, 32, settings()),
                0.0001F);
        Assert.assertEquals(16.0F, DefaultFontRendererAdapter.resolveGlyphCharSize(16.0F, 0, settings()),
                0.0001F);
    }

    private static FontRuntimeSettings settings() {
        return new FontRuntimeSettings(0, 64.0D, 16.0D, 4.0D, 0.0D, false, new String[0], null);
    }
}
