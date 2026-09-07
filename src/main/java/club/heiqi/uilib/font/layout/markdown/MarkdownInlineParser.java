package club.heiqi.uilib.font.layout.markdown;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/**
 * markdown 行内解析器（纯 JVM，不依赖 Minecraft 类型）：样式锚点 span 流 → {@link TextSegment} 序列。
 *
 * <p>设计见《规划-通用Markdown渲染器.md》§二 L1 语法面（语义裁定沿用《规划-聊天框Markdown接管.md》
 * §L1，照抄不重设计）：行内子集 + CommonMark emphasis 定界简化 +
 * 聊天误伤防护裁定。风格与 {@code LatexParser} 同哲学：宽容失败——未闭合/孤立标记一律
 * 字面输出，不抛异常。</p>
 *
 * <h3>C6a（能力②）：跨 span 连续扫描</h3>
 * <p>旧实现逐 span 独立 {@code parseInline}，跨 span 边界的定界符永不闭合——span[("**",A),
 * ("粗",B),("**",A)] 产字面 <code>**粗**</code> 而单文本 {@code **粗**} 产粗体，两种输入
 * 形态语义分叉。宿主样式流（IChatComponent 展开、业务 mod 富文本等）不知道 markdown 边界，
 * 跨锚点配对是常态而非角落，分叉即回归。本层自此在 <b>span 拼接文本上单次连续扫描</b>，产段时按字符
 * 区间回溯样式：一个 markdown 语义段跨多个样式区间时按样式边界再切成多段，每段样式 =
 * 该区间基础样式 + markdown 叠加位（叠加顺序不变：基础样式为底、markdown 只叠位）。
 * 相邻样式值相等的 span 先并组（{@link StyleValues}），保证切分只发生在真正的样式边界。</p>
 *
 * <p><b>单 span 等价性（在案硬约束）</b>：{@code parse(String, TextStyle)} 恒走
 * {@code singletonList(new MarkdownSpan(...))}，拼接文本 = 原 String、单一样式组，扫描
 * 坐标与逐位语义和改造前逐行对偶（递归子段改 from/to 区间参数、判据边界从 length 改 to，
 * 一一对应）；门禁 48 条语料 + {@code MarkdownInlineParserTest} 全绿即机器证明。</p>
 *
 * <p><b>宽容失败不变</b>：连续扫描只改变「定界符可见范围」，不新造配对——未闭合定界符
 * 仍恒字面（类头在案哲学的适用面从「单段文本」扩展到「拼接文本」，判定规则一字不改）。</p>
 *
 * <h3>支持语法</h3>
 * <ul>
 *   <li>{@code **bold**} / {@code __bold__}：粗体（{@link FontType#BOLD}），内容可嵌套斜体；</li>
 *   <li>{@code *italic*} / {@code _italic_}：斜体（词边界定界，防 snake_case/乘法误伤）；</li>
 *   <li>{@code ***bold+italic***}：粗斜组合；</li>
 *   <li>{@code ~~strike~~}：删除线；</li>
 *   <li>{@code code span}：反引号对识别为行内 code 段——打既有 {@code TextStyle.codeSpan} 位、
 *       注入 {@code codeBackgroundColor} 衬底色、按 chat3 口径写段级 {@code fontSizePx}；
 *       code 内容一律字面：不解析任何行内标记、不做 URL 链接化（下游 linkify 见
 *       {@code ChatUrlLinkifier} 恒跳过 codeSpan 段）。<b>取代关系</b>：M1 复活时的旧裁定
 *       「第一版 code 仅字面输出（等宽字体缺失，不引入假样式）」自 2026-09-04 起被
 *       chat3 出货行为取代（M4-fix F1；对拍门禁 P03 钉死，见
 *       {@code font/render/software/MarkdownChat3ParityTest}）；本层剥掉的反引号永不回补，
 *       故 code 位必须在吃定界符的当场写进段样式。C6a 起 code 段跨样式区间时同样按样式
 *       边界切段，每段都带全套 code 位。</li>
 *   <li>{@code $latex$} / {@code $$latex$$}：行内公式（{@code TextSegment.forLatex}，
 *       {@code $} 后邻居为数字时不触发——防 {@code $5.99} 误判）；公式原子不切段，
 *       基础样式取内容首字符所在区间；</li>
 *   <li>{@code [text](url)}：链接（= {@code <a>} 语义：setLink + 自动下划线），
 *       url 支持一层嵌套括号，链接文字内可嵌套粗斜体；</li>
 *   <li>反斜杠转义（转义产出的字符继承被转义字符所在样式区间）；块级语法不支持，
 *       字面输出（块级构造由 {@link MarkdownDocument} 识别后，仅将每块正文连同其样式锚点
 *       交给本类，块级标记不进入本类输入）。</li>
 * </ul>
 *
 * <h3>样式叠加</h3>
 * <p>markdown 只叠加样式位（粗体/斜体/删除线/下划线[链接]），颜色恒由 span 基础样式决定；
 * 输出每个片段持有基础样式的拷贝（{@link TextStyle#copy()}），不修改调用方传入的样式。
 * 块级文档入口（{@code MarkdownDocument.parseSpans(List)}）可另带 {@link StyleTransform}
 * 块级叠加链：施加顺序（C6b 定序裁定，取代 C6a「链压顶」）= span 基础样式铺底、块级链
 * 叠样式位、<b>span 显式色（{@code isColorExplicit}）覆盖块级色</b>、行内位最后——
 * 宿主色优先、样式位叠加，与 chat3 旧输出侧 § 桥「markdown 位先叠、§ 码后生效」对齐。</p>
 */
public final class MarkdownInlineParser {

    /** 嵌套递归深度上限（防御异常输入的栈深）。 */
    private static final int MAX_DEPTH = 16;

    /** code span 定界符 U+0060 GRAVE ACCENT（反引号，0x60 书写避免 Unicode 转义陷阱）。 */
    private static final char CODE_TICK = 0x60;

    /** 公共入口（无样式表形参）时的 code 段口径来源——恒取默认登记表，与包内形参路径同值。 */
    private static final MarkdownStyleTable CODE_TABLE_FALLBACK = MarkdownStyleTable.defaults();

    private MarkdownInlineParser() {
    }

    // ==================== 入口 ====================

    /**
     * 解析单段 markdown 文本。
     *
     * @param markdown  markdown 文本（可为 null/空，返回空列表）
     * @param baseStyle 基础样式（不可为 null）
     * @return 富文本片段序列
     */
    public static List<TextSegment> parse(String markdown, TextStyle baseStyle) {
        if (markdown == null || markdown.isEmpty()) {
            return Collections.emptyList();
        }
        return parse(Collections.singletonList(new MarkdownSpan(markdown, baseStyle)));
    }

    /**
     * 解析单段 markdown 文本（包内：带样式/排版表，code 段口径取表内登记值）。
     *
     * @param markdown  markdown 文本（可为 null/空，返回空列表）
     * @param baseStyle 基础样式（不可为 null）
     * @param styles    样式表（可为 null，取 {@link MarkdownStyleTable#defaults()}）
     * @return 富文本片段序列
     */
    static List<TextSegment> parse(String markdown, TextStyle baseStyle, MarkdownStyleTable styles) {
        if (markdown == null || markdown.isEmpty()) {
            return Collections.emptyList();
        }
        return parse(Collections.singletonList(new MarkdownSpan(markdown, baseStyle)), styles);
    }

    /**
     * 解析样式锚点 span 流（聊天组件桥的输入口径）。
     *
     * <p><b>C6a 起为跨 span 连续扫描</b>：定界符可跨 span 边界配对（拼接文本单次解析），
     * 产段按样式边界回溯基础样式。旧「逐 span 独立解析」下跨边界定界符恒字面的行为
     * 不复存在；单 span 流与 String 入口逐位等价（类头在案锁）。</p>
     *
     * @param spans 带基础样式的文本 span 流（可为 null/空，返回空列表）
     * @return 富文本片段序列
     */
    public static List<TextSegment> parse(List<MarkdownSpan> spans) {
        return parse(spans, null);
    }

    /**
     * 解析样式锚点 span 流（包内：带样式表）。
     *
     * @param spans  带基础样式的文本 span 流（可为 null/空，返回空列表）
     * @param styles code 段口径的登记表（可为 null，取 {@link MarkdownStyleTable#defaults()}）
     * @return 富文本片段序列
     */
    static List<TextSegment> parse(List<MarkdownSpan> spans, MarkdownStyleTable styles) {
        return parse(spans, styles, null);
    }

    /**
     * 解析样式锚点 span 流（包内，C6a 能力①接缝：块级文档路的行内消费）。
     *
     * <p>与公共 {@code parse(List)} 同一扫描核，仅多一条 {@link StyleTransform} 块级叠加链
     * （null = 无块级叠加）。<b>单次解析承诺</b>：本入口是「输入侧」通道——span 流只进
     * markdown 解析这一次；任何「先产段再喂回来」的输出侧反接都属规划 §二之八 旧裁定
     * 警告的双解析漂移，禁止。</p>
     *
     * @param spans          带基础样式的文本 span 流（可为 null/空，返回空列表）
     * @param styles         code 段口径登记表（可为 null，取 defaults）
     * @param blockTransform 块级样式叠加链（可为 null；施加于每个区间的 span 基础样式之上、
     *                       行内叠加位之下）
     * @return 富文本片段序列
     */
    static List<TextSegment> parse(List<MarkdownSpan> spans, MarkdownStyleTable styles,
            StyleTransform blockTransform) {
        if (spans == null || spans.isEmpty()) {
            return Collections.emptyList();
        }
        MarkdownStyleTable table = styles == null ? CODE_TABLE_FALLBACK : styles;
        // 拼接纯文本 + 每字符样式组号；相邻值相等的 span 并为同组（StyleValues 深比较），
        // 保证「单 span 文档」只有一组 → 出段粒度与旧 String 路逐位一致。
        int total = 0;
        for (int i = 0; i < spans.size(); i++) {
            total += spans.get(i).getText().length();
        }
        if (total == 0) {
            return Collections.emptyList();
        }
        StringBuilder text = new StringBuilder(total);
        int[] group = new int[total];
        List<TextStyle> groupStyles = new ArrayList<TextStyle>();
        int pos = 0;
        int current = -1;
        for (int i = 0; i < spans.size(); i++) {
            MarkdownSpan span = spans.get(i);
            String piece = span.getText();
            int gi;
            if (current >= 0 && StyleValues.same(groupStyles.get(current), span.getBaseStyle())) {
                gi = current;
            } else {
                gi = groupStyles.size();
                groupStyles.add(span.getBaseStyle());
            }
            current = gi;
            for (int k = 0; k < piece.length(); k++) {
                group[pos++] = gi;
            }
            text.append(piece);
        }
        List<TextSegment> out = new ArrayList<TextSegment>();
        parseInline(text.toString(), 0, total, 0, Layer.ROOT,
                new ScanCtx(group, groupStyles, blockTransform, table), out);
        return out;
    }

    // ==================== 扫描核心 ====================

    /**
     * 连续扫描 {@code text[from, to)}。C6a：区间参数取代旧 substring 递归——
     * 判据函数以 [from,to) 为「可见全文」，区间外字符（父段的定界残留、兄弟 span 的
     * 文本）与旧实现一样不可见，逐位语义不变；绝对坐标让样式回溯成为可能。
     */
    private static void parseInline(String text, int from, int to, int depth, Layer layer,
            ScanCtx ctx, List<TextSegment> out) {
        if (depth > MAX_DEPTH) {
            emitRange(text, from, to, layer, ctx, out);
            return;
        }
        Buf buffer = new Buf();
        int index = from;
        while (index < to) {
            char ch = text.charAt(index);
            if (ch == '\\' && index + 1 < to && isEscapable(text.charAt(index + 1))) {
                buffer.append(text.charAt(index + 1), index + 1);
                index += 2;
                continue;
            }
            if (ch == '\n') {
                buffer.append(ch, index);
                index++;
                continue;
            }
            if (startsWith(text, index, to, "***") && isOpeningDelim(text, from, to, index, '*')) {
                int close = findDelimClose(text, from, to, index + 3, "***");
                if (close >= 0) {
                    buffer.emit(layer, ctx, out);
                    parseInline(text, index + 3, close, depth + 1,
                            layer.push(Layer.BOLD_ITALIC, null), ctx, out);
                    index = close + 3;
                    continue;
                }
            }
            if (startsWith(text, index, to, "**")) {
                if (isOpeningDelim(text, from, to, index, '*')) {
                    int close = findDelimClose(text, from, to, index + 2, "**");
                    if (close >= 0) {
                        buffer.emit(layer, ctx, out);
                        parseInline(text, index + 2, close, depth + 1,
                                layer.push(Layer.BOLD, null), ctx, out);
                        index = close + 2;
                        continue;
                    }
                }
                buffer.appendPair("**", index);
                index += 2;
                continue;
            }
            if (startsWith(text, index, to, "__")) {
                if (isOpeningDelim(text, from, to, index, '_')) {
                    int close = findDelimClose(text, from, to, index + 2, "__");
                    if (close >= 0) {
                        buffer.emit(layer, ctx, out);
                        parseInline(text, index + 2, close, depth + 1,
                                layer.push(Layer.BOLD, null), ctx, out);
                        index = close + 2;
                        continue;
                    }
                }
                buffer.appendPair("__", index);
                index += 2;
                continue;
            }
            if (ch == '*' || ch == '_') {
                if (isOpeningDelim(text, from, to, index, ch)) {
                    int close = findDelimClose(text, from, to, index + 1, String.valueOf(ch));
                    if (close >= 0) {
                        buffer.emit(layer, ctx, out);
                        parseInline(text, index + 1, close, depth + 1,
                                layer.push(Layer.ITALIC, null), ctx, out);
                        index = close + 1;
                        continue;
                    }
                }
                buffer.append(ch, index);
                index++;
                continue;
            }
            if (startsWith(text, index, to, "~~")) {
                int close = findDelimClose(text, from, to, index + 2, "~~");
                if (close >= 0) {
                    buffer.emit(layer, ctx, out);
                    parseInline(text, index + 2, close, depth + 1,
                            layer.push(Layer.STRIKE, null), ctx, out);
                    index = close + 2;
                    continue;
                }
                buffer.appendPair("~~", index);
                index += 2;
                continue;
            }
            if (ch == CODE_TICK) {
                int close = text.indexOf(CODE_TICK, index + 1);
                if (close > index + 1 && close < to) {
                    buffer.emit(layer, ctx, out);
                    // F1（2026-09-04）：反引号对 = 行内 code 段。样式在吃定界符的当场写好
                    // （旧裁定「第一版仅字面输出」已被 chat3 出货行为取代）：codeSpan 位 +
                    // 衬底色 + chat3 口径段级字号，数值恒取自 MarkdownStyleTable（G4 度量同源）。
                    // 内容字面：区间原样进段，不递归 parseInline（行内标记不解析），
                    // 并清 link——下游 ChatUrlLinkifier 见 codeSpan 位即跳过，URL 不链接化。
                    // C6a：内容跨样式区间按边界切段，code 位全套随段（旧单段 = 单组特例）。
                    emitRange(text, index + 1, close, layer.push(Layer.CODE, null), ctx, out);
                    index = close + 1;
                    continue;
                }
                buffer.append(ch, index);
                index++;
                continue;
            }
            if (ch == '$') {
                int openLength = startsWith(text, index, to, "$$") ? 2 : 1;
                if (isLatexOpening(text, to, index, openLength)) {
                    int close = findDollarClose(text, to, index + openLength, openLength);
                    if (close >= 0) {
                        buffer.emit(layer, ctx, out);
                        String source = text.substring(index + openLength, close);
                        // latex 原子段不切样式区间：基础样式取内容首字符所在组。
                        out.add(TextSegment.forLatex(source,
                                resolve(index + openLength, layer, ctx)));
                        index = close + openLength;
                        continue;
                    }
                }
                buffer.appendRun(text, index, index + openLength);
                index += openLength;
                continue;
            }
            if (ch == '[') {
                LinkMatch link = tryParseLink(text, from, to, index, layer, depth, ctx);
                if (link != null) {
                    buffer.emit(layer, ctx, out);
                    out.addAll(link.segments);
                    index = link.endIndex;
                    continue;
                }
                buffer.append(ch, index);
                index++;
                continue;
            }
            buffer.append(ch, index);
            index++;
        }
        buffer.emit(layer, ctx, out);
    }

    // ==================== 链接 ====================

    private static final class LinkMatch {
        final List<TextSegment> segments;
        final int endIndex;

        LinkMatch(List<TextSegment> segments, int endIndex) {
            this.segments = segments;
            this.endIndex = endIndex;
        }
    }

    /**
     * 尝试在 index 解析 {@code [text](url)}；失败返回 null（调用方按字面继续）。
     * 链接位经 {@link Layer#LINK} 叠加：label 内每个出段位点先叠父层位、再叠 link+下划线，
     * 与旧「linkBase 拷基础样式先叠位再递归」的先后顺序同值。
     */
    private static LinkMatch tryParseLink(String text, int from, int to, int index, Layer layer,
            int depth, ScanCtx ctx) {
        int labelClose = findLinkLabelClose(text, to, index + 1);
        if (labelClose < 0) {
            return null;
        }
        int urlOpen = labelClose + 1;
        if (urlOpen >= to || text.charAt(urlOpen) != '(') {
            return null;
        }
        int urlClose = findLinkUrlClose(text, to, urlOpen + 1);
        if (urlClose < 0) {
            return null;
        }
        String url = text.substring(urlOpen + 1, urlClose);
        if (labelClose == index + 1 || url.isEmpty()) {
            return null;
        }
        List<TextSegment> inner = new ArrayList<TextSegment>();
        parseInline(text, index + 1, labelClose, depth + 1,
                layer.push(Layer.LINK, url), ctx, inner);
        return new LinkMatch(inner, urlClose + 1);
    }

    /** 找 {@code [text](url)} 的 {@code ]}：支持 {@code \]} 转义，不允许嵌套 {@code [}。 */
    private static int findLinkLabelClose(String text, int to, int from) {
        for (int index = from; index < to; index++) {
            char ch = text.charAt(index);
            if (ch == '\\' && index + 1 < to && text.charAt(index + 1) == ']') {
                index++;
                continue;
            }
            if (ch == ']') {
                return index;
            }
            if (ch == '[' || ch == '\n') {
                return -1;
            }
        }
        return -1;
    }

    /** 找 url 的 {@code )}：支持一层嵌套括号与 {@code \)} 转义，url 内不允许空白。 */
    private static int findLinkUrlClose(String text, int to, int from) {
        int depth = 1;
        for (int index = from; index < to; index++) {
            char ch = text.charAt(index);
            if (ch == '\\' && index + 1 < to
                    && (text.charAt(index + 1) == ')' || text.charAt(index + 1) == '(')) {
                index++;
                continue;
            }
            if (ch == '(') {
                depth++;
                continue;
            }
            if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
                continue;
            }
            if (Character.isWhitespace(ch)) {
                return -1;
            }
        }
        return -1;
    }

    // ==================== 定界裁定 ====================

    /**
     * emphasis 开定界（CommonMark flanking 简化 + 聊天裁定）：
     * - 后邻居非空白；
     * - 前邻居为 ASCII 字母数字时拒绝（防 {@code hello_world}/{@code a*b}/{@code x_1}）；
     * - 前邻居为定界符字符（* 或 _）时拒绝（防 {@code a***b***} 拆解误开）；
     * - CJK 邻接：{@code *} 系列放宽（中文无空格习惯，{@code 这是**粗**体} 合法），
     *   {@code _} 系列维持严格（防中文 snake_case 分隔符误伤）。
     *
     * <p>C6a：邻居可见域 = 本递归段 {@code [from,to)}——与旧 substring 递归的
     * {@code [0,length)} 逐位对偶（{@code index>from} ⇔ 旧 {@code relativeIndex>0}）。</p>
     */
    private static boolean isOpeningDelim(String text, int from, int to, int index, char delim) {
        if (index + 1 >= to) {
            return false;
        }
        char next = text.charAt(index + 1);
        if (Character.isWhitespace(next)) {
            return false;
        }
        if (index > from) {
            char prev = text.charAt(index - 1);
            if (prev == '*' || prev == '_') {
                return false;
            }
            if (isAsciiWord(prev)) {
                return false;
            }
            if (isCjk(prev) && delim == '_') {
                return false;
            }
        }
        return true;
    }

    /**
     * emphasis 闭定界：前邻居非空白；后邻居 ASCII 字母数字拒绝；CJK 后邻对 {@code _}
     * 拒绝、对 {@code *} 放宽（中文闭标记后紧跟汉字合法，如 {@code **粗**体}）。
     */
    private static boolean isClosingDelim(String text, int from, int to, int index, char delim) {
        if (index <= from || Character.isWhitespace(text.charAt(index - 1))) {
            return false;
        }
        if (index + 1 < to) {
            char next = text.charAt(index + 1);
            if (isAsciiWord(next)) {
                return false;
            }
            if (isCjk(next) && delim == '_') {
                return false;
            }
        }
        return true;
    }

    /** ASCII 字母数字（词内字符，emphasis 定界的误伤防护口径）。 */
    private static boolean isAsciiWord(char ch) {
        return ch < '\u2E80' && Character.isLetterOrDigit(ch);
    }

    /** CJK 及全角区（U+2E80 及以上）。 */
    private static boolean isCjk(char ch) {
        return ch >= '\u2E80';
    }

    /** {@code $} 开定界：非数字后邻居（防 {@code $5.99}）+ 后邻居非空白。 */
    private static boolean isLatexOpening(String text, int to, int index, int openLength) {
        int nextIndex = index + openLength;
        if (nextIndex >= to) {
            return false;
        }
        char next = text.charAt(nextIndex);
        return !Character.isWhitespace(next) && !Character.isDigit(next);
    }

    /** 找 {@code $} 闭合：内容非空、前邻居非空白；支持 {@code \$} 转义。 */
    private static int findDollarClose(String text, int to, int from, int openLength) {
        for (int index = from; index < to; index++) {
            char ch = text.charAt(index);
            if (ch == '\\' && index + 1 < to && text.charAt(index + 1) == '$') {
                index++;
                continue;
            }
            if (ch == '$') {
                if (index > from && !Character.isWhitespace(text.charAt(index - 1))) {
                    return index;
                }
                if (openLength == 2 && index == from) {
                    return -1; // 空公式不解析
                }
            }
        }
        return -1;
    }

    /**
     * 找定界闭合（emphasis 类）：跳过 {@code \delim} 转义；遇到满足闭定界的位置返回。
     * 嵌套同定界时取第一个满足条件的闭合（宽容近似，未闭合按字面由调用方处理）。
     * 跨 {@code to} 边界的定界串不算闭合（旧 substring 版的等价形态）。
     */
    private static int findDelimClose(String text, int from, int to, int start, String delim) {
        int index = start;
        while (index < to) {
            int found = text.indexOf(delim, index);
            if (found < 0 || found + delim.length() > to) {
                return -1;
            }
            int escaped = countPrecedingBackslashes(text, from, found);
            if ((escaped & 1) == 1) {
                index = found + delim.length();
                continue;
            }
            // 闭定界检查作用于定界符最后一个字符
            if (isClosingDelim(text, from, to, found + delim.length() - 1, delim.charAt(0))) {
                return found;
            }
            index = found + delim.length();
        }
        return -1;
    }

    private static int countPrecedingBackslashes(String text, int from, int index) {
        int count = 0;
        for (int cursor = index - 1; cursor >= from && text.charAt(cursor) == '\\'; cursor--) {
            count++;
        }
        return count;
    }

    private static boolean startsWith(String text, int index, int to, String token) {
        return index + token.length() <= to
                && text.regionMatches(index, token, 0, token.length());
    }

    private static boolean isEscapable(char ch) {
        switch (ch) {
            case '*':
            case '_':
            case '~':
            case '$':
            case '[':
            case ']':
            case '\\':
            case 0x60: // 反引号
                return true;
            default:
                return false;
        }
    }

    // ==================== 样式回溯输出 ====================

    /**
     * 解析第 {@code charIndex} 个源字符出段样式。C6b 定序裁定（取代 C6a「链压顶」）：
     * 区间基础样式拷贝 → 块级叠加链（标题/引用样式位）→ <b>span 携带显式色时其颜色
     * 覆盖块级色</b>（宿主色优先；无显式色的 span = 纯 caller 底色，不覆盖，引用降色
     * 照常生效）→ 行内叠加栈（自外向内）。String 路（blockTransform 恒 null）不经覆盖
     * 分支，逐位不变；与 {@code MarkdownDocument.BlockStyle.applied} 同一把尺。
     */
    private static TextStyle resolve(int charIndex, Layer layer, ScanCtx ctx) {
        TextStyle base = ctx.styleAt(charIndex);
        TextStyle style = base.copy();
        if (ctx.blockTransform != null) {
            style = ctx.blockTransform.apply(style);
            if (base.isColorExplicit()) {
                style.setColor(base.getColor());
            }
        }
        layer.applyTo(style, ctx);
        return style;
    }

    /** 源区间出段：按样式组边界切段，每段样式在段首字符处解析（{@link #resolve}）。 */
    private static void emitRange(String text, int from, int to, Layer layer, ScanCtx ctx,
            List<TextSegment> out) {
        int i = from;
        while (i < to) {
            int g = ctx.group[i];
            int j = i + 1;
            while (j < to && ctx.group[j] == g) {
                j++;
            }
            out.add(new TextSegment(text.substring(i, j), resolve(i, layer, ctx)));
            i = j;
        }
    }

    /**
     * 字面缓冲：文本与「每字符绝对源下标」平行推进（转义吃掉反斜杠、定界符字面回填都
     * 不改写来源），出段时按样式组边界切分——与 {@link #emitRange} 同一把尺。
     */
    private static final class Buf {

        private final StringBuilder sb = new StringBuilder();
        private int[] idxs = new int[16];
        private int size;

        void append(char ch, int sourceIndex) {
            grow(1);
            idxs[size++] = sourceIndex;
            sb.append(ch);
        }

        /** 双字符定界符字面回填（两字符各记自己的源下标，跨样式边界照样可切）。 */
        void appendPair(String token, int sourceIndex) {
            grow(token.length());
            for (int i = 0; i < token.length(); i++) {
                idxs[size] = sourceIndex + i;
                size++;
            }
            sb.append(token);
        }

        /** 连续源区间字面回填（如 $$ 双定界）。 */
        void appendRun(String text, int from, int to) {
            int n = to - from;
            grow(n);
            for (int i = 0; i < n; i++) {
                idxs[size] = from + i;
                size++;
            }
            sb.append(text, from, to);
        }

        private void grow(int extra) {
            if (size + extra > idxs.length) {
                idxs = Arrays.copyOf(idxs, Math.max(idxs.length * 2, size + extra));
            }
        }

        void emit(Layer layer, ScanCtx ctx, List<TextSegment> out) {
            if (size == 0) {
                return;
            }
            int i = 0;
            while (i < size) {
                int g = ctx.group[idxs[i]];
                int j = i + 1;
                while (j < size && ctx.group[idxs[j]] == g) {
                    j++;
                }
                out.add(new TextSegment(sb.substring(i, j), resolve(idxs[i], layer, ctx)));
                i = j;
            }
            sb.setLength(0);
            size = 0;
        }
    }

    /** 行内叠加栈节点：BOLD/ITALIC/粗斜/删除线/链接/CODE，父先子后施加（旧拷贝链同序）。 */
    private static final class Layer {

        static final Layer ROOT = new Layer(null, 0, null);

        static final int BOLD = 1;
        static final int ITALIC = 2;
        static final int BOLD_ITALIC = 3;
        static final int STRIKE = 4;
        static final int LINK = 5;
        static final int CODE = 6;

        private final Layer parent;
        private final int op;
        private final String link;

        Layer(Layer parent, int op, String link) {
            this.parent = parent;
            this.op = op;
            this.link = link;
        }

        Layer push(int opCode, String linkUrl) {
            return new Layer(this, opCode, linkUrl);
        }

        void applyTo(TextStyle style, ScanCtx ctx) {
            if (parent != null) {
                parent.applyTo(style, ctx);
            }
            switch (op) {
                case BOLD:
                    style.setFontType(FontType.BOLD);
                    break;
                case ITALIC:
                    style.setItalic(true);
                    break;
                case BOLD_ITALIC:
                    style.setFontType(FontType.BOLD);
                    style.setItalic(true);
                    break;
                case STRIKE:
                    style.setStrikethrough(true);
                    break;
                case LINK:
                    style.setLink(link);
                    style.setUnderline(true);
                    break;
                case CODE:
                    // 与旧 codeStyle 同序：codeSpan 位 → 衬底色 → 段级字号（>0 才写）→ 清 link。
                    style.setCodeSpan(true);
                    style.setCodeBackgroundColor(ctx.styles.getCodeBackgroundColor());
                    int codePx = ctx.styles.getCodeFontSizePx();
                    if (codePx > 0) {
                        style.setFontSizePx(codePx);
                    }
                    style.setLink(null);
                    break;
                default:
                    break;
            }
        }
    }

    /** 单次解析的可变上下文：字符→样式组映射 + 块级叠加链 + code 口径登记表。 */
    private static final class ScanCtx {

        private final int[] group;
        private final List<TextStyle> groupStyles;
        private final StyleTransform blockTransform;
        private final MarkdownStyleTable styles;

        ScanCtx(int[] group, List<TextStyle> groupStyles, StyleTransform blockTransform,
                MarkdownStyleTable styles) {
            this.group = group;
            this.groupStyles = groupStyles;
            this.blockTransform = blockTransform;
            this.styles = styles;
        }

        TextStyle styleAt(int charIndex) {
            return groupStyles.get(group[charIndex]);
        }
    }
}
