package club.heiqi.uilib.font.render.software;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;

/**
 * 装饰线基线补偿的「缺字格数据」回归（Linux CI 首红的一手复现，本机可跑）。
 *
 * <p>真机 GL 与 headless 场地共用 {@code DefaultFontRendererAdapter#drawPreparedTextIntoCollector}
 * 同一循环体。规则线自己没有字格，其「字格顶 → 基线」补偿只能向<b>产生它的公式段自己的字形</b>借；
 * 旧实现借的是「整行第一个 codepoint」的表值：Ubuntu runner 上 DejaVu Sans 无中日韩覆盖，行首「分」
 * 从未生成字格 → 直读表返回 0 → 补偿静默归零 → 分数线整体上飞一个基线（CI 实测 rule[-1,0]，
 * 而 Windows 同一条在 [12,13]，分子底边两边都是 10）。生产里同一形状还有一条更常见的触发路径：
 * 异步字形管线尚未出字的首帧。本测试把首个码点的表值清零，在 Windows 上复现同一现场。</p>
 */
public class LatexRuleBaselineMissingCellTest {

    private static final String MIXED = "分数：<latex>\\frac{1}{2}</latex> 尾部文本";

    /** 一条混排线里判据关心的三件：规则线上下沿、分子底边。 */
    private static final class Scene {
        int ruleTop;
        int ruleBottom;
        int ruleWidth;
        int numBottom;
        int glyphCount;
    }

    @Test
    public void ruleGeometryMustSurviveMissingFirstCodepointCellData() {
        Scene clean = scene(MIXED);
        Assert.assertTrue("前置：判据本身要有规则线与分子，否则测了个空", clean.numBottom > Integer.MIN_VALUE);
        Assert.assertTrue("前置：行首码点在本机应有字格基线，否则模拟不了任何事", baselineOf('分') > 0);

        int saved = baselineOf('分');
        setBaseline('分', 0); // 模拟「该码点没有字格数据」：字体无覆盖 / 字形尚未生成
        try {
            Scene polluted = scene(MIXED);
            Assert.assertEquals("行首字形缺表数据时，分数线上沿不得整体位移（旧缺陷会上飞一个基线）"
                    + "：干净态 ruleTop=" + clean.ruleTop + " 污染态 ruleTop=" + polluted.ruleTop,
                    clean.ruleTop, polluted.ruleTop);
            Assert.assertEquals("分数线厚度（下沿）同样不得随参考点变化"
                    + "：干净态 ruleBottom=" + clean.ruleBottom + " 污染态 ruleBottom=" + polluted.ruleBottom,
                    clean.ruleBottom, polluted.ruleBottom);
            Assert.assertEquals("规则线宽度不应随参考点变化", clean.ruleWidth, polluted.ruleWidth);
            Assert.assertTrue("分子必须仍在分数线上方（判据：bottom <= ruleTop + 1）"
                    + "：numBottom=" + polluted.numBottom + " ruleTop=" + polluted.ruleTop,
                    polluted.numBottom <= polluted.ruleTop + 1);
        } finally {
            setBaseline('分', saved);
        }
        Assert.assertEquals("复原后应与干净态一致（确认模拟没有留下副作用）",
                clean.ruleTop, scene(MIXED).ruleTop);
    }

    private static Scene scene(String richText) {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(richText, 16);
        List<LatexSoftwareRenderTest.Quad> rules =
                LatexSoftwareRenderTest.collectQuads(result.collector.getDecorationBatch());
        List<LatexSoftwareRenderTest.Quad> glyphs = LatexSoftwareRenderTest.collectGlyphQuads(result);
        Assert.assertFalse("混排分数应有规则线", rules.isEmpty());
        LatexSoftwareRenderTest.Quad rule = rules.get(0);
        Scene scene = new Scene();
        scene.ruleTop = rule.top;
        scene.ruleBottom = rule.bottom;
        scene.ruleWidth = rule.width();
        scene.glyphCount = glyphs.size();
        for (LatexSoftwareRenderTest.Quad glyph : glyphs) {
            if (glyph.bottom <= rule.top + 1) {
                scene.numBottom = Math.max(scene.numBottom, glyph.bottom);
            }
        }
        return scene;
    }

    /**
     * 场地内所有渲染共用同一份运行时表，借一次极短渲染即可拿到它（不为测试另开装配入口）。
     */
    private static int[] normalLineBaselines() {
        return LatexSoftwareRenderKit.render("a", 16).tables.lineBaselineYArray(FontType.NORMAL);
    }

    private static int baselineOf(int codepoint) {
        return normalLineBaselines()[codepoint];
    }

    private static void setBaseline(int codepoint, int value) {
        normalLineBaselines()[codepoint] = value;
    }
}
