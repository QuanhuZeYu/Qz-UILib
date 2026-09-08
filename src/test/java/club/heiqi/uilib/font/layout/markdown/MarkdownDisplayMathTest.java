package club.heiqi.uilib.font.layout.markdown;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import club.heiqi.uilib.font.latex.MathStyleOverride;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument.LayoutContent;

/** 新数学出口与完整旧词法隔离；只验证解析/模型，不依赖像素或参考渲染器。 */
public class MarkdownDisplayMathTest {
    private static LayoutContent content(String source) {
        return MarkdownDocument.parse(source).toLayoutContent(null, new TextStyle());
    }
    private static List<TextSegment> formulas(List<TextSegment> segments) {
        List<TextSegment> out = new ArrayList<TextSegment>();
        for (TextSegment segment : segments) if (segment.isLatex()) out.add(segment);
        return out;
    }
    private static List<MarkdownLayoutLine> mathLines(LayoutContent content) {
        List<MarkdownLayoutLine> out = new ArrayList<MarkdownLayoutLine>();
        for (MarkdownLayoutLine line : content.getLines()) {
            if (line.getKind() == MarkdownLayoutLine.Kind.MATH_DISPLAY) out.add(line);
        }
        return out;
    }
    private static String text(List<TextSegment> segments) {
        StringBuilder out = new StringBuilder();
        for (TextSegment segment : segments) out.append(segment.isLatex()
                ? "<math>" + segment.getLatexSource() + "</math>" : segment.getText());
        return out.toString();
    }
    private static TextSegment onlyMath(String source) {
        List<MarkdownLayoutLine> lines = mathLines(content(source));
        Assert.assertEquals(source, 1, lines.size());
        Assert.assertEquals(1, lines.get(0).getSegments().size());
        TextSegment segment = lines.get(0).getSegments().get(0);
        Assert.assertEquals(MathStyleOverride.DISPLAY, segment.getLatexMathStyle());
        return segment;
    }

    @Test public void inlineDisplayIsAtomicAndSingleDollarRemainsText() {
        List<TextSegment> parsed = MarkdownInlineParser.parse("a $$2+2$$ b $x$ c $$ x $$", new TextStyle());
        List<TextSegment> math = formulas(parsed);
        Assert.assertEquals(3, math.size());
        Assert.assertEquals("2+2", math.get(0).getLatexSource());
        Assert.assertEquals(MathStyleOverride.DISPLAY, math.get(0).getLatexMathStyle());
        Assert.assertEquals(MathStyleOverride.TEXT, math.get(1).getLatexMathStyle());
        Assert.assertEquals(" x ", math.get(2).getLatexSource());
        Assert.assertEquals(1, content("a $$x$$ b").getLines().size());
        Assert.assertEquals(MarkdownLayoutLine.Kind.TEXT, content("a $$x$$ b").getLines().get(0).getKind());
        Assert.assertTrue(mathLines(content("$x$")).isEmpty());
    }

    @Test public void modernDollarRunsAndPhysicalLinesAreLiteralWhenInvalid() {
        for (String source : Arrays.asList("$$$$", "$$$x$$$", "$$x$$$", "$$ $$", "$$\t$$",
                "$$x$Z", "a $$x\ny$$ b", "a $$x\ry$$ b", "a $$x\r\ny$$ b")) {
            List<TextSegment> parsed = MarkdownInlineParser.parse(source, new TextStyle());
            Assert.assertTrue(source, formulas(parsed).isEmpty());
            Assert.assertEquals(source, text(parsed));
        }
        Assert.assertEquals("<math>x$y</math>Z",
                text(MarkdownInlineParser.parse("$$x$y$$Z", new TextStyle())));
        Assert.assertEquals("x\\$y", onlyMath("$$x\\$y$$").getLatexSource());
    }

    @Test public void historicalExitsKeepFullLegacyLexingAndRootStyle() {
        for (String source : Arrays.asList("$$2+2$$", "$$ x $$")) {
            MarkdownDocument doc = MarkdownDocument.parse(source);
            Assert.assertEquals(source, text(doc.toSegments(new TextStyle())));
            Assert.assertEquals(source, text(doc.toLayoutLines(null, new TextStyle()).get(0).getSegments()));
            Assert.assertEquals(1, mathLines(doc.toLayoutContent(null, new TextStyle())).size());
        }
        MarkdownDocument multiline = MarkdownDocument.parse("a $$x\ny$$ b");
        Assert.assertEquals("x\ny", formulas(multiline.toSegments(new TextStyle())).get(0).getLatexSource());
        Assert.assertEquals(MathStyleOverride.TEXT,
                formulas(multiline.toLayoutLines(null, new TextStyle()).get(0).getSegments()).get(0).getLatexMathStyle());
        for (MarkdownLayoutLine line : multiline.toLayoutContent(null, new TextStyle()).getLines()) {
            Assert.assertTrue(formulas(line.getSegments()).isEmpty());
        }
        Assert.assertEquals(MathStyleOverride.TEXT,
                formulas(MarkdownDocument.parse("$$x$$").toSegments(new TextStyle())).get(0).getLatexMathStyle());
    }

    @Test public void successfulStandaloneInterruptsParagraphButMalformedDoesNotEatTail() {
        LayoutContent parsed = content("before\n$$x$$\nafter");
        Assert.assertEquals(3, parsed.getLines().size());
        Assert.assertEquals("before", text(parsed.getLines().get(0).getSegments()));
        Assert.assertEquals(MarkdownLayoutLine.Kind.MATH_DISPLAY, parsed.getLines().get(1).getKind());
        Assert.assertEquals("after", text(parsed.getLines().get(2).getSegments()));
        for (String source : Arrays.asList("$$\nx\nafter", "$$x\ny$$", "$$\n\n$$", "$$x$$ trailing")) {
            Assert.assertTrue(source, mathLines(content(source)).isEmpty());
            Assert.assertEquals(source, MarkdownDocument.parse(source).getSource());
        }
        Assert.assertEquals(" x ", onlyMath("   $$ x $$  ").getLatexSource());
    }

    @Test public void codeChannelsWinOverMath() {
        String tick = String.valueOf((char) 96);
        for (String source : Arrays.asList(tick + "$$x$$" + tick,
                tick + tick + tick + "\n$$\nx\n$$\n" + tick + tick + tick,
                "before " + tick + "code\n$$x$$\nend" + tick,
                "    $$x$$", "~~~\n$$x$$\n~~~")) {
            LayoutContent parsed = content(source);
            Assert.assertTrue(source, mathLines(parsed).isEmpty());
            for (MarkdownLayoutLine line : parsed.getLines()) Assert.assertTrue(formulas(line.getSegments()).isEmpty());
        }
        Assert.assertTrue(mathLines(content("\\$$x$$")).isEmpty());
        Assert.assertEquals("$$x$$", text(MarkdownInlineParser.parse("\\$$x$$", new TextStyle())));
    }

    @Test public void sourceKeepsLineEndingsWhileTexOnlyNormalizesBodySeparators() {
        String source = "before\r\n   $$  \r\n   x  \r\n   \r   y \n   $$\r\nafter\r";
        MarkdownDocument doc = MarkdownDocument.parse(source);
        Assert.assertEquals(source, doc.getSource());
        Assert.assertEquals("x  \n\ny ", onlyMath(source).getLatexSource());
        Assert.assertEquals("\nx\n", onlyMath("$$\n\nx\n\n$$").getLatexSource());
        Assert.assertEquals("x", onlyMath("$$\rx\r$$").getLatexSource());
    }

    @Test public void listMathKeepsBlanksWithoutAnyTableAndMarkerIdentity() {
        String source = "- $$\r\n  x  \r\n  \r\n    \r\n  y\r\n  $$\r\n  after\r\n- next";
        LayoutContent parsed = content(source);
        MarkdownLayoutLine math = mathLines(parsed).get(0);
        Assert.assertEquals("x  \n\n  \ny", math.getSegments().get(0).getLatexSource());
        MarkdownLayoutLine marker = parsed.getLines().get(0);
        Assert.assertEquals(MarkdownLayoutLine.Kind.LIST, marker.getKind());
        Assert.assertEquals(1, marker.getSegments().size());
        Assert.assertEquals(1, math.getListMarkerChain().size());
        Assert.assertSame(marker.getSegments().get(0), math.getListMarkerChain().get(0));
        Assert.assertEquals(marker.getBlockId() + 1, math.getBlockId());
        Assert.assertEquals(source, MarkdownDocument.parse(source).getSource());
        String withTable = source + "\n\na|b\n-| -\nx|y";
        Assert.assertEquals(math.getSegments().get(0).getLatexSource(), onlyMath(withTable).getLatexSource());
    }

    @Test public void nestedListAndQuotePrefixesAreRemovedOnce() {
        String source = "> - outer\n>   -   $$\n>       a  \n>       \n>       b\n>       $$";
        LayoutContent parsed = content(source);
        MarkdownLayoutLine line = mathLines(parsed).get(0);
        Assert.assertEquals(1, line.getQuoteLevel());
        Assert.assertEquals(2, line.getListMarkerChain().size());
        Assert.assertEquals("a  \n\nb", line.getSegments().get(0).getLatexSource());
        Assert.assertEquals("x\n\ny", onlyMath("> $$\r\n> x\r\n> \r\n> y\r\n> $$").getLatexSource());
    }

    @Test public void fencesCannotCrossListItemsQuoteBoundariesOrLazyLines() {
        for (String source : Arrays.asList("- $$\n  x\n- $$", "- $$\nx\n  $$",
                "> $$\nx\n> $$", "> $$\n> x\n$$", "> $$\n> x\n\n> $$",
                "- $$\n  x\n$$")) {
            Assert.assertTrue(source, mathLines(content(source)).isEmpty());
        }
        LayoutContent outside = content("- before\n$$x$$");
        MarkdownLayoutLine math = mathLines(outside).get(0);
        Assert.assertTrue(math.getListMarkerChain().isEmpty());
        Assert.assertEquals(0, math.getQuoteLevel());
    }

    @Test public void listParagraphBlanksKeepHistoricalFoldingOutsideMath() {
        MarkdownDocument doc = MarkdownDocument.parse("- first\n\n  continuation\n\n      indented");
        List<MarkdownLayoutLine> legacy = doc.toLayoutLines(null, new TextStyle());
        List<MarkdownLayoutLine> modern = doc.toLayoutContent(null, new TextStyle()).getLines();
        Assert.assertEquals(legacy.size(), modern.size());
        for (int i = 0; i < legacy.size(); i++) {
            Assert.assertEquals(legacy.get(i).getKind(), modern.get(i).getKind());
            Assert.assertEquals(text(legacy.get(i).getSegments()), text(modern.get(i).getSegments()));
        }
    }

    @Test public void tablesRemainInlineAndTheirAnchorsKeepMathOrder() {
        LayoutContent parsed = content("$$a$$\n\nh|v\n-| -\n$$ x $$|$$2$$\n\n$$b$$");
        Assert.assertEquals(1, parsed.getTables().size());
        List<MarkdownLayoutLine> math = mathLines(parsed);
        Assert.assertEquals(2, math.size());
        int anchor = parsed.getTables().get(0).getBeforeLineIndex();
        Assert.assertTrue(parsed.getLines().indexOf(math.get(0)) < anchor);
        Assert.assertTrue(parsed.getLines().indexOf(math.get(1)) >= anchor);
        List<MarkdownTableModel.Cell> cells = parsed.getTables().get(0).getModel().getRows().get(0).getCells();
        Assert.assertEquals(" x ", cells.get(0).getSegments().get(0).getLatexSource());
        Assert.assertEquals(MathStyleOverride.DISPLAY, cells.get(1).getSegments().get(0).getLatexMathStyle());
        LayoutContent pipes = content("h|v\n-| -\n$$x|y$$");
        for (MarkdownTableModel.Cell cell : pipes.getTables().get(0).getModel().getRows().get(0).getCells()) {
            Assert.assertTrue(formulas(cell.getSegments()).isEmpty());
        }
    }

    @Test public void spanBoundariesAndMappingPreserveFormulaSourceStyleAndStructure() {
        TextStyle first = new TextStyle(); first.setColor(0xFF112233);
        TextStyle body = new TextStyle(); body.setColor(0xFF445566);
        TextStyle tail = new TextStyle(); tail.setColor(0xFF778899);
        MarkdownDocument doc = MarkdownDocument.parseSpans(Arrays.asList(new MarkdownSpan("$", first),
                new MarkdownSpan("$ x ", body), new MarkdownSpan("$$", tail)));
        LayoutContent original = doc.toLayoutContent(null, new TextStyle());
        TextSegment formula = mathLines(original).get(0).getSegments().get(0);
        Assert.assertEquals(" x ", formula.getLatexSource());
        Assert.assertEquals(body.getColor(), formula.getStyle().getColor());
        Assert.assertEquals("$$ x $$", doc.getSource());
        List<TextSegment> retained = new ArrayList<TextSegment>();
        LayoutContent mapped = original.mapSegments(flow -> {
            for (TextSegment segment : flow) segment.getStyle().setColor(0xFFABCDEF);
            retained.addAll(flow);
            return flow;
        });
        TextSegment copy = mathLines(mapped).get(0).getSegments().get(0);
        retained.get(0).getStyle().setColor(0);
        Assert.assertEquals(0xFFABCDEF, copy.getStyle().getColor());
        Assert.assertEquals(body.getColor(), formula.getStyle().getColor());
        Assert.assertEquals(formula.getLatexSource(), copy.getLatexSource());
        Assert.assertEquals(MathStyleOverride.DISPLAY, copy.getLatexMathStyle());
        Assert.assertEquals(MathStyleOverride.DISPLAY, MarkdownInlineParser.parse(Arrays.asList(
                new MarkdownSpan("a $", first), new MarkdownSpan("$x$", body), new MarkdownSpan("$ b", tail)))
                .get(1).getLatexMathStyle());
    }
}
