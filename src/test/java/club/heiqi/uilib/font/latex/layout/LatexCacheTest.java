package club.heiqi.uilib.font.latex.layout;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;

/**
 * {@link LatexCache} M4 缓存测试：命中/失效/LRU 上限。
 */
public class LatexCacheTest {

    private static final MathMetrics METRICS = new MathMetrics() {
        @Override
        public float advance(String text, float sizePx) {
            return text.codePointCount(0, text.length()) * 0.5F * sizePx;
        }

        @Override
        public float ascent(float sizePx) {
            return 0.8F * sizePx;
        }

        @Override
        public float descent(float sizePx) {
            return 0.2F * sizePx;
        }
    };

    @Test
    public void shouldReturnSameBoxForSameKey() {
        LatexCache cache = LatexCache.getInstance();
        MathLayoutService layout = new MathLayoutService();
        String source = "\\frac{a}{b}::cache-hit-test-" + System.nanoTime();
        MathBox first = cache.getOrLayout(source, 16, 1, FontType.NORMAL, layout, METRICS);
        MathBox second = cache.getOrLayout(source, 16, 1, FontType.NORMAL, layout, METRICS);
        Assert.assertSame(first, second);
    }

    @Test
    public void shouldMissOnRuntimeVersionChange() {
        LatexCache cache = LatexCache.getInstance();
        MathLayoutService layout = new MathLayoutService();
        String source = "x^2::version-test-" + System.nanoTime();
        MathBox first = cache.getOrLayout(source, 16, 1, FontType.NORMAL, layout, METRICS);
        MathBox reloaded = cache.getOrLayout(source, 16, 2, FontType.NORMAL, layout, METRICS);
        // 版本变化 miss → 重新布局出新盒；再次查询命中新盒
        Assert.assertNotSame(first, reloaded);
        Assert.assertSame(reloaded, cache.getOrLayout(source, 16, 2, FontType.NORMAL, layout, METRICS));
    }

    @Test
    public void shouldMissOnFontTypeChange() {
        LatexCache cache = LatexCache.getInstance();
        MathLayoutService layout = new MathLayoutService();
        String source = "x^2::font-test-" + System.nanoTime();
        MathBox normal = cache.getOrLayout(source, 16, 1, FontType.NORMAL, layout, METRICS);
        MathBox bold = cache.getOrLayout(source, 16, 1, FontType.BOLD, layout, METRICS);
        Assert.assertNotSame(normal, bold);
        Assert.assertSame(bold, cache.getOrLayout(source, 16, 1, FontType.BOLD, layout, METRICS));
    }

    @Test
    public void shouldBoundEntriesByLruLimit() {
        LatexCache cache = LatexCache.getInstance();
        MathLayoutService layout = new MathLayoutService();
        String prefix = "x::lru-" + System.nanoTime() + "-";
        for (int i = 0; i < 400; i++) {
            cache.getOrLayout(prefix + i, 16, 1, FontType.NORMAL, layout, METRICS);
        }
        Assert.assertTrue("缓存条目数应受上限约束", cache.size() <= 300);
    }
}
