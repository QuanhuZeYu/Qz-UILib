package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.MathFontStyle;
import club.heiqi.uilib.font.latex.layout.MathGlyphRef;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;

public class MathFontCatalogTest {
    @Test
    public void publishesMathWithFontOrderAndKeepsOldSnapshotsImmutable() {
        FontCatalog catalog = new FontCatalog();
        Font first = new Font("Dialog", Font.PLAIN, 20);
        Font second = new Font("Serif", Font.PLAIN, 20);
        List<Font> ordered = new ArrayList<Font>(Arrays.asList(first, second));
        catalog.replaceAll(ordered);
        FontCatalog.Snapshot legacy = catalog.snapshot();
        BundledMathFont math = BundledMathFont.shared();
        MathGlyphRef glyph = math.resolve('x', MathFontStyle.ITALIC, FontType.NORMAL);
        FontCatalog.Snapshot candidate = catalog.prepareSnapshotWithMath(ordered, math);
        ordered.clear();
        Assert.assertSame(legacy, catalog.snapshot());
        Assert.assertNull(catalog.getMathFontSupport());
        Assert.assertNull(catalog.getMathPhysicalFont(glyph, 20));
        Assert.assertSame(math, candidate.getMathFontSupport());
        Assert.assertEquals(Arrays.asList(first, second), candidate.getFonts());
        Assert.assertNotNull(candidate.getMathPhysicalFont(glyph, 20));
        catalog.validate(candidate);
        catalog.publish(candidate);
        Assert.assertSame(candidate, catalog.snapshot());
        Assert.assertSame(math, catalog.getMathFontSupport());
        Assert.assertEquals(Arrays.asList(first, second), catalog.getFonts());
        Assert.assertEquals("STIXTwoMath-Regular", catalog.getMathPhysicalFont(glyph, 20).getPSName());
        Assert.assertNull(legacy.getMathFontSupport());
        Assert.assertNull(legacy.getMathPhysicalFont(glyph, 20));
        Assert.assertNull(candidate.getMathPhysicalFont(MathGlyphRef.forFontGlyph("foreign", glyph.getGlyphId()), 20));

        FontCatalog.Snapshot nextLegacy = catalog.prepareSnapshot(Arrays.asList(second, first));
        Assert.assertNull(nextLegacy.getMathFontSupport());
        catalog.publish(nextLegacy);
        Assert.assertNull(catalog.getMathFontSupport());
        Assert.assertNull(catalog.getMathPhysicalFont(glyph, 20));
        // 内容身份跨代仍稳定；旧 candidate 自身不会被修改，时效由 generation owner 另行判断。
        Assert.assertNotNull(candidate.getMathPhysicalFont(glyph, 20));
        Assert.assertEquals(glyph, math.resolve('x', MathFontStyle.ITALIC, FontType.NORMAL));
    }

    @Test(expected = IllegalStateException.class)
    public void staleMathCandidateCannotOverwriteNewLegacyDirectory() {
        FontCatalog catalog = new FontCatalog();
        List<Font> fonts = Arrays.asList(new Font("Dialog", Font.PLAIN, 20));
        FontCatalog.Snapshot stale = catalog.prepareSnapshotWithMath(fonts, BundledMathFont.shared());
        catalog.replaceAll(fonts);
        catalog.publish(stale);
    }

    @Test
    public void missingMathCharacterCanUseExistingMatcherWithoutChangingTextOrder() {
        FontCatalog catalog = new FontCatalog();
        Font dialog = new Font("Dialog", Font.PLAIN, 20);
        Font serif = new Font("Serif", Font.PLAIN, 20);
        BundledMathFont math = BundledMathFont.shared();
        catalog.publish(catalog.prepareSnapshotWithMath(Arrays.asList(dialog, serif), math));
        FontMatcher matcher = new FontMatcher(catalog, new DerivedFontCache(catalog));
        matcher.setRuntimeTables(1, new GlyphRuntimeTables());
        // 使用平台真实文字 fallback，不要求 CI 安装某个中文字体。
        // 在 Cyrillic/Hebrew/Arabic 字母中找 Dialog 可显示而 STIX 不含的码点。
        int missing = -1;
        for (int cp = 0x400; cp <= 0x6ff; cp++) {
            if (Character.isLetter(cp) && dialog.canDisplay(cp)
                    && math.resolve(cp, MathFontStyle.INHERIT, FontType.NORMAL) == null
                    && matcher.match(1, cp, FontType.NORMAL) != null) {
                missing = cp;
                break;
            }
        }
        Assert.assertTrue("Logical Dialog must offer a real fallback letter", missing >= 0);
        Assert.assertNull(math.resolve(missing, MathFontStyle.INHERIT, FontType.NORMAL));
        Font fallback = matcher.match(1, missing, FontType.NORMAL);
        Assert.assertNotNull(fallback);
        Assert.assertEquals(dialog.getName(), fallback.getName());
        Assert.assertTrue(fallback.canDisplay(missing));
        Assert.assertEquals(dialog.getName(), matcher.match(1, 'A', FontType.NORMAL).getName());
        Assert.assertEquals(Arrays.asList(dialog, serif), catalog.getFonts());
    }
}
