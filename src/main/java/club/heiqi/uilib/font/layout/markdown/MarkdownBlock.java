package club.heiqi.uilib.font.layout.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 块级文档节点模型（包内实现，非公共面；公共接缝只有 {@link MarkdownDocument}）。
 *
 * <p>设计见《规划-通用Markdown渲染器.md》§二/§二之三（裁定 B）：L1 与 L2 的唯一公共接缝
 * 是 {@code List<TextSegment>} 段流；本块模型是<b>包内</b>中间表示，不随 M3 进公共面。
 * 块模型信息（类型/级别/标记原文/围栏 info/硬换行位图/嵌套树）只供包内扁平化消费，
 * 未来若 L2 被证明必须要块级几何，另走「块模型进公共面」的独立裁定。节点不可变。</p>
 *
 * <p><b>C6a（能力①）</b>：包内加 {@link #lineAnchors}/{@link #headingAnchors} 两个样式锚点
 * 字段，把 span 流入口的每行样式随块模型带到行内解析（块层不得丢样式压成 String）。
 * 公共接缝 {@code MarkdownLayoutLine} 面冻结不受影响——锚点只活在包内，出接缝仍只有
 * {@code TextSegment}。</p>
 *
 * <p>刻意不支持（{@link MarkdownBlockParser} 按普通文本字面保留）：表格、任务列表、
 * HTML 内联、脚注、图片 {@code ![alt](url)}。缩进代码块自 C1a（2026-09-06 对齐裁定）起
 * 支持，复用 {@link Kind#CODE}（info 恒空），不设独立 Kind。</p>
 */
final class MarkdownBlock {

    /** 块类型（封闭枚举；留口靠按类型分派而非开放继承）。 */
    enum Kind {
        /** 段落（含硬换行位图）。 */
        PARAGRAPH,
        /** ATX 标题（1..6 级）。 */
        HEADING,
        /** 代码块（围栏或缩进，C1a）：内容字面，永不进段内解析器。 */
        CODE,
        /** 引用块（子块嵌套树承载层级）。 */
        QUOTE,
        /** 列表（子节点为 {@link Kind#LIST_ITEM}）。 */
        LIST,
        /** 列表项（子节点为内容块树）。 */
        LIST_ITEM,
        /** 分隔线。 */
        THEMATIC_BREAK
    }

    final Kind kind;
    /** HEADING: 级别 1..6；其余 0。 */
    final int level;
    /** HEADING: 去标记正文；其余 null。 */
    final String text;
    /** PARAGRAPH: 行首空白折叠 + rtrim 后行集；CODE: 字面行集（围栏已剥栏缩进；缩进代码已剥至多 4 空格）；其余 null。 */
    final List<String> lines;
    /** PARAGRAPH: {@code hardBreaks[i]} = 第 i 行与下一行间为硬换行（长度 = 行数 - 1）；其余 null。 */
    final boolean[] hardBreaks;
    /** CODE: 围栏 info 字符串（存而不消费，高亮属未来裁定）；其余 null。 */
    final String info;
    /**
     * LIST_ITEM: 源标记原文（"-"/"*"/"+"/"3."/"3)"）——只作块模型留档与<b>内容列</b>口径
     * （项体缩进按首项源标记宽定列，C1a 起不变）；<b>不进可见文本</b>：有序渲染序号恒取
     * 母列表 {@link #listStart} + 项下标 + 句点（C3b2 主流续排，见 {@code bareListMarker}）。
     */
    final String marker;
    /** LIST: 是否有序列表；其余 false。 */
    final boolean ordered;
    /**
     * LIST（有序）: 起始序号 = <b>首项</b>标记数字（C3b2 主流续排，2026-09-06 对齐裁定）。
     *
     * <p>CommonMark 只把首项的源数字当语义（{@code OrderedList.start}），后续项的源数字一律
     * 忽略、渲染序号恒为 {@code start + 项下标} 且定界符统一句点。本字段因此是序号的唯一真相，
     * {@code MarkdownDocument} 的两处 marker 文本合成（两接缝共用的 {@code bareListMarker}
     * 与列表归属链上的标记段）都按它推算；无序列表与其余块恒 0。</p>
     */
    final int listStart;
    /** QUOTE/LIST/LIST_ITEM: 子块树；其余空表。 */
    final List<MarkdownBlock> children;
    /**
     * C6a 能力①样式锚点（包内，随块模型走到行内解析，块层不丢样式压成 String）：
     * PARAGRAPH/CODE = 与 {@link #lines} 一一对应的<b>每行</b>锚点段列表（行内按样式区间
     * 切好的 {@link MarkdownSpan} 序列，空行 = 空列表；文本恒等于对应源行，拼接后含 

     * 的行体由扁平化层归并）；其余 null。
     */
    final List<List<MarkdownSpan>> lineAnchors;
    /**
     * C6a 能力①：HEADING 正文锚点（ATX = 单行切段；setext 多行 = 已按 
 归并的段流，
     * 文本拼接恒等于 {@link #text}）；其余 null。
     */
    final List<MarkdownSpan> headingAnchors;
    /**
     * 本块之前被剥掉的源空行数（包内，M4-fix F6）——块间距的唯一登记处。
     *
     * <p>扁平化时 {@code > 0} 的块边界会额外产一个「占位段」（{@code TextSegment} 文本为空串），
     * 由 L2 {@code MarkdownPainter} 认它加一空行（规划 §二之四 C1：零公共面变更——接缝仍是
     * {@code List<TextSegment>}，既不加 {@code TextStyle} 几何字段也不外开块模型）。</p>
     */
    final int blanksBefore;

    private MarkdownBlock(Kind kind, int level, String text, List<String> lines, boolean[] hardBreaks,
                          String info, String marker, boolean ordered, int listStart,
                          List<MarkdownBlock> children, int blanksBefore,
                          List<List<MarkdownSpan>> lineAnchors, List<MarkdownSpan> headingAnchors) {
        this.kind = kind;
        this.level = level;
        this.text = text;
        this.lines = lines == null ? null : Collections.unmodifiableList(new ArrayList<String>(lines));
        this.hardBreaks = hardBreaks;
        this.info = info;
        this.marker = marker;
        this.ordered = ordered;
        this.listStart = listStart;
        this.children = children == null
                ? Collections.<MarkdownBlock>emptyList()
                : Collections.unmodifiableList(new ArrayList<MarkdownBlock>(children));
        this.blanksBefore = blanksBefore;
        this.lineAnchors = lineAnchors == null ? null
                : Collections.unmodifiableList(new ArrayList<List<MarkdownSpan>>(lineAnchors));
        this.headingAnchors = headingAnchors == null ? null
                : Collections.<MarkdownSpan>unmodifiableList(new ArrayList<MarkdownSpan>(headingAnchors));
    }

    /**
     * 复制本节点并写入「块前空行数」（包内，F6）；节点仍不可变。
     *
     * @param blanks 本块之前被剥掉的源空行数（{@code >= 0}）
     * @return 等值副本（blanksBefore 已更新）
     */
    MarkdownBlock withBlanksBefore(int blanks) {
        return new MarkdownBlock(kind, level, text, lines, hardBreaks, info, marker, ordered, listStart,
                children, Math.max(0, blanks), lineAnchors, headingAnchors);
    }

    static MarkdownBlock paragraph(List<String> trimmedLines, boolean[] hardBreaks) {
        return paragraph(trimmedLines, hardBreaks, null);
    }

    /** C6a：带每行样式锚点的段落（{@code lineAnchors} null = String 路）。 */
    static MarkdownBlock paragraph(List<String> trimmedLines, boolean[] hardBreaks,
                                   List<List<MarkdownSpan>> lineAnchors) {
        return new MarkdownBlock(Kind.PARAGRAPH, 0, null, trimmedLines, hardBreaks, null, null, false, 0,
                null, 0, lineAnchors, null);
    }

    static MarkdownBlock heading(int level, String body) {
        return heading(level, body, null);
    }

    /** C6a：带正文样式锚点的标题。 */
    static MarkdownBlock heading(int level, String body, List<MarkdownSpan> bodyAnchors) {
        return new MarkdownBlock(Kind.HEADING, level, body, null, null, null, null, false, 0, null, 0,
                null, bodyAnchors);
    }

    static MarkdownBlock code(String info, List<String> codeLines) {
        return code(info, codeLines, null);
    }

    /** C6a：带每行样式锚点的代码块（内容恒字面，锚点只供样式回溯，永不进段内解析）。 */
    static MarkdownBlock code(String info, List<String> codeLines,
                              List<List<MarkdownSpan>> lineAnchors) {
        return new MarkdownBlock(Kind.CODE, 0, null, codeLines, null, info, null, false, 0, null, 0,
                lineAnchors, null);
    }

    static MarkdownBlock quote(List<MarkdownBlock> childBlocks) {
        return new MarkdownBlock(Kind.QUOTE, 0, null, null, null, null, null, false, 0, childBlocks, 0,
                null, null);
    }

    static MarkdownBlock list(List<MarkdownBlock> items, boolean orderedList, int start) {
        return new MarkdownBlock(Kind.LIST, 0, null, null, null, null, null, orderedList, start, items, 0,
                null, null);
    }

    static MarkdownBlock listItem(String markerText, boolean itemOrdered, List<MarkdownBlock> contentBlocks) {
        return new MarkdownBlock(Kind.LIST_ITEM, 0, null, null, null, null, markerText, itemOrdered, 0,
                contentBlocks, 0, null, null);
    }

    static MarkdownBlock thematicBreak() {
        return new MarkdownBlock(Kind.THEMATIC_BREAK, 0, null, null, null, null, null, false, 0, null, 0,
                null, null);
    }

    /** PARAGRAPH / CODE 的行集以 '\n' 连接。 */
    String joinedLines() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(i));
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return "MarkdownBlock(" + kind + (level > 0 ? " h" + level : "")
                + (marker != null ? " marker=" + marker : "")
                + (listStart > 0 ? " start=" + Integer.valueOf(listStart) : "")
                + (blanksBefore > 0 ? " blanksBefore=" + Integer.valueOf(blanksBefore) : "") + ")";
    }
}
