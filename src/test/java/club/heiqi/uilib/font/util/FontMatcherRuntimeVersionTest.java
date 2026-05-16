package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;

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

        catalog.replaceAll(Arrays.asList(dialog, serif));
        Font oldMatch = matcher.match(1, 'A', FontType.NORMAL);

        catalog.replaceAll(Arrays.asList(serif, dialog));
        derivedFontCache.clear();
        matcher.clearCache();
        Font newMatch = matcher.match(2, 'A', FontType.NORMAL);

        Assert.assertEquals(dialog.getName(), oldMatch.getName());
        Assert.assertEquals(serif.getName(), newMatch.getName());
    }
}
