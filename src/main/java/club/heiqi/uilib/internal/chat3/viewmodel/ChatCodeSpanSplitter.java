package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/**
 * 行内 code 切分（设计稿 §3.5，T6b：反引号对 → 衬底 code 段；纯 JVM，无 MC/GL 依赖）。
 *
 * <p>chat3 渲染链的段解析（MINECRAFT_FORMATTED）只产 § 样式码段；反引号对是设计稿
 * 行内 code 的轻量行级标记。本类把段流文本中的反引号配对子串切成
 * {@code TextStyle.codeSpan} 段，渲染侧据此补 0x26FFFFFF 衬底矩形。</p>
 *
 * <p>语义：</p>
 * <ul>
 *   <li>反引号对 `code` → code 子串切成 code 段（{@code codeSpan=true} +
 *       注入 {@code codeBackgroundColor}）；</li>
 *   <li>单反引号/未闭合 → 按字面透传（不切分）；</li>
 *   <li>code 段内不嵌套解析：配对内部出现的反引号视为 code 内容（不参与再次配对），
 *       且 code 段清除 {@link TextStyle#getLink() link} 语义（code 内 URL 不嵌套成链接）；</li>
 *   <li>空配对（``，两反引号紧贴）按字面透传（无内容可衬底）；</li>
 *   <li>LaTeX 段与不含反引号对的段原样透传（零拷贝）；</li>
 *   <li>反引号对不跨段识别（样式边界内，与 {@link ChatUrlLinkifier} 的 URL 同规则）。</li>
 * </ul>
 */
public final class ChatCodeSpanSplitter {

    private ChatCodeSpanSplitter() {
    }

    /**
     * 把段流中的反引号对切成 code 段（衬底色 = {@code codeBackgroundColor}）。
     *
     * <p>输入段流应为 § 解析后的纯文本样式段；不含任何 code 的段流原列表引用返回
     * （零分配）。</p>
     *
     * @param base               基础段流（不可变语义，本函数不改写输入段）
     * @param codeBackgroundColor code 衬底背景色（ARGB，设计 0x26FFFFFF）
     * @return code 切分后的段流（无 code 时同引用）
     */
    public static List<TextSegment> split(List<TextSegment> base, int codeBackgroundColor) {
        if (base == null || base.isEmpty()) {
            return base;
        }
        List<TextSegment> out = null;
        for (TextSegment segment : base) {
            List<TextSegment> pieces = splitSegment(segment, codeBackgroundColor);
            if (pieces == null) {
                if (out != null) {
                    out.add(segment);
                }
                continue;
            }
            if (out == null) {
                out = new ArrayList<TextSegment>(base.size() + 2);
                for (TextSegment pre : base) {
                    if (pre == segment) {
                        break;
                    }
                    out.add(pre);
                }
            }
            out.addAll(pieces);
        }
        return out == null ? base : out;
    }

    /**
     * 单段反引号对切分。
     *
     * @return code 切分后的段（无反引号对 = null，调用方透传原段）
     */
    private static List<TextSegment> splitSegment(TextSegment segment, int codeBackgroundColor) {
        if (segment.isLatex()) {
            return null;
        }
        String text = segment.getText();
        TextStyle style = segment.getStyle();
        List<TextSegment> out = null;
        int cursor = 0;
        int index = 0;
        int length = text.length();
        while (index < length) {
            int open = text.indexOf('`', index);
            if (open < 0) {
                break;
            }
            int close = text.indexOf('`', open + 1);
            if (close < 0) {
                // 未闭合：剩余全部按字面（不再寻找其他配对）
                break;
            }
            index = close + 1;
            if (close == open + 1) {
                // 空配对：无内容可衬底，按字面跳过
                continue;
            }
            if (out == null) {
                out = new ArrayList<TextSegment>(3);
            }
            if (open > cursor) {
                out.add(new TextSegment(text.substring(cursor, open), style));
            }
            TextStyle codeStyle = style.copy();
            codeStyle.setCodeSpan(true);
            codeStyle.setCodeBackgroundColor(codeBackgroundColor);
            // code 段内不嵌套解析：URL 链接语义交由 linkify 先行，这里必须清掉，
            // 否则 linkify 产出的 link 段被切半后 code 子段残留 link → 可 hover 的 code。
            codeStyle.setLink(null);
            out.add(new TextSegment(text.substring(open + 1, close), codeStyle));
            cursor = close + 1;
        }
        if (out == null) {
            return null;
        }
        if (cursor < length) {
            out.add(new TextSegment(text.substring(cursor), style));
        }
        return out;
    }
}
