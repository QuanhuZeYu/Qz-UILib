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
 * <p>任务列表、HTML 内联、脚注、图片 {@code ![alt](url)}（缩进代码块自 C1a 起支持）。
 * 图片说明：块层不生成任何图片节点；{@code ![alt](url)} 整体按普通文本进段内解析，
 * 依既有行内裁定（规划 §五 D2，9c4dcae5 语义照抄不改）产出字面 {@code !} + 链接段。
 * 任务列表/HTML/脚注则整行原样保留为段落/列表项文本。TABLE 通过 toLayoutContent 显式布局；
 * 两个旧出口继续输出识别表格前的字面语义（包括原段落/setext/容器边界），供尚未迁移的消费者。</p>
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
 * <h3>C6a（能力①）：块级文档的 span 流入口（方法名自 C6b 起为 {@link #parseSpans(List)}）</h3>
 * <p>{@link #parseSpans(List)} 吃 {@link MarkdownSpan} 流表达整篇文档，产同一棵块树：span 文本按换行符
 * 切成带样式的源行（一个 span 跨多行拆成多段、样式继承；空行 = 该行无文本段），
 * <b>块级检测恒在拼接后的纯文本行上跑、与 {@code parse(String)} 共用同一套判据</b>
 * （{@link MarkdownBlockParser}，不存在第二份判据）。每行的样式锚点随块模型走到行内解析，
 * 块层不把样式丢掉压成 String。输出接缝（{@link #toSegments}/{@link #toLayoutLines}）
 * 签名与语义不变：产出 {@link TextSegment} 样式 = 行内 markdown 样式叠加在 <b>span 基础
 * 样式</b>之上。<b>叠加顺序（C6b 2026-09-07 裁定改判，取代 C6a 的「span 为底、块链压顶」
 * 链序）</b>：span 基础样式铺底 → 块级链（标题粗体/字号、引用斜体等样式位）叠加 →
 * <b>span 携带显式色（{@link TextStyle#isColorExplicit()}）时其颜色覆盖块级色</b>（引用降色
 * 等块级色不洗宿主显式色；无显式色的 span = 纯 caller 底色，不做覆盖，引用降色照常
 * 生效）→ 行内位最后。此序与 chat3 旧输出侧 § 桥「markdown 样式位先叠加、§ 码后生效
 * ⇒ 服务端色优先于块级色」对齐（规划 §二之八 C6b 细账）。行内 markdown 不引入颜色——
 * 旧裁定不变。合成段（列表标记、
 * 块边界换行、F6 占位、分隔线文本）不对应任何源 span，恒取 caller baseStyle 的块级变换
 * 值——与 String 路逐位一致。L1→L2 接缝 {@link MarkdownLayoutLine} 公共面冻结：锚点只活
 * 在包内，出接缝仍只有 {@code TextSegment}。</p>
 *
 * <p>纯 JVM，不依赖 Minecraft 类型（沿用原裁定，headless 可测）。</p>
 */
public final class MarkdownDocument {

    private static final MarkdownStyleTable FALLBACK_TABLE = MarkdownStyleTable.defaults();

    /** 行路的「不在任何列表项内」空链常量（不可变，随处共享）。 */
    private static final List<TextSegment> NO_CHAIN = Collections.emptyList();

    private final String source;
    private final List<MarkdownBlock> blocks;
    /** 尚未迁移的消费者使用旧语义树；只在实际含表格时保留，出口不重新解析。 */
    private final List<MarkdownBlock> literalBlocks;

    private MarkdownDocument(String source, List<MarkdownBlock> blocks, List<MarkdownSpan> spans) {
        this.source = source;
        this.blocks = Collections.unmodifiableList(blocks);
        this.literalBlocks = containsTable(blocks)
                ? Collections.unmodifiableList(MarkdownBlockParser.parseLiteral(source, spans)) : this.blocks;
    }

    /**
     * 解析块级 markdown 文档。
     *
     * @param source 文档源文本（可为 null/空，返回空文档）
     * @return 不可变文档模型
     */
    public static MarkdownDocument parse(String source) {
        List<MarkdownBlock> parsed = MarkdownBlockParser.parse(source);
        return new MarkdownDocument(source == null ? "" : source, parsed, null);
    }

    /**
     * 解析样式锚点 span 流表达的<b>整篇块级文档</b>（C6a 能力①；原名 {@code parse(List)}，
     * C6b 改名——与 {@link #parse(String)} 的重载使 {@code parse(null)} 变二义编译失败，而
     * {@link #parse(String)} 的 javadoc 承诺「source 可为 null」是合法调用形态，零收益的
     * 源码破坏不留；本方法尚未随版本发布，属周期内自纠，公共面账见规划 §二之七·续）。
     *
     * <p>与 {@link #parse(String)} 产同一棵块树：入口先按换行符把 span 流切成带样式的源行，
     * 块检测恒跑在拼接后的纯文本上（判据与 String 路单源）；每行样式锚点随块模型走到
     * 行内解析（行内为跨 span 连续扫描，见 {@link MarkdownInlineParser} 类头）。出段样式
     * 叠加顺序 = span 基础 → 块级链 → span 显式色覆盖 → 行内位（C6b 裁定，见类头）。</p>
     *
     * <p><b>单次解析承诺</b>：这是「输入侧」通道——带样式的源文本只进 markdown 这一次。
     * 把 {@code toSegments} 产物再喂回行内入口属输出侧反接（双解析漂移），规划 §二之八
     * 旧裁定禁止，两路都不允许。</p>
     *
     * <p><b>C7 消费者现状（2026-09-07 划界）</b>：chat3 气泡路已退回 {@link #parse(String)}，
     * 本入口<b>当前零生产消费者</b>，作为「将来富文本 component / 业务 mod 样式流」的通道
     * 保留在公共面（规划 §二之八 C7 拆除清单与理由）。它承载的是<b>通用样式锚点</b>语义，
     * 与 § 无关：任何 § 识别/转换都不得挂到这条入口上（定案 4/6；反向守卫
     * {@code MarkdownL1ZeroSectionKnowledgeGuardTest}，行为正向锁
     * {@code MarkdownSectionCodeIsPlainTextLockTest}）。能力锁 {@code MarkdownSpanStreamC6aLockTest}
     * 因此保留——锁的是通道能力本身，不是某个已退役的消费者。</p>
     *
     * @param spans 带基础样式的文本 span 流（可为 null/空，返回空文档；span 文本可含换行符）
     * @return 不可变文档模型（{@link #getSource()} = span 文本按序拼接）
     */
    public static MarkdownDocument parseSpans(List<MarkdownSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return new MarkdownDocument("", Collections.<MarkdownBlock>emptyList(), null);
        }
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < spans.size(); i++) {
            joined.append(spans.get(i).getText());
        }
        return new MarkdownDocument(joined.toString(),
                MarkdownBlockParser.parse(joined.toString(), spans), spans);
    }

    /**
     * 导出文档内所有表格的最小语义契约，按深度优先文档顺序排列。
     *
     * <p>复用既有行内解析器及 span 样式锚点；引用样式沿容器继承默认样式表。
     * 表格布局由 toLayoutContent 显式导出；含表格的文档在创建时额外解析并保存旧语义树，
     * 两个旧出口共用它，避免识别表格改变历史段落/setext/容器边界；没有表格时共用原树。
     * 未迁移的消费者继续使用旧出口，本方法不暴露 capability 开关。</p>
     *
     * @param baseStyle 基础样式，不可为 null
     * @return 不可变表格列表，表格单元格隔离可变 TextStyle
     */
    public List<MarkdownTableModel> toTableModels(TextStyle baseStyle) {
        if (baseStyle == null) {
            throw new IllegalArgumentException("baseStyle 不能为空");
        }
        List<MarkdownTableModel> out = new ArrayList<MarkdownTableModel>();
        collectTables(blocks, Collections.<Integer>emptyList(), new BlockStyle(baseStyle), FALLBACK_TABLE, out);
        return Collections.unmodifiableList(out);
    }

    private static boolean containsTable(List<MarkdownBlock> nodes) {
        for (MarkdownBlock block : nodes) {
            if (block.kind == MarkdownBlock.Kind.TABLE || containsTable(block.children)) {
                return true;
            }
        }
        return false;
    }

    private static void collectTables(List<MarkdownBlock> nodes, List<Integer> parentPath,
                                      BlockStyle style, MarkdownStyleTable styles, List<MarkdownTableModel> out) {
        for (int i = 0; i < nodes.size(); i++) {
            MarkdownBlock block = nodes.get(i);
            List<Integer> path = new ArrayList<Integer>(parentPath);
            path.add(i);
            if (block.kind == MarkdownBlock.Kind.TABLE) {
                List<MarkdownTableModel.Row> rows = new ArrayList<MarkdownTableModel.Row>();
                for (List<MarkdownBlock.CellSource> sourceRow : block.table.rows) {
                    List<MarkdownTableModel.Cell> cells = new ArrayList<MarkdownTableModel.Cell>();
                    for (MarkdownBlock.CellSource cell : sourceRow) {
                        cells.add(new MarkdownTableModel.Cell(
                                tableCellSegments(cell, style, styles)));
                    }
                    rows.add(new MarkdownTableModel.Row(cells));
                }
                out.add(new MarkdownTableModel(path, block.table.alignments, rows.get(0),
                        rows.subList(1, rows.size())));
            } else {
                collectTables(block.children, path,
                        block.kind == MarkdownBlock.Kind.QUOTE ? style.quote(styles) : style, styles, out);
            }
        }
    }

    private static List<TextSegment> tableCellSegments(MarkdownBlock.CellSource cell, BlockStyle style,
                                                        MarkdownStyleTable styles) {
        if (cell.text.isEmpty()) {
            return Collections.emptyList();
        }
        List<MarkdownSpan> spans = cell.spans == null
                ? Collections.singletonList(new MarkdownSpan(cell.text, style.root)) : cell.spans;
        return MarkdownInlineParser.parse(spans, styles, style.transform(), true);
    }

    /** @return 原始源文本（null 输入归一为空串；span 路为拼接文本） */
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
     * 语义块树（仅包内测试消费）；表格通过最小数据契约导出，完整块树不进入公共面。
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
        walk(literalBlocks, new BlockStyle(baseStyle), table, out, NO_ORDINAL);
        return out;
    }

    /**
     * 扁平化为块身份行序列（M7 方案乙，2026-09-05 用户裁定：块几何进 L1→L2 接缝）。
     *
     * <p><b>与 {@link #toSegments} 的关系</b>：同一字面降级树、同一段生成原语（行内解析/引用样式/
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
        walkLayout(literalBlocks, new BlockStyle(baseStyle), table, flattener, NO_CHAIN, 0, NO_ORDINAL);
        return flattener.finish();
    }

    /**
     * 首消费者的文档布局投影：普通逻辑行与表格 units 平行存放。
     * 表格锚指向未换行的逻辑行间隙；相同锚按 units 列表顺序插入。
     * 两个历史出口继续保留字面降级，调用本方法才取得表格布局内容。
     *
     * @param styles 样式表，null 使用默认值
     * @param baseStyle 基础样式，不可为 null
     * @return 无度量的文档布局输入；不暴露完整块树
     */
    public LayoutContent toLayoutContent(MarkdownStyleTable styles, TextStyle baseStyle) {
        if (baseStyle == null) {
            throw new IllegalArgumentException("baseStyle 不能为空");
        }
        MarkdownStyleTable table = styles == null ? FALLBACK_TABLE : styles;
        List<MarkdownTableModel> models = new ArrayList<MarkdownTableModel>();
        collectTables(blocks, Collections.<Integer>emptyList(), new BlockStyle(baseStyle), table, models);
        LineFlattener f = new LineFlattener(table);
        f.tableModels = models;
        walkLayout(blocks, new BlockStyle(baseStyle), table, f, NO_CHAIN, 0, NO_ORDINAL);
        return new LayoutContent(f.finish(), f.tableUnits);
    }

    /** 平行的逻辑行与表格序列；表格自身不占据任何逻辑行。 */
    public static final class LayoutContent {
        private final List<MarkdownLayoutLine> lines;
        private final List<TableUnit> tables;

        private LayoutContent(List<MarkdownLayoutLine> lines, List<TableUnit> tables) {
            this.lines = Collections.unmodifiableList(new ArrayList<MarkdownLayoutLine>(lines));
            this.tables = Collections.unmodifiableList(new ArrayList<TableUnit>(tables));
        }

        public List<MarkdownLayoutLine> getLines() { return lines; }
        public List<TableUnit> getTables() { return tables; }
    }

    /** 表格块锚和样式投影；像素旋钮属于此布局输入，不属于零像素的 TableModel。 */
    public static final class TableUnit {
        private final int beforeLineIndex;
        private final MarkdownTableModel model;
        private final MarkdownLayoutLine context;
        private final int paddingXPx;
        private final int paddingYPx;
        private final int borderPx;
        private final int borderArgb;
        private final int headerArgb;

        private TableUnit(int beforeLineIndex, MarkdownTableModel model,
                          MarkdownLayoutLine context, MarkdownStyleTable styles) {
            this.beforeLineIndex = beforeLineIndex;
            this.model = model;
            this.context = context;
            this.paddingXPx = styles.tablePaddingXPx;
            this.paddingYPx = styles.tablePaddingYPx;
            this.borderPx = styles.tableBorderPx;
            this.borderArgb = styles.tableBorderArgb;
            this.headerArgb = styles.tableHeaderArgb;
        }

        /** 插入于此逻辑行之前；等于 lines.size() 表示文档末尾。 */
        public int getBeforeLineIndex() { return beforeLineIndex; }
        public MarkdownTableModel getModel() { return model; }
        /**
         * 仅复用行盒的引用/列表几何上下文，零文本；不是 TABLE 哨兵，
         * 不在 getLines() 流中，也不贡献空行或文本行高。
         */
        public MarkdownLayoutLine getContext() { return context; }
        public int getPaddingXPx() { return paddingXPx; }
        public int getPaddingYPx() { return paddingYPx; }
        public int getBorderPx() { return borderPx; }
        public int getBorderArgb() { return borderArgb; }
        public int getHeaderArgb() { return headerArgb; }
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
    private static void walk(List<MarkdownBlock> siblings, BlockStyle style,
                             MarkdownStyleTable table, List<TextSegment> out, int ordinalBase) {
        for (int i = 0; i < siblings.size(); i++) {
            MarkdownBlock next = siblings.get(i);
            if (i > 0) {
                addNewline(style.value(), out);
                // F6（走 C1）：块边界若吃掉过源空行，换行段后紧跟一个「占位标记段」——
                // 文本为空串、不带任何几何字段，公共接缝仍是 List<TextSegment>；
                // L2 MarkdownPainter 认它加一空行（见 MarkdownLineLayout#splitLogicalLines）。
                if (next.blanksBefore > 0) {
                    out.add(new TextSegment("", style.value()));
                }
            }
            emit(next, style, table, out, ordinalBase == NO_ORDINAL ? NO_ORDINAL : ordinalBase + i);
        }
    }

    private static void emit(MarkdownBlock block, BlockStyle style, MarkdownStyleTable table,
                             List<TextSegment> out, int ordinal) {
        switch (block.kind) {
            case PARAGRAPH:
                emitInline(block, block.joinedLines(), style, table, out);
                break;
            case HEADING:
                emitInline(block, block.text, style.heading(block.level, table), table, out);
                break;
            case CODE:
                emitCode(block, style, out);
                break;
            case QUOTE:
                walk(block.children, style.quote(table), table, out, NO_ORDINAL);
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

    /** 块正文交行内解析器（行内语义照抄既有裁定，本层只叠加块级样式位；C6a：span 路带锚点）。 */
    private static void emitInline(MarkdownBlock block, String body, BlockStyle style,
                                   MarkdownStyleTable table, List<TextSegment> out) {
        out.addAll(inlineSegments(body, anchorsOf(block, style), style, table));
    }

    /**
     * C6a：块 → 行内消费的样式锚点段流（null = String 路）。PARAGRAPH/CODE 的多行锚点
     * 经 {@link MarkdownBlockParser#joinStyledLines} 归并（行间换行符归属前一行末段，
     * 与 String 路 body 内嵌换行同粒度）；HEADING 的锚点在块模型已定（ATX 单行切段、
     * setext 归并流）。
     */
    private static List<MarkdownSpan> anchorsOf(MarkdownBlock block, BlockStyle style) {
        if (block.headingAnchors != null) {
            return block.headingAnchors;
        }
        if (block.lineAnchors == null) {
            return null;
        }
        return MarkdownBlockParser.joinStyledLines(block.lineAnchors, style.value());
    }

    /** 围栏代码：字面段，不经过行内解析（块内 {@code **}/{@code $}/{@code >} 一律字面）。
     *  C6a：span 路逐样式锚点切段——内容仍全字面（能力①「围栏内样式保留、内容不改写」），
     *  仅样式回溯，不做任何行内配对。 */
    private static void emitCode(MarkdownBlock block, BlockStyle style, List<TextSegment> out) {
        String code = block.joinedLines();
        if (code.isEmpty()) {
            return;
        }
        List<MarkdownSpan> anchored = anchorsOf(block, style);
        if (anchored == null) {
            out.add(new TextSegment(code, style.value()));
            return;
        }
        for (int i = 0; i < anchored.size(); i++) {
            MarkdownSpan span = anchored.get(i);
            out.add(new TextSegment(span.getText(), style.applied(span.getBaseStyle())));
        }
    }

    /**
     * 列表项：裸标记段 + 首个段落正文同行，其余子块换行起（C1a：标记段无前导空格）。
     *
     * @param ordinal 本项渲染序号（有序 = 母列表 start + 项下标；无序/非有序上下文
     *                {@link #NO_ORDINAL}），只用于 marker 文本合成，见 {@link #bareListMarker}
     */
    private static void emitListItem(MarkdownBlock block, BlockStyle style,
                                     MarkdownStyleTable table, List<TextSegment> out, int ordinal) {
        String marker = bareListMarker(block, table, ordinal);
        if (!marker.isEmpty()) {
            out.add(new TextSegment(marker, style.value()));
        }
        List<MarkdownBlock> children = block.children;
        for (int i = 0; i < children.size(); i++) {
            MarkdownBlock child = children.get(i);
            boolean sameLine = i == 0 && child.kind == MarkdownBlock.Kind.PARAGRAPH;
            if (!sameLine && !out.isEmpty()) {
                addNewline(style.value(), out);
                if (child.blanksBefore > 0) {
                    out.add(new TextSegment("", style.value()));   // F6 占位标记段
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

    /**
     * 行内解析（两接缝共用）：块正文交行内解析器，行内语义照抄既有裁定。
     *
     * <p>C6a 双路：{@code anchored == null} = String 路（原行为逐位不变）；非 null =
     * span 路，正文恒等于锚点段拼接（{@code MarkdownBlockParser} 保证），解析器做跨锚点
     * 连续扫描、逐区间回溯「span 基础样式 → 块级链 → 行内位」。</p>
     */
    private static List<TextSegment> inlineSegments(String body, List<MarkdownSpan> anchored,
                                                    BlockStyle style, MarkdownStyleTable table) {
        if (anchored != null) {
            return MarkdownInlineParser.parse(anchored, table, style.transform());
        }
        if (body == null || body.isEmpty()) {
            return Collections.emptyList();
        }
        return MarkdownInlineParser.parse(body, style.value(), table);
    }

    private static void emitThematicBreak(MarkdownStyleTable table, BlockStyle style,
                                          List<TextSegment> out) {
        String text = table.getThematicBreakText();
        if (text.isEmpty()) {
            return;
        }
        out.add(new TextSegment(text, style.value()));
    }

    private static void addNewline(TextStyle style, List<TextSegment> out) {
        out.add(new TextSegment("\n", style.copy()));
    }

    /**
     * 标题块级变换（C6a 起为 {@link StyleTransform}，String 路与 span 路共用同一实现）。
     *
     * <p>施加对象是「出段那一刻」的基样式拷贝：String 路 = caller baseStyle（与旧
     * {@code headingStyle(style, ...)} 传值同序同值）；span 路 = 该字符区间的 span 基础
     * 样式——字号锚（{@code getFontSizePx()>0 ? : 表默认}）随之逐区间取，与旧「单值锚」
     * 在单 span 文档下逐位一致。</p>
     */
    private static final class HeadingStep implements StyleTransform {

        private final boolean bold;
        private final boolean underline;
        private final int delta;
        private final int tableDefaultFontPx;

        HeadingStep(int level, MarkdownStyleTable table) {
            this.bold = table.isHeadingBold();
            this.underline = table.isHeadingUnderline();
            this.delta = table.getHeadingFontSizeDeltaPx(level);
            this.tableDefaultFontPx = table.getDefaultFontSizePx();
        }

        @Override
        public TextStyle apply(TextStyle style) {
            if (bold) {
                style.setFontType(FontType.BOLD);
            }
            if (underline) {
                style.setUnderline(true);
            }
            int anchor = style.getFontSizePx() > 0 ? style.getFontSizePx() : tableDefaultFontPx;
            if (delta != 0 && anchor > 0) {
                style.setFontSizePx(Math.max(1, anchor + delta));
            }
            return style;
        }
    }

    // ==================== 扁平化（块身份行路，M7） ====================

    /** 块身份路：同层兄弟行走（语义与 walk 逐点对偶——边界断行、F6 空行）。
     *  M10d：行路不再传 markerLevel——嵌套宽度事实改由标记链 {@code chain} 显式携带。 */
    private static void walkLayout(List<MarkdownBlock> siblings, BlockStyle style,
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

    /** 块身份路：按块派发（引用只加层级不占行；段恒 TEXT 身份，标题恒 HEADING 身份并带
     *  级别 1..6——C3b3 把块模型 {@code MarkdownBlock.level} 搬进行接缝，不再在派发处丢弃；
     *  M10d 起落在列表项内时照旧携带标记链——链才是正文列的触发器，kind 不是）。 */
    private static void emitLayout(MarkdownBlock block, BlockStyle style, MarkdownStyleTable table,
                                   LineFlattener f, List<TextSegment> chain, int quoteLevel,
                                   int ordinal) {
        switch (block.kind) {
            case TABLE:
                f.tableUnit(quoteLevel, chain);
                break;
            case PARAGRAPH:
                f.startBlock(MarkdownLayoutLine.Kind.TEXT, quoteLevel, chain);
                f.append(inlineSegments(block.joinedLines(), anchorsOf(block, style), style, table));
                break;
            case HEADING:
                // C3b3：块级身份 + 级别进接缝（ATX 与 setext 都走本 case，level 已在块模型定级）
                f.startBlock(MarkdownLayoutLine.Kind.HEADING, quoteLevel, chain, block.level);
                f.append(inlineSegments(block.text, block.headingAnchors,
                        style.heading(block.level, table), table));
                break;
            case CODE:
                emitCodeLayout(block, style, f, quoteLevel, chain);
                break;
            case QUOTE:
                walkLayout(block.children, style.quote(table), table, f, chain,
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
                                        table.getThematicBreakText(), style.value())),
                        chain);
                break;
            default:
                break;
        }
    }

    /** 围栏代码：每个源行一条 kind=CODE 行（块内空行也带 CODE 身份，底色归组不断裂；
     *  M10d：项内围栏携链，本块全部源行同吃正文列——底色矩形随头行平移，连续语义不变）。 */
    private static void emitCodeLayout(MarkdownBlock block, BlockStyle style, LineFlattener f,
                                       int quoteLevel, List<TextSegment> chain) {
        List<String> lines = block.lines;
        if (lines.isEmpty()) {
            return;
        }
        List<List<MarkdownSpan>> anchors = block.lineAnchors;
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                f.startBlock(MarkdownLayoutLine.Kind.CODE, quoteLevel, chain);
            } else {
                f.continueBlockLine();
            }
            // 围栏的每个源行恒成行（空源行也是显示行——与段流路 joinedLines 内嵌换行的
            // splitLogicalLines 口径一致），底色归组靠同 blockId 不断裂
            f.markStructural();
            String text = lines.get(i);
            if (text.isEmpty()) {
                continue;
            }
            if (anchors == null) {
                f.append(Collections.singletonList(new TextSegment(text, style.value())));
                continue;
            }
            // C6a：围栏行内容字面、按行内样式锚点切段（样式回溯，永不进行内解析）
            List<TextSegment> segs = new ArrayList<TextSegment>();
            for (int k = 0; k < anchors.get(i).size(); k++) {
                MarkdownSpan span = anchors.get(i).get(k);
                segs.add(new TextSegment(span.getText(), style.applied(span.getBaseStyle())));
            }
            f.append(segs);
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
    private static void emitListItemLayout(MarkdownBlock block, BlockStyle style,
                                           MarkdownStyleTable table, LineFlattener f,
                                           List<TextSegment> chain, int quoteLevel, int ordinal) {
        // 标记文本与链上标记段同源（bareListMarker）：有序 = 母列表 start + 项下标 + ". "
        // C6a：标记段是块层合成文本（非源文本），样式恒 caller 基样式 + 块级链——不随源
        // 行锚点着色（锚点只服务正文的行内解析）。
        String marker = bareListMarker(block, table, ordinal);
        TextSegment markerSegment = marker.isEmpty()
                ? null : new TextSegment(marker, style.value());
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
                f.append(inlineSegments(child.joinedLines(), anchorsOf(child, style), style, table));
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
     * 块内续行继承、换块不残留（新块必带自身链或空链）。C3b3 起标题级别（headingLevel）
     * 与 kind 同生命周期：startBlock 落定、续行继承、换块必随新块归零。
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
        private int curHeadingLevel;
        private List<TextSegment> curListChain = NO_CHAIN;
        private int idGen;
        private boolean open;
        private boolean structural;
        private List<MarkdownTableModel> tableModels = Collections.emptyList();
        private final List<TableUnit> tableUnits = new ArrayList<TableUnit>();

        void tableUnit(int quoteLevel, List<TextSegment> chain) {
            startBlock(MarkdownLayoutLine.Kind.TEXT, quoteLevel, chain);
            MarkdownLayoutLine context = buildLine(curKind, quoteLevel, curBlockId,
                    Collections.<TextSegment>emptyList());
            close(); // 空上下文不进逻辑行流；先前正文已经封口，锚因此稳定。
            tableUnits.add(new TableUnit(out.size(), tableModels.get(tableUnits.size()), context, table));
        }

        LineFlattener(MarkdownStyleTable table) {
            this.table = table;
        }

        /** 新叶子块首行（非标题：级别恒归零）：见 4 参重载。 */
        void startBlock(MarkdownLayoutLine.Kind kind, int quoteLevel, List<TextSegment> chain) {
            startBlock(kind, quoteLevel, chain, 0);
        }

        /**
         * 新叶子块首行：封前行、分配新 blockId、落定列表归属链（M10d；null 归一空链）与
         * 标题级别（C3b3；{@code kind != HEADING} 时传值无意义，构造器归一为 0）。
         */
        void startBlock(MarkdownLayoutLine.Kind kind, int quoteLevel, List<TextSegment> chain,
                int headingLevel) {
            close();
            curKind = kind;
            curQuoteLevel = quoteLevel;
            curBlockId = ++idGen;
            curHeadingLevel = kind == MarkdownLayoutLine.Kind.HEADING ? Math.max(0, headingLevel) : 0;
            curListChain = chain == null ? NO_CHAIN : chain;
            structural = false;
            open = true;
        }

        /** 块内续行：继承 kind/quoteLevel/blockId/headingLevel。 */
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
            // 经 withLeftInsetPx 追加——链在此逐字交给接缝行。C3b3：标题级别随 kind 落行。
            return new MarkdownLayoutLine(kind, quoteLevel, blockId, segments,
                    quoteLevel * step, step, barWidth, rule, accent, background, 0,
                    curHeadingLevel, curListChain);
        }
    }

    /** 引用块级变换（F3 色/斜体旋钮；施加序与旧 {@code quoteStyle} 逐行同值）。 */
    private static final class QuoteStep implements StyleTransform {

        private final boolean italic;
        private final int quoteColor;

        QuoteStep(MarkdownStyleTable table) {
            this.italic = table.isQuoteItalic();
            // F3：引用正文色旋钮（MarkdownStyleTable.getQuoteTextColor），默认对齐 chat3 现行次级色
            // FF9AA0A8（ChatMessageList.java:891-897）；0 = 不改色，继承调用方基础样式。
            this.quoteColor = table.getQuoteTextColor();
        }

        @Override
        public TextStyle apply(TextStyle style) {
            if (italic) {
                style.setItalic(true);
            }
            if (quoteColor != 0) {
                style.setColor(quoteColor);
            }
            return style;
        }
    }

    /**
     * 块级样式上下文（C6a）：caller 基样式 + 块级变换链的不可变对。
     *
     * <p><b>为什么不是样式值</b>：String 路历史上把「已施加块级变换的样式值」沿树传递；
     * span 流的基样式逐字符来自锚点，必须把「变换」本身传下去、出段时施加（见
     * {@link StyleTransform}）。{@link #value()} 给出与旧值传完全同形的 caller 样式
     * （= 链施加于 baseStyle 拷贝；每次 fresh copy，对应旧代码各产出点的 style.copy()）。</p>
     */
    private static final class BlockStyle {

        private final TextStyle root;
        private final StyleTransform transform;
        private final TextStyle resolved;

        BlockStyle(TextStyle root) {
            this(root, null);
        }

        private BlockStyle(TextStyle root, StyleTransform transform) {
            this.root = root;
            this.transform = transform;
            this.resolved = transform == null ? root : transform.apply(root.copy());
        }

        /** 合成段/换行段/占位段样式：caller 基样式叠块级链后的拷贝（旧 {@code style.copy()}）。 */
        TextStyle value() {
            return resolved.copy();
        }

        /**
         * span 基础样式回溯出段（C6b 定序，替代 C6a 的「链压顶」形参语义）：入参 = span
         * 基础样式（内部取拷贝，调用方实例零改动）；先叠块级链（标题/引用样式位），
         * <b>span 携带显式色（{@link TextStyle#isColorExplicit()}）时其颜色覆盖块级色</b>——
         * 与行内路 {@code MarkdownInlineParser.resolve} 同一把尺（宿主色优先、样式位叠加）。
         * 无链时恒等（透传拷贝）。
         */
        TextStyle applied(TextStyle spanBase) {
            TextStyle out = spanBase.copy();
            if (transform != null) {
                out = transform.apply(out);
                if (spanBase.isColorExplicit()) {
                    out.setColor(spanBase.getColor());
                }
            }
            return out;
        }

        /** 行内解析用的块级链（null = 无叠加）。 */
        StyleTransform transform() {
            return transform;
        }

        BlockStyle heading(int level, MarkdownStyleTable table) {
            return new BlockStyle(root, StyleTransform.compose(transform, new HeadingStep(level, table)));
        }

        BlockStyle quote(MarkdownStyleTable table) {
            return new BlockStyle(root, StyleTransform.compose(transform, new QuoteStep(table)));
        }
    }
}
