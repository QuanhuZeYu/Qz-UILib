package club.heiqi.uilib.ui.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;

/**
 * 段流视觉行布局器（包内实现，非公共面；对外只有 {@link MarkdownPainter} 门面）。
 *
 * <p>切分行为规格承接 chat3 行切分器既有真机裁定（K3 系列修复，规划 §六 2）：
 * {@code \n} 硬断；空白是断行机会且行尾空白丢弃；超宽先在最后一个词边界回退
 * （词整体移入续行，英文词内不硬断）；无空格长串（URL/哈希）才字符级硬断；
 * 行内尚无可见内容时强制放置首 token（不产零内容空行）；公式段是不可拆原子。
 * 与 chat3 的差异仅在输入形态：这里吃带样式的段流而非 § 格式码文本，样式随段
 * 天然续传，无需行首重发格式码。</p>
 *
 * <p>推进宽度恒走 {@link TextLayoutService#resolveAdvance(int, TextStyle, int)} 与
 * {@link TextLayoutService#getSegmentWidth(TextSegment, int)}（测量/渲染唯一同源原语，
 * G4），本类零自设字号、字宽与行高常量。纯 JVM：不 import Minecraft/AWT，不发 GL 调用。</p>
 */
final class MarkdownLineLayout {

    /** 布局 token：一个不可再断的最小放置单元（文本码点 / 空白串 / 整条公式段）。 */
    private static final class Token {

        /** 来源段（materialize 时按 [start,end) 切子串；公式段整段透传）。 */
        final TextSegment source;
        /** 文本 token 的起始字符下标（含）。 */
        final int start;
        /** 文本 token 的结束字符下标（不含）。 */
        final int end;
        /** 推进宽度（UI 像素，double 累计避免逐格取整漂移）。 */
        final double advance;
        /** 是否空白 token（断行机会，行尾可丢）。 */
        final boolean whitespace;
        /** 是否公式原子 token（不可拆，substring 无意义）。 */
        final boolean latex;

        Token(TextSegment source, int start, int end, double advance, boolean whitespace, boolean latex) {
            this.source = source;
            this.start = start;
            this.end = end;
            this.advance = advance;
            this.whitespace = whitespace;
            this.latex = latex;
        }
    }

    private MarkdownLineLayout() {
    }

    // ==================== 入口 ====================

    static List<List<TextSegment>> wrap(List<TextSegment> segments, TextLayoutService measurer,
            int maxWidthPx, int baseFontSizePx) {
        requireMeasurer(measurer);
        List<List<Token>> logicalTokens = splitLogicalLines(segments, measurer, baseFontSizePx);
        List<List<TextSegment>> out = new ArrayList<List<TextSegment>>();
        for (int i = 0; i < logicalTokens.size(); i++) {
            wrapVisualLine(logicalTokens.get(i), maxWidthPx, out, baseFontSizePx);
        }
        return Collections.unmodifiableList(out);
    }

    static List<PaintCommand> toCommands(List<TextSegment> segments, TextLayoutService measurer,
            int maxWidthPx, int baseFontSizePx) {
        requireMeasurer(measurer);
        List<List<TextSegment>> lines = wrap(segments, measurer, maxWidthPx, baseFontSizePx);
        List<PaintCommand> out = new ArrayList<PaintCommand>();
        int cursorY = 0;
        for (int i = 0; i < lines.size(); i++) {
            List<TextSegment> line = lines.get(i);
            int height = lineHeightPx(line, measurer, baseFontSizePx);
            if (!line.isEmpty()) {
                out.add(PaintCommand.segments(line, 0, cursorY, Math.max(1, baseFontSizePx)));
                appendLinkRegions(out, line, measurer, baseFontSizePx, cursorY, height);
            }
            cursorY += height;
        }
        return Collections.unmodifiableList(out);
    }

    static int measureHeight(List<TextSegment> segments, TextLayoutService measurer,
            int maxWidthPx, int baseFontSizePx) {
        List<List<TextSegment>> lines = wrap(segments, measurer, maxWidthPx, baseFontSizePx);
        int total = 0;
        for (int i = 0; i < lines.size(); i++) {
            total += lineHeightPx(lines.get(i), measurer, baseFontSizePx);
        }
        return total;
    }

    // ==================== 换行 ====================

    /** 段流 → 逻辑行 token 序列（按 {@code \n} 硬断，含段内嵌 {@code \n}）。 */
    private static List<List<Token>> splitLogicalLines(List<TextSegment> segments,
            TextLayoutService measurer, int baseFontSizePx) {
        List<List<Token>> lines = new ArrayList<List<Token>>();
        List<Token> current = new ArrayList<Token>();
        if (segments == null || segments.isEmpty()) {
            lines.add(current);
            return lines;
        }
        for (int s = 0; s < segments.size(); s++) {
            TextSegment segment = segments.get(s);
            if (segment == null) {
                continue;
            }
            if (segment.isLatex()) {
                current.add(new Token(segment, 0, 0,
                        measurer.getSegmentWidth(segment, Math.max(1, baseFontSizePx)), false, true));
                continue;
            }
            String text = segment.getText();
            int index = 0;
            while (index < text.length()) {
                char ch = text.charAt(index);
                if (ch == '\n') {
                    lines.add(current);
                    current = new ArrayList<Token>();
                    index++;
                    continue;
                }
                int end = text.offsetByCodePoints(index, 1);
                int codepoint = text.codePointAt(index);
                double advance = measurer.resolveAdvance(codepoint, segment.getStyle(), baseFontSizePx);
                if (Character.isWhitespace(codepoint)) {
                    // 同一源段内相邻空白聚合成一个 token（断行机会整体处理，行尾整丢）
                    Token last = current.isEmpty() ? null : current.get(current.size() - 1);
                    if (last != null && last.whitespace && last.source == segment && last.end == index) {
                        current.set(current.size() - 1, new Token(segment, last.start, end,
                                last.advance + advance, true, false));
                    } else {
                        current.add(new Token(segment, index, end, advance, true, false));
                    }
                } else {
                    current.add(new Token(segment, index, end, advance, false, false));
                }
                index = end;
            }
        }
        lines.add(current);
        return lines;
    }

    /** 单逻辑行 → 视觉行（词边界回退 + 无空格硬断，K3 语义），materialize 后追加到 out。 */
    private static void wrapVisualLine(List<Token> tokens, int maxWidthPx, List<List<TextSegment>> out,
            int baseFontSizePx) {
        List<Token> line = new ArrayList<Token>();
        List<Token> pending = new ArrayList<Token>();
        double lineWidth = 0.0D;
        double pendingWidth = 0.0D;
        int visibleCount = 0;
        int wsRunStart = -1; // 行内最后一个已并入空白段的起点（-1 = 无）
        int wsRunEnd = -1;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.whitespace) {
                pending.add(token);
                pendingWidth += token.advance;
                continue;
            }
            double candidate = lineWidth + pendingWidth + token.advance;
            if (maxWidthPx <= 0 || visibleCount == 0 || candidate <= (double) maxWidthPx) {
                if (!pending.isEmpty()) {
                    wsRunStart = line.size();
                    wsRunEnd = line.size() + pending.size() - 1;
                    line.addAll(pending);
                    pending.clear();
                    lineWidth += pendingWidth;
                    pendingWidth = 0.0D;
                }
                line.add(token);
                lineWidth = candidate;
                visibleCount++;
                continue;
            }
            // 超宽：先词边界回退（空白前有可见内容才允许——否则会把前导缩进断成零内容空行，K3）
            if (wsRunStart > 0) {
                out.add(materialize(line.subList(0, wsRunStart), baseFontSizePx));
                List<Token> rest = new ArrayList<Token>(line.subList(wsRunEnd + 1, line.size()));
                double restWidth = suffixWidth(line, wsRunEnd + 1);
                line = new ArrayList<Token>(rest);
                if (pending.isEmpty()) {
                    // 断行机会已被上一次回退消耗：续行只剩可见 token，退回硬断路径
                    wsRunStart = -1;
                    wsRunEnd = -1;
                } else {
                    wsRunStart = line.size();
                    wsRunEnd = line.size() + pending.size() - 1;
                    line.addAll(pending);
                }
                line.add(token);
                lineWidth = restWidth + pendingWidth + token.advance;
                pending.clear();
                pendingWidth = 0.0D;
                visibleCount++;
                continue;
            }
            // 无空格可退：字符级硬断（visibleCount==0 时上面已强制放置，此处行内必有可见内容）
            out.add(materialize(line, baseFontSizePx));
            line = new ArrayList<Token>();
            lineWidth = 0.0D;
            visibleCount = 0;
            wsRunStart = -1;
            wsRunEnd = -1;
            pending.clear();
            pendingWidth = 0.0D;
            i--; // 重试本 token（空行强制放置路径下一轮命中 visibleCount==0 分支）
        }
        if (!line.isEmpty()) {
            out.add(materialize(line, baseFontSizePx));
        } else if (out.isEmpty()) {
            // 全空白/空逻辑行 → 单空行（与 chat3「空文本 → 单空行」一致）
            out.add(Collections.<TextSegment>emptyList());
        }
    }

    private static double suffixWidth(List<Token> line, int from) {
        double width = 0.0D;
        for (int i = from; i < line.size(); i++) {
            width += line.get(i).advance;
        }
        return width;
    }

    /** token 区间 → 段流：同源段连续文本区间合并子串（样式实例共享），公式段透传原实例。 */
    private static List<TextSegment> materialize(List<Token> tokens, int baseFontSizePx) {
        List<TextSegment> out = new ArrayList<TextSegment>();
        int index = 0;
        while (index < tokens.size()) {
            Token token = tokens.get(index);
            if (token.latex) {
                out.add(token.source);
                index++;
                continue;
            }
            int start = token.start;
            int end = token.end;
            TextSegment source = token.source;
            int next = index + 1;
            while (next < tokens.size()) {
                Token follow = tokens.get(next);
                if (follow.latex || follow.source != source || follow.start != end) {
                    break;
                }
                end = follow.end;
                next++;
            }
            out.add(new TextSegment(source.getText().substring(start, end), source.getStyle()));
            index = next;
        }
        return Collections.unmodifiableList(out);
    }

    // ==================== 度量 ====================

    static int lineWidthPx(List<TextSegment> line, TextLayoutService measurer, int baseFontSizePx) {
        requireMeasurer(measurer);
        return (int) Math.ceil(lineAdvance(line, measurer, baseFontSizePx));
    }

    private static double lineAdvance(List<TextSegment> line, TextLayoutService measurer, int baseFontSizePx) {
        if (line == null || line.isEmpty()) {
            return 0.0D;
        }
        double width = 0.0D;
        for (int i = 0; i < line.size(); i++) {
            width += segmentAdvance(line.get(i), measurer, baseFontSizePx);
        }
        return width;
    }

    private static double segmentAdvance(TextSegment segment, TextLayoutService measurer, int baseFontSizePx) {
        if (segment == null) {
            return 0.0D;
        }
        if (segment.isLatex()) {
            return measurer.getSegmentWidth(segment, Math.max(1, baseFontSizePx));
        }
        String text = segment.getText();
        double width = 0.0D;
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            width += measurer.resolveAdvance(codepoint, segment.getStyle(), baseFontSizePx);
            i += Character.charCount(codepoint);
        }
        return width;
    }

    static int lineHeightPx(List<TextSegment> line, TextLayoutService measurer, int baseFontSizePx) {
        requireMeasurer(measurer);
        int base = Math.max(1, baseFontSizePx);
        int maxSize = base;
        if (line != null) {
            for (int i = 0; i < line.size(); i++) {
                TextSegment segment = line.get(i);
                if (segment == null || segment.getStyle() == null) {
                    continue;
                }
                int effective = segment.getStyle().resolveEffectiveFontSizePx(base);
                if (effective > maxSize) {
                    maxSize = effective;
                }
            }
        }
        // 行高三段量恒取注入度量同源口径（ascent+descent+lineGap，≤0 回落字号，
        // 与 TextLayoutService.getLineHeight(int) 私有实现同式）。
        int height = measurer.getAscent(maxSize) + measurer.getDescent(maxSize) + measurer.getLineGap(maxSize);
        return Math.max(1, height > 0 ? height : maxSize);
    }

    /** 链接命中区：带 link 样式的段按实测推进宽累计出矩形（LINK_REGION 纯数据命令）。 */
    private static void appendLinkRegions(List<PaintCommand> out, List<TextSegment> line,
            TextLayoutService measurer, int baseFontSizePx, int top, int height) {
        double x = 0.0D;
        for (int i = 0; i < line.size(); i++) {
            TextSegment segment = line.get(i);
            double width = segmentAdvance(segment, measurer, baseFontSizePx);
            TextStyle style = segment == null ? null : segment.getStyle();
            String url = style == null ? null : style.getLink();
            if (url != null) {
                int left = (int) Math.floor(x);
                int right = (int) Math.ceil(x + width);
                if (right > left) {
                    out.add(PaintCommand.linkRegion(left, top, right, top + height, url));
                }
            }
            x += width;
        }
    }

    private static void requireMeasurer(TextLayoutService measurer) {
        if (measurer == null) {
            throw new IllegalArgumentException("measurer 不能为空（度量同源，无注入不排版）");
        }
    }
}
