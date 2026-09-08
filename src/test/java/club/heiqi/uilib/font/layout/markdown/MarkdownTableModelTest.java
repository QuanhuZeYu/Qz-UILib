package club.heiqi.uilib.font.layout.markdown;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/** 表格契约、样式锚点和不可变性；语法对拍与历史字面反锁由各自独立测试承担。 */
public class MarkdownTableModelTest {
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF55FF55;
    private static final String NL = String.valueOf((char) 10);
    private static final String BS = String.valueOf((char) 92);
    private static final String TICK = String.valueOf((char) 96);

    private static TextStyle style(int color) {
        TextStyle result = new TextStyle();
        result.setColor(color);
        return result;
    }

    private static MarkdownSpan span(String text, int color) {
        return new MarkdownSpan(text, style(color));
    }

    private static MarkdownTableModel only(MarkdownDocument document) {
        List<MarkdownTableModel> models = document.toTableModels(new TextStyle());
        Assert.assertEquals(1, models.size());
        return models.get(0);
    }

    @Test
    public void tableIdentityAndBlockPathRemainSeparateFromLiteralLines() {
        MarkdownDocument document = MarkdownDocument.parse("# before" + NL + NL
                + "> - a|b" + NL + ">   -|-" + NL + ">   x|y");
        MarkdownTableModel model = only(document);
        Assert.assertEquals(Arrays.asList(1, 0, 0, 0), model.getBlockPath());
        Assert.assertEquals(MarkdownBlock.Kind.TABLE,
                document.blocks().get(1).children.get(0).children.get(0).children.get(0).kind);
        Assert.assertEquals(2, model.getHeader().getCells().size());
        Assert.assertEquals(1, model.getRows().size());
        Assert.assertEquals("x", model.getRows().get(0).getCells().get(0).getSegments().get(0).getText());
    }

    @Test
    public void inlineDelimitersCrossSpanBoundariesInsideOneCell() {
        MarkdownTableModel model = only(MarkdownDocument.parseSpans(Arrays.asList(
                span("a|b" + NL + "-|-" + NL + "**", RED),
                span("bold", GREEN), span("**|tail", RED))));
        List<TextSegment> cell = model.getRows().get(0).getCells().get(0).getSegments();
        Assert.assertEquals(1, cell.size());
        Assert.assertEquals("bold", cell.get(0).getText());
        Assert.assertEquals(GREEN, cell.get(0).getStyle().getColor());
        Assert.assertEquals(FontType.BOLD, cell.get(0).getStyle().getFontType());
    }

    @Test
    public void cellBoundaryStopsInlinePairing() {
        MarkdownTableModel model = only(MarkdownDocument.parse("a|b" + NL + "-|-" + NL + "**left|right**"));
        Assert.assertEquals("**left", model.getRows().get(0).getCells().get(0).getSegments().get(0).getText());
        Assert.assertEquals("right**", model.getRows().get(0).getCells().get(1).getSegments().get(0).getText());
    }

    @Test
    public void escapedPipeKeepsItsOwnAnchorInsideCode() {
        MarkdownTableModel model = only(MarkdownDocument.parseSpans(Arrays.asList(
                span("a|b" + NL + "-|-" + NL + TICK + "x" + BS, RED),
                span("|", GREEN), span("y" + TICK + "|z", RED))));
        List<TextSegment> segments = model.getRows().get(0).getCells().get(0).getSegments();
        Assert.assertEquals(3, segments.size());
        Assert.assertEquals("x", segments.get(0).getText());
        Assert.assertEquals("|", segments.get(1).getText());
        Assert.assertEquals("y", segments.get(2).getText());
        Assert.assertEquals(GREEN, segments.get(1).getStyle().getColor());
        Assert.assertTrue(segments.get(1).getStyle().isCodeSpan());
    }

    @Test
    public void quoteChainKeepsExplicitSpanColorAndAddsQuoteStyle() {
        MarkdownTableModel model = only(MarkdownDocument.parseSpans(Collections.singletonList(
                span("> a|b" + NL + "> -|-" + NL + "> x|y", GREEN))));
        TextStyle result = model.getRows().get(0).getCells().get(0).getSegments().get(0).getStyle();
        Assert.assertEquals(GREEN, result.getColor());
        TextStyle expected = MarkdownDocument.parseSpans(Collections.singletonList(span("> x", GREEN)))
                .toSegments(new TextStyle()).get(0).getStyle();
        Assert.assertTrue(StyleValues.same(expected, result));
    }

    @Test
    public void exportedCollectionsAndMutableStylesCannotMutateTheModel() {
        MarkdownTableModel model = only(MarkdownDocument.parse("a|b" + NL + "-|-" + NL + "$x$|[link](https://example.test)"));
        MarkdownTableModel.Cell cell = model.getRows().get(0).getCells().get(0);
        TextSegment first = cell.getSegments().get(0);
        Assert.assertTrue(first.isLatex());
        Assert.assertEquals("x", first.getLatexSource());
        int originalColor = first.getStyle().getColor();
        first.getStyle().setColor(RED);
        Assert.assertEquals(originalColor, cell.getSegments().get(0).getStyle().getColor());
        Assert.assertTrue(cell.getSegments().get(0).isLatex());
        assertUnmodifiable(model.getBlockPath());
        assertUnmodifiable(model.getAlignments());
        assertUnmodifiable(model.getRows());
        assertUnmodifiable(model.getHeader().getCells());
        assertUnmodifiable(cell.getSegments());
    }

    @Test
    public void exportReusesInlineParserForAllExistingAtoms() {
        String content = "**bold** ~~strike~~ " + TICK + "code" + TICK
                + " $x$ [link](https://example.test) §a";
        MarkdownTableModel model = only(MarkdownDocument.parse("a|b" + NL + "-|-" + NL + content + "|tail"));
        List<TextSegment> expected = MarkdownInlineParser.parse(content, new TextStyle());
        List<TextSegment> actual = model.getRows().get(0).getCells().get(0).getSegments();
        Assert.assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            Assert.assertEquals(expected.get(i).getText(), actual.get(i).getText());
            Assert.assertEquals(expected.get(i).getLatexSource(), actual.get(i).getLatexSource());
            Assert.assertTrue(StyleValues.same(expected.get(i).getStyle(), actual.get(i).getStyle()));
        }
    }

    @Test
    public void noTableAndEmptyDocumentsExportEmptyLists() {
        Assert.assertTrue(MarkdownDocument.parse(null).toTableModels(new TextStyle()).isEmpty());
        Assert.assertTrue(MarkdownDocument.parse("ordinary").toTableModels(new TextStyle()).isEmpty());
        Assert.assertTrue(MarkdownDocument.parse("before" + NL + "a|b" + NL + "-|-")
                .toTableModels(new TextStyle()).isEmpty());
    }

    @Test
    public void cellEscapeContextDoesNotChangeOrdinaryInlineOrLiteralExports() {
        String source = "a|b" + NL + "-|-" + NL + "x" + BS + BS + "|y|z";
        MarkdownDocument document = MarkdownDocument.parse(source);
        Assert.assertEquals("x|y", only(document).getRows().get(0).getCells().get(0)
                .getSegments().get(0).getText());
        Assert.assertEquals("x" + BS + "|y", MarkdownInlineParser.parse("x" + BS + "|y", new TextStyle())
                .get(0).getText());
        List<TextSegment> expected = MarkdownInlineParser.parse(source, new TextStyle());
        List<TextSegment> actual = document.toSegments(new TextStyle());
        Assert.assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            Assert.assertEquals(expected.get(i).getText(), actual.get(i).getText());
            Assert.assertTrue(StyleValues.same(expected.get(i).getStyle(), actual.get(i).getStyle()));
        }
    }

    @Test
    public void unmarkedBlankBetweenQuoteTablesProducesDistinctBlockAnchors() {
        MarkdownDocument document = MarkdownDocument.parse("> a|b" + NL + "> -|-" + NL + NL
                + "> c|d" + NL + "> -|-");
        List<MarkdownTableModel> models = document.toTableModels(new TextStyle());
        Assert.assertEquals(2, models.size());
        Assert.assertEquals(Arrays.asList(0, 0), models.get(0).getBlockPath());
        Assert.assertEquals(Arrays.asList(1, 0), models.get(1).getBlockPath());
    }

    @Test
    public void nestedQuoteTableAnchorsUseTheOuterContainerBoundary() {
        MarkdownDocument document = MarkdownDocument.parse("> > a|b" + NL + "> > -|-" + NL + NL
                + "> > c|d" + NL + "> > -|-");
        List<MarkdownTableModel> models = document.toTableModels(new TextStyle());
        Assert.assertEquals(2, models.size());
        Assert.assertEquals(Arrays.asList(0, 0, 0), models.get(0).getBlockPath());
        Assert.assertEquals(Arrays.asList(1, 0, 0), models.get(1).getBlockPath());
    }

    @Test
    public void mixedTableDocumentPreservesListParagraphGapOnlyInNewLayout() throws Exception {
        String ordinaryItem = "- first" + NL + NL + "  second";
        MarkdownDocument mixed = MarkdownDocument.parse(ordinaryItem + NL
                + "- a|b" + NL + "  -|-" + NL + "  x|y");
        MarkdownDocument.LayoutContent content = mixed.toLayoutContent(null, new TextStyle());
        Assert.assertEquals("样例必须实际进入含 TABLE 分支", 1, content.getTables().size());
        List<MarkdownLayoutLine> lines = content.getLines();
        Assert.assertEquals("另一列表项内部空行必须保留", MarkdownLayoutLine.NO_BLOCK,
                lines.get(1).getBlockId());
        Assert.assertTrue(lines.get(1).getSegments().isEmpty());
        Assert.assertEquals("second", lines.get(2).getSegments().get(0).getText());
        Assert.assertNotEquals("空行后的段落必须独立成块", lines.get(0).getBlockId(), lines.get(2).getBlockId());

        // 旧列表解析会把项内空行折叠。使用同一字面前缀，复用完整 getter 快照，
        // 同时比较样式、块 id、列表链和几何，避免仅可见字符串一致掩盖旧出口变化。
        List<MarkdownLayoutLine> historical = MarkdownDocument.parse(ordinaryItem)
                .toLayoutLines(null, new TextStyle());
        List<MarkdownLayoutLine> legacy = mixed.toLayoutLines(null, new TextStyle());
        Assert.assertEquals(MarkdownTableLiteralSnapshot.encode(historical),
                MarkdownTableLiteralSnapshot.encode(legacy.subList(0, historical.size())));
        Assert.assertEquals(historical.get(0).getBlockId(), historical.get(1).getBlockId());
    }

    @Test
    public void literalPipeWithoutTableKeepsHistoricalListGapBehavior() throws Exception {
        String withGap = "- first|literal" + NL + NL + "  second";
        // 去掉空行是旧列表折叠行为的正对照；pipe 没有合法 delimiter，不能开启 TABLE 树。
        MarkdownDocument historical = MarkdownDocument.parse("- first|literal" + NL + "  second");
        MarkdownDocument document = MarkdownDocument.parse(withGap);
        MarkdownDocument.LayoutContent content = document.toLayoutContent(null, new TextStyle());
        Assert.assertTrue(content.getTables().isEmpty());
        String expected = MarkdownTableLiteralSnapshot.encode(historical.toLayoutLines(null, new TextStyle()));
        Assert.assertEquals(expected, MarkdownTableLiteralSnapshot.encode(document.toLayoutLines(null, new TextStyle())));
        Assert.assertEquals(expected, MarkdownTableLiteralSnapshot.encode(content.getLines()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullBaseStyleIsRejectedEvenForAnEmptyDocument() {
        MarkdownDocument.parse(null).toTableModels(null);
    }

    private static void assertUnmodifiable(List<?> list) {
        try {
            list.clear();
            Assert.fail("exported collection must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Contract, including empty lists.
        }
    }
}
