package club.heiqi.uilib.font.api;

import org.junit.Assert;
import org.junit.Test;

/**
 * 混排字号渲染尺寸解析测试：基线按行内最大字号、glyph 按自身字号，缩放统一乘 renderScale。
 */
public class DefaultFontRendererAdapterBaselineSizeTest {

    @Test
    public void shouldScaleGlyphByOwnFontSize() {
        Assert.assertEquals(15.0F, DefaultFontRendererAdapter.resolveGlyphCharSize(1.0F, 15), 0.0001F);
        Assert.assertEquals(24.0F, DefaultFontRendererAdapter.resolveGlyphCharSize(1.0F, 24), 0.0001F);
        // 外界缩放（HUD GUI Scale）统一乘入
        Assert.assertEquals(48.0F, DefaultFontRendererAdapter.resolveGlyphCharSize(2.0F, 24), 0.0001F);
    }

    @Test
    public void shouldScaleBaselineByMaxFontSize() {
        Assert.assertEquals(24.0F, DefaultFontRendererAdapter.resolveBaselineCharSize(1.0F, 24), 0.0001F);
        Assert.assertEquals(48.0F, DefaultFontRendererAdapter.resolveBaselineCharSize(2.0F, 24), 0.0001F);
        Assert.assertEquals(16.0F, DefaultFontRendererAdapter.resolveBaselineCharSize(1.0F, 16), 0.0001F);
    }

    @Test
    public void shouldKeepSingleSizeSemanticsUnchanged() {
        // 单一字号（15px，无 span 缩放）渲染尺寸 = 15×1
        Assert.assertEquals(15.0F, DefaultFontRendererAdapter.resolveGlyphCharSize(1.0F, 15), 0.0001F);
        Assert.assertEquals(15.0F, DefaultFontRendererAdapter.resolveBaselineCharSize(1.0F, 15), 0.0001F);
    }
}
