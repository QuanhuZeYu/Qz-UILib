package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphJustificationInfo;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;

/**
 * {@link FontMatcher} 的运行时版本隔离测试。
 */
public class FontMatcherRuntimeVersionTest {

    /**
     * 验证字体顺序变化后，同码点会在新版本内重新匹配字体。
     */
    @Test
    public void shouldNotReuseMatchCacheAfterRuntimeVersionChanges() {
        FontCatalog catalog = new FontCatalog();
        Font dialog = new Font("Dialog", Font.PLAIN, 14);
        Font serif = new Font("Serif", Font.PLAIN, 14);
        DerivedFontCache derivedFontCache = new DerivedFontCache(catalog);
        FontMatcher matcher = new FontMatcher(catalog, derivedFontCache);
        GlyphRuntimeTables oldTables = new GlyphRuntimeTables();
        GlyphRuntimeTables newTables = new GlyphRuntimeTables();

        catalog.replaceAll(Arrays.asList(dialog, serif));
        matcher.setRuntimeTables(1, oldTables);
        Font oldMatch = matcher.match(1, 'A', FontType.NORMAL);

        catalog.replaceAll(Arrays.asList(serif, dialog));
        derivedFontCache.clear();
        matcher.setRuntimeTables(2, newTables);
        matcher.clearCache();
        Font newMatch = matcher.match(2, 'A', FontType.NORMAL);

        Assert.assertEquals(dialog.getName(), oldMatch.getName());
        Assert.assertEquals(serif.getName(), newMatch.getName());
    }

    /**
     * 验证上一字符留下的 hint 只能加速校验，不能越过更靠前且同样可用的字体。
     */
    @Test
    public void shouldKeepFontSortOrderWhenLastHintCanDisplayCodepoint() {
        FontCatalog catalog = new FontCatalog();
        Font firstFont = new TestFont("First", Font.PLAIN, 'A');
        Font secondFont = new TestFont("Second", Font.PLAIN, 'A', 'B');
        DerivedFontCache derivedFontCache = new DerivedFontCache(catalog);
        FontMatcher matcher = new FontMatcher(catalog, derivedFontCache);
        GlyphRuntimeTables tables = new GlyphRuntimeTables();

        catalog.replaceAll(Arrays.asList(firstFont, secondFont));
        matcher.setRuntimeTables(1, tables);

        Assert.assertEquals(1, matcher.matchFontIndex(1, 'B', FontType.NORMAL));
        Assert.assertEquals(0, matcher.matchFontIndex(1, 'A', FontType.NORMAL));
    }

    /**
     * 验证旧 runtime 的匹配结果不能写入新 runtime 的 direct-index 表或 hint。
     */
    @Test
    public void shouldNotLetStaleRuntimeWriteNewMatchCacheOrHints() {
        FontCatalog catalog = new FontCatalog();
        Font firstFont = new TestFont("First", Font.PLAIN, 'A');
        Font secondFont = new TestFont("Second", Font.PLAIN, 'A');
        DerivedFontCache derivedFontCache = new DerivedFontCache(catalog);
        FontMatcher matcher = new FontMatcher(catalog, derivedFontCache);
        GlyphRuntimeTables oldTables = new GlyphRuntimeTables();
        GlyphRuntimeTables newTables = new GlyphRuntimeTables();

        catalog.replaceAll(Collections.singletonList(firstFont));
        matcher.setRuntimeTables(1, oldTables);
        Assert.assertEquals(0, matcher.matchFontIndex(1, 'A', FontType.NORMAL));

        catalog.replaceAll(Arrays.asList(secondFont, firstFont));
        derivedFontCache.clear();
        matcher.setRuntimeTables(2, newTables);

        Assert.assertEquals(0, matcher.matchFontIndex(1, 'A', FontType.NORMAL));
        Assert.assertEquals(GlyphRuntimeTables.FONT_INDEX_UNRESOLVED, newTables.matchedFontNormal['A']);

        Assert.assertEquals(0, matcher.matchFontIndex(2, 'A', FontType.NORMAL));
        Assert.assertEquals(0, newTables.matchedFontNormal['A']);
    }

    /**
     * 验证派生字体缓存不会把旧目录字体写进新目录版本缓存。
     */
    @Test
    public void shouldNotCacheOldDerivedFontWhenCatalogSwitchesDuringLookup() {
        Font oldFont = new TestFont("Old", Font.PLAIN, 'A');
        Font newFont = new TestFont("New", Font.PLAIN, 'A');
        FontCatalog catalog = new SwitchOnFirstReadFontCatalog(oldFont, newFont);
        DerivedFontCache cache = new DerivedFontCache(catalog);

        Font newDerivedFont = cache.getDerivedFont(0, FontType.NORMAL, 16);

        Assert.assertEquals("New", newDerivedFont.getName());
    }

    private static final class SwitchOnFirstReadFontCatalog extends FontCatalog {

        private final Font newFont;
        private boolean switched;

        private SwitchOnFirstReadFontCatalog(Font oldFont, Font newFont) {
            this.newFont = newFont;
            replaceAll(Collections.singletonList(oldFont));
        }

        @Override
        public Font getFont(int index) {
            Font font = super.getFont(index);
            switchToNewCatalog();
            return font;
        }

        @Override
        public Snapshot snapshot() {
            switchToNewCatalog();
            return super.snapshot();
        }

        private void switchToNewCatalog() {
            if (switched) {
                return;
            }
            switched = true;
            replaceAll(Collections.singletonList(newFont));
        }
    }

    private static final class TestFont extends Font {

        private final int[] displayableCodepoints;

        private TestFont(String name, int style, int... displayableCodepoints) {
            super(name, style, 14);
            this.displayableCodepoints = displayableCodepoints;
        }

        @Override
        public boolean canDisplay(int codepoint) {
            for (int displayableCodepoint : displayableCodepoints) {
                if (displayableCodepoint == codepoint) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Font deriveFont(int style, float size) {
            return new TestFont(getName(), style, Arrays.copyOf(displayableCodepoints, displayableCodepoints.length));
        }

        @Override
        public GlyphVector createGlyphVector(FontRenderContext frc, String str) {
            return new TestGlyphVector(this, frc, str.codePointAt(0));
        }
    }

    private static final class TestGlyphVector extends GlyphVector {

        private final Font font;
        private final FontRenderContext fontRenderContext;
        private final int codepoint;

        private TestGlyphVector(Font font, FontRenderContext fontRenderContext, int codepoint) {
            this.font = font;
            this.fontRenderContext = fontRenderContext;
            this.codepoint = codepoint;
        }

        @Override
        public Font getFont() {
            return font;
        }

        @Override
        public FontRenderContext getFontRenderContext() {
            return fontRenderContext;
        }

        @Override
        public void performDefaultLayout() {}

        @Override
        public int getNumGlyphs() {
            return 1;
        }

        @Override
        public int getGlyphCode(int glyphIndex) {
            return font.canDisplay(codepoint) ? codepoint : font.getMissingGlyphCode();
        }

        @Override
        public int[] getGlyphCodes(int beginGlyphIndex, int numEntries, int[] codeReturn) {
            int[] codes = codeReturn == null ? new int[numEntries] : codeReturn;
            for (int index = 0; index < numEntries; index++) {
                codes[index] = getGlyphCode(beginGlyphIndex + index);
            }
            return codes;
        }

        @Override
        public Rectangle2D getLogicalBounds() {
            return bounds();
        }

        @Override
        public Rectangle2D getVisualBounds() {
            return bounds();
        }

        @Override
        public Shape getOutline() {
            return bounds();
        }

        @Override
        public Shape getOutline(float x, float y) {
            return new Rectangle2D.Float(x, y, 1.0F, 1.0F);
        }

        @Override
        public Shape getGlyphOutline(int glyphIndex) {
            return bounds();
        }

        @Override
        public Point2D getGlyphPosition(int glyphIndex) {
            return new Point2D.Float(0.0F, 0.0F);
        }

        @Override
        public void setGlyphPosition(int glyphIndex, Point2D newPos) {}

        @Override
        public AffineTransform getGlyphTransform(int glyphIndex) {
            return null;
        }

        @Override
        public void setGlyphTransform(int glyphIndex, AffineTransform newTX) {}

        @Override
        public float[] getGlyphPositions(int beginGlyphIndex, int numEntries, float[] positionReturn) {
            float[] positions = positionReturn == null ? new float[numEntries * 2] : positionReturn;
            for (int index = 0; index < numEntries * 2; index++) {
                positions[index] = 0.0F;
            }
            return positions;
        }

        @Override
        public Shape getGlyphLogicalBounds(int glyphIndex) {
            return bounds();
        }

        @Override
        public Shape getGlyphVisualBounds(int glyphIndex) {
            return bounds();
        }

        @Override
        public GlyphMetrics getGlyphMetrics(int glyphIndex) {
            return new GlyphMetrics(1.0F, bounds(), GlyphMetrics.STANDARD);
        }

        @Override
        public GlyphJustificationInfo getGlyphJustificationInfo(int glyphIndex) {
            return null;
        }

        @Override
        public boolean equals(GlyphVector set) {
            return this == set;
        }

        private Rectangle2D.Float bounds() {
            return new Rectangle2D.Float(0.0F, 0.0F, 1.0F, 1.0F);
        }
    }
}
