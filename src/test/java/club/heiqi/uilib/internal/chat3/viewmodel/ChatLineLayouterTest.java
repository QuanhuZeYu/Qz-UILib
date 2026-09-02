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
        // 10 个有效字符:断在格式码对之后,格式码对不被拆开;K3 修复②:断行续行行首
        // 重发当前生效格式码(§a),续行颜色不丢
        Assert.assertEquals(list("\u00a7aabcde", "\u00a7afghij"), split("\u00a7aabcdefghij"));
    }

    // ==================== K3 修复①:普通散文词边界回退 ====================

    @Test
    public void shouldBreakAtWordBoundaryInsteadOfInsideWord() {
        // "ab cd e":若在超宽处字符硬断,末行只剩孤字 "e"("cd" 被拆开,疑似丢字);
        // 词边界回退应把 "cd" 整词移入续行 → ["ab", "cd e"]
        Assert.assertEquals(list("ab", "cd e"), split("ab cd e"));
        // 多词散文:每行都以词边界收尾,不在词中间断
        Assert.assertEquals(list("hello", "world", "again"), split("hello world again"));
    }

    @Test
    public void shouldStillHardBreakLongUnbrokenTokenByCharacters() {
        // 无空格长串(URL/哈希,设计稿 §5.4):行内无空白 → 保持字符级硬断
        Assert.assertEquals(list("abcde", "fg"), split("abcdefg"));
        // 长词紧邻短词:短词先行,长词字符断,词边界优先但不阻塞字符断
        Assert.assertEquals(list("ab", "cdefg", "hij"), split("ab cdefghij"));
    }

    // ==================== K3 修复②:续行格式码重发 ====================

    @Test
    public void shouldReissueColorCodeOnContinuationLine() {
        Assert.assertEquals(list("\u00a7chello", "\u00a7cworld"), split("\u00a7chello world"));
    }

    @Test
    public void shouldReissueColorAndStyleCodesOnContinuationLine() {
        // 颜色 + 样式位同时生效:续行行首重发 "§c§l"
        Assert.assertEquals(list("\u00a7c\u00a7lhello", "\u00a7c\u00a7lworld"),
                split("\u00a7c\u00a7lhello world"));
    }

    @Test
    public void shouldNotReissueFormatsClearedByResetCode() {
        // §r 清空全部格式:断行续行不再重发任何格式码(§r 对留在原行内,渲染无效果)
        Assert.assertEquals(list("\u00a7chello\u00a7r", "world"), split("\u00a7chello\u00a7r world"));
    }

    @Test
    public void shouldReissueOnlyLatestColor() {
        // 颜色码后到覆盖:断行前最后颜色 §e 生效,续行重发 §e(非更早的 §c)
        Assert.assertEquals(list("\u00a7cabc\u00a7ede", "\u00a7efgh"), split("\u00a7cabc\u00a7ede fgh"));
    }

    @Test
    public void continuationReissueIsZeroWidthAndStaysWithinMaxWidth() {
        // 重发前缀(§ 对)零宽:续行有效字符数仍受行宽约束(5 字符)
        Assert.assertEquals(list("\u00a7aabcde", "\u00a7afghij", "\u00a7aklmn"), split("\u00a7aabcdefghijklmn"));
    }

    // ==================== K3 缺陷③:丢字符穷举不变量(实际未复现独立丢字符,修复①后防回归) ====================

    @Test
    public void noVisibleCharacterIsEverLostAcrossBreaks() {
        // 穷举短文本 × 多种行宽:每行超宽断行/词边界回退/格式码重发后,
        // 所有可见字符(剥 § 对与空白)必须完整保序出现——修复前逐字符硬断把词拆开
        // (如 "cd" → "c" / 孤行 "d")造成"疑似丢 1 字符"的观感,此不变量杜绝该回归。
        String[] atoms = { "a", "b", " ", "\u00a7c", "\u00a7l", "\u00a7r", "\n" };
        int checked = 0;
        for (int len = 0; len <= 6; len++) {
            int[] idx = new int[len];
            while (true) {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < len; i++) {
                    text.append(atoms[idx[i]]);
                }
                for (int width : new int[] { 4, 8, 12, 16, 20 }) {
                    List<String> lines = ChatLineLayouter.splitLines(
                            text.toString(), width, fixedMeasure(), 13);
                    Assert.assertEquals("文本 " + text + " 宽度 " + width + " 丢可见字符",
                            compact(text.toString()), compact(join(lines)));
                    checked++;
                }
                // 进位到下一个组合
                int p = len - 1;
                while (p >= 0) {
                    idx[p]++;
                    if (idx[p] < atoms.length) break;
                    idx[p] = 0;
                    p--;
                }
                if (p < 0) break;
            }
        }
        Assert.assertTrue("穷举覆盖量不足", checked > 10000);
    }

    /** 剥 § 格式码对与全部空白,仅留可见字符序列(丢字符判定口径)。 */
    private static String compact(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String join(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line);
        }
        return out.toString();
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
    public void shouldHardBreakUrlWithoutSpaces() {
        // 无空格 URL/哈希长串(设计稿 §5.4:word-break:anywhere 语义)——字符级断行,
        // 整串不允许横向溢出气泡(与超宽词硬断同分支,URL 专用锚定)
        Assert.assertEquals(list("https", "://ab", "cdefg", "hijk"), split("https://abcdefghijk"));
        Assert.assertEquals(list("0A1B2", "C3D4E"), split("0A1B2C3D4E"));
    }

    // ==================== K3 三轮修复:词边界回退不得产出可见内容为空的行 ====================

    @Test
    public void shouldNotEmitVisibleEmptyLineWhenIndentPrecedesOverflow() {
        // 真机 URL 缩进行复现:前导缩进(无可见字符)+ §6 时词边界回退把缩进单独断成
        // "§6" 零内容行(渲染为空行占 18px);修复后缩进并入首行,续行正常字符断。
        String url = "\u00a76     https://abcdefghijklmnop";
        // 4px/字符、宽 40(10 单位):5 空格 + §6 + "https" 恰好满行,
        // 超宽发生在 ':' 处——修复前缩进被单独断成 "§6" 零内容行
        List<String> lines = ChatLineLayouter.splitLines(url, 40, fixedMeasure(), 13);
        for (String line : lines) {
            Assert.assertFalse("任何行都不得可见内容为空(仅空白/§ 码):" + line,
                    visibleEmpty(line));
        }
        Assert.assertTrue("缩进保留在首行", lines.get(0).startsWith("\u00a76     https"));
        // 零可见字符丢失不变量仍成立
        Assert.assertEquals(compact(url), compact(join(lines)));
    }

    @Test
    public void shouldNotEmitVisibleEmptyLineAtNarrowWidths() {
        // 窄行硬断路径同样守卫:行内只有缩进 + § 码时不得断出空行
        String text = "\u00a7c     ab";
        List<String> lines = ChatLineLayouter.splitLines(text, 4, fixedMeasure(), 13);
        for (String line : lines) {
            Assert.assertFalse("窄行下同样无零内容行:" + line, visibleEmpty(line));
        }
        Assert.assertEquals(compact(text), compact(join(lines)));
    }

    /** 剥 § 格式码对与空白后是否为空(零内容行判定)。 */
    private static boolean visibleEmpty(String line) {
        return compact(line).isEmpty();
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


    // ==================== 跨显示行 URL 续链:断行来源标记 ====================

    @Test
    public void shouldMarkEveryLineInsideHardBrokenUrl() {
        List<ChatLineLayouter.LineFragment> fragments = fragments("https://abcdefghijk");
        Assert.assertEquals(4, fragments.size());
        Assert.assertFalse("逻辑首行不接续任何词", fragments.get(0).continuesWord());
        for (int i = 1; i < fragments.size(); i++) {
            Assert.assertTrue("无空格长串的每个续行都必须标记 continuesWord(行 " + i + ")",
                    fragments.get(i).continuesWord());
        }
    }

    @Test
    public void shouldNotMarkLineAfterPendingSpaceHardBreak() {
        // 反例本体(写测试时实测抓到):"hello world" 在 5 字符宽下,词间空格此刻还在
        // pendingSpaces 里、未并入 current,故 lastWhitespace(current) 返回 -1 而落入
        // 字符硬断分支——它仍是词边界,不得标记接续。
        List<ChatLineLayouter.LineFragment> fragments = fragments("hello world");
        Assert.assertEquals(list("hello", "world"), texts(fragments));
        Assert.assertFalse("待定空白处的断行是词边界,不是词内硬断",
                fragments.get(1).continuesWord());
    }

    @Test
    public void shouldNotMarkExplicitNewLineAsContinuation() {
        List<ChatLineLayouter.LineFragment> fragments = fragments("https\nabcde");
        Assert.assertEquals(2, fragments.size());
        Assert.assertFalse("换行符是硬边界", fragments.get(1).continuesWord());
    }

    @Test
    public void continuesWordMustEqualAbsenceOfWhitespaceAtBreakPoint() {
        // 不靠手算行数:直接对账「continuesWord ⟺ 源文本断点两侧无空白」。
        // 这是跨行 URL 续链唯一的正确性前提——误判会把相邻却不相关的词粘成一条 URL。
        String[] corpus = { "hello world", "https://abcdefghijk", "aaa http://a.co world",
                "\u00a76     https://abcdefghijklmnop", "\u4e00\u4e8c\u4e09\u56db\u4e94\u516d",
                "a\nb\nc", "x http://a.co/b http://c.co/d end",
                "     leading indent then several words here", "aaaaaaaaaaaaaaaaaaaaaaaa",
                "https://a.co/x\ty", "\u00a7a\u00a7nword1 word2" };
        for (String text : corpus) {
            List<ChatLineLayouter.LineFragment> fragments = fragments(text);
            StringBuilder joined = new StringBuilder();
            for (ChatLineLayouter.LineFragment fragment : fragments) {
                joined.append(visibleChars(fragment.getText()));
            }
            Assert.assertEquals("显示行可见字符必须无间隙铺满源文本可见字符:" + text,
                    visibleChars(text), joined.toString());

            List<Integer> srcIdx = new java.util.ArrayList<Integer>();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\u00a7' && i + 1 < text.length()) {
                    i++;
                    continue;
                }
                if (Character.isWhitespace(c)) {
                    continue;
                }
                srcIdx.add(Integer.valueOf(i));
            }
            int cursor = 0;
            int prevLast = -1;
            for (int i = 0; i < fragments.size(); i++) {
                int count = visibleChars(fragments.get(i).getText()).length();
                if (count == 0) {
                    continue; // 仅 § 码的零可见行不参与断点对账
                }
                int first = srcIdx.get(cursor).intValue();
                if (prevLast >= 0) {
                    String gap = text.substring(prevLast + 1, first);
                    boolean hasWhitespace = false;
                    for (int k = 0; k < gap.length(); k++) {
                        if (Character.isWhitespace(gap.charAt(k))) {
                            hasWhitespace = true;
                            break;
                        }
                    }
                    Assert.assertEquals("行 " + i + " continuesWord 必须等于「断点无空白」;文本=["
                                    + text + "] 断点之间=[" + gap + "]", Boolean.valueOf(!hasWhitespace),
                            Boolean.valueOf(fragments.get(i).continuesWord()));
                }
                cursor += count;
                prevLast = srcIdx.get(cursor - 1).intValue();
            }
        }
    }

    /** 剥 § 格式码对与空白后的可见字符序列(行 ↔ 源文本对账口径)。 */
    private static String visibleChars(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    @Test
    public void splitLinesMustBeExactTextProjectionOfSplitFragments() {
        String[] corpus = { "hello world", "https://abcdefghijk", "aaa http://a.co world",
                "\u00a76     https://abcdefghijklmnop", "\u4e00\u4e8c\u4e09\u56db\u4e94\u516d",
                "a\nb\nc", "", "   ", "x http://a.co/b http://c.co/d end" };
        for (String text : corpus) {
            Assert.assertEquals("splitLines 必须是 splitFragments 的纯文本投影:" + text,
                    ChatLineLayouter.splitLines(text, MAX_WIDTH, fixedMeasure(), 13),
                    texts(fragments(text)));
        }
    }

    @Test
    public void shouldShareOneCacheEntryBetweenLayoutAndFragments() {
        ChatLineLayouter layouter = new ChatLineLayouter(fixedMeasure(), 13);
        List<String> lines = layouter.layout("https://abcdefghijk", MAX_WIDTH);
        List<ChatLineLayouter.LineFragment> fragments =
                layouter.layoutFragments("https://abcdefghijk", MAX_WIDTH);
        Assert.assertSame("layout 重复调用必须命中同一缓存实例",
                layouter.layout("https://abcdefghijk", MAX_WIDTH), lines);
        Assert.assertSame("两个投影重复调用必须命中同一缓存实例",
                layouter.layoutFragments("https://abcdefghijk", MAX_WIDTH), fragments);
        Assert.assertEquals(fragments.size(), lines.size());
        for (int i = 0; i < lines.size(); i++) {
            Assert.assertEquals("同一缓存条目内两投影逐行同文本", lines.get(i),
                    fragments.get(i).getText());
        }
    }

    private static List<ChatLineLayouter.LineFragment> fragments(String text) {
        return ChatLineLayouter.splitFragments(text, MAX_WIDTH, fixedMeasure(), 13);
    }

    private static List<String> texts(List<ChatLineLayouter.LineFragment> fragments) {
        List<String> out = new java.util.ArrayList<String>(fragments.size());
        for (ChatLineLayouter.LineFragment fragment : fragments) {
            out.add(fragment.getText());
        }
        return out;
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
