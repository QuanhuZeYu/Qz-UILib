package club.heiqi.uilib.font.layout.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/**
 * markdown 行内解析器（纯 JVM，不依赖 Minecraft 类型）：样式锚点 span 流 → {@link TextSegment} 序列。
 *
 * <p>设计见《规划-聊天框Markdown接管.md》L1：行内子集 + CommonMark emphasis 定界简化 +
 * 聊天误伤防护裁定。风格与 {@code LatexParser} 同哲学：宽容失败——未闭合/孤立标记一律
 * 字面输出，不抛异常。</p>
 *
 * <h3>支持语法</h3>
 * <ul>
 *   <li>{@code **bold**} / {@code __bold__}：粗体（{@link FontType#BOLD}），内容可嵌套斜体；</li>
 *   <li>{@code *italic*} / {@code _italic_}：斜体（词边界定界，防 snake_case/乘法误伤）；</li>
 *   <li>{@code ***bold+italic***}：粗斜组合；</li>
 *   <li>{@code ~~strike~~}：删除线；</li>
 *   <li>{@code code span}：内容内不解析任何标记（第一版仅字面输出）；</li>
 *   <li>{@code $latex$} / {@code $$latex$$}：行内公式（{@code TextSegment.forLatex}，
 *       {@code $} 后邻居为数字时不触发——防 {@code $5.99} 误判）；</li>
 *   <li>{@code [text](url)}：链接（= {@code <a>} 语义：setLink + 自动下划线），
 *       url 支持一层嵌套括号，链接文字内可嵌套粗斜体；</li>
 *   <li>反斜杠转义；块级语法不支持，字面输出。</li>
 * </ul>
 *
 * <h3>样式叠加</h3>
 * <p>markdown 只叠加样式位（粗体/斜体/删除线/下划线[链接]），颜色恒由 span 基础样式决定；
 * 输出每个片段持有基础样式的拷贝（{@link TextStyle#copy()}），不修改调用方传入的样式。</p>
 */
public final class MarkdownInlineParser {

    /** 嵌套递归深度上限（防御异常输入的栈深）。 */
    private static final int MAX_DEPTH = 16;

    /** code span 定界符 U+0060 GRAVE ACCENT（反引号，0x60 书写避免 Unicode 转义陷阱）。 */
    private static final char CODE_TICK = 0x60;

    private MarkdownInlineParser() {
    }

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
     * 解析样式锚点 span 流（聊天组件桥的输入口径）。
     *
     * @param spans 带基础样式的文本 span 流（可为 null/空，返回空列表）
     * @return 富文本片段序列
     */
    public static List<TextSegment> parse(List<MarkdownSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return Collections.emptyList();
        }
        List<TextSegment> out = new ArrayList<TextSegment>();
        for (MarkdownSpan span : spans) {
            parseInline(span.getText(), span.getBaseStyle(), 0, out);
        }
        return out;
    }

    // ==================== 扫描核心 ====================

    private static void parseInline(String text, TextStyle base, int depth, List<TextSegment> out) {
        if (depth > MAX_DEPTH) {
            out.add(new TextSegment(text, base.copy()));
            return;
        }
        StringBuilder buffer = new StringBuilder();
        int index = 0;
        int length = text.length();
        while (index < length) {
            char ch = text.charAt(index);
            if (ch == '\\' && index + 1 < length && isEscapable(text.charAt(index + 1))) {
                buffer.append(text.charAt(index + 1));
                index += 2;
                continue;
            }
            if (ch == '\n') {
                buffer.append(ch);
                index++;
                continue;
            }
            if (startsWith(text, index, "***") && isOpeningDelim(text, index, '*')) {
                int close = findDelimClose(text, index + 3, "***");
                if (close >= 0) {
                    flush(buffer, base, out);
                    TextStyle inner = base.copy();
                    inner.setFontType(FontType.BOLD);
                    inner.setItalic(true);
                    parseInline(text.substring(index + 3, close), inner, depth + 1, out);
                    index = close + 3;
                    continue;
                }
            }
            if (startsWith(text, index, "**")) {
                if (isOpeningDelim(text, index, '*')) {
                    int close = findDelimClose(text, index + 2, "**");
                    if (close >= 0) {
                        flush(buffer, base, out);
                        TextStyle inner = base.copy();
                        inner.setFontType(FontType.BOLD);
                        parseInline(text.substring(index + 2, close), inner, depth + 1, out);
                        index = close + 2;
                        continue;
                    }
                }
                buffer.append("**");
                index += 2;
                continue;
            }
            if (startsWith(text, index, "__")) {
                if (isOpeningDelim(text, index, '_')) {
                    int close = findDelimClose(text, index + 2, "__");
                    if (close >= 0) {
                        flush(buffer, base, out);
                        TextStyle inner = base.copy();
                        inner.setFontType(FontType.BOLD);
                        parseInline(text.substring(index + 2, close), inner, depth + 1, out);
                        index = close + 2;
                        continue;
                    }
                }
                buffer.append("__");
                index += 2;
                continue;
            }
            if (ch == '*' || ch == '_') {
                if (isOpeningDelim(text, index, ch)) {
                    int close = findDelimClose(text, index + 1, String.valueOf(ch));
                    if (close >= 0) {
                        flush(buffer, base, out);
                        TextStyle inner = base.copy();
                        inner.setItalic(true);
                        parseInline(text.substring(index + 1, close), inner, depth + 1, out);
                        index = close + 1;
                        continue;
                    }
                }
                buffer.append(ch);
                index++;
                continue;
            }
            if (startsWith(text, index, "~~")) {
                int close = findDelimClose(text, index + 2, "~~");
                if (close >= 0) {
                    flush(buffer, base, out);
                    TextStyle inner = base.copy();
                    inner.setStrikethrough(true);
                    parseInline(text.substring(index + 2, close), inner, depth + 1, out);
                    index = close + 2;
                    continue;
                }
                buffer.append("~~");
                index += 2;
                continue;
            }
            if (ch == CODE_TICK) {
                int close = text.indexOf(CODE_TICK, index + 1);
                if (close > index + 1) {
                    flush(buffer, base, out);
                    // 第一版：code span 仅字面输出（等宽字体缺失，不引入假样式）
                    out.add(new TextSegment(text.substring(index + 1, close), base.copy()));
                    index = close + 1;
                    continue;
                }
                buffer.append(ch);
                index++;
                continue;
            }
            if (ch == '$') {
                int openLength = startsWith(text, index, "$$") ? 2 : 1;
                if (isLatexOpening(text, index, openLength)) {
                    int close = findDollarClose(text, index + openLength, openLength);
                    if (close >= 0) {
                        flush(buffer, base, out);
                        String source = text.substring(index + openLength, close);
                        out.add(TextSegment.forLatex(source, base.copy()));
                        index = close + openLength;
                        continue;
                    }
                }
                buffer.append(text, index, index + openLength);
                index += openLength;
                continue;
            }
            if (ch == '[') {
                LinkMatch link = tryParseLink(text, index, base, depth);
                if (link != null) {
                    flush(buffer, base, out);
                    out.addAll(link.segments);
                    index = link.endIndex;
                    continue;
                }
                buffer.append(ch);
                index++;
                continue;
            }
            buffer.append(ch);
            index++;
        }
        flush(buffer, base, out);
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
     */
    private static LinkMatch tryParseLink(String text, int index, TextStyle base, int depth) {
        int labelClose = findLinkLabelClose(text, index + 1);
        if (labelClose < 0) {
            return null;
        }
        int urlOpen = labelClose + 1;
        if (urlOpen >= text.length() || text.charAt(urlOpen) != '(') {
            return null;
        }
        int urlClose = findLinkUrlClose(text, urlOpen + 1);
        if (urlClose < 0) {
            return null;
        }
        String label = text.substring(index + 1, labelClose);
        String url = text.substring(urlOpen + 1, urlClose);
        if (label.isEmpty() || url.isEmpty()) {
            return null;
        }
        TextStyle linkBase = base.copy();
        linkBase.setLink(url);
        linkBase.setUnderline(true);
        List<TextSegment> inner = new ArrayList<TextSegment>();
        parseInline(label, linkBase, depth + 1, inner);
        return new LinkMatch(inner, urlClose + 1);
    }

    /** 找 {@code [text](url)} 的 {@code ]}：支持 {@code \]} 转义，不允许嵌套 {@code [}。 */
    private static int findLinkLabelClose(String text, int from) {
        for (int index = from; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '\\' && index + 1 < text.length() && text.charAt(index + 1) == ']') {
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
    private static int findLinkUrlClose(String text, int from) {
        int depth = 1;
        for (int index = from; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '\\' && index + 1 < text.length()
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
     */
    private static boolean isOpeningDelim(String text, int index, char delim) {
        if (index + 1 >= text.length()) {
            return false;
        }
        char next = text.charAt(index + 1);
        if (Character.isWhitespace(next)) {
            return false;
        }
        if (index > 0) {
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
    private static boolean isClosingDelim(String text, int index, char delim) {
        if (index <= 0 || Character.isWhitespace(text.charAt(index - 1))) {
            return false;
        }
        if (index + 1 < text.length()) {
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
    private static boolean isLatexOpening(String text, int index, int openLength) {
        int nextIndex = index + openLength;
        if (nextIndex >= text.length()) {
            return false;
        }
        char next = text.charAt(nextIndex);
        return !Character.isWhitespace(next) && !Character.isDigit(next);
    }

    /** 找 {@code $} 闭合：内容非空、前邻居非空白；支持 {@code \$} 转义。 */
    private static int findDollarClose(String text, int from, int openLength) {
        for (int index = from; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '\\' && index + 1 < text.length() && text.charAt(index + 1) == '$') {
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
     */
    private static int findDelimClose(String text, int from, String delim) {
        int index = from;
        while (index < text.length()) {
            int found = text.indexOf(delim, index);
            if (found < 0) {
                return -1;
            }
            int escaped = countPrecedingBackslashes(text, found);
            if (escaped % 2 == 1) {
                index = found + delim.length();
                continue;
            }
            // 闭定界检查作用于定界符最后一个字符
            if (isClosingDelim(text, found + delim.length() - 1, delim.charAt(0))) {
                return found;
            }
            index = found + delim.length();
        }
        return -1;
    }

    private static int countPrecedingBackslashes(String text, int index) {
        int count = 0;
        for (int cursor = index - 1; cursor >= 0 && text.charAt(cursor) == '\\'; cursor--) {
            count++;
        }
        return count;
    }

    private static boolean startsWith(String text, int index, String token) {
        return text.regionMatches(index, token, 0, token.length());
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

    // ==================== 输出 ====================

    private static void flush(StringBuilder buffer, TextStyle base, List<TextSegment> out) {
        if (buffer.length() == 0) {
            return;
        }
        out.add(new TextSegment(buffer.toString(), base.copy()));
        buffer.setLength(0);
    }
}
