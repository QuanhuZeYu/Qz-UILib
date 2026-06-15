package club.heiqi.uilib.ui.text.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link TextLayoutEngine} 的核心契约测试。
 *
 * <p>覆盖：前缀宽度向量与 caret/选区命中几何、软换行、CJK、空行、缓存稳态零测量与各维度失效，
 * 以及 O(N) 增量测量（每行只取一次前缀向量）。</p>
 */
public class TextLayoutEngineTest {

    private static final int LINE_HEIGHT = 18;
    private static final int CHAR_WIDTH = 12;

    /**
     * 计数型线性测量函数：每个码点等宽，用于断言测量调用次数与命中几何。
     */
    private static final class CountingMeasure implements TextMeasureFunction {

        private final int charWidth;
        int widthOfCalls;
        int prefixWidthsCalls;

        private CountingMeasure(int charWidth) {
            this.charWidth = charWidth;
        }

        @Override
        public int widthOf(String text) {
            widthOfCalls++;
            return text == null ? 0 : text.codePointCount(0, text.length()) * charWidth;
        }

        @Override
        public int[] prefixWidths(String text) {
            prefixWidthsCalls++;
            return TextMeasureFunction.super.prefixWidths(text);
        }
    }

    private static List<LogicalTextLine> linesOf(String text) {
        List<LogicalTextLine> result = new ArrayList<LogicalTextLine>();
        if (text.isEmpty()) {
            result.add(new LogicalTextLine(0, 0, ""));
            return result;
        }
        int lineStart = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) != '\n') {
                continue;
            }
            result.add(new LogicalTextLine(lineStart, index, text.substring(lineStart, index)));
            lineStart = index + 1;
        }
        result.add(new LogicalTextLine(lineStart, text.length(), text.substring(lineStart)));
        return result;
    }

    /**
     * 验证单逻辑行的前缀宽度向量与码点边界对齐，且 caret 命中几何正确。
     */
    @Test
    public void shouldComputePrefixWidthsForSingleLine() {
        TextLayoutEngine engine = new TextLayoutEngine();
        CountingMeasure measure = new CountingMeasure(CHAR_WIDTH);

        List<VisualLineLayout> result = engine.layout(linesOf("abc"), 0, 1, LINE_HEIGHT, false, measure);

        Assert.assertEquals(1, result.size());
        VisualLineLayout line = result.get(0);
        Assert.assertEquals("abc", line.getText());
        Assert.assertEquals(0, line.resolveBoundaryX(0));
        Assert.assertEquals(CHAR_WIDTH, line.resolveBoundaryX(1));
        Assert.assertEquals(2 * CHAR_WIDTH, line.resolveBoundaryX(2));
        Assert.assertEquals(3 * CHAR_WIDTH, line.resolveBoundaryX(3));
    }

    /**
     * 验证空行也产出一条视觉行，前缀向量退化为单元素零向量。
     */
    @Test
    public void shouldProduceSingleVisualLineForEmptyLine() {
        TextLayoutEngine engine = new TextLayoutEngine();
        CountingMeasure measure = new CountingMeasure(CHAR_WIDTH);

        List<VisualLineLayout> result = engine.layout(linesOf(""), 0, 1, LINE_HEIGHT, false, measure);

        Assert.assertEquals(1, result.size());
        Assert.assertEquals("", result.get(0).getText());
        Assert.assertEquals(0, result.get(0).resolveBoundaryX(0));
    }

    /**
     * 验证多逻辑行按行高纵向堆叠，且各行字符区间连续。
     */
    @Test
    public void shouldStackMultipleLogicalLines() {
        TextLayoutEngine engine = new TextLayoutEngine();
        CountingMeasure measure = new CountingMeasure(CHAR_WIDTH);

        List<VisualLineLayout> result = engine.layout(linesOf("ab\n\ncd"), 0, 1, LINE_HEIGHT, false, measure);

        Assert.assertEquals(3, result.size());
        Assert.assertEquals(0, result.get(0).getVisualTop());
        Assert.assertEquals(LINE_HEIGHT, result.get(1).getVisualTop());
        Assert.assertEquals(2 * LINE_HEIGHT, result.get(2).getVisualTop());
        Assert.assertEquals("ab", result.get(0).getText());
        Assert.assertEquals("", result.get(1).getText());
        Assert.assertEquals("cd", result.get(2).getText());
        Assert.assertEquals(4, result.get(2).getVisualStartIndex());
        Assert.assertEquals(6, result.get(2).getVisualEndIndex());
    }

    /**
     * 验证 CJK 码点同样按每码点等宽推进，前缀几何正确。
     */
    @Test
    public void shouldHandleCjkCodePoints() {
        TextLayoutEngine engine = new TextLayoutEngine();
        CountingMeasure measure = new CountingMeasure(CHAR_WIDTH);

        List<VisualLineLayout> result = engine.layout(linesOf("中文字"), 0, 1, LINE_HEIGHT, false, measure);

        Assert.assertEquals(1, result.size());
        VisualLineLayout line = result.get(0);
        Assert.assertEquals(CHAR_WIDTH, line.resolveBoundaryX(1));
        Assert.assertEquals(3 * CHAR_WIDTH, line.resolveBoundaryX(3));
        Assert.assertEquals(2, line.resolveClosestCaretIndex(2 * CHAR_WIDTH + 1));
    }

    /**
     * 验证软换行把超宽逻辑行拆成多条视觉行，续行前缀从零重建。
     */
    @Test
    public void shouldSoftWrapLongLine() {
        TextLayoutEngine engine = new TextLayoutEngine();
        CountingMeasure measure = new CountingMeasure(CHAR_WIDTH);

        // 可用宽度 = 2.5 字符宽，应每 2 字符断行：abcde -> ab / cd / e
        int availableWidth = CHAR_WIDTH * 2 + CHAR_WIDTH / 2;
        List<VisualLineLayout> result = engine.layout(linesOf("abcde"), availableWidth, 1, LINE_HEIGHT, true, measure);

        Assert.assertEquals(3, result.size());
        Assert.assertEquals("ab", result.get(0).getText());
        Assert.assertEquals("cd", result.get(1).getText());
        Assert.assertEquals("e", result.get(2).getText());
        Assert.assertEquals(0, result.get(1).resolveBoundaryX(0));
        Assert.assertEquals(CHAR_WIDTH, result.get(1).resolveBoundaryX(1));
        Assert.assertEquals(2, result.get(1).getVisualStartIndex());
        Assert.assertEquals(4, result.get(1).getVisualEndIndex());
    }

    /**
     * 验证软换行下首码点即超宽时仍至少容纳一个码点，避免死循环。
     */
    @Test
    public void shouldForceAtLeastOneCodePointWhenFirstExceedsWidth() {
        TextLayoutEngine engine = new TextLayoutEngine();
        CountingMeasure measure = new CountingMeasure(CHAR_WIDTH);

        List<VisualLineLayout> result = engine.layout(linesOf("abc"), 1, 1, LINE_HEIGHT, true, measure);

        Assert.assertEquals(3, result.size());
        Assert.assertEquals("a", result.get(0).getText());
        Assert.assertEquals("b", result.get(1).getText());
        Assert.assertEquals("c", result.get(2).getText());
    }

    /**
     * 验证稳态零测量：内容、宽度、纪元、行高、软换行开关都不变时复用同一结果实例，不再测量。
     */
    @Test
    public void shouldReuseCacheWhenInputsUnchanged() {
        TextLayoutEngine engine = new TextLayoutEngine();
        CountingMeasure measure = new CountingMeasure(CHAR_WIDTH);

        List<VisualLineLayout> first = engine.layout(linesOf("abc\ndef"), 0, 1, LINE_HEIGHT, false, measure);
        int prefixCallsAfterFirst = measure.prefixWidthsCalls;
        List<VisualLineLayout> second = engine.layout(linesOf("abc\ndef"), 0, 1, LINE_HEIGHT, false, measure);

        Assert.assertSame(first, second);
        Assert.assertEquals(prefixCallsAfterFirst, measure.prefixWidthsCalls);
    }

    /**
     * 验证内容变化触发重算。
     */
    @Test
    public void shouldRecomputeWhenContentChanges() {
        TextLayoutEngine engine = new TextLayoutEngine();
        CountingMeasure measure = new CountingMeasure(CHAR_WIDTH);

        List<VisualLineLayout> first = engine.layout(linesOf("abc"), 0, 1, LINE_HEIGHT, false, measure);
        List<VisualLineLayout> second = engine.layout(linesOf("abcd"), 0, 1, LINE_HEIGHT, false, measure);

        Assert.assertNotSame(first, second);
        Assert.assertEquals("abcd", second.get(0).getText());
    }

    /**
     * 验证字体测量纪元变化触发重算（即使内容与宽度不变）。
     */
    @Test
    public void shouldRecomputeWhenEpochChanges() {
        TextLayoutEngine engine = new TextLayoutEngine();
        CountingMeasure measure = new CountingMeasure(CHAR_WIDTH);

        List<VisualLineLayout> first = engine.layout(linesOf("abc"), 0, 1, LINE_HEIGHT, false, measure);
        List<VisualLineLayout> second = engine.layout(linesOf("abc"), 0, 2, LINE_HEIGHT, false, measure);

        Assert.assertNotSame(first, second);
    }

    /**
     * 验证可用宽度变化触发重算（影响软换行）。
     */
    @Test
    public void shouldRecomputeWhenWidthChanges() {
        TextLayoutEngine engine = new TextLayoutEngine();
        CountingMeasure measure = new CountingMeasure(CHAR_WIDTH);

        List<VisualLineLayout> first = engine.layout(linesOf("abcde"), CHAR_WIDTH * 2, 1, LINE_HEIGHT, true, measure);
        List<VisualLineLayout> second = engine.layout(linesOf("abcde"), CHAR_WIDTH * 4, 1, LINE_HEIGHT, true, measure);

        Assert.assertNotSame(first, second);
        Assert.assertTrue(second.size() < first.size());
    }

    /**
     * 验证每条逻辑行只取一次前缀宽度向量（O(N) 增量），而非逐前缀测量。
     */
    @Test
    public void shouldMeasureEachLineOnceViaPrefixWidths() {
        TextLayoutEngine engine = new TextLayoutEngine();
        CountingMeasure measure = new CountingMeasure(CHAR_WIDTH);

        engine.layout(linesOf("abcdef\nghijkl\nmnopqr"), 0, 1, LINE_HEIGHT, false, measure);

        // 3 条非空逻辑行，各取一次前缀向量
        Assert.assertEquals(3, measure.prefixWidthsCalls);
    }

    /**
     * 验证 invalidate 后即便输入不变也会重算。
     */
    @Test
    public void shouldRecomputeAfterInvalidate() {
        TextLayoutEngine engine = new TextLayoutEngine();
        CountingMeasure measure = new CountingMeasure(CHAR_WIDTH);

        List<VisualLineLayout> first = engine.layout(linesOf("abc"), 0, 1, LINE_HEIGHT, false, measure);
        engine.invalidate();
        List<VisualLineLayout> second = engine.layout(linesOf("abc"), 0, 1, LINE_HEIGHT, false, measure);

        Assert.assertNotSame(first, second);
    }

    /**
     * 验证默认前缀宽度实现与逐次 widthOf 数值一致（线性测量）。
     */
    @Test
    public void shouldKeepDefaultPrefixWidthsConsistentWithWidthOf() {
        CountingMeasure measure = new CountingMeasure(CHAR_WIDTH);

        int[] prefixWidths = measure.prefixWidths("hello");

        Assert.assertArrayEquals(new int[] {0, 12, 24, 36, 48, 60}, prefixWidths);
        Assert.assertEquals(measure.widthOf("hel"), prefixWidths[3]);
    }
}
