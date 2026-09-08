package club.heiqi.uilib.font.layout.markdown;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/**
 * T1 暂态降级锁：表格已被识别，但旧段流/行缝保持历史消费者可见数据逐字一致。
 * fixture 来自 de4b53a557a4e2269f2480774d688276c434d349 的真实 HEAD 隔离编译执行，
 * 并与实施前立即复制的 build/classes/java/main 执行结果交叉核验。
 * 工作站 temp/table_literal_lock_runner.py capture 记录来源；测试永不重录期望值。
 * T2 必须按消费者裁定显式迁移此锁，不能以新禁表模式互比替代历史证据。
 */
public class MarkdownTableLiteralFallbackLockTest {
    @Test
    public void oldConsumerFieldsMatchExecutedHeadFixtureExactly() throws Exception {
        String expected;
        try (InputStream in = getClass().getResourceAsStream("table-literal-head.snapshot")) {
            Assert.assertNotNull("必须提交真实 HEAD 执行 fixture，禁止运行时生成基线", in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            expected = new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
        StringBuilder actual = new StringBuilder();
        for (int i = 0; i < MarkdownTableLiteralSnapshot.NAMES.length; i++) {
            String sample = MarkdownTableLiteralSnapshot.snapshot(i);
            String prefix = MarkdownTableLiteralSnapshot.NAMES[i] + "\n";
            int start = expected.indexOf(prefix);
            Assert.assertTrue("fixture 缺少样例 " + prefix, start >= 0);
            int end = i + 1 == MarkdownTableLiteralSnapshot.NAMES.length ? expected.length()
                : expected.indexOf(MarkdownTableLiteralSnapshot.NAMES[i + 1] + "\n", start);
            Assert.assertTrue("fixture 样例顺序损坏 " + prefix, end > start);
            Assert.assertEquals("消费者完整字段变化: " + MarkdownTableLiteralSnapshot.NAMES[i],
                expected.substring(start, end), sample);
            actual.append(sample);
        }
        Assert.assertEquals("fixture 不得多余、遗漏、重排或归一化", expected, actual.toString());
    }

    @Test
    public void validTablesAreReallyRecognizedWhileLegacySeamsStayLiteral() {
        MarkdownTableModel basic = onlyTable(0);
        Assert.assertEquals(Arrays.asList(0), basic.getBlockPath());
        Assert.assertEquals(Arrays.asList(MarkdownTableModel.Alignment.NONE, MarkdownTableModel.Alignment.NONE),
            basic.getAlignments());
        Assert.assertEquals(2, basic.getHeader().getCells().size());
        Assert.assertEquals("A", text(basic.getHeader().getCells().get(0)));
        Assert.assertEquals("B", text(basic.getHeader().getCells().get(1)));
        Assert.assertEquals(1, basic.getRows().size());
        Assert.assertEquals("x", text(basic.getRows().get(0).getCells().get(0)));
        Assert.assertEquals("y", text(basic.getRows().get(0).getCells().get(1)));
        Assert.assertTrue("setext 不能伪造表", tables(2).isEmpty());
        Assert.assertEquals("多个表必须分别识别", 2, tables(7).size());
        Assert.assertNotEquals(tables(7).get(0).getBlockPath(), tables(7).get(1).getBlockPath());
        Assert.assertEquals("段表段仍需识别中间表", 1, tables(8).size());
        Assert.assertTrue("引用内表格必须保留容器路径", onlyTable(3).getBlockPath().size() > 1);
        Assert.assertTrue("列表内表格必须保留容器路径", onlyTable(4).getBlockPath().size() > 1);
        Assert.assertEquals("A|B", text(onlyTable(5).getHeader().getCells().get(0)));

        MarkdownTableModel rich = onlyTable(6);
        Assert.assertEquals("https://example.org/a",
            rich.getHeader().getCells().get(1).getSegments().get(0).getStyle().getLink());
        Assert.assertTrue(rich.getRows().get(0).getCells().get(0).getSegments().get(0).getStyle().isCodeSpan());
        TextSegment latex = rich.getRows().get(0).getCells().get(1).getSegments().get(0);
        Assert.assertTrue(latex.isLatex());
        Assert.assertEquals("x^2", latex.getLatexSource());
        MarkdownTableModel spans = onlyTable(9);
        Assert.assertEquals(0xFF123456, spans.getHeader().getCells().get(0).getSegments().get(0).getStyle().getColor());
        Assert.assertEquals(0xFFABCDEF, spans.getHeader().getCells().get(1).getSegments().get(0).getStyle().getColor());
    }

    @Test
    public void snapshotCarriesNonTextConsumerDataAndDetectsStyleOnlyChanges() throws Exception {
        String inline = MarkdownTableLiteralSnapshot.snapshot(6);
        for (String field : Arrays.asList("getLink=", "isCodeSpan=true", "isLatex=true", "getLatexSource=",
            "getFontType=", "getFontSizePx=", "getLetterSpacing=", "isStrikethrough=true", "isItalic=true")) {
            Assert.assertTrue("样例必须实际覆盖 " + field, inline.contains(field));
        }
        String geometry = MarkdownTableLiteralSnapshot.snapshot(11);
        for (String field : Arrays.asList("getBlockId=", "getQuoteLevel=1", "getLeftInsetPx=",
            "getIndentStepPx=", "getBarWidthPx=", "getRuleThicknessPx=", "getAccentArgb=",
            "getBackgroundArgb=", "getBlockContentWidthPx=", "getKind=CODE", "getKind=THEMATIC_BREAK")) {
            Assert.assertTrue("几何字段必须入快照 " + field, geometry.contains(field));
        }
        Assert.assertTrue(MarkdownTableLiteralSnapshot.snapshot(10).contains("getListMarkerChain=[TextSegment"));
        Assert.assertTrue(MarkdownTableLiteralSnapshot.snapshot(2).contains("getHeadingLevel=2"));
        TextStyle first = MarkdownTableLiteralSnapshot.base();
        TextStyle second = first.copy();
        second.setColor(0xFF102030);
        Assert.assertNotEquals("相同文本、不同样式不能被序列化抹平",
            MarkdownTableLiteralSnapshot.encode(new TextSegment("same", first)),
            MarkdownTableLiteralSnapshot.encode(new TextSegment("same", second)));
    }

    private static List<MarkdownTableModel> tables(int sample) {
        return MarkdownTableLiteralSnapshot.document(sample).toTableModels(MarkdownTableLiteralSnapshot.base());
    }

    private static MarkdownTableModel onlyTable(int sample) {
        List<MarkdownTableModel> tables = tables(sample);
        Assert.assertEquals("有效表未识别: " + MarkdownTableLiteralSnapshot.NAMES[sample], 1, tables.size());
        return tables.get(0);
    }

    private static String text(MarkdownTableModel.Cell cell) {
        StringBuilder out = new StringBuilder();
        for (TextSegment segment : cell.getSegments()) out.append(segment.getText());
        return out.toString();
    }
}
