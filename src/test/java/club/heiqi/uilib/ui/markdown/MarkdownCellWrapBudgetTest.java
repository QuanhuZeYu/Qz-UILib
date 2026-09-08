package club.heiqi.uilib.ui.markdown;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.render.software.LatexSoftwareRenderKit;

/** 单元格严格预算复用同一换行器；公式词回退既要保全文本也要保留原子。 */
public class MarkdownCellWrapBudgetTest {
    private int savedBudget;
    @Before public void setup() {
        savedBudget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
    }
    @After public void restore() { FontConfig.widthCacheMissBudgetPerWindow = savedBudget; }
    @AfterClass public static void release() { LatexSoftwareRenderKit.resetShared(); }

    @Test
    public void wordSuffixAndFormulaAreRetriedAgainstColumnBudget() {
        TextLayoutService measurer = LatexSoftwareRenderKit.currentService();
        TextStyle style = new TextStyle();
        TextSegment formula = TextSegment.forLatex("\\frac{abcdefgh}{ijklmnop}", style);
        int font = 16;
        int budget = (int) Math.ceil(measurer.getSegmentWidth(formula, font));
        List<TextSegment> prefix = Arrays.asList(new TextSegment("a b", style));
        Assert.assertTrue("前置：前缀可容纳，公式原子主导最小列宽",
                MarkdownPainter.lineWidthPx(prefix, measurer, font) < budget);
        List<TextSegment> source = Arrays.asList(prefix.get(0), formula);
        List<List<TextSegment>> lines = MarkdownLineLayout.wrapCell(source, measurer, budget, font);
        StringBuilder text = new StringBuilder();
        int atoms = 0;
        for (List<TextSegment> line : lines) {
            Assert.assertTrue("每行必须落在分配预算内",
                    MarkdownPainter.lineWidthPx(line, measurer, font) <= budget);
            for (TextSegment segment : line) {
                if (segment.isLatex()) {
                    atoms++;
                    Assert.assertEquals(formula.getLatexSource(), segment.getLatexSource());
                } else {
                    text.append(segment.getText());
                }
            }
        }
        Assert.assertEquals("回退仅移除断行空白，不能吞字", "ab", text.toString());
        Assert.assertEquals("公式原子既不能拆开也不能重复", 1, atoms);
        Assert.assertEquals("同一预算下重复排版的行数稳定", lines.size(),
                MarkdownLineLayout.wrapCell(source, measurer, budget, font).size());
        boolean legacyOverflow = false;
        for (List<TextSegment> line : MarkdownLineLayout.wrap(source, measurer, budget, font)) {
            legacyOverflow |= MarkdownPainter.lineWidthPx(line, measurer, font) > budget;
        }
        Assert.assertTrue("正对照：此样本必须击中旧词回退边界，旧降级出口未被被动迁移", legacyOverflow);
    }
}
