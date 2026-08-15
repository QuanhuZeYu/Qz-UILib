package club.heiqi.uilib.ui.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

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
 * <p>先按显式 {@code \n} 切段，再按空白分词贪心换行；单词超宽时对该词做 ellipsis；
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
                wrapSegment(widthOf, segment, maxWidthPx, lines);
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

    /** 按显式换行符切段（保留尾随空段）。 */
    private static List<String> splitExplicitLines(String text) {
        List<String> segments = new ArrayList<>();
        int start = 0;
        int len = text.length();
        for (int i = 0; i <= len; i++) {
            if (i == len || text.charAt(i) == '\n') {
                segments.add(text.substring(start, i));
                start = i + 1;
            }
        }
        return segments;
    }

    /** 单个显式段落的贪心分词换行。 */
    private static void wrapSegment(ToIntFunction<String> widthOf, String segment,
                                    int maxWidthPx, List<String> out) {
        String trimmed = segment.trim();
        if (trimmed.isEmpty()) {
            out.add("");
            return;
        }
        StringBuilder line = new StringBuilder();
        for (String word : trimmed.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (widthOf.applyAsInt(candidate) <= maxWidthPx) {
                line = new StringBuilder(candidate);
                continue;
            }
            if (line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder();
            }
            if (widthOf.applyAsInt(word) > maxWidthPx) {
                out.add(ellipsize(widthOf, word, maxWidthPx));
            } else {
                line = new StringBuilder(word);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
    }
}
