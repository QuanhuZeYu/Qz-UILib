package club.heiqi.uilib.font.layout.markdown;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link MarkdownStyleTable#scaledDesignMetrics(float)}：长度类设计量的唯一换算面。
 *
 * <p>守的是一个真实缺口：行内 code 字号等长度量此前既无公共 setter、也无统一换算口，
 * 下游无法把设计量换算成生效量 —— 实测 fs=200 时行内 code 仍是 12px 而正文 28px（应为 24）、
 * fs=50 时 code 比正文还大，即同一段内容里两种尺度（与页面几何/渲染分叉 F36 同源，
 * 独立审核 2026-09-19 实测并指出）。</p>
 */
public class MarkdownStyleTableDesignScaleTest {

    @Test
    public void scaledDesignMetricsScalesLengthsOnlyAndKeepsTheSourceIntact() {
        MarkdownStyleTable base = MarkdownStyleTable.defaults();
        MarkdownStyleTable doubled = base.scaledDesignMetrics(2.0f);

        Assert.assertEquals("行内 code 字号随倍率（缺此换算时恒为 12）",
                24, doubled.getCodeFontSizePx());
        Assert.assertEquals("引用步长随倍率", 16, doubled.getQuoteIndentPx());
        Assert.assertEquals("引用竖条宽随倍率", 4, doubled.getQuoteBarWidthPx());
        Assert.assertEquals("分隔线厚随倍率", 2, doubled.getRuleThicknessPx());
        Assert.assertEquals("表格内衬随倍率", 12, doubled.tablePaddingXPx);
        Assert.assertEquals("表格边框厚随倍率", 2, doubled.tableBorderPx);
        Assert.assertEquals("颜色与倍率无关（code 衬底）",
                base.getCodeBackgroundColor(), doubled.getCodeBackgroundColor());
        Assert.assertEquals("标记字符串与倍率无关", base.getBulletMarker(), doubled.getBulletMarker());

        Assert.assertEquals("本表不被修改（返回新表）", 12, base.getCodeFontSizePx());
        Assert.assertEquals("scale=1 逐值恒等", 12, base.scaledDesignMetrics(1.0f).getCodeFontSizePx());
        Assert.assertEquals("scale=0 长度归零", 0, base.scaledDesignMetrics(0.0f).getCodeFontSizePx());
    }

    @Test
    public void scaledDesignMetricsFollowsTheFontSizeExitForTheBaseFontSize() {
        MarkdownStyleTable designed = MarkdownStyleTable.defaults();
        designed.setDefaultFontSizePx(14);
        designed.setHeadingFontSizeDeltaPx(1, 10);
        MarkdownStyleTable scaled = designed.scaledDesignMetrics(1.5f);

        Assert.assertEquals("基准字号与字号域解析出口同源", 21, scaled.getDefaultFontSizePx());
        Assert.assertEquals("标题增量随倍率（等比，层级不被压缩）", 15,
                scaled.getHeadingFontSizeDeltaPx(1));
    }
}
