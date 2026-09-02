package club.heiqi.uilib.ui.text;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;

/**
 * {@link TextEllipsizer} 单元测试。
 *
 * <p>宽度模型：每字符固定 8px（{@code width(s) = s.length() * 8}），省略号「…」= 1 字符 = 8px。
 * 覆盖边界：空串 / 恰好放下 / 超宽 / 单字超宽 / 省略号自身放不下 / 宽度非正 / 显式换行保留 /
 * 分词换行 / 行数截断强制省略号。</p>
 */
public class TextEllipsizerTest {

    /** 固定 8px/字符的测量替身；style 重载按同一 charWidth 模型测量（不引入真实字号缩放）。 */
    private static final class FakeMeasureService implements TextMeasureService {
        private final int charWidth;

        FakeMeasureService(int charWidth) {
            this.charWidth = charWidth;
        }

        @Override
        public int getEpoch() {
            return 0;
        }

        @Override
        public int getStringWidth(String text) {
            return (text == null ? 0 : text.length()) * charWidth;
        }

        @Override
        public int getStringWidth(String text, TextContentMode textContentMode) {
            return getStringWidth(text);
        }

        @Override
        public int getStringWidth(String text, TextContentMode textContentMode,
                                  UiFontWeight fontWeight, UiFontStyle fontStyle) {
            return getStringWidth(text);
        }

        @Override
        public int getStringWidth(String text, club.heiqi.uilib.ui.text.TextMeasureStyle style) {
            return getStringWidth(text);
        }

        @Override
        public int getLineHeight() {
            return 16;
        }

        @Override
        public int getLineHeight(club.heiqi.uilib.ui.text.TextMeasureStyle style) {
            return 16;
        }

        @Override
        public int getLineHeight(String text, club.heiqi.uilib.ui.text.TextMeasureStyle style) {
            return 16;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            return text;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth,
                club.heiqi.uilib.ui.text.TextMeasureStyle style) {
            return text;
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Arrays.asList(text);
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth,
                club.heiqi.uilib.ui.text.TextMeasureStyle style) {
            return Arrays.asList(text);
        }
    }

    private static final int CHAR = 8;
    private static final FakeMeasureService SERVICE = new FakeMeasureService(CHAR);

    private static int px(int chars) {
        return chars * CHAR;
    }

    // ==================== ellipsize 核心（宽度函数重载） ====================

    @Test
    public void nullAndEmptyReturnedAsIs() {
        Assert.assertNull(TextEllipsizer.ellipsize(s -> px(s.length()), null, 100));
        Assert.assertEquals("", TextEllipsizer.ellipsize(s -> px(s.length()), "", 100));
    }

    @Test
    public void exactFitReturnedAsIs() {
        Assert.assertEquals("abc", TextEllipsizer.ellipsize(s -> px(s.length()), "abc", px(3)));
    }

    @Test
    public void overflowingTextGetsEllipsisSuffix() {
        // "abcde" = 40px > 32px；省略号 8px → 前缀最多 24px = 3 字符
        Assert.assertEquals("abc" + TextEllipsizer.ELLIPSIS,
                TextEllipsizer.ellipsize(s -> px(s.length()), "abcde", px(4)));
        Assert.assertEquals(px(4),
                px(("abc" + TextEllipsizer.ELLIPSIS).length()));
    }

    @Test
    public void singleCharTooWideCollapsesToEllipsis() {
        // "ab" = 16px > 8px；单字符 8px + 省略号 8px > 8px → 前缀空 + 省略号
        Assert.assertEquals(TextEllipsizer.ELLIPSIS,
                TextEllipsizer.ellipsize(s -> px(s.length()), "ab", px(1)));
    }

    @Test
    public void ellipsisAloneTooWideReturnsEmpty() {
        // 省略号 8px > 4px → 空串（调用方自行裁剪/隐藏）
        Assert.assertEquals("", TextEllipsizer.ellipsize(s -> px(s.length()), "abcdef", 4));
        Assert.assertEquals("", TextEllipsizer.ellipsize(s -> px(s.length()), "abc", 0));
        Assert.assertEquals("", TextEllipsizer.ellipsize(s -> px(s.length()), "abc", -5));
    }

    // ==================== 测量服务重载 ====================

    @Test
    public void serviceOverloadMatchesFunctionOverload() {
        // 替身不引入真实字号缩放（charWidth 模型），只验证委托链路
        String viaService = TextEllipsizer.ellipsize(SERVICE, "abcdefghij", px(4));
        Assert.assertNotNull(viaService);
        Assert.assertTrue(viaService.endsWith(TextEllipsizer.ELLIPSIS));
    }

    @Test
    public void styledServiceOverloadUsesStyleScale() {
        // 替身 charWidth 模型不缩放 → 与函数重载逐位一致
        TextMeasureStyle style = TextMeasureStyle.fontSizePx(16);
        String viaStyle = TextEllipsizer.ellipsize(SERVICE, "abcde", px(4), style);
        Assert.assertEquals("abc" + TextEllipsizer.ELLIPSIS, viaStyle);
    }

    // ==================== wrapLines ====================

    @Test
    public void wrapOnWords() {
        // "aaa bbb ccc ddd"：词 24px + 空格 8px；宽度 40px（5 字符）→ 每行一个词
        List<String> lines = TextEllipsizer.wrapLines(s -> px(s.length()),
                "aaa bbb ccc ddd", px(5), 0);
        Assert.assertEquals(Arrays.asList("aaa", "bbb", "ccc", "ddd"), lines);
    }

    @Test
    public void twoWordsPerLineWhenTheyFit() {
        // 宽度 7 字符 = 56px："aaa bbb" = 56px 恰好放下
        List<String> lines = TextEllipsizer.wrapLines(s -> px(s.length()),
                "aaa bbb ccc ddd", px(7), 0);
        Assert.assertEquals(Arrays.asList("aaa bbb", "ccc ddd"), lines);
    }

    @Test
    public void overlongWordEllipsizedOnItsOwnLine() {
        // "abcdefgh" = 64px > 32px → 前缀 3 字符 + 省略号 = 32px
        List<String> lines = TextEllipsizer.wrapLines(s -> px(s.length()),
                "abcdefgh xyz", px(4), 0);
        Assert.assertEquals(Arrays.asList("abc" + TextEllipsizer.ELLIPSIS, "xyz"), lines);
    }

    @Test
    public void explicitNewlinesPreserved() {
        List<String> lines = TextEllipsizer.wrapLines(s -> px(s.length()),
                "aaa\nbbb ccc", px(10), 0);
        Assert.assertEquals(Arrays.asList("aaa", "bbb ccc"), lines);
    }

    @Test
    public void maxLinesTruncatesAndForcesEllipsis() {
        // 宽度 16px：单字词 8px 放得下、"a b"=24px 放不下 → 每行一词，共 6 行，上限 2
        List<String> lines = TextEllipsizer.wrapLines(s -> px(s.length()),
                "a b c d e f", px(2), 2);
        Assert.assertEquals(2, lines.size());
        Assert.assertEquals("a", lines.get(0));
        // 末行强制省略：保留词 "b" 并追加省略号（16px 内）
        Assert.assertEquals("b" + TextEllipsizer.ELLIPSIS, lines.get(1));
    }

    @Test
    public void noWidthCapDoesNotWrapButRespectsMaxLines() {
        List<String> lines = TextEllipsizer.wrapLines(s -> px(s.length()),
                "aaa\nbbb\nccc", 0, 2);
        Assert.assertEquals(Arrays.asList("aaa", "bbb"), lines);
    }

    @Test
    public void nullAndEmptyTextYieldEmptyLines() {
        Assert.assertTrue(TextEllipsizer.wrapLines(s -> px(s.length()), null, 100, 0).isEmpty());
        Assert.assertTrue(TextEllipsizer.wrapLines(s -> px(s.length()), "", 100, 0).isEmpty());
    }

    // ==================== 控制字符口径（UnicodeTextClassifier 接入） ====================

    @Test
    public void unicodeNewlineFamilySplitsSegments() {
        List<String> ls = TextEllipsizer.wrapLines(s -> px(s.length()), "a\u2028b", px(100), 0);
        Assert.assertEquals(Arrays.asList("a", "b"), ls);
        List<String> crlf = TextEllipsizer.wrapLines(s -> px(s.length()), "a\r\nb", px(100), 0);
        Assert.assertEquals(Arrays.asList("a", "b"), crlf);
    }

    @Test
    public void unicodeWhitespaceFamilySplitsWords() {
        // BA 类空格（U+3000）参与分词；段首尾可断空白剥除
        List<String> lines = TextEllipsizer.wrapLines(s -> px(s.length()), "a\u3000b", px(1), 0);
        Assert.assertEquals(Arrays.asList("a", "b"), lines);
        List<String> trimmed = TextEllipsizer.wrapLines(s -> px(s.length()), "\u3000a\u3000", px(100), 0);
        Assert.assertEquals(Arrays.asList("a"), trimmed);
    }

    @Test
    public void nbspGlueIsNotAWordSeparator() {
        // NBSP 是 GL 禁断胶水：词内不拆开（与普通空格分词行为对照）
        List<String> glue = TextEllipsizer.wrapLines(s -> px(s.length()), "a\u00A0b", px(100), 0);
        Assert.assertEquals(Arrays.asList("a\u00A0b"), glue);
        List<String> space = TextEllipsizer.wrapLines(s -> px(s.length()), "a b", px(1), 0);
        Assert.assertEquals(Arrays.asList("a", "b"), space);
    }

    @Test
    public void zwspSplitsOverlongWordWithoutEllipsis() {
        // "ab<ZWSP>cd" 词内软断行：两段各 2 字符恰好放下，不省略
        List<String> lines = TextEllipsizer.wrapLines(s -> px(s.length()), "ab\u200Bcd", px(2), 0);
        Assert.assertEquals(Arrays.asList("ab", "cd"), lines);
    }

    @Test
    public void softHyphenBreakAppendsVisibleHyphen() {
        List<String> lines = TextEllipsizer.wrapLines(s -> px(s.length()), "ab\u00ADcd", px(3), 0);
        Assert.assertEquals(Arrays.asList("ab-", "cd"), lines);
    }

    @Test
    public void trailingSoftHyphenStaysInvisible() {
        // 词尾软连字符无断行 → 不显示连字符
        List<String> lines = TextEllipsizer.wrapLines(s -> px(s.length()), "ab\u00AD", px(100), 0);
        Assert.assertEquals(Arrays.asList("ab"), lines);
    }


    // ==================== breakLongWords：超宽词按码点折行（链接 tooltip 用） ====================

    @Test
    public void breakLongWordsSplitsOverlongUrlWithoutEllipsis() {
        String url = "https://a.co/GT-New-Horizons-odpack";
        List<String> lines = TextEllipsizer.wrapLines(s -> px(s.length()), url, px(6), 0, true);
        Assert.assertEquals(Arrays.asList("https:", "//a.co", "/GT-Ne", "w-Hori", "zons-o", "dpack"), lines);
        StringBuilder joined = new StringBuilder();
        for (String line : lines) {
            Assert.assertFalse("折行结果不得含省略号:" + line, line.contains(TextEllipsizer.ELLIPSIS));
            joined.append(line);
        }
        Assert.assertEquals("逐行拼回必须等于原 URL(零丢失)", url, joined.toString());
    }

    @Test
    public void defaultOverloadKeepsLegacyEllipsisForOverlongWord() {
        // 四参重载 = breakLongWords=false：既有调用点行为逐字节不变
        List<String> legacy = TextEllipsizer.wrapLines(s -> px(s.length()),
                "https://a.co/GT-New-Horizons-odpack", px(6), 0);
        Assert.assertEquals(1, legacy.size());
        Assert.assertTrue("旧行为仍是省略号截断:" + legacy,
                legacy.get(0).endsWith(TextEllipsizer.ELLIPSIS));
        Assert.assertEquals(Arrays.asList(legacy.get(0)),
                TextEllipsizer.wrapLines(s -> px(s.length()),
                        "https://a.co/GT-New-Horizons-odpack", px(6), 0, false));
    }

    @Test
    public void brokenWordTailContinuesWithFollowingWords() {
        // 切开的最后一段留在当前行，后续词照常续排（不得硬拆成两行浪费一行）
        List<String> lines = TextEllipsizer.wrapLines(s -> px(s.length()), "averylongword xy",
                px(6), 0, true);
        Assert.assertEquals(Arrays.asList("averyl", "ongwor", "d xy"), lines);
    }

    @Test
    public void breakLongWordsTerminatesWhenSingleCodePointExceedsWidth() {
        // 连一个字符都放不下：每行推进一个码点，切分循环必须终止（防死循环锚定）
        // 宽度直接给 4px（不是 px(4)=32px）：小于一个字符 8px，任何单码点都放不下
        List<String> lines = TextEllipsizer.wrapLines(s -> s.length() * 8, "abc", 4, 0, true);
        Assert.assertEquals(Arrays.asList("a", "b", "c"), lines);
    }

    @Test
    public void breakLongWordsNeverSplitsSurrogatePair() {
        // 宽度模型按 UTF-16 单元计数（emoji = 2），折行按码点推进 → 不得产出孤立代理
        List<String> lines = TextEllipsizer.wrapLines(s -> px(s.length()), "a\uD83D\uDE00b",
                px(2), 0, true);
        Assert.assertEquals(Arrays.asList("a", "\uD83D\uDE00", "b"), lines);
        for (String line : lines) {
            Assert.assertFalse("行内不得出现孤立代理:" + line,
                    Character.isHighSurrogate(line.charAt(line.length() - 1)));
        }
    }

    @Test
    public void breakLongWordsStillRespectsMaxLinesEllipsis() {
        // 行数上限优先：折出的行超上限时末行仍带「还有更多」提示
        List<String> lines = TextEllipsizer.wrapLines(s -> px(s.length()),
                "https://a.co/GT-New-Horizons-odpack", px(6), 2, true);
        Assert.assertEquals(2, lines.size());
        Assert.assertTrue("超行数末行必须带省略号:" + lines,
                lines.get(1).endsWith(TextEllipsizer.ELLIPSIS));
    }
}
