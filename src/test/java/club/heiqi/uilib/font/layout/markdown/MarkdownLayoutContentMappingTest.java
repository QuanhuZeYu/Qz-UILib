package club.heiqi.uilib.font.layout.markdown;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument.LayoutContent;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument.TableUnit;
import club.heiqi.uilib.font.render.software.LatexSoftwareRenderKit;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;

/** 段流映射的结构、可变样式隔离与真实 L2 消费锁；不通过聊天消费者证明本 API。 */
public class MarkdownLayoutContentMappingTest {
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

    private static LayoutContent content(String source) {
        return MarkdownDocument.parse(source).toLayoutContent(null, new TextStyle());
    }

    private static String text(List<TextSegment> segments) {
        StringBuilder out = new StringBuilder();
        for (TextSegment segment : segments) {
            out.append(segment.isLatex() ? "$" + segment.getLatexSource() + "$" : segment.getText());
        }
        return out.toString();
    }

    private static List<MarkdownTableModel.Cell> cells(LayoutContent content) {
        List<MarkdownTableModel.Cell> out = new ArrayList<MarkdownTableModel.Cell>();
        for (TableUnit table : content.getTables()) {
            out.addAll(table.getModel().getHeader().getCells());
            for (MarkdownTableModel.Row row : table.getModel().getRows()) out.addAll(row.getCells());
        }
        return out;
    }

    @Test
    public void mapsEveryLineBodyAndCellWithoutChangingStructure() {
        LayoutContent input = content("# title\n\n- body\n  continued\n\n> a|b\n> :-| -:\n> x|y\n\nc|d\n-| -\nu|v");
        Assert.assertEquals(2, input.getTables().size());
        List<String> visited = new ArrayList<String>();
        LayoutContent output = input.mapSegments(flow -> {
            visited.add(text(flow));
            List<TextSegment> changed = new ArrayList<TextSegment>();
            for (TextSegment segment : flow) {
                changed.add(new TextSegment("[" + segment.getText() + "]", segment.getStyle()));
            }
            return changed;
        });
        Assert.assertEquals(input.getLines().size() + cells(input).size(), visited.size());
        for (String cell : Arrays.asList("a", "b", "x", "y", "c", "d", "u", "v")) {
            Assert.assertTrue(visited.contains(cell));
        }
        boolean mappedListBody = false;
        for (int i = 0; i < input.getLines().size(); i++) {
            MarkdownLayoutLine old = input.getLines().get(i);
            MarkdownLayoutLine next = output.getLines().get(i);
            sameLineMetadata(old, next);
            if (old.getKind() == MarkdownLayoutLine.Kind.LIST && !old.getSegments().isEmpty()
                    && old.getSegments().get(0) == old.getListMarkerChain().get(old.getListMarkerChain().size() - 1)) {
                Assert.assertEquals(old.getSegments().get(0).getText(), next.getSegments().get(0).getText());
                Assert.assertSame(next.getSegments().get(0), next.getListMarkerChain().get(next.getListMarkerChain().size() - 1));
                Assert.assertTrue(text(next.getSegments()).contains("[body"));
                mappedListBody = true;
            }
        }
        Assert.assertTrue(mappedListBody);
        sameTableMetadata(input, output);
        List<MarkdownTableModel.Cell> before = cells(input);
        List<MarkdownTableModel.Cell> after = cells(output);
        for (int i = 0; i < before.size(); i++) {
            Assert.assertEquals("[" + text(before.get(i).getSegments()) + "]", text(after.get(i).getSegments()));
        }
    }

    @Test
    public void identityPreservesStylesFormulaAndL2Plan() throws Exception {
        TextStyle base = new TextStyle();
        base.setColor(0xFF123456);
        base.setLetterSpacing(0.5F);
        base.setFontSizePx(17);
        LayoutContent input = MarkdownDocument.parseSpans(Collections.singletonList(new MarkdownSpan(
                "# heading\n\n> - a|b\n>   -|-\n>   **bold** $x^2$|[link](https://example.test)\n\n~~~\ncode\n~~~", base)))
                .toLayoutContent(null, base);
        LayoutContent output = input.mapSegments(UnaryOperator.identity());
        sameTableMetadata(input, output);
        for (int i = 0; i < input.getLines().size(); i++) {
            sameLineMetadata(input.getLines().get(i), output.getLines().get(i));
            sameSegments(input.getLines().get(i).getSegments(), output.getLines().get(i).getSegments());
        }
        boolean formula = false;
        List<MarkdownTableModel.Cell> a = cells(input), b = cells(output);
        for (int i = 0; i < a.size(); i++) {
            sameSegments(a.get(i).getSegments(), b.get(i).getSegments());
            for (TextSegment s : b.get(i).getSegments()) formula |= s.isLatex();
        }
        Assert.assertTrue(formula);
        for (int width : new int[] {48, 400}) {
            samePlan(layout(input, width), layout(output, width));
        }
    }

    @Test
    public void firstTableInListKeepsMarkerOnHeaderAfterNonIdentityMapping() {
        LayoutContent input = content("> - parent\n>   - a|b\n>     -|-\n>     x|y");
        LayoutContent mapped = input.mapSegments(flow -> {
            for (TextSegment segment : flow) segment.getStyle().setUnderline(true);
            return flow;
        });
        TableUnit table = mapped.getTables().get(0);
        MarkdownLayoutLine marker = mapped.getLines().get(table.getBeforeLineIndex() - 1);
        Assert.assertEquals(1, marker.getSegments().size());
        List<TextSegment> chain = table.getContext().getListMarkerChain();
        Assert.assertEquals(2, chain.size());
        for (int i = 0; i < chain.size(); i++) Assert.assertSame(marker.getListMarkerChain().get(i), chain.get(i));
        Assert.assertSame(marker.getSegments().get(0), chain.get(chain.size() - 1));
        Assert.assertNotSame(input.getTables().get(0).getContext().getListMarkerChain().get(0), chain.get(0));
        MarkdownPainter.ContentLayout plan = layout(mapped, 400);
        Integer headerY = null, markerY = null;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() != PaintCommandType.SEGMENTS) continue;
            String value = text(command.getSegments());
            if (value.equals("a")) headerY = command.getTop();
            if (value.equals(marker.getSegments().get(0).getText().trim())) markerY = command.getTop();
        }
        Assert.assertNotNull(headerY);
        Assert.assertEquals(headerY, markerY);
        Assert.assertTrue(cells(mapped).get(0).getSegments().get(0).getStyle().isUnderline());
    }

    @Test
    public void mapperInputReturnedListsAndStylesAreDetachedInBothDirections() {
        LayoutContent input = content("plain\n\na|b\n-|-\nx|$x^2$");
        List<List<TextSegment>> retained = new ArrayList<List<TextSegment>>();
        LayoutContent output = input.mapSegments(flow -> {
            for (TextSegment segment : flow) segment.getStyle().setColor(0xFF102030);
            retained.add(flow);
            return flow;
        });
        for (List<TextSegment> flow : retained) {
            for (TextSegment segment : flow) segment.getStyle().setColor(0xFFABCDEF);
            flow.clear();
        }
        Assert.assertEquals("plain", text(output.getLines().get(0).getSegments()));
        Assert.assertEquals(0xFF102030, output.getLines().get(0).getSegments().get(0).getStyle().getColor());
        Assert.assertEquals(0xFFFFFFFF, input.getLines().get(0).getSegments().get(0).getStyle().getColor());
        for (MarkdownTableModel.Cell cell : cells(output)) {
            List<TextSegment> read = cell.getSegments();
            Assert.assertEquals(0xFF102030, read.get(0).getStyle().getColor());
            read.get(0).getStyle().setColor(0);
            Assert.assertEquals(0xFF102030, cell.getSegments().get(0).getStyle().getColor());
        }
        output.getLines().get(0).getSegments().get(0).getStyle().setColor(0);
        Assert.assertEquals(0xFFFFFFFF, input.getLines().get(0).getSegments().get(0).getStyle().getColor());
        input.getLines().get(0).getSegments().get(0).getStyle().setColor(1);
        Assert.assertEquals(0, output.getLines().get(0).getSegments().get(0).getStyle().getColor());
        immutable(() -> output.getLines().clear());
        immutable(() -> output.getTables().clear());
        immutable(() -> output.getLines().get(0).getSegments().clear());
    }

    @Test
    public void invalidMapperResultsAndThrownFailuresNeverPolluteOriginal() {
        LayoutContent input = content("plain\n\na|b\n-|-\nx|y");
        illegal(() -> input.mapSegments(null));
        illegal(() -> input.mapSegments(flow -> null));
        illegal(() -> input.mapSegments(flow -> Collections.singletonList(null)));
        RuntimeException failure = new RuntimeException("mapper failure");
        try {
            input.mapSegments(flow -> {
                for (TextSegment segment : flow) segment.getStyle().setColor(0);
                if (text(flow).equals("x")) throw failure;
                return flow;
            });
            Assert.fail("mapper exception must propagate");
        } catch (RuntimeException actual) {
            Assert.assertSame(failure, actual);
        }
        Assert.assertEquals(0xFFFFFFFF, input.getLines().get(0).getSegments().get(0).getStyle().getColor());
        for (MarkdownTableModel.Cell cell : cells(input)) {
            Assert.assertEquals(0xFFFFFFFF, cell.getSegments().get(0).getStyle().getColor());
        }
    }

    @Test
    public void emptyResultRetainsLinesCellsAndMarkerStructure() {
        LayoutContent input = content("- body\n\na|b\n-|-\nx|y");
        LayoutContent output = input.mapSegments(flow -> Collections.emptyList());
        Assert.assertEquals(input.getLines().size(), output.getLines().size());
        Assert.assertEquals(cells(input).size(), cells(output).size());
        for (MarkdownTableModel.Cell cell : cells(output)) Assert.assertTrue(cell.getSegments().isEmpty());
        Assert.assertEquals(1, output.getLines().get(0).getSegments().size());
        Assert.assertSame(output.getLines().get(0).getSegments().get(0),
                output.getLines().get(0).getListMarkerChain().get(0));
        LayoutContent empty = content("");
        LayoutContent copy = empty.mapSegments(flow -> { Assert.fail("no content to map"); return flow; });
        Assert.assertTrue(copy.getLines().isEmpty());
        Assert.assertTrue(copy.getTables().isEmpty());
    }

    private static void sameLineMetadata(MarkdownLayoutLine a, MarkdownLayoutLine b) {
        Assert.assertEquals(a.getKind(), b.getKind());
        Assert.assertEquals(a.getHeadingLevel(), b.getHeadingLevel());
        Assert.assertEquals(a.getBlockId(), b.getBlockId());
        Assert.assertEquals(a.getQuoteLevel(), b.getQuoteLevel());
        Assert.assertEquals(a.getLeftInsetPx(), b.getLeftInsetPx());
        Assert.assertEquals(a.getIndentStepPx(), b.getIndentStepPx());
        Assert.assertEquals(a.getBarWidthPx(), b.getBarWidthPx());
        Assert.assertEquals(a.getRuleThicknessPx(), b.getRuleThicknessPx());
        Assert.assertEquals(a.getAccentArgb(), b.getAccentArgb());
        Assert.assertEquals(a.getBackgroundArgb(), b.getBackgroundArgb());
        Assert.assertEquals(a.getBlockContentWidthPx(), b.getBlockContentWidthPx());
        Assert.assertEquals(text(a.getListMarkerChain()), text(b.getListMarkerChain()));
    }

    private static void sameTableMetadata(LayoutContent a, LayoutContent b) {
        Assert.assertEquals(a.getTables().size(), b.getTables().size());
        for (int i = 0; i < a.getTables().size(); i++) {
            TableUnit x = a.getTables().get(i), y = b.getTables().get(i);
            Assert.assertEquals(x.getBeforeLineIndex(), y.getBeforeLineIndex());
            Assert.assertEquals(x.getModel().getBlockPath(), y.getModel().getBlockPath());
            Assert.assertEquals(x.getModel().getAlignments(), y.getModel().getAlignments());
            Assert.assertEquals(x.getModel().getRows().size(), y.getModel().getRows().size());
            Assert.assertEquals(x.getPaddingXPx(), y.getPaddingXPx());
            Assert.assertEquals(x.getPaddingYPx(), y.getPaddingYPx());
            Assert.assertEquals(x.getBorderPx(), y.getBorderPx());
            Assert.assertEquals(x.getBorderArgb(), y.getBorderArgb());
            Assert.assertEquals(x.getHeaderArgb(), y.getHeaderArgb());
            sameLineMetadata(x.getContext(), y.getContext());
            Assert.assertTrue(y.getContext().getSegments().isEmpty());
        }
    }

    private static void sameSegments(List<TextSegment> a, List<TextSegment> b) throws Exception {
        Assert.assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            Assert.assertEquals(a.get(i).getText(), b.get(i).getText());
            Assert.assertEquals(a.get(i).getLatexSource(), b.get(i).getLatexSource());
            Assert.assertNotSame(a.get(i), b.get(i));
            Assert.assertNotSame(a.get(i).getStyle(), b.get(i).getStyle());
            // TextStyle 没有值 equals；检查每个无参 getter，覆盖完整样式而非仅颜色。
            for (Method getter : TextStyle.class.getDeclaredMethods()) {
                if (getter.getParameterTypes().length == 0
                        && (getter.getName().startsWith("get") || getter.getName().startsWith("is"))) {
                    Assert.assertEquals(getter.getName(), getter.invoke(a.get(i).getStyle()), getter.invoke(b.get(i).getStyle()));
                }
            }
        }
    }

    private static MarkdownPainter.ContentLayout layout(LayoutContent content, int width) {
        return MarkdownPainter.layoutContent(content, LatexSoftwareRenderKit.currentService(), width, 16);
    }

    private static void samePlan(MarkdownPainter.ContentLayout a, MarkdownPainter.ContentLayout b) throws Exception {
        Assert.assertEquals(a.getWidthPx(), b.getWidthPx());
        Assert.assertEquals(a.getHeightPx(), b.getHeightPx());
        Assert.assertEquals(a.getCommands().size(), b.getCommands().size());
        for (int i = 0; i < a.getCommands().size(); i++) {
            PaintCommand x = a.getCommands().get(i), y = b.getCommands().get(i);
            Assert.assertEquals(x.getType(), y.getType());
            Assert.assertEquals(x.getLeft(), y.getLeft());
            Assert.assertEquals(x.getTop(), y.getTop());
            Assert.assertEquals(x.getRight(), y.getRight());
            Assert.assertEquals(x.getBottom(), y.getBottom());
            Assert.assertEquals(x.getColor(), y.getColor());
            Assert.assertEquals(x.getLinkUrl(), y.getLinkUrl());
            if (x.getSegments() != null) sameSegments(x.getSegments(), y.getSegments());
        }
    }

    private static void illegal(Runnable call) {
        try { call.run(); Assert.fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { Assert.assertFalse(expected.getMessage().isEmpty()); }
    }

    private static void immutable(Runnable call) {
        try { call.run(); Assert.fail("expected immutable list"); }
        catch (UnsupportedOperationException expected) { /* expected */ }
    }
}
