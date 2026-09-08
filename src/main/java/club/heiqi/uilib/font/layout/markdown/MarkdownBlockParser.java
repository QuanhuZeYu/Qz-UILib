package club.heiqi.uilib.font.layout.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.layout.TextStyle;

/**
 * 块级 markdown 扫描器（包内实现，对外唯一入口是 {@link MarkdownDocument}）。
 * 纯 JVM：不 import net.minecraft / cpw.mods / java.awt（规划 §四 G2 锁）。
 *
 * <p>语法面（《规划-通用Markdown渲染器.md》§二 L1 语法面，M2 范围；缩进代码块为 C1a
 * 2026-09-06 对齐裁定新增）：ATX 标题 {@code #..######}、围栏代码（3 连 {@code `} 与
 * {@code ~~~}）、缩进代码块（块起点 ≥4 前导空格，CommonMark 0.30 §4.4，产出与围栏同款
 * {@link MarkdownBlock.Kind#CODE} 节点、info 恒空）、引用块 {@code >}（可嵌套）、
 * 无序/有序列表（含缩进续行与嵌套子列表，嵌套/续行判定恒按父项内容列）、
 * 分隔线 {@code ---}/{@code ***}/{@code ___}、setext 标题（段落紧邻的一行 {@code ===} →H1 /
 * {@code ---} →H2，C3b2 2026-09-06 对齐裁定新增，见 {@link #readParagraph}）、段落与空行、
 * 硬换行（行尾两空格或行尾未转义反斜杠）。</p>
 *
 * <p>TABLE 的语义由本层识别，显式布局出口交 L2 绘制；历史出口保持字面降级。刻意不支持：任务列表、
 * HTML 内联、脚注、图片 {@code ![alt](url)}。图片的「字面」边界说明：块层不识别 {@code !}，
 * 正文原样交 {@link MarkdownInlineParser} 后 {@code [alt](url)} 部分按既有行内裁定解析为链接、
 * {@code !} 为字面文本——行内语义照抄 9c4dcae5 裁定（规划 §五 D2），本层一行不改。</p>
 *
 * <p><b>§（U+00A7）在本层就是普通字符（C4 归位 + C7 定案 4，2026-09-07）</b>：L1 对 § 零认知
 * 且零分支——本层代码里不存在任何 § 的识别、剥离、转换或上色机制，§ 与 'a'、'\u2022' 一样只是
 * 一个普通文本字符；一切后果都由「它是普通字符」这一条推出，不为它开特例：既不构成前导空白、
 * 也不参与任何块标记检测；<b>行首 § 因此吃掉块标记</b>（{@code §a- x} 是段落字面而非列表项，
 * {@code §a# t} 不是标题，{@code §a> q} 不是引用）；<b>行内 code、围栏代码与 latex 原子内的 §
 * 同样原样保留</b>（这些域本就不解析任何标记，§ 也就没有额外特权）；出段也不因 § 切段或上色。
 * 该语义由 {@code MarkdownSectionCodeIsPlainTextLockTest} 逐条正向钉死——每例都真的把 § 喂进
 * {@code parse} 再断言结果，不靠「输入恰好不含 §」过关；反向则由常驻守卫
 * {@code MarkdownL1ZeroSectionKnowledgeGuardTest} 钉住 L1 代码面 § 知识恒 0。
 *
 * <p>「为什么这里可以什么都不做」只是输入事实、不是本层的行为依据：chat3 划界（C7）后，玩家
 * 消息内容取自原版 {@code chat.type.text} 的结构参数（原版服务端逐字符拒收 §，见
 * {@code ChatAllowedCharacters.isAllowedCharacter}），兜底通道取自 unformatted 文本，两条通道
 * 都不再往 markdown 里塞样式码；而 markdown 侧遇到 §（例如业务 mod 直接递交的富文本）依旧按
 * 上面那条规则处理。MC 特有格式若要成为语义，只能作为调用方的显式扩展存在（AGENTS.md 主权
 * 条款），永远不进解析核心。</p>
 *
 * <p>相对 CommonMark 的已裁简化（均有测试钉死）：制表符不展开（块缩进只数行首空格，
 * 标记后空格/制表符均接受）；行首反斜杠不构成块转义（{@code \# x} 整行按字面段落处理，
 * {@code #} 不在本包 escapable 集，反斜杠由行内层字面输出）；backtick 围栏 info 校验已按
 * CommonMark 实现（含反引号不算开栏）；嵌套深度上限 {@code MAX_BLOCK_DEPTH}（超限层按段落
 * 字面收拢，宽容失败）；setext 下划线只认单字符连续串（{@code = =}/{@code - -} 夹空白按段落
 * 续行字面，同主流实证）；无表格文档及历史出口的列表项体内部源空行折叠（空行只在项与项/列表边界生效——M4 既有
 * 简化、非 C1a 范围；副作用 = 项内「段落行 + 空行 + ≥4 空格行」按段落续行折叠而非缩进代码，
 * 其余缩进代码与惰性续行判定均与 CommonMark 一致）。</p>
 *
 * <p><b>C6a 能力①（块级文档 span 流入口）</b>：{@link #parse(String, List)} 吃
 * {@link MarkdownSpan} 流拼接出的同一份纯文本，按 
 切成<b>带样式锚点的源行</b>
 * （{@link SrcLine#spans}，一个 span 跨多行拆成多段、样式继承、空行 = 无文本段）。
 * 块级检测（标题/围栏/引用/列表/分隔线/缩进代码/内容列）恒在拼接后的纯文本行上跑，
 * 与 String 入口<b>共用同一套判据函数</b>——不存在第二份判据，也不对行文本做任何
 * 改写；剥标记/剥缩进等文本截断经 {@link #dropLead}/{@link #dropTail} 同步裁剪该行
 * 锚点段，样式随正文走到行内解析，块层不把样式压掉。</p>
 *
 * <p>风格与 {@link MarkdownInlineParser} 同哲学：宽容失败、不抛异常、纯函数；
 * 零每帧解析裁定（规划 §六 3）——只在文档到达时调用一次，缓存责任在消费层。</p>
 */
final class MarkdownBlockParser {

    /** 块级嵌套（引用/列表）递归深度上限，防御异常输入的栈深。 */
    private static final int MAX_BLOCK_DEPTH = 16;

    /** 围栏定界符反引号 U+0060（0x60 书写避免 Unicode 转义陷阱，同 MarkdownInlineParser）。 */
    private static final char CODE_TICK = 0x60;

    /** 有序列表序号最大位数（超出按普通文本）。 */
    private static final int MAX_ORDINAL_DIGITS = 9;

    /**
     * 带「惰性」标记的源行（C4 N2 收紧）：在引用/列表收拢时<b>未携带本容器标记</b>而被
     * 惰性续行吸收的行 {@code lazy=true}；按 CommonMark，setext 下划线行不得是惰性续行，
     * {@link #readParagraph} 对这类行跳过升格判定。其余行（首行、带标记行、内容列续行）
     * 恒 {@code lazy=false}；被再次收拢的行保持原标记随文本走（外层惰性不因内层剥标记洗白）。
     */
    private static final class SrcLine {
        final String text;
        final boolean lazy;
        /** C6a 能力①：本行文本的样式锚点段（null = String 路；空列表 = 空行/零宽行）。 */
        final List<MarkdownSpan> spans;
        // 同一原文的私有区间；容器切片仅推进 sourceStart，不重建原始换行。
        final String source;
        final int sourceStart;
        final int sourceEnd;
        final String lineEnding;
        final boolean foldedListBlank;

        SrcLine(String text, boolean lazy) {
            this(text, lazy, null);
        }

        SrcLine(String text, boolean lazy, List<MarkdownSpan> spans) {
            this(text, lazy, spans, null, -1, -1, "");
        }

        SrcLine(String text, boolean lazy, List<MarkdownSpan> spans, String source,
                int sourceStart, int sourceEnd, String lineEnding) {
            this(text, lazy, spans, source, sourceStart, sourceEnd, lineEnding, false);
        }

        SrcLine(String text, boolean lazy, List<MarkdownSpan> spans, String source,
                int sourceStart, int sourceEnd, String lineEnding, boolean foldedListBlank) {
            this.foldedListBlank = foldedListBlank;
            this.text = text;
            this.lazy = lazy;
            this.spans = spans;
            this.source = source;
            this.sourceStart = sourceStart;
            this.sourceEnd = sourceEnd;
            this.lineEnding = lineEnding;
        }

        SrcLine slice(int from, int to) {
            return new SrcLine(text.substring(from, to), lazy,
                    dropTail(dropLead(spans, from), text.length() - to), source,
                    sourceStart < 0 ? -1 : sourceStart + from,
                    sourceStart < 0 ? -1 : sourceStart + to, lineEnding, foldedListBlank);
        }

        SrcLine asLazy() {
            return new SrcLine(text, true, spans, source, sourceStart, sourceEnd, lineEnding, foldedListBlank);
        }

        SrcLine foldedBlank() {
            return new SrcLine(text, lazy, spans, source, sourceStart, sourceEnd, lineEnding, true);
        }

        static SrcLine plain(String text) {
            return new SrcLine(text, false);
        }

        /** span 路的零宽行（空行）：锚点为空列表，与 String 路的 null 判别块路径归属。 */
        static SrcLine blank(boolean spanPath) {
            return new SrcLine("", false, spanPath ? Collections.<MarkdownSpan>emptyList() : null);
        }
    }

    /**
     * C6a 能力①：span 流在拼接文本上的绝对坐标区间表（仅解析期使用，不进块模型）。
     * {@link #slice} 取 {@code [from,to)} 的行区间并把<b>相邻同值样式</b>并成一段——
     * 单 span 文档切行后逐行仍同值 ⇒ 归并为每行一段，拼接归并后与 String 路同粒度。
     */
    private static final class SpanRuns {
        private final List<MarkdownSpan> spans;
        private final int[] start;
        private final int[] end;

        SpanRuns(List<MarkdownSpan> spans) {
            this.spans = spans;
            this.start = new int[spans.size()];
            this.end = new int[spans.size()];
            int pos = 0;
            for (int i = 0; i < spans.size(); i++) {
                start[i] = pos;
                pos += spans.get(i).getText().length();
                end[i] = pos;
            }
        }

        List<MarkdownSpan> slice(int from, int to) {
            List<MarkdownSpan> out = new ArrayList<MarkdownSpan>();
            for (int i = 0; i < spans.size(); i++) {
                int s = Math.max(from, start[i]);
                int e = Math.min(to, end[i]);
                if (s >= e) {
                    continue;
                }
                MarkdownSpan span = spans.get(i);
                String piece = (s == start[i] && e == end[i])
                        ? span.getText()
                        : span.getText().substring(s - start[i], e - start[i]);
                appendMerged(out, piece, span.getBaseStyle());
            }
            return out;
        }
    }

    /** 相邻同值并段（{@link StyleValues#same}）；{@code out} 尾段文本随之增长。 */
    static void appendMerged(List<MarkdownSpan> out, String text, TextStyle style) {
        if (text.isEmpty()) {
            return;
        }
        if (!out.isEmpty()) {
            MarkdownSpan last = out.get(out.size() - 1);
            if (StyleValues.same(last.getBaseStyle(), style)) {
                out.set(out.size() - 1,
                        new MarkdownSpan(last.getText() + text, last.getBaseStyle()));
                return;
            }
        }
        out.add(new MarkdownSpan(text, style));
    }

    /** 裁掉锚点段列表头 {@code count} 个字符（null 透传；被裁空的段丢弃）。 */
    static List<MarkdownSpan> dropLead(List<MarkdownSpan> line, int count) {
        if (line == null || count <= 0) {
            return line;
        }
        List<MarkdownSpan> out = new ArrayList<MarkdownSpan>(line.size());
        int skip = count;
        for (int i = 0; i < line.size(); i++) {
            MarkdownSpan span = line.get(i);
            String t = span.getText();
            if (skip >= t.length()) {
                skip -= t.length();
                continue;
            }
            appendMerged(out, skip > 0 ? t.substring(skip) : t, span.getBaseStyle());
            skip = 0;
        }
        return out;
    }

    /** 裁掉锚点段列表尾 {@code count} 个字符（null 透传）。 */
    static List<MarkdownSpan> dropTail(List<MarkdownSpan> line, int count) {
        if (line == null || count <= 0) {
            return line;
        }
        int total = 0;
        for (int i = 0; i < line.size(); i++) {
            total += line.get(i).getText().length();
        }
        int keep = Math.max(0, total - count);
        List<MarkdownSpan> out = new ArrayList<MarkdownSpan>(line.size());
        int used = 0;
        for (int i = 0; i < line.size() && used < keep; i++) {
            MarkdownSpan span = line.get(i);
            String t = span.getText();
            int take = Math.min(t.length(), keep - used);
            used += take;
            appendMerged(out, take == t.length() ? t : t.substring(0, take), span.getBaseStyle());
        }
        return out;
    }

    /** 锚点段列表取 {@code [from,to)} 子区间（{@code dropLead} + {@code dropTail} 复合）。 */
    static List<MarkdownSpan> sliceSpans(List<MarkdownSpan> line, int from, int to) {
        List<MarkdownSpan> cut = dropLead(line, from);
        if (cut == null) {
            return null;
        }
        int len = 0;
        for (int i = 0; i < cut.size(); i++) {
            len += cut.get(i).getText().length();
        }
        return dropTail(cut, len - to);
    }

    /**
     * C6a 能力①：把「每行锚点段」归并为块级段流——行间补 
（归属前一行末段尾，
     * 前无内容段则挂后段头部；全文零可见字符时兜底用 {@code fallbackStyle}），相邻同值
     * 并段。String 路（{@code lineAnchors} null）恒返回 null。
     */
    static List<MarkdownSpan> joinStyledLines(List<List<MarkdownSpan>> lineAnchors,
                                              TextStyle fallbackStyle) {
        if (lineAnchors == null) {
            return null;
        }
        List<MarkdownSpan> out = new ArrayList<MarkdownSpan>();
        int pending = 0;
        for (int i = 0; i < lineAnchors.size(); i++) {
            if (i > 0) {
                pending++;
            }
            for (int k = 0; k < lineAnchors.get(i).size(); k++) {
                MarkdownSpan piece = lineAnchors.get(i).get(k);
                String text = piece.getText();
                if (text.isEmpty()) {
                    continue;
                }
                String prefix = "";
                if (pending > 0) {
                    if (!out.isEmpty()) {
                        // 行界换行符归属前一行末段尾（样式随前段，与 String 路同段嵌入一致）
                        MarkdownSpan tail = out.get(out.size() - 1);
                        out.set(out.size() - 1, new MarkdownSpan(
                                tail.getText() + repeatNewlines(pending), tail.getBaseStyle()));
                    } else {
                        prefix = repeatNewlines(pending);
                    }
                    pending = 0;
                }
                appendMerged(out, prefix + text, piece.getBaseStyle());
            }
        }
        if (pending > 0) {
            if (!out.isEmpty()) {
                MarkdownSpan tailSpan = out.get(out.size() - 1);
                out.set(out.size() - 1, new MarkdownSpan(
                        tailSpan.getText() + repeatNewlines(pending), tailSpan.getBaseStyle()));
            } else {
                appendMerged(out, repeatNewlines(pending), fallbackStyle);
            }
        }
        return out;
    }

    private static String repeatNewlines(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append('\n');
        }
        return sb.toString();
    }

    private MarkdownBlockParser() {
    }

    /**
     * 块级解析入口。
     *
     * @param source 文档源文本（可为 null/空，返回空表）
     * @return 顶层块序列
     */
    static List<MarkdownBlock> parse(String source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return parseTableAware(splitLines(source, null));
    }

    /**
     * 块级解析入口（C6a 能力①）：{@code source} 必须是 {@code spans} 文本按序拼接的同一份
     * 纯文本（由 {@code MarkdownDocument.parseSpans(List)} 保证）。块检测只跑在 {@code source}
     * 上——与 String 入口共用同一套判据；行切分（{@link #splitLines}）同步把每行文本切成
     * 该行的样式锚点段随 {@link SrcLine} 走。
     *
     * @param source 拼接后的文档源文本（可为 null/空，返回空表）
     * @param spans  样式锚点 span 流（null/空 ⇒ 退化为 String 路，块模型不带锚点）
     * @return 顶层块序列
     */
    static List<MarkdownBlock> parse(String source, List<MarkdownSpan> spans) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        if (spans == null || spans.isEmpty()) {
            return parse(source);
        }
        return parseTableAware(splitLines(source, new SpanRuns(spans)));
    }

    /** T1 旧出口专用：同源判据关闭表格识别，仅在文档创建时调用。 */
    static List<MarkdownBlock> parseLiteral(String source, List<MarkdownSpan> spans) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return parseBlocks(splitLines(source, spans == null || spans.isEmpty() ? null : new SpanRuns(spans)),
                0, false, false);
    }

    /**
     * 表格必须看到列表项中的真实空行，才能升级段落后的单行表头。
     * 无管线候选直接走历史解析；候选未成表时也保留原有列表空行折叠语义，
     * 防止仅含字面管线的非表格文档被本批顺带迁移。额外扫描只在创建文档时发生。
     */
    private static List<MarkdownBlock> parseTableAware(List<SrcLine> lines) {
        if (!hasTableCandidate(lines)) {
            return parseBlocks(lines, 0, false, true);
        }
        List<MarkdownBlock> semantic = parseBlocks(lines, 0, true, true);
        return containsTable(semantic) ? semantic : parseBlocks(lines, 0, false, true);
    }

    /** 一次成功识别才提交消费范围；失败不推进外层游标。 */
    private static final class MathBlockMatch {
        final MarkdownBlock block;
        final int end;
        MathBlockMatch(MarkdownBlock block, int end) { this.block = block; this.end = end; }
    }

    private static boolean mathFence(SrcLine line) {
        return !line.lazy && leadingSpaces(line.text) <= 3 && trim(line.text).equals("$$");
    }

    private static MathBlockMatch matchMathBlock(List<SrcLine> lines, int start) {
        SrcLine opening = lines.get(start);
        int indent = leadingSpaces(opening.text);
        if (opening.lazy || indent > 3 || !opening.text.startsWith("$$", indent)
                || opening.text.startsWith("$$$", indent)) return null;
        List<SrcLine> body = new ArrayList<SrcLine>();
        int end;
        if (!mathFence(opening)) {
            int close = MarkdownInlineParser.findDisplayClose(opening.text, indent + 2, opening.text.length());
            if (close < 0 || !isBlank(opening.text.substring(close + 2))) return null;
            body.add(opening.slice(indent + 2, close));
            end = start + 1;
        } else {
            end = start + 1;
            while (end < lines.size() && !mathFence(lines.get(end))) {
                SrcLine line = lines.get(end);
                if (line.lazy) return null;
                int strip = Math.min(indent, leadingSpaces(line.text));
                body.add(line.slice(strip, line.text.length()));
                end++;
            }
            if (end == lines.size()) return null;
            end++;
        }
        List<String> texts = new ArrayList<String>();
        List<List<MarkdownSpan>> anchors = opening.spans == null ? null : new ArrayList<List<MarkdownSpan>>();
        boolean visible = false;
        for (SrcLine line : body) {
            // 区间指向原文，提取后的正文只规范行间换行为 LF；首尾围栏换行不进体。
            String text = line.source == null ? line.text : line.source.substring(line.sourceStart, line.sourceEnd);
            texts.add(text);
            visible |= !isBlank(text);
            if (anchors != null) anchors.add(line.spans);
        }
        return visible ? new MathBlockMatch(MarkdownBlock.mathDisplay(texts, anchors), end) : null;
    }

    // ==================== 行工具 ====================

    private static List<SrcLine> splitLines(String source, SpanRuns runs) {
        List<SrcLine> out = new ArrayList<SrcLine>();
        int start = 0;
        int n = source.length();
        for (int i = 0; i < n; i++) {
            char ch = source.charAt(i);
            if (ch == '\n') {
                out.add(line(source, start, i, runs));
                start = i + 1;
            } else if (ch == '\r') {
                out.add(line(source, start, i, runs));
                if (i + 1 < n && source.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        out.add(line(source, start, n, runs));
        return out;
    }

    /** 切一行：文本 = {@code source[from,to)}；span 路同步取该行的样式锚点段。 */
    private static SrcLine line(String source, int from, int to, SpanRuns runs) {
        String ending = to == source.length() ? "" : source.startsWith("\r\n", to)
                ? "\r\n" : source.substring(to, to + 1);
        return new SrcLine(source.substring(from, to), false,
                runs == null ? null : runs.slice(from, to), source, from, to, ending);
    }

    /** 块语法空白集（U+0020 及以下，同 CommonMark ASCII 空白口径；NBSP 不算空白）。 */
    private static boolean isSpaceChar(char ch) {
        return ch <= ' ';
    }

    private static boolean isBlank(String s) {
        return trim(s).isEmpty();
    }

    private static String trim(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && isSpaceChar(s.charAt(start))) {
            start++;
        }
        while (end > start && isSpaceChar(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    private static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && isSpaceChar(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }

    /** 行首空格数（制表符不计入块缩进——已裁简化，见类 javadoc）。 */
    private static int leadingSpaces(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static int runLength(String s, char ch) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == ch) {
            i++;
        }
        return i;
    }

    /** 剥掉行首至多 count 个空格。 */
    private static String stripFirst(String line, int count) {
        int k = 0;
        while (k < count && k < line.length() && line.charAt(k) == ' ') {
            k++;
        }
        return line.substring(k);
    }

    /** 剥掉行首全部块语法空白（段落行折叠用；NBSP 不算空白，同 isSpaceChar 口径）。 */
    private static String stripLeadingSpace(String line) {
        int k = 0;
        while (k < line.length() && isSpaceChar(line.charAt(k))) {
            k++;
        }
        return k == 0 ? line : line.substring(k);
    }

    // ==================== 块识别 ====================

    /** ATX 标题级别；0 = 非标题。要求 1..6 个 # 后为空格/制表符/行尾。 */
    private static int headingLevel(String body) {
        int i = 0;
        while (i < body.length() && body.charAt(i) == '#') {
            i++;
        }
        if (i == 0 || i > 6) {
            return 0;
        }
        if (i == body.length()) {
            return i;
        }
        char next = body.charAt(i);
        return (next == ' ' || next == '\t') ? i : 0;
    }

    /**
     * C6a：ATX 标题块（文本恒走 {@link #headingBody}——与 String 路同一判据与产出；
     * span 路另算 {@link #atxRange} 的开闭裁切量，把样式锚点同步裁到正文区间）。
     */
    private static MarkdownBlock atxHeading(SrcLine src, int ind, int level) {
        String body = src.text.substring(ind);
        String text = headingBody(body, level);
        List<MarkdownSpan> anchors = null;
        if (src.spans != null) {
            int[] range = atxRange(body, level);
            anchors = sliceSpans(dropLead(src.spans, ind), range[0], range[1]);
        }
        return MarkdownBlock.heading(level, text, anchors);
    }

    /**
     * {@link #headingBody} 的区间形态：正文在 {@code body} 上的 {@code [from,to)}。
     * 剥前导 # 序列 + 两侧空白 + 带空白隔开的闭 # 序列——与字符串版逐分支等值。
     */
    private static int[] atxRange(String body, int level) {
        int a = Math.min(level, body.length());
        int b = body.length();
        while (a < b && isSpaceChar(body.charAt(a))) {
            a++;
        }
        while (b > a && isSpaceChar(body.charAt(b - 1))) {
            b--;
        }
        int run = 0;
        while (run < b - a && body.charAt(b - 1 - run) == '#') {
            run++;
        }
        if (run == 0) {
            return new int[] {a, b};
        }
        if (run == b - a) {
            return new int[] {a, a};
        }
        char before = body.charAt(b - run - 1);
        if (before == ' ' || before == '\t') {
            b -= run;
            while (b > a && isSpaceChar(body.charAt(b - 1))) {
                b--;
            }
            return new int[] {a, b};
        }
        return new int[] {a, b};
    }

    /** 去开闭 # 序列后的标题正文。 */
    private static String headingBody(String body, int level) {
        String s = trim(body.substring(level));
        int end = s.length();
        int run = 0;
        while (run < end && s.charAt(end - 1 - run) == '#') {
            run++;
        }
        if (run == 0) {
            return s;
        }
        if (run == end) {
            return "";
        }
        char before = s.charAt(end - run - 1);
        if (before == ' ' || before == '\t') {
            return trim(s.substring(0, end - run));
        }
        return s;
    }

    /** 围栏起始定界符字符；0 = 非围栏。backtick 围栏 info 含反引号时不算起始（CommonMark）。 */
    private static char fenceStart(String body, int minLen) {
        if (body.isEmpty()) {
            return 0;
        }
        char ch = body.charAt(0);
        if (ch != CODE_TICK && ch != '~') {
            return 0;
        }
        int run = runLength(body, ch);
        if (run < minLen) {
            return 0;
        }
        if (ch == CODE_TICK && body.indexOf(CODE_TICK, run) >= 0) {
            return 0;
        }
        return ch;
    }

    /** 分隔线：整行仅一种 {@code *}/{@code -}/{@code _} 加空白，且标记字符数 >= 3。 */
    private static boolean isThematicBreak(String body) {
        char unit = 0;
        int count = 0;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == ' ' || ch == '\t') {
                continue;
            }
            if (ch != '*' && ch != '-' && ch != '_') {
                return false;
            }
            if (unit == 0) {
                unit = ch;
            } else if (ch != unit) {
                return false;
            }
            count++;
        }
        return count >= 3;
    }

    /** 列表起始信息。 */
    private static final class ListStart {
        final boolean ordered;
        final String marker;
        final char bullet;
        final char delim;
        final int number;
        final int contentCol;
        final String content;
        /** C6a：内容列后正文的样式锚点段（仅 {@link #matchListStart(SrcLine)} 填；null = String 路）。 */
        List<MarkdownSpan> contentSpans;

        ListStart(boolean ordered, String marker, char bullet, char delim, int number,
                  int contentCol, String content) {
            this.ordered = ordered;
            this.marker = marker;
            this.bullet = bullet;
            this.delim = delim;
            this.number = number;
            this.contentCol = contentCol;
            this.content = content;
        }
    }

    /** C6a：带样式锚点同步裁切的列表起始检测（判据恒走 String 版 {@link #matchListStart(String)}）。 */
    private static ListStart matchListStart(SrcLine line) {
        ListStart st = matchListStart(line.text);
        if (st != null && line.spans != null) {
            st.contentSpans = sliceSpans(line.spans, st.contentCol, line.text.length());
        }
        return st;
    }

    /** 列表起始检测：无序 {@code [-*+]} + 空格/制表符/行尾；有序 1..9 位数字 + {@code .}/{@code )} + 同上。 */
    private static ListStart matchListStart(String line) {
        int ind = leadingSpaces(line);
        if (ind > 3 || ind >= line.length()) {
            return null;
        }
        String body = line.substring(ind);
        char c0 = body.charAt(0);
        if ((c0 == '-' || c0 == '*' || c0 == '+')
                && (body.length() == 1 || body.charAt(1) == ' ' || body.charAt(1) == '\t')) {
            return finishListStart(false, String.valueOf(c0), c0, (char) 0, 0, line, ind, 1);
        }
        int digits = 0;
        while (digits < body.length() && digits <= MAX_ORDINAL_DIGITS
                && Character.isDigit(body.charAt(digits))) {
            digits++;
        }
        if (digits == 0 || digits > MAX_ORDINAL_DIGITS || digits >= body.length()) {
            return null;
        }
        char delim = body.charAt(digits);
        if (delim != '.' && delim != ')') {
            return null;
        }
        if (digits + 1 < body.length()
                && body.charAt(digits + 1) != ' ' && body.charAt(digits + 1) != '\t') {
            return null;
        }
        int number = Integer.parseInt(body.substring(0, digits));
        return finishListStart(true, body.substring(0, digits + 1), (char) 0, delim, number, line, ind,
                digits + 1);
    }

    /** 计算内容列与首行内容（CommonMark「>=5 空格缩进归内容」规则）。 */
    private static ListStart finishListStart(boolean ordered, String marker, char bullet, char delim,
                                             int number, String line, int ind, int markerEnd) {
        int absolute = ind + markerEnd;
        int idx = absolute;
        int spaces = 0;
        while (idx < line.length() && (line.charAt(idx) == ' ' || line.charAt(idx) == '\t')) {
            idx++;
            spaces++;
        }
        int contentCol;
        if (isBlank(line.substring(absolute)) || spaces >= 5) {
            contentCol = absolute + 1;
        } else {
            contentCol = idx;
        }
        String content = contentCol < line.length() ? line.substring(contentCol) : "";
        return new ListStart(ordered, marker, bullet, delim, number, contentCol, content);
    }

    /** 该行是否强制结束段落/引用惰性续行/列表惰性续行。 */
    private static boolean interruptsParagraph(String line) {
        if (isBlank(line)) {
            return true;
        }
        int ind = leadingSpaces(line);
        if (ind > 3) {
            return false;
        }
        String body = line.substring(ind);
        if (fenceStart(body, 3) != 0 || headingLevel(body) > 0 || isThematicBreak(body)
                || body.charAt(0) == '>') {
            return true;
        }
        ListStart ls = matchListStart(line);
        if (ls == null) {
            return false;
        }
        if (isBlank(ls.content)) {
            return false; // 空项不打断段落（防 "*" 单字符行拆碎正文）
        }
        return !ls.ordered || ls.number == 1;
    }

    // ==================== 块消费 ====================

    private static List<MarkdownBlock> parseBlocks(List<SrcLine> lines, int depth, boolean tablesEnabled, boolean mathEnabled) {
        List<MarkdownBlock> blocks = new ArrayList<MarkdownBlock>();
        int i = 0;
        int n = lines.size();
        // F6：本层连续空行数 → 打在下一个新块上（blanksBefore），扁平化时产占位段
        int blanks = 0;
        while (i < n) {
            String line = lines.get(i).text;
            if (isBlank(line)) {
                if (lines.get(i).foldedListBlank) { i++; continue; }
                blanks++;
                i++;
                continue;
            }
            int ind = leadingSpaces(line);
            int before = blocks.size();
            if (ind > 3) {
                // C1a（2026-09-06 对齐裁定，CommonMark 0.30 §4.4）：块起点前导 ≥4 空格 = 缩进
                // 代码块。旧 M5 F2「depth==0 且 ≥4 空格 + 列表标记 → 深缩进独立列表」
                //（readDeepList + baseLevel）裁定作废——同一行现落代码块字面。
                // 「缩进代码不得中断段落」由 readParagraph 天然保证：段落已开始后，≥4 空格行
                // 是续行（interruptsParagraph 对 ind>3 恒 false，行首空白折叠后进正文），只有
                // 块上下文（段落已被空行/其它块终结）才会走到本分支。
                // C4（§ 归位）后本层不存在任何「行改写视图」：块检测与剥空格恒按原始行列计，
                // 行首 § 是普通文本字符、不构成前导空白，这类行不进本分支。
                i = readIndentedCode(lines, i, blocks);
                stamp(blocks, before, blanks); blanks = 0;
                continue;
            }
            String body = line.substring(ind);
            if (fenceStart(body, 3) != 0) {
                i = readFence(lines, i, blocks);
                stamp(blocks, before, blanks); blanks = 0;
                continue;
            }
            MathBlockMatch math = mathEnabled ? matchMathBlock(lines, i) : null;
            if (math != null) {
                blocks.add(math.block);
                i = math.end;
                stamp(blocks, before, blanks); blanks = 0;
                continue;
            }
            int heading = headingLevel(body);
            if (heading > 0) {
                blocks.add(atxHeading(lines.get(i), ind, heading));
                i++;
                stamp(blocks, before, blanks); blanks = 0;
                continue;
            }
            if (isThematicBreak(body)) {
                blocks.add(MarkdownBlock.thematicBreak());
                i++;
                stamp(blocks, before, blanks); blanks = 0;
                continue;
            }
            if (body.charAt(0) == '>') {
                i = readQuote(lines, i, blocks, depth, tablesEnabled, mathEnabled);
                stamp(blocks, before, blanks); blanks = 0;
                continue;
            }
            if (matchListStart(line) != null) {
                i = readList(lines, i, blocks, depth, tablesEnabled, mathEnabled);
                stamp(blocks, before, blanks); blanks = 0;
                continue;
            }
            i = readParagraph(lines, i, blocks, tablesEnabled, mathEnabled);
            stamp(blocks, before, blanks); blanks = 0;
        }
        return blocks;
    }

    /**
     * 把「本层刚消费掉的连续空行数」写到新产出的那个块上（F6）。
     *
     * <p>只在块边界处计一次：源里 {@code >=1} 个空行都记为「有一个块间空行」，与 chat3 一致
     * （chat3 的行切分器对 {@code 甲\n\n\n乙} 也只产 {@code 甲}/{@code ""}/{@code 乙} 三行，
     * 因为连续 {@code \n} 在 {@code splitFragments} 里逐 {@code \n} 断行——这里钳到 1，
     * 由 L2 的占位段加一空行；多块语料在 {@code MarkdownBlockParserTest} 无覆盖，测试自钉）。</p>
     */
    private static void stamp(List<MarkdownBlock> blocks, int from, int blanks) {
        if (blanks <= 0 || blocks.size() <= from) {
            return;
        }
        blocks.set(from, blocks.get(from).withBlanksBefore(Math.min(1, blanks)));
    }

    private static List<MarkdownBlock> parseWithDepthCap(List<SrcLine> lines, int childDepth, boolean tablesEnabled, boolean mathEnabled) {
        if (childDepth >= MAX_BLOCK_DEPTH) {
            return singletonParagraphFallback(lines);
        }
        return parseBlocks(lines, childDepth, tablesEnabled, mathEnabled);
    }

    private static List<MarkdownBlock> singletonParagraphFallback(List<SrcLine> rawLines) {
        if (rawLines.isEmpty()) {
            return Collections.emptyList();
        }
        List<MarkdownBlock> out = new ArrayList<MarkdownBlock>(1);
        out.add(makeParagraph(rawLines));
        return out;
    }

    /** 围栏代码：闭合要求同字符、长度 >= 开栏、行首 <=3 空格、行尾仅空白；未闭合消费到 EOF（宽容）。 */
    private static int readFence(List<SrcLine> lines, int start, List<MarkdownBlock> out) {
        String opener = lines.get(start).text;
        int fenceIndent = leadingSpaces(opener);
        String head = opener.substring(fenceIndent);
        char ch = head.charAt(0);
        int openLen = runLength(head, ch);
        String info = trim(head.substring(openLen));
        List<String> body = new ArrayList<String>();
        boolean spanPath = lines.get(start).spans != null;
        List<List<MarkdownSpan>> anchors = spanPath ? new ArrayList<List<MarkdownSpan>>() : null;
        int j = start + 1;
        int n = lines.size();
        while (j < n) {
            SrcLine src = lines.get(j);
            if (src.foldedListBlank) { j++; continue; }
            String line = src.text;
            int ind = leadingSpaces(line);
            if (ind <= 3 && !isBlank(line)) {
                String rest = line.substring(ind);
                if (rest.charAt(0) == ch) {
                    int run = runLength(rest, ch);
                    if (run >= openLen && isBlank(rest.substring(run))) {
                        j++;
                        break;
                    }
                }
            }
            String kept = isBlank(line) ? "" : stripFirst(line, fenceIndent);
            body.add(kept);
            if (anchors != null) {
                anchors.add(isBlank(line) ? Collections.<MarkdownSpan>emptyList()
                        : dropLead(src.spans, line.length() - kept.length()));
            }
            j++;
        }
        out.add(MarkdownBlock.code(info, body, anchors));
        return j;
    }

    /**
     * 缩进代码块（C1a，CommonMark 0.30 §4.4）：一个或多个「缩进块」= 连续前导 >=4 空格的
     * 非空行；块间空行并入本块（每行剥至多 4 个前导空格，多余空格保留——含空行上的第 5 个
     * 空格起，例 112）、尾随空行不属于本块（交外层循环计 blanksBefore，F6），首个前导 <4 的
     * 非空行即刻结束本块（例 114）。内容 = 剥后的字面文本，行内 markdown 一律不解析
     * （与围栏 CODE 同一字面口径）；info 恒为空串（CommonMark：缩进代码无 info string）。
     *
     * <p>只在块上下文被调用（见 parseBlocks 的 ind>3 分支——缩进代码不得中断段落，
     * 段落已开始后的 ≥4 空格行由 readParagraph 收为续行）。本方法只吃原始行（C4 起
     * 块层对 § 零认知，不存在视图/原文两套口径）。</p>
     *
     * @return 消费到的下一源行下标
     */
    private static int readIndentedCode(List<SrcLine> lines, int start, List<MarkdownBlock> out) {
        List<String> body = new ArrayList<String>();
        boolean spanPath = lines.get(start).spans != null;
        List<List<MarkdownSpan>> anchors = spanPath ? new ArrayList<List<MarkdownSpan>>() : null;
        int j = start;
        int n = lines.size();
        while (j < n) {
            String line = lines.get(j).text;
            if (isBlank(line)) {
                int k = j;
                while (k < n && isBlank(lines.get(k).text)) {
                    k++;
                }
                if (k < n && leadingSpaces(lines.get(k).text) >= 4) {
                    for (int b = j; b < k; b++) {
                        addIndentedCodeLine(lines.get(b), body, anchors);
                    }
                    j = k;
                    continue;
                }
                break; // 尾随空行不入块（例 117）；外层循环继续计空行/收后续块
            }
            if (leadingSpaces(line) < 4) {
                break; // 前导 <4 的非空行即刻结束（例 114）
            }
            addIndentedCodeLine(lines.get(j), body, anchors); // 剥至多 4（例 116：首行 8 空格剥 4 留 4）
            j++;
        }
        out.add(MarkdownBlock.code("", body, anchors));
        return j;
    }

    /** 缩进代码收一行：剥至多 4 前导空格，锚点同步裁。 */
    private static void addIndentedCodeLine(SrcLine src, List<String> body,
                                            List<List<MarkdownSpan>> anchors) {
        if (src.foldedListBlank) return;
        String kept = stripFirst(src.text, 4);
        body.add(kept);
        if (anchors != null) {
            anchors.add(dropLead(src.spans, src.text.length() - kept.length()));
        }
    }

    /** 引用块：消费连续引用行与惰性续行；空行后仍带标记则并入同一引用（多段落）。 */
    private static int readQuote(List<SrcLine> lines, int start, List<MarkdownBlock> out, int depth,
                                 boolean tablesEnabled, boolean mathEnabled) {
        List<SrcLine> inner = new ArrayList<SrcLine>();
        boolean spanPath = lines.get(start).spans != null;
        int j = start;
        int n = lines.size();
        while (j < n) {
            SrcLine src = lines.get(j);
            String line = src.text;
            if (isBlank(line)) {
                int k = j;
                while (k < n && isBlank(lines.get(k).text)) {
                    k++;
                }
                if (k >= n) {
                    j = k;
                    break;
                }
                String next = lines.get(k).text;
                int ni = leadingSpaces(next);
                if (ni <= 3 && next.charAt(ni) == '>') {
                    // TablesExtension：表格后的无标记空行结束引用；旧字面模式仍保留历史收拢。
                    if (tablesEnabled && hasTableCandidate(inner)) {
                        List<MarkdownBlock> parsed = parseWithDepthCap(inner, depth + 1, true, mathEnabled);
                        if (containsTable(parsed)) {
                            out.add(MarkdownBlock.quote(parsed));
                            return j;
                        }
                    }
                    inner.add(mathEnabled ? lines.get(j).asLazy() : SrcLine.blank(spanPath));
                    j = k;
                    continue;
                }
                j = k;
                break;
            }
            int ind = leadingSpaces(line);
            if (ind <= 3 && line.charAt(ind) == '>') {
                String content = line.substring(ind + 1);
                int strip = ind + 1;
                if (content.startsWith(" ") || content.startsWith("\t")) {
                    content = content.substring(1);
                    strip++;
                }
                inner.add(src.slice(strip, src.text.length()));
            } else if (!interruptsParagraph(line)
                    && !(mathEnabled && matchMathBlock(lines, j) != null)) {
                // 惰性续行：本行不带 '>' 标记，N2 收紧在此打「惰性」标记——
                // 收拢后它不得再被 readParagraph 判成 setext 下划线（CommonMark 同款规则）。
                // 锚点原样随行文本走（未剥任何字符）。
                inner.add(src.asLazy());
            } else {
                break;
            }
            j++;
        }
        out.add(MarkdownBlock.quote(parseWithDepthCap(inner, depth + 1, tablesEnabled, mathEnabled)));
        return j;
    }

    private static boolean hasTableCandidate(List<SrcLine> lines) {
        // 仅为保守性能前筛：内层仍可能带引用/列表标记，不能在此复判表格语法。
        // 真正识别只由随后 parseWithDepthCap 的容器递归完成。
        for (SrcLine line : lines) {
            if (line.text.indexOf('|') >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTable(List<MarkdownBlock> blocks) {
        for (MarkdownBlock block : blocks) {
            if (block.kind == MarkdownBlock.Kind.TABLE || containsTable(block.children)) {
                return true;
            }
        }
        return false;
    }

    /** 同列表判定：有序/无序一致且同一 bullet 字符 / 同一有序定界符。 */
    private static boolean sameKind(ListStart candidate, boolean ordered, char unit) {
        if (candidate == null || candidate.ordered != ordered) {
            return false;
        }
        return ordered ? candidate.delim == unit : candidate.bullet == unit;
    }

    /**
     * 列表：项内容列 = 父标记实际宽度（「- 」宽 2、「12. 」宽 4；序号后 1..4 空格并入标记与内容
     * 之间、>=5 空格时首列归内容）；缩进 >= 内容列的行剥列后入项体（嵌套子列表/续行因此自然
     * 成立），< 内容列的同标记行 = 下一项、他类块 = 列表结束、非块标记行 = 段落惰性续行
     * （CommonMark 0.30 §5.2 内容列口径，C1a 起为唯一判据）；空行后视下一行归属决定松/断。
     *
     * <p>C1a（2026-09-06 裁定）：块起点前导 0-3 空格被内容列天然吸收 = 顶级列表项（前导空格
     * 不进项体文本）；「层级 = 前导空格 / 2 + baseLevel」的 M5 F2 深缩进机制退役，
     * 前导 >=4 空格的「列表标记」行进缩进代码块字面（parseBlocks 的 ind>3 分支）。</p>
     */
    private static int readList(List<SrcLine> lines, int start, List<MarkdownBlock> out, int depth,
                                boolean tablesEnabled, boolean mathEnabled) {
        ListStart first = matchListStart(lines.get(start));
        boolean ordered = first.ordered;
        char unit = ordered ? first.delim : first.bullet;
        int n = lines.size();
        List<MarkdownBlock> items = new ArrayList<MarkdownBlock>();
        boolean listEnded = false;
        int i = start;
        int pendingItemBlanks = 0;
        while (i < n && !listEnded) {
            SrcLine itemSrc = lines.get(i);
            ListStart st = matchListStart(itemSrc);
            if (!sameKind(st, ordered, unit)) {
                break;
            }
            List<SrcLine> body = new ArrayList<SrcLine>();
            if (!st.content.isEmpty()) {
                body.add(itemSrc.slice(itemSrc.text.length() - st.content.length(), itemSrc.text.length()));
            }
            int contentCol = st.contentCol;
            int j = i + 1;
            while (j < n) {
                String line = lines.get(j).text;
                if (isBlank(line)) {
                    int k = j;
                    while (k < n && isBlank(lines.get(k).text)) {
                        k++;
                    }
                    if (k >= n) {
                        j = k;
                        listEnded = true;
                        break;
                    }
                    String next = lines.get(k).text;
                    int ni = leadingSpaces(next);
                    ListStart nx = matchListStart(next);
                    if (ni < contentCol) {
                        if (sameKind(nx, ordered, unit)) {
                            pendingItemBlanks = k - j; // 空行分隔的同列表项（loose list，F6）
                            j = k;
                            break;
                        }
                        j = k;
                        listEnded = true; // 他类块开始，本列表结束
                        break;
                    }
                    if (mathEnabled) {
                        // 保留每个源空白行，只有成功数学围栏会消费它们；普通旧列表正文仍折叠。
                        for (int blank = j; blank < k; blank++) {
                            SrcLine original = lines.get(blank);
                            SrcLine stripped = original.slice(Math.min(contentCol, leadingSpaces(original.text)),
                                    original.text.length());
                            body.add(tablesEnabled && blank == j ? stripped : stripped.foldedBlank());
                        }
                    } else if (tablesEnabled) {
                        body.add(SrcLine.blank(lines.get(j).spans != null));
                    }
                    j = k; // 空行后仍有缩进 >= 内容列，项体继续
                    continue;
                }
                int ni = leadingSpaces(line);
                ListStart nx = matchListStart(line);
                if (nx != null && ni < contentCol) {
                    if (sameKind(nx, ordered, unit)) {
                        break; // 同列表下一项
                    }
                    listEnded = true; // 他类列表开启
                    break;
                }
                if (ni >= contentCol) {
                    SrcLine src = lines.get(j);
                    body.add(src.slice(contentCol, src.text.length()));
                    j++;
                    continue;
                }
                if (nx == null && !interruptsParagraph(line)
                        && !(mathEnabled && matchMathBlock(lines, j) != null)) {
                    // 惰性续行：本行低于内容列且不带任何列表标记——N2 收紧打「惰性」
                    // 标记，内层 readParagraph 不得拿它当 setext 下划线。锚点原样随行走。
                    body.add(lines.get(j).asLazy());
                    j++;
                    continue;
                }
                listEnded = true;
                break;
            }
            MarkdownBlock item = MarkdownBlock.listItem(st.marker, st.ordered,
                    parseWithDepthCap(body, depth + 1, tablesEnabled, mathEnabled));
            // loose list：项与项之间被源空行分开 → 空行数打在下一项上（F6）
            items.add(pendingItemBlanks > 0 ? item.withBlanksBefore(1) : item);
            pendingItemBlanks = 0;
            i = j;
        }
        // C3b2（2026-09-06 对齐裁定，CommonMark 0.30 §5.1）：有序列表只认**首项**源数字为
        // start（后续项源数字一律不参与语义）。序号真相在此登记进块模型，两接缝的渲染序号由
        // MarkdownDocument 按「start + 项下标」合成（见其 bareListMarker）；无序列表恒 0。
        out.add(MarkdownBlock.list(items, ordered, ordered ? first.number : 0));
        return i;
    }

    /**
     * 段落收集（含 C3b2 setext 标题判定，2026-09-06 对齐裁定，CommonMark 0.30 §5.2）：收集中
     * 遇到「≤3 前导空格 + 仅由 ≥1 个 `=`（→H1）或 `-`（→H2）连续组成 + 任意尾随空白」的
     * 下划线行 ⇒ 已收集的段落<b>整体</b>升格为 setext 标题，下划线行本身<b>不产内容行</b>；
     * 多行段落的正文按软换行以 `\n` 连接（与 ATX 同一条标题样式通道，H1 走 delta[0]、
     * H2 走 delta[1]）。
     *
     * <p>判序在 {@code interruptsParagraph} <b>之前</b>：{@code ---} 单看是分隔线，但段落紧邻时
     * 主流优先判 setext（实证 commonmark-java 0.21：`a\n---` → H2；`---\na` → TB + 段落，
     * 后者由 {@code parseBlocks} 的分隔线分支承接——块起点/空行后的下划线行走不到本方法）。
     * `= =`/`- -` 这类夹空白形态主流判段落续行而非下划线（实证 0.21：`a\n= =` → 段落
     * `a= =`），本层同口径：下划线须是单一字符的连续串。<b>C4 N2 收紧（2026-09-06）</b>：
     * 引用/列表的惰性续行在收拢处即打「惰性」标记（{@link SrcLine#lazy}），本方法对惰性行
     * 跳过 setext 判定——{@code > 甲} + 惰性 {@code ===} 保持段内字面（CommonMark：setext 的
     * 下划线行不得是惰性续行）；自带标记的下划线（{@code > 甲\n> ===}）照常升格。</p>
     */
    private static int readParagraph(List<SrcLine> lines, int start, List<MarkdownBlock> out,
                                     boolean tablesEnabled, boolean mathEnabled) {
        List<SrcLine> raw = new ArrayList<SrcLine>();
        int j = start;
        int n = lines.size();
        while (j < n) {
            String line = lines.get(j).text;
            if (isBlank(line)) {
                if (lines.get(j).foldedListBlank) { j++; continue; }
                break;
            }
            if (!raw.isEmpty()) {
                if (mathEnabled && matchMathBlock(lines, j) != null
                        && !codeSpanCrossesLine(lines, start, j)) break;
                // TablesExtension 0.21 实测：仅单行段落可升级；惰性分隔行不得升级。
                // 无 pipe 的 --- 仍走既有 setext 优先级。
                if (tablesEnabled && raw.size() == 1 && !lines.get(j).lazy) {
                    MarkdownBlock.TableData table = tableHeader(raw.get(0), lines.get(j));
                    if (table != null) {
                        return readTable(lines, j + 1, table, out);
                    }
                }
                // N2 收紧（C4）：惰性续行不得充当 setext 下划线（readQuote/readList 收拢
                // 时已打标记），> 甲 + 惰性 === 保持段内字面——与 CommonMark 对齐。
                int setext = lines.get(j).lazy ? 0 : setextUnderlineLevel(line);
                if (setext > 0) {
                    MarkdownBlock para = makeParagraph(raw);
                    out.add(MarkdownBlock.heading(setext, para.joinedLines(),
                            joinStyledLines(para.lineAnchors, null)));
                    return j + 1;
                }
                if (interruptsParagraph(line)) {
                    break;
                }
            }
            raw.add(lines.get(j));
            j++;
        }
        out.add(makeParagraph(raw));
        return j;
    }

    /** 既有行内 code 可以跨物理行；数学块不得中断已闭合 code span 的内部源行。 */
    private static boolean codeSpanCrossesLine(List<SrcLine> lines, int start, int candidate) {
        StringBuilder paragraph = new StringBuilder();
        int boundary = -1;
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i).text;
            if (isBlank(line) || (i > start && interruptsParagraph(line))) break;
            if (i > start) paragraph.append('\n');
            if (i == candidate) boundary = paragraph.length();
            paragraph.append(line);
        }
        if (boundary < 0) return false;
        String text = paragraph.toString();
        for (int cursor = 0; cursor < text.length();) {
            int open = text.indexOf(CODE_TICK, cursor);
            if (open < 0 || open >= boundary) return false;
            int slashes = 0;
            for (int k = open - 1; k >= 0 && text.charAt(k) == '\\'; k--) slashes++;
            if ((slashes & 1) != 0) { cursor = open + 1; continue; }
            // 与现行 inline 的单反引号配对相同：空对不消费，close 不另作转义解释。
            int close = text.indexOf(CODE_TICK, open + 1);
            if (close > open + 1) {
                if (close >= boundary) return true;
                cursor = close + 1;
            } else {
                cursor = open + 1;
            }
        }
        return false;
    }

    /** 表格升级判据只在单行段落之后调用，不另造段落边界。 */
    private static MarkdownBlock.TableData tableHeader(SrcLine header, SrcLine delimiter) {
        if (header.text.indexOf('|') < 0 || delimiter.text.indexOf('|') < 0
                || leadingSpaces(delimiter.text) > 3) {
            return null;
        }
        List<MarkdownBlock.CellSource> headings = tableCells(header);
        List<MarkdownBlock.CellSource> separators = tableCells(delimiter);
        if (headings.isEmpty() || headings.size() != separators.size()) {
            return null;
        }
        List<MarkdownTableModel.Alignment> alignments = new ArrayList<MarkdownTableModel.Alignment>();
        for (MarkdownBlock.CellSource separator : separators) {
            String text = separator.text;
            int from = text.startsWith(":") ? 1 : 0;
            int to = text.endsWith(":") ? text.length() - 1 : text.length();
            if (from >= to) {
                return null;
            }
            for (int i = from; i < to; i++) {
                if (text.charAt(i) != '-') {
                    return null;
                }
            }
            boolean left = from != 0;
            boolean right = to != text.length();
            alignments.add(left ? (right ? MarkdownTableModel.Alignment.CENTER : MarkdownTableModel.Alignment.LEFT)
                    : (right ? MarkdownTableModel.Alignment.RIGHT : MarkdownTableModel.Alignment.NONE));
        }
        List<List<MarkdownBlock.CellSource>> rows = new ArrayList<List<MarkdownBlock.CellSource>>();
        rows.add(headings);
        return new MarkdownBlock.TableData(alignments, rows);
    }

    private static int readTable(List<SrcLine> lines, int start, MarkdownBlock.TableData header,
                                 List<MarkdownBlock> out) {
        List<List<MarkdownBlock.CellSource>> rows = new ArrayList<List<MarkdownBlock.CellSource>>(header.rows);
        int j = start;
        while (j < lines.size()) {
            SrcLine line = lines.get(j);
            if (isBlank(line.text) || interruptsParagraph(line.text) || matchListStart(line.text) != null) {
                break;
            }
            List<MarkdownBlock.CellSource> cells = tableCells(line);
            if (cells.isEmpty()) {
                break;
            }
            List<MarkdownBlock.CellSource> row = new ArrayList<MarkdownBlock.CellSource>();
            for (int c = 0; c < header.alignments.size(); c++) {
                row.add(c < cells.size() ? cells.get(c) : new MarkdownBlock.CellSource("",
                        line.spans == null ? null : Collections.<MarkdownSpan>emptyList()));
            }
            rows.add(row);
            j++;
        }
        out.add(MarkdownBlock.table(new MarkdownBlock.TableData(header.alignments, rows)));
        return j;
    }

    /**
     * TablesExtension 实测：紧邻反斜杠的 pipe 永不分列（不是奇偶反斜杠规则），
     * 且在进入行内解析前只删除紧邻 pipe 的一个反斜杠；code 内也如此。
     * 所有切片同步裁剪原 span，保留 pipe 自己的样式，不借用被删反斜杠的样式。
     */
    private static List<MarkdownBlock.CellSource> tableCells(SrcLine line) {
        String text = line.text;
        int from = 0;
        int to = text.length();
        while (from < to && isSpaceChar(text.charAt(from))) { from++; }
        while (to > from && isSpaceChar(text.charAt(to - 1))) { to--; }
        // 单独一个 pipe 没有 cell；两个外沿 pipe 之间则是一个空 cell。
        if (to - from == 1 && text.charAt(from) == '|') {
            return Collections.emptyList();
        }
        if (from < to && text.charAt(from) == '|') { from++; }
        if (to > from && text.charAt(to - 1) == '|'
                && (to < 2 || text.charAt(to - 2) != (char) 92)) { to--; }
        List<MarkdownBlock.CellSource> cells = new ArrayList<MarkdownBlock.CellSource>();
        int start = from;
        for (int i = from; i <= to; i++) {
            if (i == to || (text.charAt(i) == '|' && (i == 0 || text.charAt(i - 1) != (char) 92))) {
                cells.add(tableCell(line, start, i));
                start = i + 1;
            }
        }
        return cells;
    }

    private static MarkdownBlock.CellSource tableCell(SrcLine line, int from, int to) {
        while (from < to && isSpaceChar(line.text.charAt(from))) { from++; }
        while (to > from && isSpaceChar(line.text.charAt(to - 1))) { to--; }
        StringBuilder text = new StringBuilder();
        List<MarkdownSpan> spans = line.spans == null ? null : new ArrayList<MarkdownSpan>();
        int start = from;
        for (int i = from; i < to; i++) {
            if (line.text.charAt(i) == (char) 92 && i + 1 < to && line.text.charAt(i + 1) == '|') {
                appendCellPiece(line, start, i, text, spans);
                start = i + 1;
            }
        }
        appendCellPiece(line, start, to, text, spans);
        return new MarkdownBlock.CellSource(text.toString(), spans);
    }

    private static void appendCellPiece(SrcLine line, int from, int to, StringBuilder text,
                                        List<MarkdownSpan> spans) {
        text.append(line.text, from, to);
        if (spans != null) {
            // 显式组合两端裁剪：旧 sliceSpans 的第三参实际按保留长度处理，
            // 与其区间注释不同；这里用绝对源坐标，不能让相邻 cell 的 span 漏入。
            for (MarkdownSpan span : dropTail(dropLead(line.spans, from), line.text.length() - to)) {
                appendMerged(spans, span.getText(), span.getBaseStyle());
            }
        }
    }

    /**
     * setext 下划线行判据（包内唯一出处）：{@code =} 连续串 → 1 级、{@code -} 连续串 → 2 级；
     * 前导空格 >3、夹其它字符（含空白）、或长度为空 → 0 = 非下划线行。
     */
    private static int setextUnderlineLevel(String line) {
        if (leadingSpaces(line) > 3) {
            return 0;
        }
        String t = trim(line);
        if (t.isEmpty()) {
            return 0;
        }
        char unit = t.charAt(0);
        if (unit != '=' && unit != '-') {
            return 0;
        }
        for (int i = 1; i < t.length(); i++) {
            if (t.charAt(i) != unit) {
                return 0;
            }
        }
        return unit == '=' ? 1 : 2;
    }

    /**
     * 段落收拢：每行剥行首空白（C1a，CommonMark：段落行——含首行与列表项内续行——的行首
     * 空白是块缩进/惰性续行缩进，一律折叠剥除、不进文本，例 113/291；旧「字面保留」裁定
     * 作废）、行尾 rtrim；行间硬换行位图（长度 = 行数 - 1）。
     */
    private static MarkdownBlock makeParagraph(List<SrcLine> rawLines) {
        int size = rawLines.size();
        boolean[] flags = size > 1 ? new boolean[size - 1] : new boolean[0];
        List<String> kept = new ArrayList<String>(size);
        boolean spanPath = size > 0 && rawLines.get(0).spans != null;
        List<List<MarkdownSpan>> anchors = spanPath ? new ArrayList<List<MarkdownSpan>>() : null;
        for (int i = 0; i < size; i++) {
            SrcLine src = rawLines.get(i);
            String line = stripLeadingSpace(src.text);
            List<MarkdownSpan> slice = anchors == null ? null
                    : dropLead(src.spans, src.text.length() - line.length());
            // 尾裁基准 = 行首折叠后、任何尾剥前的全长（硬换行剥除内部先做 rtrim，
            // 若以剥后长度为基准会把行尾两空格漏裁——C6a 锁 1 实测回归即此）。
            int tailBase = line.length();
            if (i < size - 1) {
                flags[i] = isHardBreakLine(line);
                if (flags[i]) {
                    line = stripHardBreakMarker(line); // 两空格已被 rtrim 吃掉，反斜杠在此剥除
                }
            }
            String text = rtrim(line);
            if (anchors != null) {
                slice = dropTail(slice, tailBase - text.length());
                anchors.add(slice);
            }
            kept.add(text);
        }
        return MarkdownBlock.paragraph(kept, flags, anchors);
    }

    /** 硬换行行判定：行尾两空格（含更多），或 rtrim 后以奇数个未转义反斜杠结尾。 */
    private static boolean isHardBreakLine(String line) {
        if (line.endsWith("  ")) {
            return true;
        }
        String trimmed = rtrim(line);
        int backslashes = 0;
        int idx = trimmed.length();
        while (idx > 0 && trimmed.charAt(idx - 1) == '\\') {
            backslashes++;
            idx--;
        }
        return backslashes > 0 && (backslashes & 1) == 1;
    }

    /** 硬换行命中后剥除行尾定界：奇数反斜杠去最后一个，再统一交给 rtrim。 */
    private static String stripHardBreakMarker(String line) {
        String trimmed = rtrim(line);
        int backslashes = 0;
        int idx = trimmed.length();
        while (idx > 0 && trimmed.charAt(idx - 1) == '\\') {
            backslashes++;
            idx--;
        }
        if (backslashes > 0 && (backslashes & 1) == 1) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
