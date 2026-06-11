package club.heiqi.uilib.font.glyph;

import java.awt.Font;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;

/**
 * AWT 字形生成的 ink bounds 契约测试。
 */
public class GlyphGeneratorInkBoundsTest {

    /**
     * 可见字符应生成按实际像素 bounds 扩展的 atlas slot 与基线数据。
     */
    @Test
    public void shouldGenerateInkBoundsSlotForVisibleGlyph() {
        GlyphGenerationResult result = createGenerator().generate(task('A'));

        Assert.assertNotNull(result);
        GlyphInfo glyphInfo = result.getGlyphInfo();
        Assert.assertTrue("可见字符应存在 bitmap", glyphInfo.hasBitmap());
        Assert.assertTrue("slot 宽度应来自实际 ink bounds", glyphInfo.getSlotWidth() > 0);
        Assert.assertTrue("slot 高度应来自实际 ink bounds", glyphInfo.getSlotHeight() > 0);
        Assert.assertEquals(glyphInfo.getSlotWidth(), result.getImage().getWidth());
        Assert.assertEquals(glyphInfo.getSlotHeight(), result.getImage().getHeight());
        Assert.assertTrue("基线 X 应落在 slot 内", glyphInfo.getAtlasBaselineX() >= 0);
        Assert.assertTrue("基线 Y 应落在 slot 内", glyphInfo.getAtlasBaselineY() >= 0);
        Assert.assertTrue("advance 应保留 AWT 推进量", glyphInfo.getAdvance() > 0.0F);
    }

    /**
     * 空白字符应只保留 advance，不上传无意义 bitmap。
     */
    @Test
    public void shouldGenerateAdvanceOnlyForWhitespaceGlyph() {
        GlyphGenerationResult result = createGenerator().generate(task(' '));

        Assert.assertNotNull(result);
        GlyphInfo glyphInfo = result.getGlyphInfo();
        Assert.assertFalse("空白字符不应占用 atlas bitmap", glyphInfo.hasBitmap());
        Assert.assertEquals(0, glyphInfo.getSlotWidth());
        Assert.assertEquals(0, glyphInfo.getSlotHeight());
        Assert.assertTrue("空白字符仍应保留 advance", glyphInfo.getAdvance() > 0.0F);
    }

    private static GlyphGenerationTask task(int codepoint) {
        return new GlyphGenerationTask(1, codepoint, FontType.NORMAL, 64, GlyphGenerationPriority.HIGH);
    }

    private static GlyphGenerator createGenerator() {
        FontCatalog fontCatalog = new FontCatalog();
        fontCatalog.replaceAll(Collections.singletonList(new Font("Dialog", Font.PLAIN, 14)));
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        FontMatcher fontMatcher = new FontMatcher(fontCatalog, derivedFontCache);
        fontMatcher.setRuntimeTables(1, new GlyphRuntimeTables());
        return new GlyphGenerator(fontMatcher, derivedFontCache);
    }
}
