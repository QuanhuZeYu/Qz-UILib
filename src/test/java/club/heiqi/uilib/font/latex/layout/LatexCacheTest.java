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

        @Override
        public float xHeight(float sizePx) {
            return 0.45F * sizePx;
        }
    };

    @Test
    public void shouldReturnSameBoxForSameKey() {
        LatexCache cache = LatexCache.getInstance();
        MathLayoutService layout = new MathLayoutService();
        String source = "\\frac{a}{b}::cache-hit-test-" + System.nanoTime();
        MathBox first = cache.getOrLayout(source, 16, 1, FontType.NORMAL, layout, METRICS, 0);
        MathBox second = cache.getOrLayout(source, 16, 1, FontType.NORMAL, layout, METRICS, 0);
        Assert.assertSame(first, second);
    }

    @Test
    public void shouldMissOnRuntimeVersionChange() {
        LatexCache cache = LatexCache.getInstance();
        MathLayoutService layout = new MathLayoutService();
        String source = "x^2::version-test-" + System.nanoTime();
        MathBox first = cache.getOrLayout(source, 16, 1, FontType.NORMAL, layout, METRICS, 0);
        MathBox reloaded = cache.getOrLayout(source, 16, 2, FontType.NORMAL, layout, METRICS, 0);
        // 版本变化 miss → 重新布局出新盒；再次查询命中新盒
        Assert.assertNotSame(first, reloaded);
        Assert.assertSame(reloaded, cache.getOrLayout(source, 16, 2, FontType.NORMAL, layout, METRICS, 0));
    }

    @Test
    public void shouldMissOnFontTypeChange() {
        LatexCache cache = LatexCache.getInstance();
        MathLayoutService layout = new MathLayoutService();
        String source = "x^2::font-test-" + System.nanoTime();
        MathBox normal = cache.getOrLayout(source, 16, 1, FontType.NORMAL, layout, METRICS, 0);
        MathBox bold = cache.getOrLayout(source, 16, 1, FontType.BOLD, layout, METRICS, 0);
        Assert.assertNotSame(normal, bold);
        Assert.assertSame(bold, cache.getOrLayout(source, 16, 1, FontType.BOLD, layout, METRICS, 0));
    }

    @Test
    public void shouldMissOnInkEpochChange() {
        LatexCache cache = LatexCache.getInstance();
        MathLayoutService layout = new MathLayoutService();
        String source = "x^2::ink-epoch-test-" + System.nanoTime();
        MathBox first = cache.getOrLayout(source, 16, 1, FontType.NORMAL, layout, METRICS, 0);
        // 字形 ink 就绪代变化（字形异步生成完成）→ 失效重布局，避免回退锚定盒被永久缓存
        MathBox ready = cache.getOrLayout(source, 16, 1, FontType.NORMAL, layout, METRICS, 7);
        Assert.assertNotSame(first, ready);
        Assert.assertSame(ready, cache.getOrLayout(source, 16, 1, FontType.NORMAL, layout, METRICS, 7));
    }

    @Test
    public void shouldBoundEntriesByLruLimit() {
        LatexCache cache = LatexCache.getInstance();
        MathLayoutService layout = new MathLayoutService();
        String prefix = "x::lru-" + System.nanoTime() + "-";
        for (int i = 0; i < 400; i++) {
            cache.getOrLayout(prefix + i, 16, 1, FontType.NORMAL, layout, METRICS, 0);
        }
        Assert.assertTrue("缓存条目数应受上限约束", cache.size() <= 300);
    }
}
