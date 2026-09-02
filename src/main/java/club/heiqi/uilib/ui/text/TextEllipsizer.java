package club.heiqi.uilib.ui.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

import club.heiqi.uilib.font.util.UnicodeTextClassifier;

/**
 * 文本省略号工具 —— 按像素宽度截断文本并追加「…」。
 *
 * <p>核心算法与具体度量服务解耦：{@link #ellipsize(ToIntFunction, String, int)} 只依赖一个
 * 「文本 → UI 像素宽度」的宽度函数，{@link TextMeasureService} 与 scene 控件（经
 * {@code SceneRuntime#measureTextWidth} 适配）都能喂同一套逻辑，避免两套世界各写一份截断实现。</p>
 *
 * <h3>边界语义（测试锚定）</h3>
 * <ul>
 *   <li>null / 空串 → 原样返回；</li>
 *   <li>恰好放下（含正好等于）→ 原样返回；</li>
 *   <li>超宽 → 二分搜索最长前缀 p 使 {@code width(p) + width("…") <= maxWidthPx}，
 *       返回 {@code p + "…"}；</li>
 *   <li>单字超宽（连一个字符加省略号都放不下）→ 返回 {@code "…"}（省略号自身能放下时）；</li>
 *   <li>省略号自身都放不下（或 {@code maxWidthPx <= 0}）→ 返回空串，由调用方自行裁剪/隐藏。</li>
 * </ul>
 *
 * <h3>多行换行（{@link #wrapLines}）</h3>
 * <p>先按 Unicode 换行类切段（{@code \r\n} 折叠），再按 Unicode 空白家族分词贪心换行；
 * ZWSP/软连字符提供词内折行机会（断行处补连字符）；单词超宽时对该词做 ellipsis；
 * {@code maxLines > 0} 时截断并省略末行（省略号计入末行宽度）。连续空白会被折叠为单词间的
 * 单空格（tooltip 展示可接受的轻量语义）。</p>
 */
public final class TextEllipsizer {

    /** 追加用省略号。 */
    public static final String ELLIPSIS = "…";

    private TextEllipsizer() {
    }

    /**
     * 基于测量服务的便捷重载：用 {@link TextMeasureStyle#DEFAULT} 测量 UI 像素宽度。
     *
     * @param service    文本测量服务（非 null）
     * @param text       原始文本（可为 null）
     * @param maxWidthPx 目标 UI 像素宽度
     * @return 截断后的文本
     */
    public static String ellipsize(TextMeasureService service, String text, int maxWidthPx) {
        return ellipsize(service, text, maxWidthPx, TextMeasureStyle.DEFAULT);
    }

    /**
     * 基于测量服务 + 语义化样式的重载。
     *
     * @param service    文本测量服务（非 null）
     * @param text       原始文本（可为 null）
     * @param maxWidthPx 目标 UI 像素宽度
     * @param style      文本样式快照，null 时用 {@link TextMeasureStyle#DEFAULT}
     * @return 截断后的文本
     */
    public static String ellipsize(TextMeasureService service, String text, int maxWidthPx,
                                   TextMeasureStyle style) {
        Objects.requireNonNull(service, "service");
        TextMeasureStyle resolved = style == null ? TextMeasureStyle.DEFAULT : style;
        return ellipsize(t -> service.getStringWidth(t, resolved), text, maxWidthPx);
    }

    /**
     * 核心截断：按宽度函数把文本截断到 {@code maxWidthPx} 内并追加省略号。
     *
     * <p>宽度函数应满足「前缀单调」：{@code width(a) <= width(a + b)}（真实字体度量恒成立），
     * 这是二分搜索正确性的前提。</p>
     *
     * @param widthOf    文本 → UI 像素宽度的函数（非 null）
     * @param text       原始文本（可为 null）
     * @param maxWidthPx 目标 UI 像素宽度
     * @return 截断后的文本
     */
    public static String ellipsize(ToIntFunction<String> widthOf, String text, int maxWidthPx) {
        Objects.requireNonNull(widthOf, "widthOf");
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (maxWidthPx <= 0) {
            return "";
        }
        if (widthOf.applyAsInt(text) <= maxWidthPx) {
            return text;
        }
        int ellipsisWidth = widthOf.applyAsInt(ELLIPSIS);
        if (ellipsisWidth > maxWidthPx) {
            return "";
        }
        // 二分搜索最长前缀 p（可为空）满足 width(p) + ellipsisWidth <= maxWidthPx。
        // 前提：width("")=0 且 ellipsisWidth <= maxWidthPx，故 lo=0 恒满足条件。
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            int prefixWidth = widthOf.applyAsInt(text.substring(0, mid)) + ellipsisWidth;
            if (prefixWidth <= maxWidthPx) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return text.substring(0, lo) + ELLIPSIS;
    }

    /**
     * 多行换行 + 行数上限截断。
     *
     * <p>先按显式 {@code \n} 切段，再按 {@code maxWidthPx} 贪心换行；{@code maxLines > 0}
     * 且超出行数时截断，并在末行末尾强制追加省略号（即使末行本身放得下，省略号仍计入宽度，
     * 保证「还有更多」的视觉提示）。{@code maxWidthPx <= 0} 表示不换行（只保留显式换行切段），
     * 此模式下超行数只做纯截断不追加省略号。</p>
     *
     * @param widthOf    文本 → UI 像素宽度的函数（非 null）
     * @param text       原始文本（可为 null）
     * @param maxWidthPx 换行宽度（UI 像素），&lt;=0 表示不限宽
     * @param maxLines   最大行数，&lt;=0 表示不限
     * @return 换行后的行列表（null/空文本返回空列表）
     */
    public static List<String> wrapLines(ToIntFunction<String> widthOf, String text,
                                         int maxWidthPx, int maxLines) {
        return wrapLines(widthOf, text, maxWidthPx, maxLines, false);
    }

    /**
     * 多行换行 + 行数上限截断（可选「超宽词按码点折行」）。
     *
     * <p>{@code breakLongWords = false} 保持历史语义：无折行机会的超宽词（URL/哈希/base64）
     * 对该词做 ellipsis。<b>但 tooltip 的存在意义就是揭示被截断的内容</b>，把 URL 再截一次
     * 等于白做——需要完整展示的场景（链接 tooltip、路径提示）应传 true，让该词逐行切开。</p>
     *
     * @param widthOf         文本 → UI 像素宽度的函数（非 null）
     * @param text            原始文本（可为 null）
     * @param maxWidthPx      换行宽度（UI 像素），&lt;=0 表示不限宽
     * @param maxLines        最大行数，&lt;=0 表示不限
     * @param breakLongWords  true = 超宽词按码点折行；false = 超宽词 ellipsis（旧行为）
     * @return 换行后的行列表（null/空文本返回空列表）
     */
    public static List<String> wrapLines(ToIntFunction<String> widthOf, String text,
                                         int maxWidthPx, int maxLines, boolean breakLongWords) {
        Objects.requireNonNull(widthOf, "widthOf");
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        if (maxWidthPx <= 0) {
            for (String segment : splitExplicitLines(text)) {
                lines.add(segment);
            }
        } else {
            for (String segment : splitExplicitLines(text)) {
                wrapSegment(widthOf, segment, maxWidthPx, lines, breakLongWords);
            }
        }
        if (maxLines > 0 && lines.size() > maxLines) {
            lines.subList(maxLines, lines.size()).clear();
            if (maxWidthPx > 0) {
                int last = lines.size() - 1;
                lines.set(last, appendEllipsisAlways(widthOf, lines.get(last), maxWidthPx));
            }
        }
        return lines;
    }

    /** 截断标记：无论原文是否放得下，都取最长前缀并追加省略号（保证「还有更多」提示）。 */
    private static String appendEllipsisAlways(ToIntFunction<String> widthOf, String text,
                                               int maxWidthPx) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 防双省略号：原行已因单词超宽被 ellipsize 过时先剥掉尾缀
        if (text.endsWith(ELLIPSIS)) {
            text = text.substring(0, text.length() - ELLIPSIS.length());
        }
        int ellipsisWidth = widthOf.applyAsInt(ELLIPSIS);
        if (ellipsisWidth > maxWidthPx) {
            return "";
        }
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            int prefixWidth = widthOf.applyAsInt(text.substring(0, mid)) + ellipsisWidth;
            if (prefixWidth <= maxWidthPx) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return text.substring(0, lo) + ELLIPSIS;
    }

    /** 按 Unicode 换行类切段（保留尾随空段；{@code \r\n} 折叠为一个换行）。 */
    private static List<String> splitExplicitLines(String text) {
        List<String> segments = new ArrayList<>();
        int start = 0;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            if (UnicodeTextClassifier.isLineBreak(ch)) {
                if (ch == '\r' && i + 1 < len && text.charAt(i + 1) == '\n') {
                    i++;
                }
                segments.add(text.substring(start, i));
                start = i + 1;
            }
        }
        segments.add(text.substring(start));
        return segments;
    }

    /** 单个显式段落的贪心分词换行（Unicode 空白家族分词 + ZWSP/软连字符拆词 + 行尾抛光）。 */
    private static void wrapSegment(ToIntFunction<String> widthOf, String segment,
                                    int maxWidthPx, List<String> out, boolean breakLongWords) {
        String trimmed = stripUnicodeEdges(segment);
        if (trimmed.isEmpty()) {
            out.add("");
            return;
        }
        StringBuilder line = new StringBuilder();
        for (String rawWord : splitWords(trimmed)) {
            if (rawWord.isEmpty()) {
                continue;
            }
            boolean wordStart = true;
            for (String word : splitSoftBreaks(rawWord)) {
                appendWord(widthOf, word, maxWidthPx, line, out, wordStart, breakLongWords);
                wordStart = false;
            }
        }
        if (line.length() > 0) {
            out.add(polishLineTail(line));
        }
    }

    /** 追加一个词：软断行拆出的同词子词不带前导空格，跨词仍以单空格拼接。 */
    private static void appendWord(ToIntFunction<String> widthOf, String word, int maxWidthPx,
                                   StringBuilder line, List<String> out, boolean wordStart,
                                   boolean breakLongWords) {
        String candidate = line.length() == 0 ? word
                : line.toString() + (wordStart ? " " : "") + word;
        if (widthOf.applyAsInt(candidate) <= maxWidthPx) {
            line.setLength(0);
            line.append(candidate);
            return;
        }
        if (line.length() > 0) {
            out.add(polishLineTail(line));
            line.setLength(0);
        }
        if (widthOf.applyAsInt(word) > maxWidthPx) {
            if (!breakLongWords) {
                out.add(polishLineTail(new StringBuilder(ellipsize(widthOf, word, maxWidthPx))));
                return;
            }
            // 词比整行还宽(URL 这类零折行机会的词):逐行按码点切开。切开后的最后一段留在
            // line 里,后续词照常续排——否则会把"剩余部分"和下一个词硬拆成两行。
            int pos = 0;
            while (pos < word.length()) {
                int end = longestFitEnd(widthOf, word, pos, maxWidthPx);
                if (end >= word.length()) {
                    line.setLength(0);
                    line.append(word, pos, end);
                } else {
                    out.add(word.substring(pos, end));
                }
                pos = end;
            }
            return;
        }
        line.setLength(0);
        line.append(word);
    }

    /**
     * 从 {@code from} 起、在 {@code maxWidthPx} 内能放下的最多码点数对应的结束下标。
     *
     * <p>按码点计数二分(不是按 char),避免把代理对切两半;返回值恒 &gt; {@code from}
     * (单码点超宽时至少推进一个码点),故调用方的切分循环必然终止。</p>
     */
    private static int longestFitEnd(ToIntFunction<String> widthOf, String word, int from,
                                     int maxWidthPx) {
        int total = word.codePointCount(from, word.length());
        int lo = 0;
        int hi = total;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            int end = word.offsetByCodePoints(from, mid);
            if (widthOf.applyAsInt(word.substring(from, end)) <= maxWidthPx) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return word.offsetByCodePoints(from, Math.max(1, lo));
    }

    /** 按断词分隔（可断空格 + tab）切词；GL 胶水（NBSP 等）保留在词内不拆开。 */
    private static List<String> splitWords(String text) {
        List<String> words = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            int charCount = Character.charCount(codepoint);
            if (UnicodeTextClassifier.isWordBoundary(codepoint)) {
                if (start >= 0) {
                    words.add(text.substring(start, i));
                    start = -1;
                }
            } else if (start < 0) {
                start = i;
            }
            i += charCount;
        }
        if (start >= 0) {
            words.add(text.substring(start));
        }
        return words;
    }

    /** 按 ZWSP/软连字符拆词；断行机会保留为子词尾软连字符标记（词尾软连字符丢弃，不显示）。 */
    private static List<String> splitSoftBreaks(String word) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int i = 0;
        while (i < word.length()) {
            int codepoint = word.codePointAt(i);
            int codepointLength = Character.charCount(codepoint);
            if (UnicodeTextClassifier.isSoftBreakOpportunity(codepoint)) {
                boolean atEnd = i + codepointLength >= word.length();
                String part = word.substring(start, i);
                if (UnicodeTextClassifier.isSoftHyphen(codepoint) && !atEnd) {
                    part += "\u00AD";
                }
                if (!part.isEmpty()) {
                    parts.add(part);
                }
                start = i + codepointLength;
            }
            i += codepointLength;
        }
        if (start < word.length()) {
            parts.add(word.substring(start));
        }
        return parts;
    }

    /** 行尾抛光：剥尾部可折叠空白（CSS 口径：仅 U+0020/tab）与 ZWSP；软连字符替换为可见连字符（断行补字）。 */
    private static String polishLineTail(StringBuilder line) {
        while (line.length() > 0) {
            int codepoint = line.codePointBefore(line.length());
            int codepointLength = Character.charCount(codepoint);
            if (UnicodeTextClassifier.isTrailingFoldable(codepoint) || codepoint == 0x200B) {
                line.setLength(line.length() - codepointLength);
                continue;
            }
            if (UnicodeTextClassifier.isSoftHyphen(codepoint)) {
                line.setLength(line.length() - codepointLength);
                line.append('-');
                return line.toString();
            }
            break;
        }
        return line.toString();
    }

    /** 剥段首尾的断词空白与零宽字符（Unicode 空白家族统一口径）。 */
    private static String stripUnicodeEdges(String text) {
        int start = 0;
        int end = text.length();
        while (start < end) {
            int codepoint = text.codePointAt(start);
            if (UnicodeTextClassifier.isWordBoundary(codepoint) || UnicodeTextClassifier.isZeroWidth(codepoint)) {
                start += Character.charCount(codepoint);
            } else {
                break;
            }
        }
        while (end > start) {
            int codepoint = text.codePointBefore(end);
            if (UnicodeTextClassifier.isWordBoundary(codepoint) || UnicodeTextClassifier.isZeroWidth(codepoint)) {
                end -= Character.charCount(codepoint);
            } else {
                break;
            }
        }
        return text.substring(start, end);
    }
}
