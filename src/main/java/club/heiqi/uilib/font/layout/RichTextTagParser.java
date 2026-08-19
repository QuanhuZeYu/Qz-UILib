package club.heiqi.uilib.font.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import club.heiqi.uilib.font.FontType;

/**
 * UILib 现代富文本标签解析器 —— 纯解析核心，输出与字体布局服务同构的 {@link TextSegment} 序列。
 *
 * <h3>标签集</h3>
 * <ul>
 *   <li>{@code <color=#RRGGBB>} / {@code <color=#AARRGGBB>} / {@code <color=red>}（CSS 16 基础色名）</li>
 *   <li>{@code <b>} 粗体、{@code <i>} 斜体、{@code <u>} 下划线、{@code <s>} 删除线</li>
 *   <li>{@code <mark>} 行内高亮（默认 {@code #FFEB3B}，可 {@code <mark=#RRGGBB>} 自定义背景色）</li>
 *   <li>{@code <size=N>} 绝对像素字号（{@value #MIN_FONT_SIZE_PX}..{@value #MAX_FONT_SIZE_PX}，越界截断）</li>
 *   <li>{@code <br>} / {@code <br/>} 硬换行；闭合标签 {@code </name>} 或通用闭合 {@code </>}</li>
 * </ul>
 *
 * <h3>语义</h3>
 * <p>标签任意嵌套、样式继承父级；闭合后回退父样式。转义实体：{@code &lt;}、{@code &gt;}、{@code &amp;}。
 * 换行标签与裸换行符统一折叠为样式片段内的 {@code '\n'}（零宽，不产生字形）。</p>
 *
 * <h3>容错（现代组件惯例：宽容失败）</h3>
 * <ul>
 *   <li>未知标签原样保留为字面文本（含尖括号）；</li>
 *   <li>未闭合标签自动闭合到文本末尾；</li>
 *   <li>多余/错配闭合标签忽略（错配时吞掉中间已开样式）；</li>
 *   <li>坏属性（非法颜色/字号）忽略，文本继承父样式。</li>
 * </ul>
 *
 * <p>{@link #serialize} 是 {@link #parse} 的逆操作：把样式片段序列化成可再解析的标签文本，
 * 供换行/裁剪后重建行文本（行尾显式闭合、行首按样式差异自动重开，跨行样式续传零特判）。</p>
 */
public final class RichTextTagParser {

    /** 解析器接受的最大像素字号。 */
    public static final int MAX_FONT_SIZE_PX = 256;
    /** 解析器接受的最小像素字号。 */
    public static final int MIN_FONT_SIZE_PX = 1;
    /** {@code <mark>} 无值时的默认高亮背景色（Material Yellow 500）。 */
    public static final int DEFAULT_MARK_COLOR = 0xFFFFEB3B;

    private static final Map<String, Integer> NAMED_COLORS = createNamedColors();

    private RichTextTagParser() {
    }

    /**
     * 解析富文本标签为带样式的片段序列。
     *
     * @param text      富文本（可含标签与实体）
     * @param baseStyle 基准样式；为 null 时使用默认白色普通样式
     * @return 文本片段列表（空文本返回空列表）
     */
    public static List<TextSegment> parse(String text, TextStyle baseStyle) {
        List<TextSegment> segments = new ArrayList<TextSegment>();
        if (text == null || text.isEmpty()) {
            return segments;
        }
        TextStyle current = baseStyle == null ? createDefaultStyle() : baseStyle.copy();
        Deque<TagFrame> stack = new ArrayDeque<TagFrame>();
        StringBuilder builder = new StringBuilder();
        int index = 0;
        int length = text.length();
        while (index < length) {
            char ch = text.charAt(index);
            if (ch == '<') {
                TagMatch match = matchTag(text, index);
                if (match == null) {
                    builder.append(ch);
                    index++;
                    continue;
                }
                index = match.endIndex;
                if (match.lineBreak) {
                    builder.append('\n');
                    continue;
                }
                if (match.closing) {
                    TextStyle after = popMatching(stack, match.name);
                    if (after != null) {
                        flush(builder, current, segments);
                        current = after;
                    }
                    continue;
                }
                TextStyle next = applyTag(current, match);
                if (next != current) {
                    flush(builder, current, segments);
                    stack.push(new TagFrame(match.name, current));
                    current = next;
                }
                continue;
            }
            if (ch == '&') {
                int semicolon = text.indexOf(';', index + 1);
                if (semicolon > index + 1 && semicolon - index <= 8) {
                    String decoded = decodeEntity(text.substring(index + 1, semicolon));
                    if (decoded != null) {
                        builder.append(decoded);
                        index = semicolon + 1;
                        continue;
                    }
                }
                builder.append(ch);
                index++;
                continue;
            }
            builder.append(ch);
            index++;
        }
        flush(builder, current, segments);
        return segments;
    }

    /**
     * 把样式片段序列化成可再解析的标签文本（行尾显式闭合全部样式）。
     *
     * @param segments  文本片段列表
     * @param baseStyle 基准样式；为 null 时使用默认白色普通样式
     * @return 标签文本；空列表返回空串
     */
    public static String serialize(List<TextSegment> segments, TextStyle baseStyle) {
        if (segments == null || segments.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        TextStyle current = baseStyle == null ? createDefaultStyle() : baseStyle.copy();
        for (TextSegment segment : segments) {
            TextStyle next = segment.getStyle() == null ? current : segment.getStyle();
            appendStyleDiff(out, current, next);
            out.append(escapeText(segment.getText()));
            current = next.copy();
        }
        appendClosings(out, current);
        return out.toString();
    }

    // ==================== 标签匹配 ====================

    private static TagMatch matchTag(String text, int startIndex) {
        int endIndex = text.indexOf('>', startIndex + 1);
        if (endIndex < 0) {
            return null;
        }
        String raw = text.substring(startIndex + 1, endIndex);
        if (raw.isEmpty()) {
            return null;
        }
        if (raw.charAt(0) == '/') {
            String name = raw.substring(1).trim().toLowerCase();
            if (name.isEmpty()) {
                return TagMatch.closing("", endIndex + 1);
            }
            return isKnownTagName(name) ? TagMatch.closing(name, endIndex + 1) : null;
        }
        String trimmed = raw.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        int nameEnd = 0;
        while (nameEnd < trimmed.length()) {
            char ch = trimmed.charAt(nameEnd);
            if (ch == '=' || ch == ' ' || ch == '\t') {
                break;
            }
            nameEnd++;
        }
        if (nameEnd == 0) {
            return null;
        }
        String name = trimmed.substring(0, nameEnd).toLowerCase();
        if (!isKnownTagName(name)) {
            return null;
        }
        if ("br".equals(name)) {
            return TagMatch.lineBreak(endIndex + 1);
        }
        String value = parseValue(trimmed, nameEnd);
        return TagMatch.open(name, value, endIndex + 1);
    }

    private static String parseValue(String trimmed, int nameEnd) {
        int cursor = nameEnd;
        while (cursor < trimmed.length() && (trimmed.charAt(cursor) == ' ' || trimmed.charAt(cursor) == '\t')) {
            cursor++;
        }
        if (cursor < trimmed.length() && trimmed.charAt(cursor) == '=') {
            cursor++;
            while (cursor < trimmed.length() && (trimmed.charAt(cursor) == ' ' || trimmed.charAt(cursor) == '\t')) {
                cursor++;
            }
            return trimmed.substring(cursor).trim();
        }
        return cursor < trimmed.length() ? trimmed.substring(cursor).trim() : "";
    }

    private static boolean isKnownTagName(String name) {
        return "color".equals(name) || "b".equals(name) || "i".equals(name) || "u".equals(name)
                || "s".equals(name) || "br".equals(name) || "size".equals(name) || "mark".equals(name);
    }

    private static String decodeEntity(String name) {
        if ("lt".equals(name)) {
            return "<";
        }
        if ("gt".equals(name)) {
            return ">";
        }
        if ("amp".equals(name)) {
            return "&";
        }
        return null;
    }

    // ==================== 样式应用 ====================

    private static TextStyle applyTag(TextStyle current, TagMatch match) {
        if ("b".equals(match.name)) {
            if (current.getFontType() == FontType.BOLD) {
                return current;
            }
            TextStyle next = current.copy();
            next.setFontType(FontType.BOLD);
            return next;
        }
        if ("i".equals(match.name)) {
            if (current.isItalic()) {
                return current;
            }
            TextStyle next = current.copy();
            next.setItalic(true);
            return next;
        }
        if ("u".equals(match.name)) {
            if (current.isUnderline()) {
                return current;
            }
            TextStyle next = current.copy();
            next.setUnderline(true);
            return next;
        }
        if ("s".equals(match.name)) {
            if (current.isStrikethrough()) {
                return current;
            }
            TextStyle next = current.copy();
            next.setStrikethrough(true);
            return next;
        }
        if ("color".equals(match.name)) {
            Integer color = parseColor(match.value);
            if (color == null) {
                return current;
            }
            TextStyle next = current.copy();
            next.setColor(color.intValue());
            return next;
        }
        if ("size".equals(match.name)) {
            Integer size = parseSize(match.value);
            if (size == null) {
                return current;
            }
            TextStyle next = current.copy();
            next.setFontSizePx(size.intValue());
            return next;
        }
        if ("mark".equals(match.name)) {
            int markColor = DEFAULT_MARK_COLOR;
            if (match.value != null && !match.value.trim().isEmpty()) {
                Integer parsed = parseColor(match.value);
                if (parsed == null) {
                    return current;
                }
                markColor = parsed.intValue();
            }
            if (current.getMarkColor() == markColor) {
                return current;
            }
            TextStyle next = current.copy();
            next.setMarkColor(markColor);
            return next;
        }
        return current;
    }

    private static Integer parseColor(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("#")) {
            String hex = trimmed.substring(1);
            try {
                if (hex.length() == 6) {
                    return Integer.valueOf(0xFF000000 | Integer.parseInt(hex, 16));
                }
                if (hex.length() == 8) {
                    return Integer.valueOf((int) Long.parseLong(hex, 16));
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
            return null;
        }
        return NAMED_COLORS.get(trimmed.toLowerCase());
    }

    private static Integer parseSize(String value) {
        if (value == null) {
            return null;
        }
        try {
            int size = Integer.parseInt(value.trim());
            return Integer.valueOf(Math.max(MIN_FONT_SIZE_PX, Math.min(MAX_FONT_SIZE_PX, size)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static TextStyle popMatching(Deque<TagFrame> stack, String name) {
        if (name.isEmpty()) {
            TagFrame top = stack.poll();
            return top == null ? null : top.style;
        }
        TagFrame matched = null;
        while (!stack.isEmpty()) {
            TagFrame frame = stack.poll();
            if (frame.name.equals(name)) {
                matched = frame;
                break;
            }
        }
        return matched == null ? null : matched.style;
    }

    private static void flush(StringBuilder builder, TextStyle style, List<TextSegment> segments) {
        if (builder.length() == 0) {
            return;
        }
        segments.add(new TextSegment(builder.toString(), style.copy()));
        builder.setLength(0);
    }

    // ==================== 序列化 ====================

    private static void appendStyleDiff(StringBuilder out, TextStyle current, TextStyle next) {
        // 阶段一：先关闭全部退出的样式（逆序，最内层先关）。
        // 阶段二：再打开全部新样式。先关后开保证诸如 </color><size=N> 的序列在容错解析下
        // 不会让外层闭合误弹刚压栈的内层帧。
        if (current.getMarkColor() != 0 && current.getMarkColor() != next.getMarkColor()) {
            out.append("</mark>");
        }
        if (current.getFontSizePx() > 0 && current.getFontSizePx() != next.getFontSizePx()) {
            out.append("</size>");
        }
        if (current.isStrikethrough() && !next.isStrikethrough()) {
            out.append("</s>");
        }
        if (current.isUnderline() && !next.isUnderline()) {
            out.append("</u>");
        }
        if (current.isItalic() && !next.isItalic()) {
            out.append("</i>");
        }
        if (current.getFontType() == FontType.BOLD && next.getFontType() != FontType.BOLD) {
            out.append("</b>");
        }
        if (current.isColorExplicit() && !next.isColorExplicit()) {
            out.append("</color>");
        }
        if (current.isColorExplicit() && next.isColorExplicit() && current.getColor() != next.getColor()) {
            out.append("</color>");
        }
        if (next.isColorExplicit() && (!current.isColorExplicit() || current.getColor() != next.getColor())) {
            out.append("<color=#").append(String.format("%08X", Integer.valueOf(next.getColor()))).append('>');
        }
        if (next.getFontType() == FontType.BOLD && current.getFontType() != FontType.BOLD) {
            out.append("<b>");
        }
        if (next.isItalic() && !current.isItalic()) {
            out.append("<i>");
        }
        if (next.isUnderline() && !current.isUnderline()) {
            out.append("<u>");
        }
        if (next.isStrikethrough() && !current.isStrikethrough()) {
            out.append("<s>");
        }
        if (next.getFontSizePx() > 0 && next.getFontSizePx() != current.getFontSizePx()) {
            out.append("<size=").append(next.getFontSizePx()).append('>');
        }
        if (next.getMarkColor() != 0 && next.getMarkColor() != current.getMarkColor()) {
            if (next.getMarkColor() == DEFAULT_MARK_COLOR) {
                out.append("<mark>");
            } else {
                out.append("<mark=#").append(String.format("%08X", Integer.valueOf(next.getMarkColor())))
                        .append('>');
            }
        }
    }

    private static void appendClosings(StringBuilder out, TextStyle style) {
        if (style.getMarkColor() != 0) {
            out.append("</mark>");
        }
        if (style.getFontSizePx() > 0) {
            out.append("</size>");
        }
        if (style.isStrikethrough()) {
            out.append("</s>");
        }
        if (style.isUnderline()) {
            out.append("</u>");
        }
        if (style.isItalic()) {
            out.append("</i>");
        }
        if (style.getFontType() == FontType.BOLD) {
            out.append("</b>");
        }
        if (style.isColorExplicit()) {
            out.append("</color>");
        }
    }

    private static String escapeText(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '<') {
                out.append("&lt;");
            } else if (ch == '>') {
                out.append("&gt;");
            } else if (ch == '&') {
                out.append("&amp;");
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static TextStyle createDefaultStyle() {
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        return style;
    }

    private static Map<String, Integer> createNamedColors() {
        Map<String, Integer> colors = new HashMap<String, Integer>();
        colors.put("black", Integer.valueOf(0xFF000000));
        colors.put("silver", Integer.valueOf(0xFFC0C0C0));
        colors.put("gray", Integer.valueOf(0xFF808080));
        colors.put("white", Integer.valueOf(0xFFFFFFFF));
        colors.put("maroon", Integer.valueOf(0xFF800000));
        colors.put("red", Integer.valueOf(0xFFFF0000));
        colors.put("purple", Integer.valueOf(0xFF800080));
        colors.put("fuchsia", Integer.valueOf(0xFFFF00FF));
        colors.put("green", Integer.valueOf(0xFF008000));
        colors.put("lime", Integer.valueOf(0xFF00FF00));
        colors.put("olive", Integer.valueOf(0xFF808000));
        colors.put("yellow", Integer.valueOf(0xFFFFFF00));
        colors.put("navy", Integer.valueOf(0xFF000080));
        colors.put("blue", Integer.valueOf(0xFF0000FF));
        colors.put("teal", Integer.valueOf(0xFF008080));
        colors.put("aqua", Integer.valueOf(0xFF00FFFF));
        return colors;
    }

    /** 标签匹配结果。 */
    private static final class TagMatch {

        /** 小写标签名；通用闭合 {@code </>} 时为 {@code ""}。 */
        private final String name;
        /** 标签值；无值标签为 {@code ""}。 */
        private final String value;
        /** {@code '>'} 之后的索引。 */
        private final int endIndex;
        /** 是否闭合标签。 */
        private final boolean closing;
        /** 是否换行标签。 */
        private final boolean lineBreak;

        private TagMatch(String name, String value, int endIndex, boolean closing, boolean lineBreak) {
            this.name = name;
            this.value = value;
            this.endIndex = endIndex;
            this.closing = closing;
            this.lineBreak = lineBreak;
        }

        private static TagMatch open(String name, String value, int endIndex) {
            return new TagMatch(name, value, endIndex, false, false);
        }

        private static TagMatch closing(String name, int endIndex) {
            return new TagMatch(name, "", endIndex, true, false);
        }

        private static TagMatch lineBreak(int endIndex) {
            return new TagMatch("br", "", endIndex, false, true);
        }
    }

    /** 标签栈帧：标签名与进入标签前的父样式。 */
    private static final class TagFrame {

        private final String name;
        private final TextStyle style;

        private TagFrame(String name, TextStyle style) {
            this.name = name;
            this.style = style;
        }
    }
}
