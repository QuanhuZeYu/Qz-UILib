package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/**
 * 聊天 URL 自动链接化（纯 JVM，无 MC/GL 依赖）。
 *
 * <p>chat3 渲染链的段解析（MINECRAFT_FORMATTED）只产 § 样式码段，聊天文本自身不带
 * {@code <a=...>} 标签（链接只在原版 {@code IChatComponent} 的 ChatStyle clickEvent 上，
 * 文本中无标记）。设计稿 §3.5/§5.2 的链接语义（默认色 0xFF7AB8F5、hover 提亮 + 下划线 +
 * 手型 + tooltip）需要一个可见的链接段来源：本类把行文本段流中的 URL 子串切成
 * {@code TextStyle.link} 段，作为链接 hover 的唯一输入。</p>
 *
 * <p>语义（对齐设计稿 §5.2）：</p>
 * <ul>
 *   <li>识别前缀：{@code http://}、{@code https://}、{@code www.}（大小写不敏感）；</li>
 *   <li>URL 体 = 首个空白/中英文标点前的连续文本；尾随标点（ASCII 与常见中文标点）
 *       逐个剥离，剥离后为空视为非链接；</li>
 *   <li>链接段默认色 = 注入 {@code linkColor}（设计 0xFF7AB8F5），<b>默认无下划线</b>
 *       （设计稿 §3.5：链接默认无下划线）；</li>
 *   <li>LaTeX 段与不含 URL 的段原样透传（零拷贝）；T6b 起 code 段（{@code codeSpan}
 *       标记）同 LaTeX 段处理——code 是文本语义边界，不嵌套链接化；</li>
 *   <li>URL 不跨段识别（样式边界内）：聊天消息中 URL 中间出现 § 码极其罕见，跨段会引入
 *       段落拼接复杂度，当前不处理。</li>
 * </ul>
 */
public final class ChatUrlLinkifier {

    /** 链接命中区扩展（设计稿 §5.2）：文本包围盒上下各扩 2px。 */
    public static final int HIT_PAD_Y = 2;
    /** 链接命中区扩展（设计稿 §5.2）：文本包围盒左右各扩 1px。 */
    public static final int HIT_PAD_X = 1;

    /** ASCII 尾随标点（逐个剥离）。 */
    private static final String TRAILING_ASCII = ".,;:!?'\")]}>&";
    /** 中文尾随标点（逐个剥离）。 */
    private static final String TRAILING_CJK = "，。；：！？、」』】》〉’‘“”…·";

    private ChatUrlLinkifier() {
    }

    /** 前缀匹配结果：URL 起点索引（原始文本内）与剥离标点后的 url 值。 */
    static final class Match {
        final int start;
        final int end;
        final String url;

        Match(int start, int end, String url) {
            this.start = start;
            this.end = end;
            this.url = url;
        }
    }

    /**
     * 把段流中的 URL 子串切成 link 段（颜色 = {@code linkColor}，默认无下划线）。
     *
     * <p>输入段流应为 § 解析后的纯文本样式段（text 不含格式码）；不含任何链接的段流原列表
     * 引用返回（零分配）。</p>
     *
     * @param base      基础段流（不可变语义，本函数不改写输入段）
     * @param linkColor 链接默认色（ARGB，设计 0xFF7AB8F5）
     * @return 链接化后的段流（无 URL 时同引用）
     */
    public static List<TextSegment> linkify(List<TextSegment> base, int linkColor) {
        if (base == null || base.isEmpty()) {
            return base;
        }
        List<TextSegment> out = null;
        for (TextSegment segment : base) {
            if (segment.isLatex()) {
                if (out != null) {
                    out.add(segment);
                }
                continue;
            }
            // T6b:code 段是文本语义边界,不嵌套链接化(与 LaTeX 段同级处理;
            // 解析序 = 先 code 切分后 linkify,URL 扫描不得吞掉反引号标记)。
            if (segment.getStyle().isCodeSpan()) {
                if (out != null) {
                    out.add(segment);
                }
                continue;
            }
            List<Match> matches = findUrls(segment.getText());
            if (matches.isEmpty()) {
                if (out != null) {
                    out.add(segment);
                }
                continue;
            }
            if (out == null) {
                out = new ArrayList<TextSegment>(base.size() + matches.size());
                for (TextSegment pre : base) {
                    if (pre == segment) {
                        break;
                    }
                    out.add(pre);
                }
            }
            appendUrlSplit(out, segment, matches, linkColor);
        }
        return out == null ? base : out;
    }

    /**
     * 链接 hover 变体：link 段 → hover 提亮色 + 下划线（设计稿 §3.5）；非 link 段原引用透传。
     *
     * @param base       链接化段流（{@link #linkify} 产物）
     * @param hoverColor hover 提亮色（ARGB，设计 0xFF9CCBF8）
     * @return hover 段流（无 link 段时同引用）
     */
    public static List<TextSegment> hoverLinkify(List<TextSegment> base, int hoverColor) {
        if (base == null || base.isEmpty()) {
            return base;
        }
        List<TextSegment> out = null;
        int index = 0;
        for (TextSegment segment : base) {
            if (segment.getStyle().getLink() == null) {
                if (out != null) {
                    out.add(segment);
                }
                index++;
                continue;
            }
            if (out == null) {
                out = new ArrayList<TextSegment>(base.size());
                out.addAll(base.subList(0, index));
            }
            TextStyle style = segment.getStyle().copy();
            style.setColor(hoverColor);
            style.setUnderline(true);
            out.add(new TextSegment(segment.getText(), style));
            index++;
        }
        return out == null ? base : out;
    }

    /**
     * 扫描文本中的 URL 子串（前缀 + 分隔符终止 + 尾随标点剥离）。
     *
     * @param text 纯文本（不含 § 格式码）
     * @return 匹配列表（按出现顺序，互不重叠）
     */
    static List<Match> findUrls(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        List<Match> matches = null;
        int index = 0;
        int length = text.length();
        while (index < length) {
            int start = indexOfScheme(text, index);
            if (start < 0) {
                break;
            }
            int end = scanUrlEnd(text, start);
            if (end > start) {
                String url = text.substring(start, end);
                int trimmedEnd = stripTrailingPunctuation(url);
                if (trimmedEnd > 0) {
                    if (matches == null) {
                        matches = new ArrayList<Match>(2);
                    }
                    matches.add(new Match(start, start + trimmedEnd, url.substring(0, trimmedEnd)));
                }
                index = end;
            } else {
                index = start + 1;
            }
        }
        return matches == null ? Collections.<Match>emptyList() : matches;
    }

    /** 找前缀起点（http:// / https:// / www.，大小写不敏感）。 */
    private static int indexOfScheme(String text, int from) {
        int length = text.length();
        for (int i = from; i < length; i++) {
            char c = text.charAt(i);
            if (c == 'h' || c == 'H') {
                if (startsWithIgnoreCase(text, i, "http://") || startsWithIgnoreCase(text, i, "https://")) {
                    return i;
                }
            } else if (c == 'w' || c == 'W') {
                if (startsWithIgnoreCase(text, i, "www.")) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean startsWithIgnoreCase(String text, int offset, String prefix) {
        if (offset + prefix.length() > text.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            char a = text.charAt(offset + i);
            char b = prefix.charAt(i);
            if (Character.toLowerCase(a) != Character.toLowerCase(b)) {
                return false;
            }
        }
        return true;
    }

    /** URL 体终点：首个空白或中英文分隔标点（内部括号内容保留，尾随剥离单独处理）。 */
    private static int scanUrlEnd(String text, int start) {
        int index = start;
        int length = text.length();
        while (index < length) {
            char c = text.charAt(index);
            if (Character.isWhitespace(c) || isDelimiter(c)) {
                break;
            }
            index++;
        }
        return index;
    }

    /**
     * 分隔标点：除空白外的强分隔符。URL 内部常见字符（? ! : . 查询串/端口/路径）刻意放行，
     * 由 {@link #stripTrailingPunctuation} 兜底剥离尾部标点——否则查询串/端口会被截断
     * （首版实测 "http://a.com/b?p=1" 在 '?' 处中断）；逗号/分号/括号/引号/中文句读是
     * 聊天文本中最常见的"URL 紧邻标点"，按终止处理。
     */
    private static boolean isDelimiter(char c) {
        switch (c) {
            case ',': case ';':
            case '，': case '。': case '；': case '、':
            case '(': case ')': case '[': case ']': case '{': case '}':
            case '（': case '）': case '【': case '】': case '《': case '》':
            case '\'': case '"': case '‘': case '’': case '“': case '”':
                return true;
            default:
                return false;
        }
    }

    /** 剥离尾随标点（URL 内部允许括号内容，只剥离结尾纯标点尾巴）。 */
    private static int stripTrailingPunctuation(String url) {
        int end = url.length();
        while (end > 0) {
            char c = url.charAt(end - 1);
            boolean ascii = TRAILING_ASCII.indexOf(c) >= 0;
            boolean cjk = TRAILING_CJK.indexOf(c) >= 0;
            if (!ascii && !cjk) {
                break;
            }
            end--;
        }
        return end;
    }

    /** 按匹配切分单段：非 link 文本原 style，link 文本换 link 色 + setLink。 */
    private static void appendUrlSplit(List<TextSegment> out, TextSegment segment,
            List<Match> matches, int linkColor) {
        String text = segment.getText();
        TextStyle baseStyle = segment.getStyle();
        int cursor = 0;
        for (Match match : matches) {
            if (match.start > cursor) {
                out.add(new TextSegment(text.substring(cursor, match.start), baseStyle));
            }
            TextStyle linkStyle = baseStyle.copy();
            linkStyle.setColor(linkColor);
            linkStyle.setLink(match.url);
            out.add(new TextSegment(text.substring(match.start, match.end), linkStyle));
            cursor = match.end;
        }
        if (cursor < text.length()) {
            out.add(new TextSegment(text.substring(cursor), baseStyle));
        }
    }
}
