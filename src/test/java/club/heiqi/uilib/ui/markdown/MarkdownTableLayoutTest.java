package club.heiqi.uilib.ui.markdown;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import club.heiqi.uilib.font.layout.markdown.MarkdownSpan;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument.LayoutContent;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument.TableUnit;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.font.render.software.LatexSoftwareRenderKit;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;

/** T2 三案证据及生产布局：真实共享度量，判据从输入和布局推导，不硬编码平台像素。 */
public class MarkdownTableLayoutTest {
    private static final int FONT = 16;
    private static final String TABLE = "a|b\n-| -\nx|y";
    private int savedBudget;

    @Before
    public void unlimitedMeasurement() {
        savedBudget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
    }

    @After
    public void restoreBudget() { FontConfig.widthCacheMissBudgetPerWindow = savedBudget; }

    @AfterClass
    public static void releaseShared() { LatexSoftwareRenderKit.resetShared(); }

    private static TextLayoutService service() { return LatexSoftwareRenderKit.currentService(); }
    private static LayoutContent content(String text) {
        return MarkdownDocument.parse(text).toLayoutContent(null, new TextStyle());
    }
    private static List<String> order(LayoutContent input) {
        List<String> out = new ArrayList<String>();
        int t = 0;
        for (int i = 0; i <= input.getLines().size(); i++) {
            while (t < input.getTables().size() && input.getTables().get(t).getBeforeLineIndex() == i) {
                out.add("TABLE");
                t++;
            }
            if (i < input.getLines().size()) {
                StringBuilder text = new StringBuilder();
                for (TextSegment segment : input.getLines().get(i).getSegments()) {
                    text.append(segment.getText());
                }
                out.add(text.toString());
            }
        }
        Assert.assertEquals(input.getTables().size(), t);
        return out;
    }

    @Test
    public void parallelUnitsPreserveParagraphTableParagraphAndBlankBoundaries() {
        Assert.assertEquals(Arrays.asList("before", "", "TABLE", "", "after"),
                order(content("before\n\n" + TABLE + "\n\nafter")));
        Assert.assertEquals(Collections.singletonList("TABLE"), order(content(TABLE)));
        Assert.assertEquals(Arrays.asList("TABLE", "", "after"), order(content(TABLE + "\n\nafter")));
        Assert.assertEquals(Arrays.asList("before", "", "TABLE"), order(content("before\n\n" + TABLE)));
    }

    @Test
    public void adjacentTablesRetainOrderAndDistinctSemanticAnchors() {
        LayoutContent input = content(TABLE + "\n\nc|d\n-| -\nu|v");
        Assert.assertEquals(Arrays.asList("TABLE", "", "TABLE"), order(input));
        Assert.assertEquals(Arrays.asList(0), input.getTables().get(0).getModel().getBlockPath());
        Assert.assertEquals(Arrays.asList(1), input.getTables().get(1).getModel().getBlockPath());
        Assert.assertEquals(Arrays.asList("TABLE", "TABLE"), order(content(
                "> a|b\n> -|-\n> x|y\n>> q|r\n>> -| -\n>> s|t")));
    }

    @Test
    public void quoteListContextNeverBecomesSentinelOrLeaksToFollowingParagraph() {
        LayoutContent input = content("> - a|b\n>   -|-\n>   x|y\n\nafter");
        Assert.assertEquals(1, input.getTables().size());
        TableUnit table = input.getTables().get(0);
        Assert.assertEquals(Arrays.asList(0, 0, 0, 0), table.getModel().getBlockPath());
        Assert.assertEquals(1, table.getContext().getQuoteLevel());
        Assert.assertEquals(1, table.getContext().getListMarkerChain().size());
        Assert.assertTrue(table.getContext().getSegments().isEmpty());
        Assert.assertFalse(input.getLines().contains(table.getContext()));
        MarkdownLayoutLine after = input.getLines().get(input.getLines().size() - 1);
        Assert.assertEquals(0, after.getQuoteLevel());
        Assert.assertTrue(after.getListMarkerChain().isEmpty());
        MarkdownTableLayout.Result plan = MarkdownTableLayout.layout(table, service(), 400, FONT);
        int expectedInset = MarkdownLineLayout.layoutLines(Collections.singletonList(table.getContext()),
                service(), 400, FONT).get(0).getLeftInsetPx();
        PaintCommand first = texts(plan.commands).get(0);
        Assert.assertEquals(expectedInset + table.getBorderPx() + table.getPaddingXPx(), first.getLeft());
    }

    @Test
    public void sentinelPrototypeAddsObservableBlankLineToExistingConsumerSeam() {
        MarkdownDocument document = MarkdownDocument.parse(TABLE);
        List<MarkdownLayoutLine> literal = document.toLayoutLines(null, new TextStyle());
        List<MarkdownLayoutLine> sentinel = new ArrayList<MarkdownLayoutLine>(literal);
        sentinel.add(0, MarkdownLayoutLine.blank());
        List<MarkdownLayoutLine> original = MarkdownPainter.wrapLayoutLines(literal, service(), 400, FONT);
        List<MarkdownLayoutLine> proposed = MarkdownPainter.wrapLayoutLines(sentinel, service(), 400, FONT);
        Assert.assertEquals(original.size() + 1, proposed.size());
        Assert.assertTrue(proposed.get(0).getSegments().isEmpty());
        Assert.assertEquals(height(original)
                        + MarkdownPainter.lineHeightPx(Collections.<TextSegment>emptyList(), service(), FONT),
                height(proposed));
        // 并非表格身份：旧消费者会按真实空显示行计预算，模型还必须另行旁挂。
        Assert.assertEquals(MarkdownLayoutLine.Kind.TEXT, proposed.get(0).getKind());
    }

    @Test
    public void unionPrototypeWouldReplaceFrozenGenericReturnType() throws Exception {
        ParameterizedType type = (ParameterizedType) MarkdownDocument.class
                .getMethod("toLayoutLines", MarkdownStyleTable.class, TextStyle.class).getGenericReturnType();
        Assert.assertEquals(MarkdownLayoutLine.class, type.getActualTypeArguments()[0]);
        Assert.assertTrue(java.lang.reflect.Modifier.isFinal(MarkdownLayoutLine.class.getModifiers()));
        Assert.assertFalse(MarkdownLayoutLine.class.isAssignableFrom(TableUnit.class));
    }

    @Test
    public void bodyIntrinsicWidthInfluencesColumnAllocationBeforeWrapping() {
        TableUnit small = content("a|b\n-| -\nx|y").getTables().get(0);
        TableUnit large = content("a|b\n-| -\nabcdefghijklmno|y").getTables().get(0);
        MarkdownTableLayout.Result a = MarkdownTableLayout.layout(small, service(), 0, FONT);
        MarkdownTableLayout.Result b = MarkdownTableLayout.layout(large, service(), 0, FONT);
        Assert.assertTrue(b.columnWidths[0] > a.columnWidths[0]);
        Assert.assertEquals(a.columnWidths[1], b.columnWidths[1]);
        Assert.assertEquals(a.rowHeights[0], b.rowHeights[0]);
        Assert.assertEquals(a.rowHeights[1], b.rowHeights[1]);
    }

    @Test
    public void narrowerAllocationWrapsCellsAndRowHeightUsesTallestCell() {
        TableUnit unit = content("a|b\n-| -\nalpha beta gamma delta epsilon zeta|short").getTables().get(0);
        MarkdownTableLayout.Result wide = MarkdownTableLayout.layout(unit, service(), 0, FONT);
        int narrowWidth = Math.max(1, wide.width / 2);
        MarkdownTableLayout.Result narrow = MarkdownTableLayout.layout(unit, service(), narrowWidth, FONT);
        Assert.assertTrue(narrow.width <= narrowWidth);
        Assert.assertTrue(narrow.rowHeights[1] > wide.rowHeights[1]);
        int expected = 0;
        for (int c = 0; c < unit.getModel().getAlignments().size(); c++) {
            List<List<TextSegment>> lines = MarkdownPainter.wrapLines(
                    unit.getModel().getRows().get(0).getCells().get(c).getSegments(), service(),
                    narrow.columnWidths[c], FONT);
            int height = 0;
            for (List<TextSegment> line : lines) {
                height += MarkdownPainter.lineHeightPx(line, service(), FONT);
            }
            expected = Math.max(expected, height);
        }
        Assert.assertEquals(expected + 2 * unit.getPaddingYPx(), narrow.rowHeights[1]);
        Assert.assertEquals(unit.getBorderPx() * (narrow.rowHeights.length + 1)
                + narrow.rowHeights[0] + narrow.rowHeights[1], narrow.height);
    }

    @Test
    public void alignmentAndLinkRegionsShareThePositionedTextOrigin() {
        TableUnit unit = content("long left|long middle|long right\n:--|:-:|--:\n[x](https://x.test)|x|x")
                .getTables().get(0);
        MarkdownTableLayout.Result result = MarkdownTableLayout.layout(unit, service(), 0, FONT);
        List<PaintCommand> text = texts(result.commands);
        Assert.assertEquals(6, text.size());
        int x = unit.getBorderPx() + unit.getPaddingXPx();
        for (int c = 0; c < 3; c++) {
            PaintCommand command = text.get(3 + c);
            int slack = result.columnWidths[c] - MarkdownPainter.lineWidthPx(command.getSegments(), service(), FONT);
            Assert.assertEquals(x + (c == 1 ? slack / 2 : c == 2 ? slack : 0), command.getLeft());
            x += result.columnWidths[c] + 2 * unit.getPaddingXPx() + unit.getBorderPx();
        }
        List<PaintCommand> links = new ArrayList<PaintCommand>();
        for (PaintCommand command : result.commands) {
            if (command.getType() == PaintCommandType.LINK_REGION) { links.add(command); }
        }
        Assert.assertEquals(1, links.size());
        Assert.assertEquals(text.get(3).getLeft(), links.get(0).getLeft());
        Assert.assertEquals(text.get(3).getTop(), links.get(0).getTop());
        Assert.assertTrue(links.get(0).getRight() > links.get(0).getLeft());
    }

    @Test
    public void emptyCellsAndUnbreakableFormulaKeepPositiveGeometry() {
        TableUnit unit = content("a|b\n-| -\n|$x^2$").getTables().get(0);
        MarkdownTableLayout.Result result = MarkdownTableLayout.layout(unit, service(), 1, FONT);
        Assert.assertTrue(result.width > 1);
        for (int width : result.columnWidths) { Assert.assertTrue(width > 0); }
        for (int height : result.rowHeights) { Assert.assertTrue(height > 0); }
        int formulas = 0;
        for (PaintCommand command : texts(result.commands)) {
            for (TextSegment segment : command.getSegments()) {
                if (segment.isLatex()) { formulas++; }
            }
        }
        Assert.assertEquals(1, formulas);
    }

    @Test
    public void documentPlanPositionsFollowingParagraphAfterWholeTableAndBlank() {
        LayoutContent input = content("before\n\n" + TABLE + "\n\nafter");
        MarkdownPainter.ContentLayout plan = MarkdownPainter.layoutContent(input, service(), 400, FONT);
        List<PaintCommand> text = texts(plan.getCommands());
        Assert.assertEquals("before", text.get(0).getSegments().get(0).getText());
        PaintCommand last = text.get(text.size() - 1);
        Assert.assertEquals("after", last.getSegments().get(0).getText());
        MarkdownTableLayout.Result table = MarkdownTableLayout.layout(input.getTables().get(0), service(), 400, FONT);
        int lineHeight = MarkdownPainter.lineHeightPx(Collections.<TextSegment>emptyList(), service(), FONT);
        Assert.assertEquals(3 * lineHeight + table.height, last.getTop());
        Assert.assertEquals(last.getTop() + lineHeight, plan.getHeightPx());
        Assert.assertTrue(plan.getWidthPx() >= table.width);
        try {
            plan.getCommands().clear();
            Assert.fail("commands must be read-only");
        } catch (UnsupportedOperationException expected) { }
    }

    @Test
    public void nonTableDocumentReusesExistingLinePlanExactly() {
        String source = "# head\n\n> quoted\n\n- item\n\n~~~\ncode\n~~~";
        MarkdownDocument document = MarkdownDocument.parse(source);
        List<MarkdownLayoutLine> visual = MarkdownPainter.wrapLayoutLines(
                document.toLayoutLines(null, new TextStyle()), service(), 140, FONT);
        List<PaintCommand> expected = MarkdownPainter.toLayoutPaintCommands(
                document.toLayoutLines(null, new TextStyle()), service(), 140, FONT);
        MarkdownPainter.ContentLayout actual = MarkdownPainter.layoutContent(
                document.toLayoutContent(null, new TextStyle()), service(), 140, FONT);
        Assert.assertEquals(expected.size(), actual.getCommands().size());
        for (int i = 0; i < expected.size(); i++) {
            PaintCommand a = expected.get(i);
            PaintCommand b = actual.getCommands().get(i);
            Assert.assertEquals(a.getType(), b.getType());
            Assert.assertEquals(a.getLeft(), b.getLeft());
            Assert.assertEquals(a.getTop(), b.getTop());
            Assert.assertEquals(a.getRight(), b.getRight());
            Assert.assertEquals(a.getBottom(), b.getBottom());
        }
        Assert.assertEquals(height(visual), actual.getHeightPx());
        Assert.assertEquals(0, MarkdownPainter.layoutContent(content(""), service(), 140, FONT).getHeightPx());
    }

    @Test
    public void firstTableInListSharesHeaderTopWithoutSwallowingRealParagraph() {
        for (String prefix : Arrays.asList("", "> ")) {
            LayoutContent input = content(String.join(String.valueOf((char) 10),
                    prefix + "- a|b", prefix + "  -|-", prefix + "  x|y"));
            TableUnit unit = input.getTables().get(0);
            MarkdownTableLayout.Result table = MarkdownTableLayout.layout(unit, service(), 400, FONT);
            MarkdownPainter.ContentLayout plan = MarkdownPainter.layoutContent(input, service(), 400, FONT);
            Assert.assertEquals(table.height, plan.getHeightPx());
            List<PaintCommand> text = texts(plan.getCommands());
            PaintCommand marker = text.get(text.size() - 1);
            Assert.assertEquals(unit.getContext().getListMarkerChain().get(0).getText().trim(),
                    marker.getSegments().get(0).getText());
            Assert.assertEquals(text.get(0).getTop(), marker.getTop());
            Assert.assertTrue(marker.getLeft() < text.get(0).getLeft());
        }
        LayoutContent withParagraph = content(String.join(String.valueOf((char) 10),
                "- before", "", "  a|b", "  -|-", "  x|y"));
        TableUnit unit = withParagraph.getTables().get(0);
        MarkdownPainter.ContentLayout plan = MarkdownPainter.layoutContent(withParagraph, service(), 400, FONT);
        Assert.assertTrue(plan.getHeightPx() > MarkdownTableLayout.layout(unit, service(), 400, FONT).height);
        Assert.assertEquals("before", texts(plan.getCommands()).get(0).getSegments().get(1).getText());
    }

    @Test
    public void tallFormulaFitsItsCellAndFollowingRowForPureAndMixedLines() {
        String slash = String.valueOf((char) 92);
        String formula = slash + "frac{" + slash + "frac{a}{b}}{" + slash + "frac{c}{d}}";
        for (String prefix : Arrays.asList("", "text ")) {
            TableUnit unit = content(String.join(String.valueOf((char) 10),
                    "a|b", "-|-", prefix + "$" + formula + "$|x", "y|z")).getTables().get(0);
            MarkdownTableLayout.Result plan = MarkdownTableLayout.layout(unit, service(), 0, FONT);
            List<TextSegment> segments = unit.getModel().getRows().get(0).getCells().get(0).getSegments();
            TextSegment latex = segments.get(segments.size() - 1);
            Assert.assertTrue(latex.isLatex());
            MathBox box = new MathLayoutService().layout(LatexParser.parse(formula), FONT,
                    service().createMathMetrics(latex.getStyle(), FONT));
            Assert.assertTrue("fixture must exceed font line height", box.getTotalHeight()
                    > MarkdownPainter.lineHeightPx(segments, service(), FONT));
            Assert.assertTrue(plan.rowHeights[1] >= Math.ceil(box.getTotalHeight()) + 2 * unit.getPaddingYPx());
            List<PaintCommand> text = texts(plan.commands);
            PaintCommand formulaCommand = text.get(2);
            int rowTop = unit.getBorderPx() * 2 + plan.rowHeights[0];
            double inkTop;
            double inkBottom;
            if (prefix.isEmpty()) {
                double pureHeight = Math.max(MarkdownPainter.lineHeightPx(segments, service(), FONT),
                        Math.ceil(box.getTotalHeight() + 2 * TextLayoutService.LATEX_LINE_PAD_EM * FONT));
                inkTop = formulaCommand.getTop() + (pureHeight - box.getTotalHeight()) / 2;
                inkBottom = inkTop + box.getTotalHeight();
            } else {
                inkTop = formulaCommand.getTop() + service().getAscent(FONT) - box.getHeight();
                inkBottom = formulaCommand.getTop() + service().getAscent(FONT) + box.getDepth();
            }
            Assert.assertTrue(inkTop >= rowTop + unit.getPaddingYPx());
            Assert.assertTrue(inkBottom <= rowTop + plan.rowHeights[1] - unit.getPaddingYPx());
            Assert.assertTrue(text.get(4).getTop() > inkBottom);
        }
    }

    @Test
    public void cellFontSizeAndQuotedCustomStylesSurviveTheNewProjection() {
        TextStyle large = new TextStyle();
        large.setFontSizePx(FONT * 2);
        String nl = String.valueOf((char) 10);
        MarkdownDocument document = MarkdownDocument.parseSpans(Arrays.asList(
                new MarkdownSpan("> a|b" + nl + "> -|-" + nl + "> ", new TextStyle()),
                new MarkdownSpan("large", large), new MarkdownSpan("|small", new TextStyle())));
        MarkdownStyleTable styles = MarkdownStyleTable.defaults();
        styles.setQuoteItalic(true);
        styles.setQuoteTextColor(0xFF123456);
        TableUnit unit = document.toLayoutContent(styles, new TextStyle()).getTables().get(0);
        TextStyle projected = unit.getModel().getRows().get(0).getCells().get(0).getSegments().get(0).getStyle();
        Assert.assertEquals(FONT * 2, projected.getFontSizePx());
        Assert.assertTrue(projected.isItalic());
        Assert.assertEquals(styles.getQuoteTextColor(), projected.getColor());
        MarkdownTableLayout.Result plan = MarkdownTableLayout.layout(unit, service(), 0, FONT);
        Assert.assertTrue(plan.rowHeights[1] > plan.rowHeights[0]);
    }

    @Test
    public void linkedMixedFormulaIncludesInkAboveTextTopWhilePlainLinkKeepsTextBox() {
        String slash = String.valueOf((char) 92);
        String formula = slash + "frac{" + slash + "frac{a}{b}}{" + slash + "frac{c}{d}}";
        TableUnit unit = content(String.join(String.valueOf((char) 10), "a|b", "-|-",
                "cell text [$" + formula + "$](https://formula.test) [plain](https://plain.test)|x"))
                .getTables().get(0);
        MarkdownTableLayout.Result result = MarkdownTableLayout.layout(unit, service(), 0, FONT);
        PaintCommand formulaText = texts(result.commands).get(2);
        PaintCommand formulaLink = null;
        PaintCommand plainLink = null;
        TextSegment latex = null;
        for (TextSegment segment : formulaText.getSegments()) {
            if (segment.isLatex()) { latex = segment; }
        }
        for (PaintCommand command : result.commands) {
            if ("https://formula.test".equals(command.getLinkUrl())) { formulaLink = command; }
            if ("https://plain.test".equals(command.getLinkUrl())) { plainLink = command; }
        }
        Assert.assertNotNull(latex);
        Assert.assertNotNull(formulaLink);
        Assert.assertNotNull(plainLink);
        MathBox box = new MathLayoutService().layout(LatexParser.parse(formula), FONT,
                service().createMathMetrics(latex.getStyle(), FONT));
        double inkTop = formulaText.getTop() + service().getAscent(FONT) - box.getHeight();
        double inkBottom = formulaText.getTop() + service().getAscent(FONT) + box.getDepth();
        Assert.assertTrue("fixture needs ink above text origin", inkTop < formulaText.getTop());
        Assert.assertTrue("formula link includes upper ink", formulaLink.getTop() <= inkTop);
        Assert.assertTrue("formula link includes lower ink", formulaLink.getBottom() >= inkBottom);
        Assert.assertEquals(formulaText.getTop(), plainLink.getTop());
        Assert.assertTrue(formulaLink.getTop() < plainLink.getTop());
        Assert.assertTrue(formulaLink.getRight() > formulaLink.getLeft());
    }

    @Test
    public void cappedEqualAllocationKeepsShortColumnsNaturalWhileLongDescriptionWraps() {
        TableUnit unit = content(String.join(String.valueOf((char) 10),
                "Left|Center|Right", ":--|:-:|--:",
                "Formula|Words that wrap within a narrow cell without losing the row border "
                        + "and a longer description that should yield space to short readable columns|128"))
                .getTables().get(0);
        MarkdownTableLayout.Result natural = MarkdownTableLayout.layout(unit, service(), 0, FONT);
        MarkdownTableLayout.Result minimum = MarkdownTableLayout.layout(unit, service(), 1, FONT);
        int minimumTotal = 0;
        int naturalTotal = 0;
        for (int c = 0; c < natural.columnWidths.length; c++) {
            minimumTotal += minimum.columnWidths[c];
            naturalTotal += natural.columnWidths[c];
        }
        int decorations = natural.width - naturalTotal;
        int shortGrowth = Math.max(natural.columnWidths[0] - minimum.columnWidths[0],
                natural.columnWidths[2] - minimum.columnWidths[2]);
        int growthBudget = natural.columnWidths.length * shortGrowth;
        // 由当前真度量构造：每列均分额度足够保全两个短列，但不足整表 natural 宽。
        for (int remainder = 0; remainder < natural.columnWidths.length; remainder++) {
            int availableWidth = decorations + minimumTotal + growthBudget + remainder;
            Assert.assertTrue("long description must exceed the constructed budget", availableWidth < natural.width);
            MarkdownTableLayout.Result constrained = MarkdownTableLayout.layout(unit, service(), availableWidth, FONT);
            Assert.assertEquals("short Formula column remains natural", natural.columnWidths[0], constrained.columnWidths[0]);
            Assert.assertEquals("short Right column remains natural", natural.columnWidths[2], constrained.columnWidths[2]);
            Assert.assertTrue(constrained.columnWidths[1] < natural.columnWidths[1]);
            Assert.assertEquals("integer remainder stays inside the budget", availableWidth, constrained.width);
            Assert.assertTrue("long description absorbs wrapping", constrained.rowHeights[1] > natural.rowHeights[1]);
            for (int c = 0; c < constrained.columnWidths.length; c++) {
                Assert.assertTrue(constrained.columnWidths[c] >= minimum.columnWidths[c]);
                Assert.assertTrue(constrained.columnWidths[c] <= natural.columnWidths[c]);
            }
            List<String> visibleLines = new ArrayList<String>();
            for (PaintCommand command : texts(constrained.commands)) {
                StringBuilder text = new StringBuilder();
                for (TextSegment segment : command.getSegments()) { text.append(segment.getText()); }
                visibleLines.add(text.toString());
            }
            Assert.assertTrue(visibleLines.contains("Right"));
            Assert.assertTrue(visibleLines.contains("Formula"));
        }
    }

    private static int height(List<MarkdownLayoutLine> lines) {
        int height = 0;
        for (MarkdownLayoutLine line : lines) {
            height += MarkdownPainter.lineHeightPx(line.getSegments(), service(), FONT);
        }
        return height;
    }

    private static List<PaintCommand> texts(List<PaintCommand> commands) {
        List<PaintCommand> out = new ArrayList<PaintCommand>();
        for (PaintCommand command : commands) {
            if (command.getType() == PaintCommandType.SEGMENTS) { out.add(command); }
        }
        return out;
    }
}
