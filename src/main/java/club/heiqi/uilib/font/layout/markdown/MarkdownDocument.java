package club.heiqi.uilib.font.layout.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/**
 * 块级 markdown 文档数据模型（D3 最小公共面的解析入口；L1 = 本包，规划 §二）。
 *
 * <p>用法：{@code MarkdownDocument doc = MarkdownDocument.parse(source)}，
 * 扁平文本流走 {@link #toSegments(TextStyle)}（输出仍全是 L0 的 {@link TextSegment}，
 * 「绘制走既有签名」到 M3/L2 接线，本期不写绘制）。解析是纯函数、零每帧成本裁定见
 * 规划 §六 3：只在消息/文档到达时调用一次，缓存责任在消费层。</p>
 *
 * <h3>块级语法面（本次 M2 新增）</h3>
 * <ul>
 *   <li>ATX 标题 {@code #}..{@code ######}（可带闭序列）；</li>
 *   <li>围栏代码块（三连反引号或 {@code ~~~}，info 串存而不用，内容永不进行内解析）；</li>
 *   <li>引用块 {@code >}（可嵌套，含惰性续行）；</li>
 *   <li>无序/有序列表（{@code -}/{@code *}/{@code +}、{@code N.}/{@code N)}，
 *       含缩进续行与嵌套子列表）；</li>
 *   <li>分隔线 {@code ---}/{@code ***}/{@code ___}；</li>
 *   <li>段落与空行；硬换行（行尾两空格或行尾未转义反斜杠）。</li>
 * </ul>
 *
 * <h3>刻意不支持（必须字面输出，见 MarkdownBlockParser javadoc 与测试钉死）</h3>
 * <p>表格、任务列表、HTML 内联、脚注、图片 {@code ![alt](url)}、缩进代码块。
 * 图片说明：块层不生成任何图片节点；{@code ![alt](url)} 整体按普通文本进段内解析，
 * 依既有行内裁定（规划 §五 D2，9c4dcae5 语义照抄不改）产出字面 {@code !} + 链接段。
 * 表格/任务列表/HTML/脚注则整行原样保留为段落/列表项文本。</p>
 *
 * <h3>块级与行内的分工</h3>
 * <p>本层只识别块结构、剥除块标记，然后把每块正文交给 {@link MarkdownInlineParser}
 * 解析（行内语义一行不改）；围栏代码块正文除外——字面进段，定界符与内容均不解析。
 * 块级结构在扁平文本流中的表达：块与块之间插入 {@code \n} 段、段落内软换行/硬换行
 * 均为 {@code \n}（硬/软区分保留在块模型的换行位图里，段流统一为 {@code \n}，盒模型不入段流）；
 * 引用与列表的缩进不进文本流（裁定 B：块级几何随块模型留包内、不入公共接缝；
 * M3 落地的 L2 按段流排版，缩进如需可见再随块模型公共面另裁）。
 * 列表项标记：无序归一为 {@code MarkdownStyleTable.getBulletMarker()} + 空格
 * （默认实心圆点，与 chat3 现行视觉对齐），有序保留源序号原文 + 空格。</p>
 *
 * <p>纯 JVM，不依赖 Minecraft 类型（沿用原裁定，headless 可测）。</p>
 */
public final class MarkdownDocument {

    private static final MarkdownStyleTable FALLBACK_TABLE = MarkdownStyleTable.defaults();

    private final String source;
    private final List<MarkdownBlock> blocks;

    private MarkdownDocument(String source, List<MarkdownBlock> blocks) {
        this.source = source;
        this.blocks = Collections.unmodifiableList(blocks);
    }

    /**
     * 解析块级 markdown 文档。
     *
     * @param source 文档源文本（可为 null/空，返回空文档）
     * @return 不可变文档模型
     */
    public static MarkdownDocument parse(String source) {
        List<MarkdownBlock> parsed = MarkdownBlockParser.parse(source);
        return new MarkdownDocument(source == null ? "" : source, parsed);
    }

    /** @return 原始源文本（null 输入归一为空串） */
    public String getSource() {
        return source;
    }

    /** @return 顶层块数量 */
    public int getBlockCount() {
        return blocks.size();
    }

    /** @return 文档是否不含任何块 */
    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    /**
     * 块树接缝（包内：测试消费；裁定 B 记死——M3 落地 L2 未消费块模型，公共面不外开，
     * 唯一公共接缝恒为 {@link #toSegments} 的段流）。
     *
     * @return 顶层块序列（不可变）
     */
    List<MarkdownBlock> blocks() {
        return blocks;
    }

    /**
     * 扁平化为 L0 片段流（默认样式表）。
     *
     * @param baseStyle 基础样式（不可为 null；解析输出只叠加样式位、不改其颜色）
     * @return 富文本片段序列
     */
    public List<TextSegment> toSegments(TextStyle baseStyle) {
        return toSegments(FALLBACK_TABLE, baseStyle);
    }

    /**
     * 扁平化为 L0 片段流。
     *
     * @param styles    样式/排版表（可为 null，取 {@link MarkdownStyleTable#defaults()}）
     * @param baseStyle 基础样式（不可为 null）
     * @return 富文本片段序列
     */
    public List<TextSegment> toSegments(MarkdownStyleTable styles, TextStyle baseStyle) {
        if (baseStyle == null) {
            throw new IllegalArgumentException("baseStyle 不能为空");
        }
        MarkdownStyleTable table = styles == null ? FALLBACK_TABLE : styles;
        List<TextSegment> out = new ArrayList<TextSegment>();
        walk(blocks, baseStyle, table, out, 0);
        return out;
    }

    // ==================== 扁平化 ====================

    /**
     * 同层兄弟块扁平化。
     *
     * @param markerLevel 当前列表嵌套层数（F2：0 = 不在任何列表内）
     */
    private static void walk(List<MarkdownBlock> siblings, TextStyle style, MarkdownStyleTable table,
                             List<TextSegment> out, int markerLevel) {
        for (int i = 0; i < siblings.size(); i++) {
            MarkdownBlock next = siblings.get(i);
            if (i > 0) {
                addNewline(style, out);
                // F6（走 C1）：块边界若吃掉过源空行，换行段后紧跟一个「占位标记段」——
                // 文本为空串、不带任何几何字段，公共接缝仍是 List<TextSegment>；
                // L2 MarkdownPainter 认它加一空行（见 MarkdownLineLayout#splitLogicalLines）。
                if (next.blanksBefore > 0) {
                    out.add(new TextSegment("", style.copy()));
                }
            }
            emit(next, style, table, out, markerLevel);
        }
    }

    private static void emit(MarkdownBlock block, TextStyle style, MarkdownStyleTable table,
                             List<TextSegment> out, int markerLevel) {
        switch (block.kind) {
            case PARAGRAPH:
                emitInline(block.joinedLines(), style, table, out);
                break;
            case HEADING:
                emitInline(block.text, headingStyle(style, block.level, table), table, out);
                break;
            case CODE:
                emitCode(block, style, out);
                break;
            case QUOTE:
                walk(block.children, quoteStyle(style, table), table, out, markerLevel);
                break;
            case LIST:
                walk(block.children, style, table, out, markerLevel + 1);
                break;
            case LIST_ITEM:
                emitListItem(block, style, table, out, markerLevel);
                break;
            case THEMATIC_BREAK:
                emitThematicBreak(table, style, out);
                break;
            default:
                break;
        }
    }

    /** 块正文交行内解析器（行内语义照抄既有裁定，本层只叠加块级样式位）。 */
    private static void emitInline(String body, TextStyle style, MarkdownStyleTable table,
                                 List<TextSegment> out) {
        if (body == null || body.isEmpty()) {
            return;
        }
        out.addAll(MarkdownInlineParser.parse(body, style, table));
    }

    /** 围栏代码：字面段，不经过行内解析（块内 {@code **}/{@code $}/{@code >} 一律字面）。 */
    private static void emitCode(MarkdownBlock block, TextStyle style, List<TextSegment> out) {
        String code = block.joinedLines();
        if (code.isEmpty()) {
            return;
        }
        out.add(new TextSegment(code, style.copy()));
    }

    /** 列表项：标记段 + 首个段落正文同行，其余子块换行起。 */
    private static void emitListItem(MarkdownBlock block, TextStyle style, MarkdownStyleTable table,
                                     List<TextSegment> out, int markerLevel) {
        String marker;
        if (block.ordered) {
            marker = block.marker + " "; // 有序：保留源序号原文（"3." / "3)"），与 chat3 现行裁定一致
        } else {
            marker = table.getBulletMarker(); // 空串 = 标记完全不输出（含空格）
            if (!marker.isEmpty()) {
                marker = marker + " ";
            }
        }
        // F2：嵌套列表每级缩进写成标记段文本里的前导空格，每级 2 个空格——复刻 chat3
        // 出货口径（ChatMessageList.java:952-956：level 由前导空格数 / 2 得出，每级 append "  "）。
        // 缩进靠扁平段流表达，不把块模型 / 缩进 px 开进公共面（规划 §二之三 裁 B 不变）。
        int level = Math.max(0, markerLevel - 1);
        if (!marker.isEmpty() && level > 0) {
            StringBuilder indented = new StringBuilder(marker.length() + 2 * level);
            for (int l = 0; l < level; l++) {
                indented.append("  ");
            }
            indented.append(marker);
            marker = indented.toString();
        }
        if (!marker.isEmpty()) {
            out.add(new TextSegment(marker, style.copy()));
        }
        List<MarkdownBlock> children = block.children;
        for (int i = 0; i < children.size(); i++) {
            MarkdownBlock child = children.get(i);
            boolean sameLine = i == 0 && child.kind == MarkdownBlock.Kind.PARAGRAPH;
            if (!sameLine && !out.isEmpty()) {
                addNewline(style, out);
                if (child.blanksBefore > 0) {
                    out.add(new TextSegment("", style.copy()));   // F6 占位标记段
                }
            }
            emit(child, style, table, out, markerLevel);
        }
    }

    private static void emitThematicBreak(MarkdownStyleTable table, TextStyle style,
                                          List<TextSegment> out) {
        String text = table.getThematicBreakText();
        if (text.isEmpty()) {
            return;
        }
        out.add(new TextSegment(text, style.copy()));
    }

    private static void addNewline(TextStyle style, List<TextSegment> out) {
        out.add(new TextSegment("\n", style.copy()));
    }

    private static TextStyle headingStyle(TextStyle base, int level, MarkdownStyleTable table) {
        TextStyle style = base.copy();
        if (table.isHeadingBold()) {
            style.setFontType(FontType.BOLD);
        }
        if (table.isHeadingUnderline()) {
            style.setUnderline(true);
        }
        int delta = table.getHeadingFontSizeDeltaPx(level);
        int anchor = base.getFontSizePx() > 0 ? base.getFontSizePx() : table.getDefaultFontSizePx();
        if (delta != 0 && anchor > 0) {
            style.setFontSizePx(Math.max(1, anchor + delta));
        }
        return style;
    }

    private static TextStyle quoteStyle(TextStyle base, MarkdownStyleTable table) {
        TextStyle style = base.copy();
        if (table.isQuoteItalic()) {
            style.setItalic(true);
        }
        // F3：引用正文色旋钮（MarkdownStyleTable.getQuoteTextColor），默认对齐 chat3 现行次级色
        // FF9AA0A8（ChatMessageList.java:891-897）；0 = 不改色，继承调用方基础样式。
        int quoteColor = table.getQuoteTextColor();
        if (quoteColor != 0) {
            style.setColor(quoteColor);
        }
        return style;
    }
}
