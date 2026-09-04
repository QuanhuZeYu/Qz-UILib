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
 * <p>刻意不支持（{@link MarkdownBlockParser} 按普通文本字面保留）：表格、任务列表、
 * HTML 内联、脚注、图片 {@code ![alt](url)}、缩进代码块。</p>
 */
final class MarkdownBlock {

    /** 块类型（封闭枚举；留口靠按类型分派而非开放继承）。 */
    enum Kind {
        /** 段落（含硬换行位图）。 */
        PARAGRAPH,
        /** ATX 标题（1..6 级）。 */
        HEADING,
        /** 围栏代码块（内容字面，永不进段内解析器）。 */
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
    /** PARAGRAPH: rtrim 后行集；CODE: 字面行集（已剥围栏缩进）；其余 null。 */
    final List<String> lines;
    /** PARAGRAPH: {@code hardBreaks[i]} = 第 i 行与下一行间为硬换行（长度 = 行数 - 1）；其余 null。 */
    final boolean[] hardBreaks;
    /** CODE: 围栏 info 字符串（存而不消费，高亮属未来裁定）；其余 null。 */
    final String info;
    /** LIST_ITEM: 源标记原文（"-"/"*"/"+"/"3."/"3)"）；其余 null。 */
    final String marker;
    /** LIST: 是否有序列表；其余 false。 */
    final boolean ordered;
    /** QUOTE/LIST/LIST_ITEM: 子块树；其余空表。 */
    final List<MarkdownBlock> children;

    private MarkdownBlock(Kind kind, int level, String text, List<String> lines, boolean[] hardBreaks,
                          String info, String marker, boolean ordered, List<MarkdownBlock> children) {
        this.kind = kind;
        this.level = level;
        this.text = text;
        this.lines = lines == null ? null : Collections.unmodifiableList(new ArrayList<String>(lines));
        this.hardBreaks = hardBreaks;
        this.info = info;
        this.marker = marker;
        this.ordered = ordered;
        this.children = children == null
                ? Collections.<MarkdownBlock>emptyList()
                : Collections.unmodifiableList(new ArrayList<MarkdownBlock>(children));
    }

    static MarkdownBlock paragraph(List<String> trimmedLines, boolean[] hardBreaks) {
        return new MarkdownBlock(Kind.PARAGRAPH, 0, null, trimmedLines, hardBreaks, null, null, false, null);
    }

    static MarkdownBlock heading(int level, String body) {
        return new MarkdownBlock(Kind.HEADING, level, body, null, null, null, null, false, null);
    }

    static MarkdownBlock code(String info, List<String> codeLines) {
        return new MarkdownBlock(Kind.CODE, 0, null, codeLines, null, info, null, false, null);
    }

    static MarkdownBlock quote(List<MarkdownBlock> childBlocks) {
        return new MarkdownBlock(Kind.QUOTE, 0, null, null, null, null, null, false, childBlocks);
    }

    static MarkdownBlock list(List<MarkdownBlock> items, boolean orderedList) {
        return new MarkdownBlock(Kind.LIST, 0, null, null, null, null, null, orderedList, items);
    }

    static MarkdownBlock listItem(String markerText, boolean itemOrdered, List<MarkdownBlock> contentBlocks) {
        return new MarkdownBlock(Kind.LIST_ITEM, 0, null, null, null, null, markerText, itemOrdered, contentBlocks);
    }

    static MarkdownBlock thematicBreak() {
        return new MarkdownBlock(Kind.THEMATIC_BREAK, 0, null, null, null, null, null, false, null);
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
                + (marker != null ? " marker=" + marker : "") + ")";
    }
}
