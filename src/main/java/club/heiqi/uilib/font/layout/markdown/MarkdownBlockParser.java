package club.heiqi.uilib.font.layout.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 块级 markdown 扫描器（包内实现，对外唯一入口是 {@link MarkdownDocument}）。
 * 纯 JVM：不 import net.minecraft / cpw.mods / java.awt（规划 §四 G2 锁）。
 *
 * <p>语法面（《规划-通用Markdown渲染器.md》§二 L1 语法面，M2 范围）：ATX 标题
 * {@code #..######}、围栏代码（3 连 {@code `} 与 {@code ~~~}）、引用块 {@code >}
 * （可嵌套）、无序/有序列表（含缩进续行与嵌套子列表）、分隔线 {@code ---}/{@code ***}/
 * {@code ___}、段落与空行、硬换行（行尾两空格或行尾未转义反斜杠）。</p>
 *
 * <p>刻意不支持（按普通文本字面保留，不进任何专用节点，不做默默吞掉）：表格、任务列表、
 * HTML 内联、脚注、图片 {@code ![alt](url)}、缩进代码（4 空格缩进行是段落/列表续行）。
 * 图片的「字面」边界说明：块层不识别 {@code !}，正文原样交 {@link MarkdownInlineParser}
 * 后 {@code [alt](url)} 部分按既有行内裁定解析为链接、{@code !} 为字面文本——行内语义
 * 照抄 9c4dcae5 裁定（规划 §五 D2），本层一行不改。</p>
 *
 * <p>相对 CommonMark 的已裁简化（均有测试钉死）：制表符不展开（块缩进只数行首空格，
 * 标记后空格/制表符均接受）；行首反斜杠不构成块转义（{@code \# x} 整行按字面段落处理，
 * {@code #} 不在本包 escapable 集，反斜杠由行内层字面输出）；backtick 围栏 info 校验已按
 * CommonMark 实现（含反引号不算开栏）；无 setext 标题（{@code ---}
 * 恒为分隔线）；嵌套深度上限 {@code MAX_BLOCK_DEPTH}（超限层按段落字面收拢，宽容失败）。</p>
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
        return parseBlocks(splitLines(source), 0);
    }

    // ==================== 行工具 ====================

    private static List<String> splitLines(String source) {
        List<String> out = new ArrayList<String>();
        int start = 0;
        int n = source.length();
        for (int i = 0; i < n; i++) {
            char ch = source.charAt(i);
            if (ch == '\n') {
                out.add(source.substring(start, i));
                start = i + 1;
            } else if (ch == '\r') {
                out.add(source.substring(start, i));
                if (i + 1 < n && source.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        out.add(source.substring(start));
        return out;
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

    private static List<MarkdownBlock> parseBlocks(List<String> lines, int depth) {
        List<MarkdownBlock> blocks = new ArrayList<MarkdownBlock>();
        int i = 0;
        int n = lines.size();
        while (i < n) {
            String line = lines.get(i);
            if (isBlank(line)) {
                i++;
                continue;
            }
            int ind = leadingSpaces(line);
            if (ind <= 3) {
                String body = line.substring(ind);
                if (fenceStart(body, 3) != 0) {
                    i = readFence(lines, i, blocks);
                    continue;
                }
                int heading = headingLevel(body);
                if (heading > 0) {
                    blocks.add(MarkdownBlock.heading(heading, headingBody(body, heading)));
                    i++;
                    continue;
                }
                if (isThematicBreak(body)) {
                    blocks.add(MarkdownBlock.thematicBreak());
                    i++;
                    continue;
                }
                if (body.charAt(0) == '>') {
                    i = readQuote(lines, i, blocks, depth);
                    continue;
                }
                if (matchListStart(line) != null) {
                    i = readList(lines, i, blocks, depth);
                    continue;
                }
            }
            i = readParagraph(lines, i, blocks);
        }
        return blocks;
    }

    private static List<MarkdownBlock> parseWithDepthCap(List<String> lines, int childDepth) {
        if (childDepth >= MAX_BLOCK_DEPTH) {
            return singletonParagraphFallback(lines);
        }
        return parseBlocks(lines, childDepth);
    }

    private static List<MarkdownBlock> singletonParagraphFallback(List<String> rawLines) {
        if (rawLines.isEmpty()) {
            return Collections.emptyList();
        }
        List<MarkdownBlock> out = new ArrayList<MarkdownBlock>(1);
        out.add(makeParagraph(rawLines));
        return out;
    }

    /** 围栏代码：闭合要求同字符、长度 >= 开栏、行首 <=3 空格、行尾仅空白；未闭合消费到 EOF（宽容）。 */
    private static int readFence(List<String> lines, int start, List<MarkdownBlock> out) {
        String opener = lines.get(start);
        int fenceIndent = leadingSpaces(opener);
        String head = opener.substring(fenceIndent);
        char ch = head.charAt(0);
        int openLen = runLength(head, ch);
        String info = trim(head.substring(openLen));
        List<String> body = new ArrayList<String>();
        int j = start + 1;
        int n = lines.size();
        while (j < n) {
            String line = lines.get(j);
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
            body.add(isBlank(line) ? "" : stripFirst(line, fenceIndent));
            j++;
        }
        out.add(MarkdownBlock.code(info, body));
        return j;
    }

    /** 引用块：消费连续引用行与惰性续行；空行后仍带标记则并入同一引用（多段落）。 */
    private static int readQuote(List<String> lines, int start, List<MarkdownBlock> out, int depth) {
        List<String> inner = new ArrayList<String>();
        int j = start;
        int n = lines.size();
        while (j < n) {
            String line = lines.get(j);
            if (isBlank(line)) {
                int k = j;
                while (k < n && isBlank(lines.get(k))) {
                    k++;
                }
                if (k >= n) {
                    j = k;
                    break;
                }
                String next = lines.get(k);
                int ni = leadingSpaces(next);
                if (ni <= 3 && next.charAt(ni) == '>') {
                    inner.add("");
                    j = k;
                    continue;
                }
                j = k;
                break;
            }
            int ind = leadingSpaces(line);
            if (ind <= 3 && line.charAt(ind) == '>') {
                String content = line.substring(ind + 1);
                if (content.startsWith(" ") || content.startsWith("\t")) {
                    content = content.substring(1);
                }
                inner.add(content);
            } else if (!interruptsParagraph(line)) {
                inner.add(line);
            } else {
                break;
            }
            j++;
        }
        out.add(MarkdownBlock.quote(parseWithDepthCap(inner, depth + 1)));
        return j;
    }

    /** 同列表判定：有序/无序一致且同一 bullet 字符 / 同一有序定界符。 */
    private static boolean sameKind(ListStart candidate, boolean ordered, char unit) {
        if (candidate == null || candidate.ordered != ordered) {
            return false;
        }
        return ordered ? candidate.delim == unit : candidate.bullet == unit;
    }

    /**
     * 列表：项内容列 = 标记 + 1..4 空格（>=5 空格时内容带余空格）；缩进 >= 内容列的行剥列后入项体
     * （嵌套子列表因此自然成立）；未缩进行按惰性续行收拢；空行后视下一行归属决定松/断。
     */
    private static int readList(List<String> lines, int start, List<MarkdownBlock> out, int depth) {
        ListStart first = matchListStart(lines.get(start));
        boolean ordered = first.ordered;
        char unit = ordered ? first.delim : first.bullet;
        int n = lines.size();
        List<MarkdownBlock> items = new ArrayList<MarkdownBlock>();
        boolean listEnded = false;
        int i = start;
        while (i < n && !listEnded) {
            ListStart st = matchListStart(lines.get(i));
            if (!sameKind(st, ordered, unit)) {
                break;
            }
            List<String> body = new ArrayList<String>();
            if (!st.content.isEmpty()) {
                body.add(st.content);
            }
            int contentCol = st.contentCol;
            int j = i + 1;
            while (j < n) {
                String line = lines.get(j);
                if (isBlank(line)) {
                    int k = j;
                    while (k < n && isBlank(lines.get(k))) {
                        k++;
                    }
                    if (k >= n) {
                        j = k;
                        listEnded = true;
                        break;
                    }
                    String next = lines.get(k);
                    int ni = leadingSpaces(next);
                    ListStart nx = matchListStart(next);
                    if (ni < contentCol) {
                        if (sameKind(nx, ordered, unit)) {
                            j = k; // 空行分隔的同列表项（loose list）
                            break;
                        }
                        j = k;
                        listEnded = true; // 他类块开始，本列表结束
                        break;
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
                    body.add(line.substring(contentCol));
                    j++;
                    continue;
                }
                if (nx == null && !interruptsParagraph(line)) {
                    body.add(line); // 惰性续行
                    j++;
                    continue;
                }
                listEnded = true;
                break;
            }
            items.add(MarkdownBlock.listItem(st.marker, st.ordered, parseWithDepthCap(body, depth + 1)));
            i = j;
        }
        out.add(MarkdownBlock.list(items, ordered));
        return i;
    }

    private static int readParagraph(List<String> lines, int start, List<MarkdownBlock> out) {
        List<String> raw = new ArrayList<String>();
        int j = start;
        int n = lines.size();
        while (j < n) {
            String line = lines.get(j);
            if (isBlank(line) || (!raw.isEmpty() && interruptsParagraph(line))) {
                break;
            }
            raw.add(line);
            j++;
        }
        out.add(makeParagraph(raw));
        return j;
    }

    /** 段落收拢：行尾 rtrim；行间硬换行位图（长度 = 行数 - 1）。 */
    private static MarkdownBlock makeParagraph(List<String> rawLines) {
        int size = rawLines.size();
        boolean[] flags = size > 1 ? new boolean[size - 1] : new boolean[0];
        List<String> kept = new ArrayList<String>(size);
        for (int i = 0; i < size; i++) {
            String line = rawLines.get(i);
            if (i < size - 1) {
                flags[i] = isHardBreakLine(line);
                if (flags[i]) {
                    line = stripHardBreakMarker(line); // 两空格已被 rtrim 吃掉，反斜杠在此剥除
                }
            }
            kept.add(rtrim(line));
        }
        return MarkdownBlock.paragraph(kept, flags);
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
