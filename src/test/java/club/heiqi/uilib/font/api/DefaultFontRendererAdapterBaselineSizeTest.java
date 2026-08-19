package club.heiqi.uilib.font.api;

import java.awt.Font;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;

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
    public void shouldResolveSegmentWidthInCallerFontSize() {
        TextLayoutService textLayoutService = createService();
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        int baseSize = (int) FontRuntimeSettings.capture().getCharSize();

        // 段字号 = 调用方 px 字号（15）：宽 = 缓存宽（1.0，settings.charSize 坐标系）× 15/base
        double width15 = DefaultFontRendererAdapter.resolveSegmentCodepointWidth(textLayoutService, 'A', style, 15);
        Assert.assertEquals(15.0D / Math.max(1, baseSize), width15, 0.0001D);
        // 段字号 = 引擎基准字号：宽 = 缓存宽（比例 1.0）——带字号路径与无字号路径同值
        double widthBase = DefaultFontRendererAdapter.resolveSegmentCodepointWidth(textLayoutService, 'A', style,
                Math.max(1, baseSize));
        Assert.assertEquals(1.0D, widthBase, 0.0001D);
    }

    private static TextLayoutService createService() {
        FontCatalog fontCatalog = new FontCatalog();
        fontCatalog.replaceAll(Arrays.asList(new Font("Dialog", Font.PLAIN, 14)));
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        GlyphPageManager glyphPageManager = new GlyphPageManager();
        glyphPageManager.getRuntimeTables().widthArray(FontType.NORMAL)['A'] = 1.0F;
        TextLayoutService service = new TextLayoutService(new FontMatcher(fontCatalog, derivedFontCache),
                glyphPageManager, derivedFontCache);
        service.setRuntimeVersion(1);
        return service;
    }

    @Test
    public void shouldKeepSingleSizeSemanticsUnchanged() {
        // 单一字号（15px，无 span 缩放）渲染尺寸 = 15×1
        Assert.assertEquals(15.0F, DefaultFontRendererAdapter.resolveGlyphCharSize(1.0F, 15), 0.0001F);
        Assert.assertEquals(15.0F, DefaultFontRendererAdapter.resolveBaselineCharSize(1.0F, 15), 0.0001F);
    }
}
