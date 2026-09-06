package club.heiqi.uilib.internal.chat3.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatCardComposer;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatUrlLinkifier;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;

/**
 * chat3 消息级 markdown 管道（M5 接线本体；规划《通用Markdown渲染器》§三 M5/§二 L3）。
 *
 * <p><b>M7 接线升级（2026-09-05，方案乙）</b>：链路换吃块身份行接缝，顺序仍 = 门禁 B 路
 * 定义顺序的对应升级形：消息原文 → <b>行首 § 输入清洗</b>（{@link #stripLeadingSectionCodes}，
 * C4 归位：MC 特有格式只在本集成层消费，L1 对 § 零认知；C4-fix 乙′：清洗只在命中块标记时
 * 消费 § 序列，未命中的行原样进 parse、行首色由输出侧桥解释）→ {@link MarkdownDocument#parse(String)} →
 * {@link MarkdownDocument#toLayoutLines(MarkdownStyleTable, TextStyle)}（逻辑行 +
 * kind/quoteLevel/行盒几何/块归属）→ <b>&sect; 桥</b>（{@link #bridgeSectionCodes}，逐行
 * 段流，语义同旧——桥本就段局部，行内窗口拼接与整条流等价：URL 扫描以空白为终止，
 * 永不跨行）→ {@link ChatUrlLinkifier#linkify}（<b>换行前</b>整条流链接化的逐行形态，
 * 理由同上）→ {@link MarkdownPainter#wrapLayoutLines}（L2 换行，折行宽度按行扣除引用缩进）。
 * 产出的 {@link RenderedLine} 携带引用层级与 CODE/RULE 身份，{@link ChatMessageList} 据此
 * 用既有 SceneNode 能力（背景色节点/竖条/嵌套行）表达三项块级几何——<b>块模型与 L1/L2
 * 类型不外泄出本文件</b>（复生锁 G3 断言④口径不变），消费方面向 {@link RenderedLine}
 * 自有视图类型，可见 API 面零变化。</p>
 *
 * <p><b>每帧零解析（规划 §六 3）</b>：两级 LRU 沿用 {@code ChatLineLayouter} 既有布局缓存
 * 纪律——逻辑行缓存 key = 原文@基础色#配色代（解析/桥/链接化与字体无关，配色变更即时失效）；
 * 视觉行缓存 key = 逻辑行 key#定行宽#字号#度量纪元（{@code FontService.getRuntimeVersion()}）。
 * 渲染帧只在结构重建时命中缓存，不逐帧 parse。缓存按实例隔离。</p>
 *
 * <p>系统消息不走本管道（§3.5 排版规则仅作用于气泡内，行级旧行为原样保留在
 * {@link ChatMessageList} 的系统路）。</p>
 */
final class ChatMarkdownPipeline {

    /** 逻辑行缓存上限（历史 100 行 + 配色切换余量）。 */
    private static final int LOGICAL_CACHE_MAX = 200;
    /** 视觉行缓存上限（同 {@code ChatLineLayouter.MAX_ENTRIES} 口径）。 */
    private static final int LINES_CACHE_MAX = 160;

    /** 逻辑行缓存：原文@基础色#配色代 → 桥+链接化后的逻辑行（含块身份）。 */
    private final Map<String, List<MarkdownLayoutLine>> logicalCache = newLru(LOGICAL_CACHE_MAX);
    /** 视觉行缓存：逻辑行 key#定行宽#字号#度量纪元 → 换行产物。 */
    private final Map<String, List<RenderedLine>> linesCache = newLru(LINES_CACHE_MAX);

    /**
     * 一条已渲染视觉行（chat3 自有视图，不含任何 markdown 层类型引用）。
     *
     * <p>身份字段来源 = L1 块身份行接缝（{@link MarkdownLayoutLine}）逐视觉行透传：
     * quoteLevel>0 ⇒ 该引用层的竖条 + 水平缩进；{@link #isRule()} ⇒ 真横线（
     * ruleThicknessPx 高、ruleArgb 色的背景条，替掉旧字面 dash 文本行）；
     * {@link #isCode()} ⇒ 围栏行底色（同 blockId 相邻行色块相接 = 整段底色）。</p>
     */
    static final class RenderedLine {

        private final List<TextSegment> segments;
        private final int quoteLevel;
        private final int leftInsetPx;
        private final int indentStepPx;
        private final int blockId;
        private final boolean code;
        private final boolean rule;
        private final int ruleThicknessPx;
        private final int accentArgb;
        private final int backgroundArgb;
        private final int blockContentWidthPx;

        RenderedLine(List<TextSegment> segments, int quoteLevel, int leftInsetPx, int indentStepPx,
                int blockId, boolean code, boolean rule, int ruleThicknessPx, int accentArgb,
                int backgroundArgb, int blockContentWidthPx) {
            this.segments = segments;
            this.quoteLevel = quoteLevel;
            this.leftInsetPx = leftInsetPx;
            this.indentStepPx = indentStepPx;
            this.blockId = blockId;
            this.code = code;
            this.rule = rule;
            this.ruleThicknessPx = ruleThicknessPx;
            this.accentArgb = accentArgb;
            this.backgroundArgb = backgroundArgb;
            this.blockContentWidthPx = blockContentWidthPx;
        }

        /** @return 本视觉行段流（不可变；与段流接缝逐字等值） */
        List<TextSegment> segments() {
            return segments;
        }

        /** @return 引用嵌套层数（0 = 非引用行） */
        int quoteLevel() {
            return quoteLevel;
        }

        /**
         * @return 行文本左偏移（UI px；接缝唯一「行左偏移」真相，L1/L2 逐字送达）——
         *         构成 = 引用份额（{@code quoteLevel × indentStepPx}）+ 列表续行的正文列
         *         （M10b 由 L2 量标记段实测宽后经 {@code withLeftInsetPx} 写回）。
         *
         * <p><b>M10c 消费端（2026-09-05 裁定；旧句「消费端行盒/钳宽 reserve 同源用」
         * 在写下时是假的——全仓曾只有本类省略号路读它——本轮使其成真）</b>：
         * {@code ChatMessageList} 气泡路只施加差值
         * {@code leftInsetPx - quoteLevel × indentStepPx}（正文列残余）——引用份额已由其
         * 嵌套 row 结构表达，整值施加=把引用缩进算两遍。钳宽 reserve 同扣该残余。</p>
         */
        int leftInsetPx() {
            return leftInsetPx;
        }

        /**
         * @return 每层引用水平步长（UI px；非引用行 0）——<b>逐字透传 L2 的
         *         {@code MarkdownLayoutLine#getIndentStepPx()}</b>，本层零再算。
         *
         * <p>M10c（规划 §二之七·续 第 12 条）：消费端反解正文列要用它。B2（2026-09-06
         * 合并批次）已兑现同源派生：{@code ChatMessageList} 的引用嵌套几何、ruleLine 宽、
         * 钳宽 reserve 三处直取本步长（pitch = indentStepPx 构造等值），旧「私有常数
         * 2+6=8 与样式表 quoteIndentPx=8 等值是巧合不是同源」就此销账；
         * 「leftInsetPx − quoteLevel×indentStepPx」与视图嵌套步长从此同尺同值。
         * 范式与 {@link #blockContentWidthPx()} 的 M8 透传完全相同；生产路与换行替身
         * 注入路（wrapOverride）同源填充，不留两口径。</p>
         */
        int indentStepPx() {
            return indentStepPx;
        }

        /** @return 块归属 id（CODE 相邻同行用于统一底色块宽） */
        int blockId() {
            return blockId;
        }

        /** @return true = 围栏代码块行（底色衬底） */
        boolean isCode() {
            return code;
        }

        /** @return true = 分隔线行（真横线，不渲染文本段） */
        boolean isRule() {
            return rule;
        }

        /** @return 横线厚度（仅 isRule 有意义） */
        int ruleThicknessPx() {
            return ruleThicknessPx;
        }

        /** @return 横线色（仅 isRule 有意义） */
        int accentArgb() {
            return accentArgb;
        }

        /** @return 围栏底色（仅 isCode 有意义） */
        int backgroundArgb() {
            return backgroundArgb;
        }

        /**
         * @return 块内统一内容宽（UI px；{@code 0} = 不适用）——<b>逐字透传 L2 的
         * {@code MarkdownLayoutLine.getBlockContentWidthPx()}</b>，本层零再算。
         *
         * <p>M8 单一真相：围栏底色的「块内统一宽」只在 L2（{@code MarkdownPainter.wrapLayoutLines}
         * 持度量服务处）算一次；本视图类型只做搬运，{@code ChatMessageList} 读它钉行节点宽。
         * 换行替身注入路（headless 测试用，不经 L2 度量）拿不到度量，故保持定义值 {@code 0}，
         * 消费端按「不适用 → 用本行实测宽」处理——这是<b>缺度量</b>而非第二套块宽口径。</p>
         */
        int blockContentWidthPx() {
            return blockContentWidthPx;
        }
    }

    /**
     * 消息原文 → 显示行（每行 = 段流 + 块身份）。
     *
     * @param messageText   去前缀消息原文（{@code ChatCardComposer.MessageLines.getDisplayText()}；
     *                    进链路前先经 {@link #stripLeadingSectionCodes} 行首 § 输入清洗；
     *                    乙′：命中块标记才消费，未命中的行原文直进）
     * @param baseColor     气泡正文基础色（ARGB）
     * @param maxWidthPx    定行宽（与行切分器同口径；{@code <= 0} = 只按行边界硬断）
     * @param fontSizePx    正文基准字号（UI px）
     * @param postProcessor 段流后处理（T8 LaTeX 行高约束；null = 关闭）
     * @param wrapOverride  视觉行换行注入（headless 测试用与行切分器同源度量的替身；
     *                      null = 生产路 {@link MarkdownPainter#wrapLayoutLines} +
     *                      {@code FontService} 度量——与 {@code uiLibMeasure}/{@code uiLibSegmentMeasurer}
     *                      三者同源，钳宽/换行/渲染一把尺）。注入时按逻辑行逐次调用
     *                      （maxWidthPx 已扣该行左偏移），产行继承该逻辑行身份
     * @return 不可变视觉行列表（至少一行；空文本 → 单空行）
     */
    synchronized List<RenderedLine> layout(String messageText, int baseColor, int maxWidthPx,
            int fontSizePx, ChatMessageList.SegmentPostProcessor postProcessor,
            ChatMessageList.SegmentFlowWrapper wrapOverride) {
        // C4-fix 乙′（归位宪法不变：L1 对 § 零认知，MC 特有格式只在本集成层消费）：逐行清洗
        // 只在「≤3 空格 + 行首 § 码对」交替视图命中块标记（标题/围栏/引用/列表/分隔线）时消费
        // § 序列；不命中的行（如 §c红色警告）原样进 parse，行首色由输出侧桥解释——chat3 观感
        // 在「物理行首 §」形态上与 C4 前逐位一致（C4 初版「无条件剥」丢行首色，已改判，细账见
        // 规划 §二之八 C4-fix）。清洗仍是原文的纯函数且幂等（命中的视图行首必是触发字符、无可
        // 剥 § 序列；不命中的行二次判定结果不变）⇒ 两级缓存 key 继续吃清洗后文本：清洗后同串
        // （如「§a- x」与「- x」）产物逐段等值，共享条目是去重不是串味；清洗后不同串则 key 必
        // 不同。「串味」需要「同 key 不同语义」，而 key 唯一决定 parse 输入。
        String text = stripLeadingSectionCodes(messageText == null ? "" : messageText);
        List<MarkdownLayoutLine> logical = logicalCached(text, baseColor, postProcessor);
        int epoch = FontService.getInstance().getRuntimeVersion();
        String key = cacheKey(text, baseColor) + '#' + maxWidthPx + '#' + fontSizePx + '#' + epoch
                + (wrapOverride == null ? "" : "#w");
        List<RenderedLine> hit = linesCache.get(key);
        if (hit != null) {
            return hit;
        }
        List<RenderedLine> lines;
        if (wrapOverride == null) {
            TextLayoutService measurer = FontService.getInstance().getTextLayoutService();
            lines = render(MarkdownPainter.wrapLayoutLines(logical, measurer, maxWidthPx, fontSizePx));
        } else {
            List<RenderedLine> out = new ArrayList<RenderedLine>(logical.size());
            for (int i = 0; i < logical.size(); i++) {
                MarkdownLayoutLine line = logical.get(i);
                int availPx = maxWidthPx <= 0
                        ? maxWidthPx : Math.max(1, maxWidthPx - line.getLeftInsetPx());
                List<List<TextSegment>> visual =
                        wrapOverride.wrap(line.getSegments(), availPx, fontSizePx);
                for (List<TextSegment> segments : visual) {
                    // M10c：步长与 leftInsetPx 同源逐字透传——替身注入路也必须填，
                    // 不留「生产有列、替身无列」的两口径（消费端反解式两路同式）。
                    out.add(toRendered(line.getKind() == MarkdownLayoutLine.Kind.CODE,
                            line.getKind() == MarkdownLayoutLine.Kind.THEMATIC_BREAK,
                            line.getQuoteLevel(), line.getLeftInsetPx(), line.getIndentStepPx(),
                            line.getBlockId(),
                            line.getRuleThicknessPx(), line.getAccentArgb(),
                            line.getBackgroundArgb(), line.getBlockContentWidthPx(), segments));
                }
            }
            lines = Collections.unmodifiableList(out);
        }
        linesCache.put(key, lines);
        return lines;
    }

    /** 解析 → 桥 → 链接化（换行前），带逻辑行缓存；后处理在入缓存前逐行施加。 */
    private List<MarkdownLayoutLine> logicalCached(String text, int baseColor,
            ChatMessageList.SegmentPostProcessor postProcessor) {
        String key = cacheKey(text, baseColor) + (postProcessor == null ? "" : "#p");
        List<MarkdownLayoutLine> hit = logicalCache.get(key);
        if (hit != null) {
            return hit;
        }
        TextStyle base = new TextStyle();
        base.setColor(baseColor);
        List<MarkdownLayoutLine> logical =
                MarkdownDocument.parse(text).toLayoutLines(chatStyleTable(), base);
        List<MarkdownLayoutLine> processed = new ArrayList<MarkdownLayoutLine>(logical.size());
        for (int i = 0; i < logical.size(); i++) {
            MarkdownLayoutLine line = logical.get(i);
            List<TextSegment> segments = line.getSegments();
            if (postProcessor != null && !segments.isEmpty()) {
                segments = postProcessor.postProcess(segments, ChatMarkdownSettings.getChatFontSizePx());
            }
            segments = bridgeSectionCodes(segments);
            segments = ChatUrlLinkifier.linkify(segments, ChatMarkdownSettings.getLinkArgb());
            processed.add(line.withSegments(segments));
        }
        processed = Collections.unmodifiableList(processed);
        logicalCache.put(key, processed);
        return processed;
    }

    /** markdown 行 → RenderedLine 视图（块模型/L1 类型到此为止，不再外传）。 */
    private static List<RenderedLine> render(List<MarkdownLayoutLine> visualLines) {
        List<RenderedLine> out = new ArrayList<RenderedLine>(visualLines.size());
        for (int i = 0; i < visualLines.size(); i++) {
            MarkdownLayoutLine line = visualLines.get(i);
            out.add(toRendered(line.getKind() == MarkdownLayoutLine.Kind.CODE,
                    line.getKind() == MarkdownLayoutLine.Kind.THEMATIC_BREAK,
                    line.getQuoteLevel(), line.getLeftInsetPx(), line.getIndentStepPx(),
                    line.getBlockId(),
                    line.getRuleThicknessPx(), line.getAccentArgb(), line.getBackgroundArgb(),
                    line.getBlockContentWidthPx(), line.getSegments()));
        }
        return Collections.unmodifiableList(out);
    }

    private static RenderedLine toRendered(boolean code, boolean rule, int quoteLevel,
            int leftInsetPx, int indentStepPx, int blockId, int ruleThicknessPx, int accentArgb,
            int backgroundArgb, int blockContentWidthPx, List<TextSegment> segments) {
        return new RenderedLine(Collections.unmodifiableList(new ArrayList<TextSegment>(segments)),
                quoteLevel, leftInsetPx, indentStepPx, blockId, code, rule, ruleThicknessPx,
                accentArgb, backgroundArgb, blockContentWidthPx);
    }

    /**
     * chat3 侧 markdown 样式表（每次构建取设置现值——引用色与 chat3 次级色恒同源，
     * F3 旋钮语义 + G4 单一登记面；code 字号/衬底沿用 {@link MarkdownStyleTable} 默认登记值，
     * 与 {@code ChatMarkdownSettings.getCodeFontSizePx()}/{@code getCodeBackgroundArgb()}
     * 出货口径一致）。
     *
     * <p>M7：{@code setThematicBreakText("")} 用既有旋钮关掉字面 dash 横线文本——
     * 分隔线由行身份（RULE）+ SceneNode 背景条（L2 侧 BACKGROUND 命令）表达，
     * 不再是一串 '-'（用户裁定三项之一；规划 §二之三 M7 注记）。</p>
     */
    static MarkdownStyleTable chatStyleTable() {
        MarkdownStyleTable table = MarkdownStyleTable.defaults();
        table.setQuoteTextColor(ChatMarkdownSettings.getTextSecondaryArgb());
        table.setThematicBreakText("");
        return table;
    }

    /**
     * § 桥（输出侧）：把 toLayoutLines 产物里残留在段文本中的 § 样式码解释为样式（chat3 现有
     * § 颜色语义的出口面）。C4-fix 乙′配套：{@link #stripLeadingSectionCodes} 只消费命中块标记
     * 行的行首码对——<b>未命中行的行首码与行中/段中残留码都在本方法出口上色</b>（这正是
     * {@code §c红色警告} 行首色保住的机制，C4 前旧 NONE 分支同款）——输入清洗与输出解释均在
     * 本集成层，L1 零认知。
     *
     * <p>逐段扫描：latex 原子段（TeX 源是数学文本，§ 无意义）、code 衬底段（code 内容恒字面，
     * F1/旧裁定「code 内不解析任何标记」）与不含 § 的段原样透传（零拷贝）。命中的段以
     * {@link TextStyle#applyFormat(char, int)}（L0 唯一 § 语义实现，度量/颜色同源）按码对
     * 切分：每个可视 run 持有原段样式拷贝 + 依序 applyFormat——markdown 样式位（粗/斜/删/
     * 下/code/链接位/段级字号）先叠加，§ 码后生效，{@code §r} 重置与 {@code TextLayoutService
     * .parseSegments} 同语义。连续 § 码（如 {@code §c§l}）与 A 路同样逐对消费；纯格式码段
     * （如行首残留 {@code §f} 独占段）整段消失，与 parseSegments 的空 run 丢弃一致。</p>
     *
     * @param segments 行段流（只读）
     * @return 桥后段流（无 § 残留时同引用）
     */
    static List<TextSegment> bridgeSectionCodes(List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return segments;
        }
        List<TextSegment> out = null;
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            String text = segment.getText();
            if (segment.isLatex() || segment.getStyle().isCodeSpan()
                    || text == null || text.indexOf('\u00a7') < 0) {
                if (out != null) {
                    out.add(segment);
                }
                continue;
            }
            List<TextSegment> runs = splitRunsOnFormatCodes(segment);
            if (runs == null) {
                if (out != null) {
                    out.add(segment);
                }
                continue;
            }
            if (out == null) {
                out = new ArrayList<TextSegment>(segments.size() + 4);
                for (int k = 0; k < i; k++) {
                    out.add(segments.get(k));
                }
            }
            out.addAll(runs);
        }
        return out == null ? segments : out;
    }

    /**
     * 单段 § 码切分（与 {@code TextLayoutService.parseSegments} 同一扫描语义，差别仅在起点
     * 样式 = 本段样式拷贝而非新建——markdown 样式位因此得以保留）。
     *
     * @return 切分段列表；文本确无可消费 § 对时返回 null（调用方透传原段）
     */
    private static List<TextSegment> splitRunsOnFormatCodes(TextSegment segment) {
        String text = segment.getText();
        if (text.indexOf('\u00a7') < 0) {
            return null;
        }
        int baseColor = segment.getStyle().getColor();
        List<TextSegment> out = new ArrayList<TextSegment>(2);
        TextStyle current = segment.getStyle().copy();
        StringBuilder buffer = new StringBuilder(text.length());
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                if (buffer.length() > 0) {
                    out.add(new TextSegment(buffer.toString(), current));
                    buffer.setLength(0);
                }
                current = current.copy();
                current.applyFormat(Character.toLowerCase(text.charAt(i + 1)), baseColor);
                i += 2;
                continue;
            }
            buffer.append(c);
            i++;
        }
        if (buffer.length() > 0) {
            out.add(new TextSegment(buffer.toString(), current));
        }
        return out;
    }

    /**
     * 输入侧 § 清洗（C4 归位 + C4-fix 乙′，规划 §二之五 F4 承接面的归位形 → §二之八 C4-fix
     * 改判）：逐行做「至多 3 个空格 + 连续 § 码对」交替检测视图（{@link #markerView}），
     * <b>只有视图中确实命中块标记（标题/围栏/引用/列表/分隔线）才采用剥后视图</b>；不命中的
     * 行一字不动。码集 0-9a-f 颜色、k-o 样式、r 重置，大小写同义；非法码字符（§z）与行尾
     * 孤立 § 原样保留，非破坏性宽容。
     *
     * <p><b>判据出处（C4-fix 裁定，用户裁定方案乙′）</b>：旧 L1（{@code bd58b343^}）
     * markerView 的「命中块标记才消费、未命中原样保留」精确复刻旧 chat3 观感——旧
     * ChatMarkdownLineRule.classify 的剥码只作用于其检测局部变量、从不改显示文本；旧
     * ChatMessageList 的 NONE 分支走 parseCached(renderLine)，renderLine 含行首 § 码 ⇒
     * 不命中块标记的行<b>颜色保留</b>；只有命中列表/公式的行才用 markdown.getContent()
     * （无码）⇒ 那类行行首色才丢。C4 初版误按「无条件剥」口径搬迁，实测丢失
     * {@code §c红色警告} 的行首色（当时如实登记的代价⑤C），本批改判乙′：判据与循环结构
     * 整体搬进集成层（L1 仍零认知 §，宪法满足），未命中行的行首码与行中/段中码一律由输出侧
     * {@link #bridgeSectionCodes} 解释上色；命中行消费的是行首码对——与 C4 前逐位一致
     * （那类行旧口径同样是剥后无码显示）。</p>
     *
     * <p>纯函数 + 幂等：命中的视图行首必是 ≤3 空格 + 块标记触发字符（二次调用无可剥 § 序列、
     * 恒同引用，如 {@code §a- x} 剥后 {@code - x} 二次不动）；不命中的行恒返回原行（二次判定
     * 结果不变，如 {@code §c红色警告} 二次仍不剥）⇒ 二次调用恒同引用。这是缓存 key 用清洗后
     * 文本仍自洽的前提（见 {@link #layout} 的注释）。</p>
     *
     * <p><b>粗检漂移风险与锁</b>：本类粗检判据与 L1 真判据是两份代码（L1 私有面跨包不可见、
     * 公共面冻结，不得为此扩 L1、不得反射进 L1 私有），由
     * {@code ChatMarkdownPipelineTest#coarseBlockMarkerAgreesWithL1BlockIdentity} 对一批样本行
     * 断言「chat3 粗检命中 ⇔ 该行剥后交给 L1 实际产出块级身份（非段落字面）」，漂移即红。</p>
     *
     * @param text 消息原文（非 null）
     * @return 清洗后文本（无任何行命中块标记时同引用）
     */
    static String stripLeadingSectionCodes(String text) {
        if (text.indexOf('\u00a7') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        boolean changed = false;
        int pos = 0;
        while (true) {
            int br = text.indexOf('\n', pos);
            int end = br < 0 ? text.length() : br;
            String line = text.substring(pos, end);
            String cleaned = markerView(line);
            if (cleaned != line) {
                changed = true;
            }
            out.append(cleaned);
            if (br < 0) {
                break;
            }
            out.append('\n');
            pos = br + 1;
        }
        // 无任何行命中 ⇒ 同引用返回（「无命中不复制」与桥的零拷贝纪律同款；命中的视图行首
        // 必是块标记触发字符 ⇒ 二次调用恒不命中、恒同引用 = 幂等）
        return changed ? out.toString() : text;
    }

    /**
     * 单行块标记检测视图（乙′本体；自 C4 从 L1 拆除的 markerView 逐句照搬——循环结构与判据
     * 蓝本 {@code git show bd58b343^} 该文件 :172-210，勿简化成「只剥最前面连续码对」：
     * 蓝本是「至多 3 个空格 + 连续 § 码对」<b>交替</b>循环，空格位置保留进视图（嵌套列表
     * 缩进语义不变），{@code §f §a- x}、{@code §f  - x} 这类形态因此能正确命中；停住处首个
     * 非空白字符不是块标记触发字符时返回原行、不做任何剥除（{@code §f + 4 空格 + 标记}
     * ind&gt;3 不算命中 ⇒ 整行保留，行首色进桥））。
     *
     * @param line 源行（{@code \r} 只在行尾、不碍行首判定）
     * @return 命中块标记 ⇒ 剥后视图（新串）；否则原行同引用
     */
    static String markerView(String line) {
        if (line == null || line.isEmpty() || line.indexOf('\u00a7') < 0) {
            return line;
        }
        StringBuilder view = new StringBuilder(line.length());
        int i = 0;
        int stripped = 0;
        while (i < line.length()) {
            int spaces = 0;
            while (i + spaces < line.length() && line.charAt(i + spaces) == ' ' && spaces < 3) {
                spaces++;
            }
            view.append(line, i, i + spaces);
            int cursor = i + spaces;
            int eaten = 0;
            while (cursor + 1 < line.length() && line.charAt(cursor) == '\u00a7'
                    && isChatFormatCode(Character.toLowerCase(line.charAt(cursor + 1)))) {
                cursor += 2;
                eaten += 2;
            }
            stripped += eaten;
            if (eaten == 0) {
                // 蓝本 M5 修原样保留：本跳刚追加进视图的空格不得随 substring(i) 二次追加
                // （否则「§f + 空格 + 标记」视图缩进翻倍落回字面，丢失旧口径「行首 § 码后
                // 照常取标记」行为）
                i = cursor;
                break;
            }
            i = cursor;
        }
        if (stripped == 0) {
            return line;
        }
        view.append(line.substring(i));
        // 只有「视图中确实命中一个块标记」才允许消费 § 序列，否则原样交回（普通段落行的
        // 行首 § 码一字不动——容忍不得变成吞字）
        String candidate = view.toString();
        return coarseBlockStart(candidate) ? candidate : line;
    }

    /** 乙′粗检命中判据（一致性锁的 chat3 侧读数）：视图与原文不同引用 = 命中块标记才消费。 */
    static boolean coarseHitsBlockMarker(String line) {
        return markerView(line) != line;
    }

    // ==================== 块标记粗检（L1 真判据的包私有同构复刻，勿扩公共面） ====================

    /**
     * 反引号 U+0060：以无强转十六进制赋值书写——复生锁 G3 断言①禁 chat3 源码出现反引号
     * 字面、反斜杠-u 转义文本与 char 强转 0x60 三种形态（注释虽被扫描器剥除，书写面仍从严）。
     */
    private static final char FENCE_TICK_HEX = 0x60;

    /** 星号 U+002A：单引号字面被复生锁 G3 断言①列为旧行级规则定界形态，同样以常量代。 */
    private static final char MARK_STAR_HEX = 0x2A;

    /** 有序列表序号最大位数（L1 同值口径——粗检与真判据的漂移点之一就是这里）。 */
    private static final int MAX_ORDINAL_DIGITS = 9;

    /**
     * 块起始粗检：判据结构与 L1 已退役的 isBlockStart（蓝本 {@code bd58b343^} :212-222）
     * 逐句同构——围栏/ATX/分隔线/引用（对剥 ≤3 空格后的 body）或列表起始（对整视图，内部
     * 自数缩进）。<b>刻意不含 setext 下划线</b>（蓝本同缺：独立 {@code ===} 不是块起点，
     * 段落上下文中才可能升格，行首 § 序列不为其消费——一致性锁对此有专项样本）。
     */
    private static boolean coarseBlockStart(String view) {
        int ind = coarseLeadingSpaces(view);
        if (ind > 3 || ind >= view.length()) {
            return false;
        }
        String body = view.substring(ind);
        if (coarseFenceStart(body, 3) != 0 || coarseHeadingLevel(body) > 0
                || coarseThematicBreak(body) || body.charAt(0) == '>') {
            return true;
        }
        return coarseListStart(view);
    }

    /** 行首空格数（制表符不计入块缩进——L1 同款已裁简化，粗检必须同口径）。 */
    private static int coarseLeadingSpaces(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    /** ATX 标题级别；0 = 非标题。1..6 个 # 后须为空格/制表符/行尾（L1 同款）。 */
    private static int coarseHeadingLevel(String body) {
        int i = 0;
        while (i < body.length() && body.charAt(i) == '#') {
            i++;
        }
        if (i == 0 || i > 6) {
            return 0;
        }
        if (i == body.length()) {
            return i;
        }
        char next = body.charAt(i);
        return (next == ' ' || next == '\t') ? i : 0;
    }

    /** 围栏起始定界符字符；0 = 非围栏；backtick 围栏 info 含反引号不算起始（L1 同款）。 */
    private static char coarseFenceStart(String body, int minLen) {
        if (body.isEmpty()) {
            return 0;
        }
        char ch = body.charAt(0);
        if (ch != FENCE_TICK_HEX && ch != '~') {
            return 0;
        }
        int run = 0;
        while (run < body.length() && body.charAt(run) == ch) {
            run++;
        }
        if (run < minLen) {
            return 0;
        }
        if (ch == FENCE_TICK_HEX && body.indexOf(ch, run) >= 0) {
            return 0;
        }
        return ch;
    }

    /** 分隔线：整行仅一种星号/连字符/下划线加空白，且标记字符数 &gt;= 3（L1 同款）。 */
    private static boolean coarseThematicBreak(String body) {
        char unit = 0;
        int count = 0;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == ' ' || ch == '\t') {
                continue;
            }
            if (ch != '-' && ch != '_' && ch != MARK_STAR_HEX) {
                return false;
            }
            if (unit == 0) {
                unit = ch;
            } else if (ch != unit) {
                return false;
            }
            count++;
        }
        return count >= 3;
    }

    /**
     * 列表起始粗检（bool 版 matchListStart，蓝本 L1 :283-313）：无序 星/连/加 + 空格/制表符/
     * 行尾；有序 1..9 位数字 + 句点/右括号 + 同上（数字不解析、无异常面）。
     */
    private static boolean coarseListStart(String line) {
        int ind = coarseLeadingSpaces(line);
        if (ind > 3 || ind >= line.length()) {
            return false;
        }
        String body = line.substring(ind);
        char c0 = body.charAt(0);
        if ((c0 == '-' || c0 == MARK_STAR_HEX || c0 == '+')
                && (body.length() == 1 || body.charAt(1) == ' ' || body.charAt(1) == '\t')) {
            return true;
        }
        int digits = 0;
        while (digits < body.length() && digits <= MAX_ORDINAL_DIGITS
                && Character.isDigit(body.charAt(digits))) {
            digits++;
        }
        if (digits == 0 || digits > MAX_ORDINAL_DIGITS || digits >= body.length()) {
            return false;
        }
        char delim = body.charAt(digits);
        if (delim != '.' && delim != ')') {
            return false;
        }
        return digits + 1 >= body.length() || body.charAt(digits + 1) == ' '
                || body.charAt(digits + 1) == '\t';
    }

    /** MC 格式码字符集（0-9a-f 颜色、k-o 样式、r 重置；入参已小写化）。 */
    private static boolean isChatFormatCode(char lower) {
        return (lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f')
                || (lower >= 'k' && lower <= 'o') || lower == 'r';
    }

    /** 配色代指纹（次级色/链接色变更 → 两级缓存整体失效重算）。 */
    private static String cacheKey(String text, int baseColor) {
        return text + '@' + Integer.toHexString(baseColor) + '#'
                + Integer.toHexString(ChatMarkdownSettings.getTextSecondaryArgb()) + '#'
                + Integer.toHexString(ChatMarkdownSettings.getLinkArgb());
    }

    private static <V> Map<String, V> newLru(final int max) {
        return new LinkedHashMap<String, V>(64, 0.75F, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
                return size() > max;
            }
        };
    }

    /** 测试工厂：普通文本行视图（同包测试构造 clamp/形状断言样本用）。 */
    static RenderedLine renderedForTest(List<TextSegment> segments) {
        // 结构判据用的合成行：无块身份 ⇒ 几何字段恒定义值 0（不是「漏填」的第三口径）。
        return toRendered(false, false, 0, 0, 0, MarkdownLayoutLine.NO_BLOCK, 0, 0, 0, 0, segments);
    }

    /** 视觉行是否引用行（M5 旧结构判据，headless/调试兜底用；M7 生产判据 = RenderedLine.quoteLevel）。 */
    static boolean isQuoteRow(List<TextSegment> line) {
        int quoteColor = ChatMarkdownSettings.getTextSecondaryArgb();
        for (int i = 0; i < line.size(); i++) {
            TextSegment segment = line.get(i);
            String text = segment.getText();
            if (text == null || text.isEmpty()) {
                continue;
            }
            return segment.getStyle().getColor() == quoteColor;
        }
        return false;
    }

    /** 视觉行是否块级公式独占行（M5 结构判据，保留）：整行恰为一个 latex 原子段。 */
    static boolean isBlockMathRow(List<TextSegment> line) {
        return line.size() == 1 && line.get(0).isLatex();
    }

    /**
     * HUD 形态 8 行截断（设计稿 §5.4，M5 起作用于 L2 视觉行，M7 作用于 RenderedLine）：
     * 超过 8 行保留前 8 行，末行段流尾部追加省略号（与 {@code ChatCardComposer.ELLIPSIS}
     * 同款；行宽可用时有度量注入则先逐码点回退再补，保持「省略号不撑爆行」旧口径）。
     */
    static List<RenderedLine> clampHudLines(List<RenderedLine> lines,
            ChatMessageList.SegmentMeasurer measurer, int fontSizePx, int maxWidthPx) {
        int max = ChatCardComposer.HUD_MAX_LINES;
        if (lines.size() <= max) {
            return lines;
        }
        List<RenderedLine> out = new ArrayList<RenderedLine>(max);
        for (int i = 0; i < max - 1; i++) {
            out.add(lines.get(i));
        }
        RenderedLine last = lines.get(max - 1);
        List<TextSegment> segments = new ArrayList<TextSegment>(last.segments());
        appendEllipsis(segments, measurer, fontSizePx, Math.max(1, maxWidthPx - last.leftInsetPx()));
        out.add(toRendered(last.isCode(), last.isRule(), last.quoteLevel(), last.leftInsetPx(),
                last.indentStepPx(),
                last.blockId(), last.ruleThicknessPx(), last.accentArgb(), last.backgroundArgb(),
                last.blockContentWidthPx(), segments));
        return Collections.unmodifiableList(out);
    }

    /** 末行补省略号：可用度量在场且超宽时先逐码点回退再补（「省略号不撑爆行」旧口径）。 */
    private static void appendEllipsis(List<TextSegment> last,
            ChatMessageList.SegmentMeasurer measurer, int fontSizePx, int maxWidthPx) {
        String ellipsis = ChatCardComposer.ELLIPSIS;
        TextSegment tail = last.isEmpty() ? null : last.get(last.size() - 1);
        if (tail == null || tail.isLatex()) {
            // 空行或公式收尾：省略号独立成段（latex 原子不可剪）
            last.add(ellipsisSegment(tail));
            return;
        }
        String text = tail.getText();
        if (measurer != null && maxWidthPx > 0) {
            float base = 0.0F;
            for (TextSegment segment : last) {
                base += Math.max(0.0F, measurer.widthOf(segment, fontSizePx));
            }
            float tailWidth = Math.max(0.0F, measurer.widthOf(tail, fontSizePx));
            for (;;) {
                TextSegment candidate = new TextSegment(text + ellipsis, tail.getStyle());
                float width = base - tailWidth
                        + Math.max(0.0F, measurer.widthOf(candidate, fontSizePx));
                if (width <= maxWidthPx || text.isEmpty()) {
                    break;
                }
                text = text.substring(0, text.length() - 1);
            }
        }
        last.set(last.size() - 1, new TextSegment(text + ellipsis, tail.getStyle()));
    }

    private static TextSegment ellipsisSegment(TextSegment styleSource) {
        TextStyle style = styleSource == null ? new TextStyle() : styleSource.getStyle().copy();
        return new TextSegment(ChatCardComposer.ELLIPSIS, style);
    }
}
