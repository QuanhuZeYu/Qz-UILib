package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * ChatLineLayouter 契约测试:断词/超宽词硬断/换行符/格式码对不可拆/行尾空白丢弃/缓存。
 *
 * <p>度量 mock:等宽 4px/字符,§ 格式码对零宽(与 UILib advance 口径一致)。</p>
 */
public class ChatLineLayouterTest {

    private static final int CHAR_WIDTH = 4;
    private static final int MAX_WIDTH = 20; // 5 字符

    @Test
    public void shouldKeepShortTextInSingleLine() {
        Assert.assertEquals(list("hello"), split("hello"));
    }

    @Test
    public void shouldBreakAtSpaces() {
        Assert.assertEquals(list("hello", "world"), split("hello world"));
    }

    @Test
    public void shouldHardBreakOversizedWords() {
        Assert.assertEquals(list("abcde", "fg"), split("abcdefg"));
    }

    @Test
    public void shouldBreakAtNewlines() {
        Assert.assertEquals(list("a", "b"), split("a\nb"));
        Assert.assertEquals(list("hello", "hi"), split("hello\nhi"));
    }

    @Test
    public void shouldKeepFormatCodePairsIntact() {
        // §a 零宽:5 个有效字符恰好满行,格式码对留在行内
        Assert.assertEquals(list("\u00a7ahello"), split("\u00a7ahello"));
        // 10 个有效字符:断在格式码对之后,格式码对不被拆开
        Assert.assertEquals(list("\u00a7aabcde", "fghij"), split("\u00a7aabcdefghij"));
    }

    @Test
    public void shouldDropTrailingSpaces() {
        Assert.assertEquals(list("hello", "world"), split("hello   world"));
    }

    @Test
    public void shouldReturnSingleEmptyLineForEmptyText() {
        Assert.assertEquals(list(""), split(""));
    }

    @Test
    public void shouldHandleLongChineseText() {
        Assert.assertEquals(list("一二三四五", "六"), split("一二三四五六"));
    }

    @Test
    public void shouldCachePerEpochAndKey() {
        EpochMeasure measure = new EpochMeasure();
        ChatLineLayouter layouter = new ChatLineLayouter(measure, 13);

        List<String> first = layouter.layout("hello world", MAX_WIDTH);
        List<String> second = layouter.layout("hello world", MAX_WIDTH);
        Assert.assertSame("epoch 未变时命中缓存", first, second);

        measure.epoch++;
        List<String> third = layouter.layout("hello world", MAX_WIDTH);
        Assert.assertNotSame("epoch 变化后缓存整体失效", first, third);
        Assert.assertEquals(first, third);
    }

    private static List<String> split(String text) {
        return ChatLineLayouter.splitLines(text, MAX_WIDTH, fixedMeasure(), 13);
    }

    private static List<String> list(String... items) {
        return java.util.Arrays.asList(items);
    }

    private static ChatLineLayouter.Measure fixedMeasure() {
        return new ChatLineLayouter.Measure() {
            @Override
            public float advance(String text, int fontSizePx) {
                int effective = 0;
                for (int i = 0; i < text.length(); i++) {
                    if (text.charAt(i) == '\u00a7' && i + 1 < text.length()) {
                        i++;
                        continue;
                    }
                    effective++;
                }
                return effective * CHAR_WIDTH;
            }

            @Override
            public int epoch() {
                return 0;
            }
        };
    }

    private static final class EpochMeasure implements ChatLineLayouter.Measure {

        private int epoch = 0;

        @Override
        public float advance(String text, int fontSizePx) {
            int effective = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\u00a7' && i + 1 < text.length()) {
                    i++;
                    continue;
                }
                effective++;
            }
            return effective * CHAR_WIDTH;
        }

        @Override
        public int epoch() {
            return epoch;
        }
    }
}
