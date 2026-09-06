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
 *   <li>缩进代码块（块起点 ≥4 前导空格，C1a 2026-09-06 对齐裁定新增：产出与围栏同款 CODE
 *       节点、info 恒空、内容字面；不得中断段落，段落已开始后的 ≥4 空格行是折叠缩进的续行）；</li>
 *   <li>引用块 {@code >}（可嵌套，含惰性续行）；</li>
 *   <li>无序/有序列表（{@code -}/{@code *}/{@code +}、{@code N.}/{@code N)}，
 *       含缩进续行与嵌套子列表）；</li>
 *   <li>分隔线 {@code ---}/{@code ***}/{@code ___}；</li>
 *   <li>段落与空行；硬换行（行尾两空格或行尾未转义反斜杠）。</li>
 * </ul>
 *
 * <h3>刻意不支持（必须字面输出，见 MarkdownBlockParser javadoc 与测试钉死）</h3>
 * <p>表格、任务列表、HTML 内联、脚注、图片 {@code ![alt](url)}（缩进代码块自 C1a 起支持）。
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
 * （默认实心圆点，与 chat3 现行视觉对齐）；有序按 CommonMark 续排——渲染序号恒为
 * <b>母列表 start（= 首项源数字）+ 项下标</b> 再跟句点与一个空格（C3b2 2026-09-06 对齐裁定，
 * 取代旧「有序保留源序号原文」裁定）：源里 {@code 3. 乙} + {@code 4) 丙} 因定界符不同另起一
 * 列表，两项仍渲染成 {@code 3. }/{@code 4. }；嵌套有序子列表各记自己的 start、互不影响。
 * <b>C1a（2026-09-06 对齐裁定）</b>：两接缝标记段一律裸体（无前导空格）——旧 M5 F2
 * 「每级 2 个前导空格写进段流标记文本」机制连同 {@code markerLevel}/{@code baseLevel}
 * 参数链退役；列表层级只由行接缝 {@code listMarkerChain} 几何承载（M10d），
 * 可见文本不再编码层级。</p>
 *
 * <p>纯 JVM，不依赖 Minecraft 类型（沿用原裁定，headless 可测）。</p>
 */
public final class MarkdownDocument {

    private static final MarkdownStyleTable FALLBACK_TABLE = MarkdownStyleTable.defaults();

    /** 行路的「不在任何列表项内」空链常量（不可变，随处共享）。 */
    private static final List<TextSegment> NO_CHAIN = Collections.emptyList();

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
        walk(blocks, baseStyle, table, out, NO_ORDINAL);
        return out;
    }

    /**
     * 扁平化为块身份行序列（M7 方案乙，2026-09-05 用户裁定：块几何进 L1→L2 接缝）。
     *
     * <p><b>与 {@link #toSegments} 的关系</b>：同一块树、同一段生成原语（行内解析/引用样式/
     * 标题样式/列表标记全部共用，防两路漂移）。行接缝的逐行可见文本恒等于段接缝按 \n 与
     * F6 占位切分的行——由 {@code MarkdownLayoutLinesTest} 在门禁语料上逐字钉死。
     * M10d 曾许可的唯一文本差（段接缝标记段叠 F2 「  」前导、行接缝剥净）已随 <b>C1a
     * （2026-09-06 对齐裁定）作废</b>：段路不再叠前导，两接缝可见文本严格同源等值，
     * 层级归属两路统一由 {@code listMarkerChain} 显式携带、L2 沿链求和成像素（几何从不
     * 编码进可见文本）。本方法不产任何新可见文本，也不删任何可见文本（分隔线文本仍由
     * 既有 {@code setThematicBreakText} 旋钮决定；几何另以行的块身份与行盒字段表达，
     * 二者正交）。</p>
     *
     * <p><b>身份字段</b>：kind（TEXT/LIST/CODE/THEMATIC_BREAK）、quoteLevel（引用嵌套层数，
     * 续行天然继承）、blockId（同一块的所有行同值——L2 据此把围栏底色合并为覆盖全部
     * 显示行的单矩形）、leftInsetPx 等行盒几何（数值恒取自 {@link MarkdownStyleTable}
     * 包内登记项，L2 零自设常量，G4）、listMarkerChain（列表项归属链，M10d）。</p>
     *
     * @param styles    样式/排版表（可为 null，取 {@link MarkdownStyleTable#defaults()}）
     * @param baseStyle 基础样式（不可为 null）
     * @return 逻辑行序列（不可变列表；空文档返回空列表）
     */
    public List<MarkdownLayoutLine> toLayoutLines(MarkdownStyleTable styles, TextStyle baseStyle) {
        if (baseStyle == null) {
            throw new IllegalArgumentException("baseStyle 不能为空");
        }
        MarkdownStyleTable table = styles == null ? FALLBACK_TABLE : styles;
        LineFlattener flattener = new LineFlattener(table);
        walkLayout(blocks, baseStyle, table, flattener, NO_CHAIN, 0, NO_ORDINAL);
        return flattener.finish();
    }

    // ==================== 扁平化（段流路） ====================

    /**
     * 同层兄弟块扁平化（C1a 起 markerLevel 参数链退役：列表层级不再编码进段流文本，
     * 标记恒裸体，见 {@link #bareListMarker}）。
     *
     * <p>{@code ordinalBase} 是 C3b2 的有序续排基准：本层兄弟同属一个有序列表时 = 该列表
     * {@code listStart}（第 i 项渲染序号 = ordinalBase + i），否则 {@link #NO_ORDINAL}——
     * 只随「列表 → 项」这一跳生效，项内子块/引用/顶层一律重置，嵌套子列表自记自身 start。</p>
     */
    private static void walk(List<MarkdownBlock> siblings, TextStyle style, MarkdownStyleTable table,
                             List<TextSegment> out, int ordinalBase) {
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
            emit(next, style, table, out, ordinalBase == NO_ORDINAL ? NO_ORDINAL : ordinalBase + i);
        }
    }

    private static void emit(MarkdownBlock block, TextStyle style, MarkdownStyleTable table,
                             List<TextSegment> out, int ordinal) {
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
                walk(block.children, quoteStyle(style, table), table, out, NO_ORDINAL);
                break;
            case LIST:
                walk(block.children, style, table, out,
                        block.ordered ? block.listStart : NO_ORDINAL);
                break;
            case LIST_ITEM:
                emitListItem(block, style, table, out, ordinal);
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
        out.addAll(inlineSegments(body, style, table));
    }

    /** 围栏代码：字面段，不经过行内解析（块内 {@code **}/{@code $}/{@code >} 一律字面）。 */
    private static void emitCode(MarkdownBlock block, TextStyle style, List<TextSegment> out) {
        String code = block.joinedLines();
        if (code.isEmpty()) {
            return;
        }
        out.add(new TextSegment(code, style.copy()));
    }

    /**
     * 列表项：裸标记段 + 首个段落正文同行，其余子块换行起（C1a：标记段无前导空格）。
     *
     * @param ordinal 本项渲染序号（有序 = 母列表 start + 项下标；无序/非有序上下文
     *                {@link #NO_ORDINAL}），只用于 marker 文本合成，见 {@link #bareListMarker}
     */
    private static void emitListItem(MarkdownBlock block, TextStyle style, MarkdownStyleTable table,
                                     List<TextSegment> out, int ordinal) {
        String marker = bareListMarker(block, table, ordinal);
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
            emit(child, style, table, out, NO_ORDINAL);
        }
    }

    /** 有序项「无序号上下文」哨兵（{@code walk} 非列表层传它；合成时回落到源标记取数）。 */
    private static final int NO_ORDINAL = -1;

    /**
     * 列表标记裸体（无任何前导空格；两接缝标记文本的<b>唯一单源</b>，链上标记段与标记段文本
     * 同出此处）：无序 = 样式表符号 + 空格；有序 = <b>{@code (start + 项下标) + ". "}</b>
     * （C3b2 2026-09-06 对齐裁定，CommonMark 0.30 §5.1）——定界符一律归一句点，源序号与源
     * 定界符都不进可见文本；圆点被样式表配成空串 ⇒ 空串（标记完全不输出，含空格）。
     *
     * <p><b>C1a（2026-09-06 对齐裁定）</b>：段流路（{@code toSegments}）旧由 {@code listMarker}
     * 在本裸体上叠 M5 F2「每级 2 前导空格」——该机制连同 {@code markerLevel} 参数链、块模型
     * {@code baseLevel} 字段退役：主流引擎（CommonMark）不把列表层级编码进可见文本，本方法
     * 自此是两接缝标记段的同一来源（行接缝 M10d 起本就直用本方法 + {@code listMarkerChain}）。
     * 块模型与缩进 px 仍不开进公共面（规划 §二之三 裁 B 未重开的那一半不变）。</p>
     *
     * @param ordinal 本项渲染序号（母列表 {@code listStart + 项下标}）；{@link #NO_ORDINAL}
     *                = 调用方没走列表项通道（防御回落：从源标记原文取首项序号，仍归一句点）
     */
    private static String bareListMarker(MarkdownBlock block, MarkdownStyleTable table, int ordinal) {
        if (block.ordered) {
            int n = ordinal == NO_ORDINAL ? sourceOrdinal(block.marker) : ordinal;
            return n + ". "; // 序号推算恒按 start+下标；源定界符 ")" 归一为 "."（主流口径）
        }
        String bullet = table.getBulletMarker(); // 空串 = 标记完全不输出（含空格）
        return bullet.isEmpty() ? bullet : bullet + " ";
    }

    /** 从源标记原文取数（"3."/"3)"→3；取不到数回 1，防御用，正常路径不走）。 */
    private static int sourceOrdinal(String markerText) {
        int n = 0;
        int i = 0;
        while (i < markerText.length() && Character.isDigit(markerText.charAt(i))) {
            n = n * 10 + (markerText.charAt(i) - '0');
            i++;
        }
        return i == 0 ? 1 : n;
    }

    /** 行内解析（两路共用）：块正文交行内解析器，行内语义照抄既有裁定。 */
    private static List<TextSegment> inlineSegments(String body, TextStyle style,
                                                    MarkdownStyleTable table) {
        if (body == null || body.isEmpty()) {
            return Collections.emptyList();
        }
        return MarkdownInlineParser.parse(body, style, table);
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

    // ==================== 扁平化（块身份行路，M7） ====================

    /** 块身份路：同层兄弟行走（语义与 walk 逐点对偶——边界断行、F6 空行）。
     *  M10d：行路不再传 markerLevel——嵌套宽度事实改由标记链 {@code chain} 显式携带。 */
    private static void walkLayout(List<MarkdownBlock> siblings, TextStyle style,
                                   MarkdownStyleTable table, LineFlattener f,
                                   List<TextSegment> chain, int quoteLevel, int ordinalBase) {
        for (int i = 0; i < siblings.size(); i++) {
            MarkdownBlock next = siblings.get(i);
            if (i > 0) {
                f.breakLine();
                if (next.blanksBefore > 0) {
                    f.blankLine();
                }
            }
            emitLayout(next, style, table, f, chain, quoteLevel,
                    ordinalBase == NO_ORDINAL ? NO_ORDINAL : ordinalBase + i);
        }
    }

    /** 块身份路：按块派发（引用只加层级不占行；标题/段恒 TEXT 身份，但 M10d 起落在列表项
     *  内时照旧携带标记链——链才是正文列的触发器，kind 不是）。 */
    private static void emitLayout(MarkdownBlock block, TextStyle style, MarkdownStyleTable table,
                                   LineFlattener f, List<TextSegment> chain, int quoteLevel,
                                   int ordinal) {
        switch (block.kind) {
            case PARAGRAPH:
                f.startBlock(MarkdownLayoutLine.Kind.TEXT, quoteLevel, chain);
                f.append(inlineSegments(block.joinedLines(), style, table));
                break;
            case HEADING:
                f.startBlock(MarkdownLayoutLine.Kind.TEXT, quoteLevel, chain);
                f.append(inlineSegments(block.text, headingStyle(style, block.level, table), table));
                break;
            case CODE:
                emitCodeLayout(block, style, f, quoteLevel, chain);
                break;
            case QUOTE:
                walkLayout(block.children, quoteStyle(style, table), table, f, chain,
                        quoteLevel + 1, NO_ORDINAL);
                break;
            case LIST:
                walkLayout(block.children, style, table, f, chain, quoteLevel,
                        block.ordered ? block.listStart : NO_ORDINAL);
                break;
            case LIST_ITEM:
                emitListItemLayout(block, style, table, f, chain, quoteLevel, ordinal);
                break;
            case THEMATIC_BREAK:
                f.ruleLine(quoteLevel,
                        table.getThematicBreakText().isEmpty()
                                ? Collections.<TextSegment>emptyList()
                                : Collections.singletonList(new TextSegment(
                                        table.getThematicBreakText(), style.copy())),
                        chain);
                break;
            default:
                break;
        }
    }

    /** 围栏代码：每个源行一条 kind=CODE 行（块内空行也带 CODE 身份，底色归组不断裂；
     *  M10d：项内围栏携链，本块全部源行同吃正文列——底色矩形随头行平移，连续语义不变）。 */
    private static void emitCodeLayout(MarkdownBlock block, TextStyle style, LineFlattener f,
                                       int quoteLevel, List<TextSegment> chain) {
        List<String> lines = block.lines;
        if (lines.isEmpty()) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                f.startBlock(MarkdownLayoutLine.Kind.CODE, quoteLevel, chain);
            } else {
                f.continueBlockLine();
            }
            // 围栏的每个源行恒成行（空源行也是显示行——与段流路 joinedLines 内嵌 \n 的
            // splitLogicalLines 口径一致），底色归组靠同 blockId 不断裂
            f.markStructural();
            String text = lines.get(i);
            if (!text.isEmpty()) {
                f.append(Collections.singletonList(new TextSegment(text, style.copy())));
            }
        }
    }

    /**
     * 列表项：标记 + 首段同行；其余子块断行起（与 emitListItem 逐点对偶）。
     *
     * <p><b>M10b 行身份（2026-09-05 裁定 2）</b>：标记文本在 {@code startBlock} <b>之前</b>
     * 算好——标记非空 ⇒ 本块首行为 {@link MarkdownLayoutLine.Kind#LIST}，圆点被样式表配成
     * 空串（有序恒有续排序号，永不空）⇒ 退 {@link MarkdownLayoutLine.Kind#TEXT}。{@code LineFlattener
     * .appendOne} 按内嵌换行符断行并让续行继承 curKind/curBlockId，故<b>同一 blockId 内只有
     * 第一行带标记段</b>（且它就是 {@code segments.get(0)}）。旧文中「L2 据这两条把该块其余
     * 视觉行追加正文列」只覆盖了同块续行——2026-09-05「做全」追加裁定推翻其覆盖面，见下。</p>
     *
     * <p><b>M10d「做全」</b>：标记段文本 = {@link #bareListMarker}（<b>无</b> F2 前导空格——
     * 几何不编码在可见文本里）；本级裸标记段追加到父链尾得 {@code ownChain}，交给该
     * <b>项</b>而非只交首块——首块行、懒延续、软折，连同项名下<b>另起 blockId</b> 的后续
     * 段落/标题/引用/围栏/嵌套子项（子项链 = ownChain + 子项标记）全部携带。正文列由 L2
     * 沿链求和；本层零度量、不产 px。</p>
     */
    private static void emitListItemLayout(MarkdownBlock block, TextStyle style,
                                           MarkdownStyleTable table, LineFlattener f,
                                           List<TextSegment> chain, int quoteLevel, int ordinal) {
        // 标记文本与链上标记段同源（bareListMarker）：有序 = 母列表 start + 项下标 + ". "
        String marker = bareListMarker(block, table, ordinal);
        TextSegment markerSegment = marker.isEmpty()
                ? null : new TextSegment(marker, style.copy());
        // 空串圆点 ⇒ 本级无可渲染标记，链不追加（零宽级不进链；几何恒等）
        List<TextSegment> ownChain = markerSegment == null ? chain : appendChain(chain, markerSegment);
        f.startBlock(markerSegment == null
                ? MarkdownLayoutLine.Kind.TEXT : MarkdownLayoutLine.Kind.LIST, quoteLevel, ownChain);
        if (markerSegment != null) {
            f.append(Collections.singletonList(markerSegment));
        }
        List<MarkdownBlock> children = block.children;
        for (int i = 0; i < children.size(); i++) {
            MarkdownBlock child = children.get(i);
            boolean sameLine = i == 0 && child.kind == MarkdownBlock.Kind.PARAGRAPH;
            if (sameLine) {
                // 首段并入标记行（与段流路 emitListItem 无分隔符紧接同构）
                f.append(inlineSegments(child.joinedLines(), style, table));
                continue;
            }
            if (child.blanksBefore > 0) {
                f.blankLine();
            }
            // 项内子块不是「项」，序号上下文到此为止（嵌套子列表自带自身 start）
            emitLayout(child, style, table, f, ownChain, quoteLevel, NO_ORDINAL);
        }
    }

    /** 链追加一级（产出不可变新表；旧表零改动，兄弟项/后续块共享父链不互相污染）。 */
    private static List<TextSegment> appendChain(List<TextSegment> chain, TextSegment element) {
        ArrayList<TextSegment> out = new ArrayList<TextSegment>(chain.size() + 1);
        out.addAll(chain);
        out.add(element);
        return Collections.unmodifiableList(out);
    }

    /**
     * 行收集器：段流事件 → 逻辑行（M7）。块归属 id 每叶子块一个；续行（段内嵌 \n、
     * 围栏源行、列表项子块断行）继承同值——L2 用「连续同 id」判定块矩形合并。
     * M10d 起列表归属链（listMarkerChain）与块身份同生命周期：startBlock 落定、
     * 块内续行继承、换块不残留（新块必带自身链或空链）。
     *
     * <p>行盒几何与装饰色在行封口时从样式表包内登记项解析（G4 度量同源；公共面零膨胀
     * ——链写端走 {@code MarkdownLayoutLine} 的包内全参构造器，公共面只 +1 读端 getter）。</p>
     */
    private static final class LineFlattener {

        private final MarkdownStyleTable table;
        private final List<MarkdownLayoutLine> out = new ArrayList<MarkdownLayoutLine>();
        private final List<TextSegment> cur = new ArrayList<TextSegment>();
        private MarkdownLayoutLine.Kind curKind = MarkdownLayoutLine.Kind.TEXT;
        private int curQuoteLevel;
        private int curBlockId = MarkdownLayoutLine.NO_BLOCK;
        private List<TextSegment> curListChain = NO_CHAIN;
        private int idGen;
        private boolean open;
        private boolean structural;

        LineFlattener(MarkdownStyleTable table) {
            this.table = table;
        }

        /** 新叶子块首行：封前行、分配新 blockId、落定列表归属链（M10d；null 归一空链）。 */
        void startBlock(MarkdownLayoutLine.Kind kind, int quoteLevel, List<TextSegment> chain) {
            close();
            curKind = kind;
            curQuoteLevel = quoteLevel;
            curBlockId = ++idGen;
            curListChain = chain == null ? NO_CHAIN : chain;
            structural = false;
            open = true;
        }

        /** 块内续行：继承 kind/quoteLevel/blockId。 */
        void continueBlockLine() {
            close();
            structural = false;
            open = true;
        }

        /** 标记本行为结构行（零段也落账——围栏空源行、分隔线行的空文本形态）。 */
        void markStructural() {
            structural = true;
        }

        /** 块边界断行（对应段流路的 \n 分隔段）。 */
        void breakLine() {
            close();
        }

        /** F6 块边界空行（对应段流路的空文本占位段）。 */
        void blankLine() {
            close();
            out.add(MarkdownLayoutLine.blank());
        }

        /** 分隔线行：恒成行（可见文本由样式表旋钮决定，横线几何由 kind 承载——正交；
         *  M10d 项内横线随链落进正文列，与引用层级正交）。 */
        void ruleLine(int quoteLevel, List<TextSegment> textSegments, List<TextSegment> chain) {
            startBlock(MarkdownLayoutLine.Kind.THEMATIC_BREAK, quoteLevel, chain);
            structural = true;
            append(textSegments);
            close();
        }

        /** 追加段：内嵌 \n 断行，续行继承行身份（可见文本一字不改，只按行分装）。 */
        void append(List<TextSegment> segments) {
            for (int i = 0; i < segments.size(); i++) {
                appendOne(segments.get(i));
            }
        }

        private void appendOne(TextSegment segment) {
            if (segment.isLatex()) {
                open = true;
                cur.add(segment);
                return;
            }
            String text = segment.getText();
            if (text.indexOf('\n') < 0) {
                if (!text.isEmpty()) {
                    open = true;
                    cur.add(segment);
                }
                return;
            }
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    String part = text.substring(start, i);
                    if (!part.isEmpty()) {
                        cur.add(new TextSegment(part, segment.getStyle()));
                    }
                    structural = false;
                    close();
                    open = true; // 内嵌换行的续行同属本块
                    start = i + 1;
                }
            }
            String tail = text.substring(start);
            if (!tail.isEmpty()) {
                open = true;
                cur.add(new TextSegment(tail, segment.getStyle()));
            }
        }

        List<MarkdownLayoutLine> finish() {
            close();
            return Collections.unmodifiableList(out);
        }

        private void close() {
            if (!open) {
                return;
            }
            if (!cur.isEmpty() || structural) {
                out.add(buildLine(curKind, curQuoteLevel, curBlockId, cur));
            }
            cur.clear();
            open = false;
            structural = false;
        }

        private MarkdownLayoutLine buildLine(MarkdownLayoutLine.Kind kind, int quoteLevel,
                                             int blockId, List<TextSegment> segments) {
            int step = quoteLevel > 0 ? table.getQuoteIndentPx() : 0;
            int barWidth = quoteLevel > 0 ? table.getQuoteBarWidthPx() : 0;
            int rule = kind == MarkdownLayoutLine.Kind.THEMATIC_BREAK ? table.getRuleThicknessPx() : 0;
            int accent = quoteLevel > 0 || kind == MarkdownLayoutLine.Kind.THEMATIC_BREAK
                    ? table.getBlockAccentArgb() : 0;
            int background = kind == MarkdownLayoutLine.Kind.CODE ? table.getCodeBackgroundColor() : 0;
            // M10d：leftInsetPx 只写引用份额（L1 零度量）；列表正文列由 L2 沿链求和后
            // 经 withLeftInsetPx 追加——链在此逐字交给接缝行。
            return new MarkdownLayoutLine(kind, quoteLevel, blockId, segments,
                    quoteLevel * step, step, barWidth, rule, accent, background, 0, curListChain);
        }
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
