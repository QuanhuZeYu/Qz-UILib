package club.heiqi.uilib.font.layout.markdown;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/**
 * TablesExtension 与最小表格契约的独立对拍。块树索引路径、结构、列对齐和每字符行内语义均比较；
 * 不读写行接缝 oracle。LaTeX 是 UILib 扩展，不冒充 CommonMark 参考能力。
 */
@RunWith(Parameterized.class)
public class MarkdownTableOracleParityTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> samples() {
        List<Object[]> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : CommonMarkTableOracle.corpus().entrySet()) {
            result.add(new Object[] {entry.getKey(), entry.getValue()});
        }
        return result;
    }

    private final String name;
    private final String source;

    public MarkdownTableOracleParityTest(String name, String source) {
        this.name = name;
        this.source = source;
    }

    @Test
    public void tableStructureAndInlineSemanticsMatchReference() {
        List<List<Object>> reference = new ArrayList<>();
        collectReference(CommonMarkTableOracle.parse(source), new ArrayList<Integer>(), reference);
        List<List<Object>> actual = new ArrayList<>();
        for (MarkdownTableModel table : MarkdownDocument.parse(source).toTableModels(new TextStyle())) {
            List<Object> value = new ArrayList<>();
            List<String> alignments = new ArrayList<>();
            for (MarkdownTableModel.Alignment alignment : table.getAlignments()) {
                alignments.add(alignment.name());
            }
            value.add(table.getBlockPath());
            value.add(alignments);
            value.add(actualRow(table.getHeader()));
            List<Object> body = new ArrayList<>();
            for (MarkdownTableModel.Row row : table.getRows()) body.add(actualRow(row));
            value.add(body);
            actual.add(value);
        }
        assertEquals(name + " source=" + source, reference, actual);
    }

    private static List<Object> actualRow(MarkdownTableModel.Row row) {
        List<Object> result = new ArrayList<>();
        for (MarkdownTableModel.Cell cell : row.getCells()) {
            List<String> tokens = new ArrayList<>();
            for (TextSegment segment : cell.getSegments()) {
                assertFalse("LaTeX is outside this oracle corpus", segment.isLatex());
                TextStyle style = segment.getStyle();
                appendTokens(tokens, segment.getText(), style.getFontType() == FontType.BOLD,
                    style.isItalic(), style.isStrikethrough(), style.isCodeSpan(), style.getLink());
            }
            result.add(tokens);
        }
        return result;
    }

    private static void collectReference(Node parent, List<Integer> parentPath, List<List<Object>> result) {
        int siblingIndex = 0;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext(), siblingIndex++) {
            List<Integer> path = new ArrayList<>(parentPath);
            path.add(siblingIndex);
            if (node instanceof TableBlock) {
                List<Object> value = new ArrayList<>();
                List<String> alignments = new ArrayList<>();
                List<Object> body = new ArrayList<>();
                Object header = null;
                for (Node section = node.getFirstChild(); section != null; section = section.getNext()) {
                    for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                        List<Object> cells = new ArrayList<>();
                        for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                            if (section instanceof TableHead) {
                                TableCell.Alignment alignment = ((TableCell) cell).getAlignment();
                                alignments.add(alignment == null ? "NONE" : alignment.name());
                            }
                            List<String> tokens = new ArrayList<>();
                            referenceTokens(cell, tokens, false, false, false, false, null);
                            cells.add(tokens);
                        }
                        if (section instanceof TableHead) header = cells;
                        else body.add(cells);
                    }
                }
                value.add(path);
                value.add(alignments);
                value.add(header);
                value.add(body);
                result.add(value);
            } else {
                collectReference(node, path, result);
            }
        }
    }

    private static void referenceTokens(Node parent, List<String> result, boolean bold,
        boolean italic, boolean strike, boolean code, String link) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Text) {
                appendTokens(result, ((Text) child).getLiteral(), bold, italic, strike, code, link);
            } else if (child instanceof Code) {
                appendTokens(result, ((Code) child).getLiteral(), bold, italic, strike, true, link);
            } else if (child instanceof StrongEmphasis || child instanceof Emphasis
                || child instanceof Strikethrough || child instanceof Link) {
                referenceTokens(child, result, bold || child instanceof StrongEmphasis,
                    italic || child instanceof Emphasis, strike || child instanceof Strikethrough,
                    code, child instanceof Link ? ((Link) child).getDestination() : link);
            } else {
                throw new AssertionError("Unmapped reference inline node: " + child);
            }
        }
    }

    private static void appendTokens(List<String> result, String text, boolean bold,
        boolean italic, boolean strike, boolean code, String link) {
        for (int i = 0; i < text.length(); i++) {
            result.add(text.charAt(i) + ":" + bold + ":" + italic + ":" + strike + ":" + code + ":" + link);
        }
    }
}
