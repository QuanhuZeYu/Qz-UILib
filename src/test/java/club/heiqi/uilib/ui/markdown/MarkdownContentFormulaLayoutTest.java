package club.heiqi.uilib.ui.markdown;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.latex.LatexParser;
import club.heiqi.uilib.font.latex.layout.MathBox;
import club.heiqi.uilib.font.latex.layout.MathLayoutService;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument.LayoutContent;
import club.heiqi.uilib.font.render.software.LatexSoftwareRenderKit;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;

/** 文档首尾公式的实际墨迹 oracle；不读取 L2 VisualLine 或复制其边界聚合算法。 */
public class MarkdownContentFormulaLayoutTest {
    private static final int FONT = 16;
    private static final String TABLE = "a|b\n-|-\nx|y";
    private static final String FORMULA = "\\frac{\\frac{a}{b}}{\\frac{c}{d}}";
    private int savedBudget;

    @Before public void unlimitedMetrics() {
        savedBudget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
    }
    @After public void restoreMetrics() { FontConfig.widthCacheMissBudgetPerWindow = savedBudget; }
    @AfterClass public static void releaseShared() { LatexSoftwareRenderKit.resetShared(); }
    private static TextLayoutService service() { return LatexSoftwareRenderKit.currentService(); }
    private static LayoutContent content(String source) {
        return MarkdownDocument.parse(source).toLayoutContent(null, new TextStyle());
    }
    private static String mixed(String name) {
        return name + " [$" + FORMULA + "$](https://" + name + ".formula.test) "
                + "[plain](https://" + name + ".plain.test)";
    }
    private static MarkdownPainter.ContentLayout layout(String source) {
        return MarkdownPainter.layoutContent(content(source), service(), 0, FONT);
    }

    @Test public void leadingFormulaInkIsInsideContentAndPrecedesTable() {
        MarkdownPainter.ContentLayout plan = layout(mixed("before") + "\n\n" + TABLE);
        PaintCommand text = formulaTexts(plan).get(0);
        double[] ink = mixedInk(text);
        Assert.assertTrue("fixture extends above the text origin", ink[0] < text.getTop());
        Assert.assertTrue("首行公式上缘不能靠负 scroll 才看见", ink[0] >= 0);
        Assert.assertTrue("公式完整占位，不能与后面的表格重叠", ink[1] <= firstBackgroundTop(plan));
        assertLinks(plan, text, "before", ink);
    }

    @Test public void trailingFormulaInkFitsSceneScrollExtent() {
        MarkdownPainter.ContentLayout plan = layout(TABLE + "\n\n" + mixed("after"));
        PaintCommand text = formulaTexts(plan).get(0);
        double[] ink = mixedInk(text);
        Assert.assertTrue("末行公式上缘在表格之后", ink[0] >= lastBackgroundBottom(plan));
        Assert.assertTrue("content height 必须含公式深度", ink[1] <= plan.getHeightPx());
        assertScrollReachesBottom(plan, ink[1]);
        assertLinks(plan, text, "after", ink);
    }

    @Test public void formulasSurroundingTableKeepOrderAfterRealPostprocess() {
        LayoutContent input = content(mixed("before") + "\n\n" + TABLE + "\n\n" + mixed("after"));
        // 与聊天同一服务做真实缩放后才度量；缩一次并不把公式压回字体行高。
        input = input.mapSegments(segments -> service().applyLatexLineHeightConstraint(
                segments, FONT, fontHeight(), 1.6F, 0.85F));
        MarkdownPainter.ContentLayout plan = MarkdownPainter.layoutContent(input, service(), 0, FONT);
        List<PaintCommand> formulas = formulaTexts(plan);
        Assert.assertEquals(2, formulas.size());
        double[] before = mixedInk(formulas.get(0));
        double[] after = mixedInk(formulas.get(1));
        Assert.assertTrue(before[0] >= 0);
        Assert.assertTrue(before[1] <= firstBackgroundTop(plan));
        Assert.assertTrue(lastBackgroundBottom(plan) <= after[0]);
        Assert.assertTrue(after[1] <= plan.getHeightPx());
        assertLinks(plan, formulas.get(0), "before", before);
        assertLinks(plan, formulas.get(1), "after", after);
    }

    @Test public void standaloneFormulaReservesItsWholeBoxBeforeFollowingParagraph() {
        MarkdownPainter.ContentLayout plan = layout(TABLE + "\n\n$$" + FORMULA + "$$\n\nend");
        PaintCommand formula = formulaTexts(plan).get(0);
        MathBox box = mathBox(latex(formula));
        PaintCommand end = null;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.SEGMENTS && "end".equals(textOf(command))) end = command;
        }
        Assert.assertNotNull(end);
        Assert.assertTrue("fixture taller than font metrics", box.getTotalHeight() > fontHeight());
        Assert.assertTrue("独占公式后续段落须让出整个公式盒", end.getTop() - formula.getTop() >= box.getTotalHeight());
        MarkdownPainter.ContentLayout tail = layout(TABLE + "\n\n$$" + FORMULA + "$$");
        PaintCommand tailFormula = formulaTexts(tail).get(0);
        Assert.assertTrue("末尾独占公式完整进入文档高度", tail.getHeightPx() - tailFormula.getTop() >= box.getTotalHeight());
        assertScrollReachesBottom(tail, tailFormula.getTop() + box.getTotalHeight());
    }

    @Test public void plainTextBlocksKeepHistoricalCommandGeometry() {
        String source = "# heading\n\n> quoted [plain](https://plain.test)\n\n- first\n- second\n\n"
                + "~~~\ncode\nsecond line\n~~~\n\n---";
        LayoutContent input = content(source);
        List<PaintCommand> old = MarkdownPainter.toLayoutPaintCommands(input.getLines(), service(), 200, FONT);
        List<PaintCommand> current = MarkdownPainter.layoutContent(input, service(), 200, FONT).getCommands();
        Assert.assertEquals(old.size(), current.size());
        for (int i = 0; i < old.size(); i++) {
            PaintCommand expected = old.get(i), actual = current.get(i);
            Assert.assertEquals(expected.getType(), actual.getType());
            Assert.assertEquals(expected.getLeft(), actual.getLeft());
            Assert.assertEquals(expected.getTop(), actual.getTop());
            Assert.assertEquals(expected.getRight(), actual.getRight());
            Assert.assertEquals(expected.getBottom(), actual.getBottom());
            Assert.assertEquals(expected.getColor(), actual.getColor());
            Assert.assertEquals(expected.getTextStyle(), actual.getTextStyle());
            Assert.assertEquals(expected.getLinkUrl(), actual.getLinkUrl());
            Assert.assertEquals(textOf(expected), textOf(actual));
        }
    }

    @Test public void historicalFormulaCommandsStillUseOriginalFontLineBoxes() {
        LayoutContent input = content(mixed("old") + "\n\nend");
        List<PaintCommand> old = MarkdownPainter.toLayoutPaintCommands(input.getLines(), service(), 0, FONT);
        PaintCommand formula = null, plainLink = null, formulaLink = null;
        for (PaintCommand command : old) {
            if (command.getType() == PaintCommandType.SEGMENTS && textOf(command).startsWith("old")) formula = command;
            if ("https://old.formula.test".equals(command.getLinkUrl())) formulaLink = command;
            if ("https://old.plain.test".equals(command.getLinkUrl())) plainLink = command;
        }
        Assert.assertNotNull(formula);
        Assert.assertNotNull(formulaLink);
        Assert.assertNotNull(plainLink);
        Assert.assertEquals(0, formula.getTop());
        Assert.assertEquals(fontHeight(), formulaLink.getBottom() - formulaLink.getTop());
        Assert.assertEquals(plainLink.getTop(), formulaLink.getTop());
        Assert.assertTrue("positive control: 旧行路没有悄悄迁移", mixedInk(formula)[0] < 0);
        Assert.assertTrue(MarkdownPainter.layoutContent(input, service(), 0, FONT).getHeightPx()
                > input.getLines().size() * fontHeight());
    }

    private static void assertLinks(MarkdownPainter.ContentLayout plan, PaintCommand text, String name, double[] ink) {
        PaintCommand formula = null, plain = null;
        for (PaintCommand command : plan.getCommands()) {
            if (("https://" + name + ".formula.test").equals(command.getLinkUrl())) formula = command;
            if (("https://" + name + ".plain.test").equals(command.getLinkUrl())) plain = command;
        }
        Assert.assertNotNull(formula);
        Assert.assertNotNull(plain);
        Assert.assertTrue(formula.getTop() <= ink[0]);
        Assert.assertTrue(formula.getBottom() >= ink[1]);
        Assert.assertEquals(text.getTop(), plain.getTop());
        Assert.assertEquals("plain链接不借用公式的额外纵向区域", fontHeight(), plain.getBottom() - plain.getTop());
        Assert.assertTrue(formula.getTop() < plain.getTop());
    }

    private static void assertScrollReachesBottom(MarkdownPainter.ContentLayout plan, double inkBottom) {
        int viewportHeight = fontHeight();
        SceneNode viewport = SceneNode.column(0).setScrollable(true).setClipChildren(true)
                .setPreferredWidth(Math.max(1, plan.getWidthPx())).setPreferredHeight(viewportHeight);
        SceneNode body = SceneNode.column(0).setPreferredWidth(Math.max(1, plan.getWidthPx()))
                .setPreferredHeight(plan.getHeightPx());
        viewport.appendChild(body);
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(viewport,
                new Constraints(Math.max(1, plan.getWidthPx()), viewportHeight));
        int max = SceneGeometry.maxScrollY(viewport);
        Assert.assertTrue("fixture needs real scrolling", max > 0);
        viewport.setScrollOffsetY(max);
        double bottomInViewport = SceneGeometry.absoluteBox(body, 0, 0).getY() + inkBottom;
        Assert.assertTrue("滚到 max 后公式实际底边可达", bottomInViewport <= viewportHeight);
        Assert.assertTrue("实际底边不是被滚过视口顶部", bottomInViewport > 0);
    }

    private static List<PaintCommand> formulaTexts(MarkdownPainter.ContentLayout plan) {
        List<PaintCommand> out = new ArrayList<PaintCommand>();
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.SEGMENTS) {
                for (TextSegment segment : command.getSegments()) {
                    if (segment.isLatex()) { out.add(command); break; }
                }
            }
        }
        return out;
    }
    private static TextSegment latex(PaintCommand text) {
        for (TextSegment segment : text.getSegments()) if (segment.isLatex()) return segment;
        throw new AssertionError("missing formula");
    }
    private static MathBox mathBox(TextSegment segment) {
        int size = segment.getStyle().resolveEffectiveFontSizePx(FONT);
        return new MathLayoutService().layout(LatexParser.parse(segment.getLatexSource()), size,
                service().createMathMetrics(segment.getStyle(), size));
    }
    private static double[] mixedInk(PaintCommand text) {
        MathBox box = mathBox(latex(text));
        // 混排正文提供基线：从真实数学盒直接推出墨迹，独立于生产行高/offset聚合。
        double baseline = text.getTop() + service().getAscent(FONT);
        return new double[] {baseline - box.getHeight(), baseline + box.getDepth()};
    }
    private static int fontHeight() {
        return service().getAscent(FONT) + service().getDescent(FONT) + service().getLineGap(FONT);
    }
    private static String textOf(PaintCommand command) {
        StringBuilder text = new StringBuilder();
        if (command.getSegments() != null) for (TextSegment s : command.getSegments()) text.append(s.getText());
        return text.toString();
    }
    private static int firstBackgroundTop(MarkdownPainter.ContentLayout plan) {
        int top = Integer.MAX_VALUE;
        for (PaintCommand c : plan.getCommands()) if (c.getType() == PaintCommandType.BACKGROUND) top = Math.min(top, c.getTop());
        return top;
    }
    private static int lastBackgroundBottom(MarkdownPainter.ContentLayout plan) {
        int bottom = 0;
        for (PaintCommand c : plan.getCommands()) if (c.getType() == PaintCommandType.BACKGROUND) bottom = Math.max(bottom, c.getBottom());
        return bottom;
    }
}
