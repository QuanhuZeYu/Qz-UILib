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
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;

/**
 * chat3 消息级 markdown 管道（M5 接线本体；规划《通用Markdown渲染器》§三 M5/§二 L3）。
 *
 * <p><b>C7 划界（2026-09-07，取代 C6b 方案甲的「§ → 样式锚点 span 流」输入转换）</b>：
 * 顺序 = 门禁 B 路定义顺序：气泡消息原文 → {@link MarkdownDocument#parse(String)} →
 * {@link MarkdownDocument#toLayoutLines(MarkdownStyleTable, TextStyle)}（逻辑行 +
 * kind/quoteLevel/行盒几何/块归属）→ {@link ChatUrlLinkifier#linkify}（<b>换行前</b>整条流
 * 链接化的逐行形态，理由同旧）→ {@link MarkdownPainter#wrapLayoutLines}（L2 换行，折行
 * 宽度按行扣除引用缩进）。<b>本层不再有任何 § 机制</b>：既无 C6b 的输入转换器，也无乙′ 的
 * 预清洗/输出桥——玩家消息的内容侧在 {@code StructuredChatReader}（原版
 * {@code chat.type.text} 结构参数）与 {@code SenderExtractor}（正则兜底）两条通道上<b>都已
 * 取自 unformatted 源</b>，而 {@code ChatComponentStyle.getFormattedText()} 逐组件注样式码
 * （实测 {@code <§rSteve§r> §r<b>hi</b>§r}）正是旧 § 残渣的唯一来源，从源头断开后残渣不存在。
 * 于是 markdown 路径的输入是纯文本，§（若来自非 vanilla 服务端格式）按定案 4 当<b>普通字符
 * 原样显示</b>——L1 对 § 零认知，本层也不引入任何 § 识别/剥离/转换（常驻守卫
 * {@code MarkdownL1ZeroSectionKnowledgeGuardTest} + 正向锁
 * {@code MarkdownSectionCodeIsPlainTextLockTest}）。
 * {@link MarkdownDocument#parseSpans(java.util.List)} 入口保留（将来富文本 component 通道用），
 * 但本层不再经它，且它不得被任何 § 相关代码使用。
 * 产出的 {@link RenderedLine} 携带引用层级与 CODE/RULE 身份，{@link ChatMessageList} 据此
 * 用既有 SceneNode 能力（背景色节点/竖条/嵌套行）表达三项块级几何——<b>块模型与 L1/L2
 * 类型不外泄出本文件</b>（复生锁 G3 断言④口径不变），消费方面向 {@link RenderedLine}
 * 自有视图类型，可见 API 面零变化。</p>
 *
 * <p><b>每帧零解析（规划 §六 3）</b>：两级 LRU 沿用 {@code ChatLineLayouter} 既有布局缓存
 * 纪律——逻辑行缓存 key = <b>最终喂进 {@code MarkdownDocument.parse} 的那个字符串</b>
 * @基础色#配色代（解析/链接化与字体无关，配色变更即时失效）；视觉行缓存 key = 逻辑行
 * key#定行宽#字号#度量纪元（{@code DefaultTextMeasureService#getEpoch()}：字体换代代 + 宽度收敛代）。
 * 渲染帧只在结构重建时
 * 命中缓存，不逐帧 parse。缓存按实例隔离。<b>单轨纪律（C7 第 8 条）</b>：键与 parse 输入同源
 * 同值，不存在「一处用原文、一处用结构内容」的两把尺（见 {@link #cacheKey}）。</p>
 *
 * <p>系统消息不走本管道（§3.5 排版规则仅作用于气泡内，行级旧行为原样保留在
 * {@link ChatMessageList} 的系统路）。</p>
 */
final class ChatMarkdownPipeline {

    /** 逻辑行缓存上限（历史 100 行 + 配色切换余量）。 */
    private static final int LOGICAL_CACHE_MAX = 200;
    /** 视觉行缓存上限（同 {@code ChatLineLayouter.MAX_ENTRIES} 口径）。 */
    private static final int LINES_CACHE_MAX = 160;

    /** 逻辑行缓存：parse 输入文本@基础色#配色代 → 解析+链接化后的逻辑行（含块身份；C7：§ 转换退役）。 */
    private final Map<String, List<MarkdownLayoutLine>> logicalCache = newLru(LOGICAL_CACHE_MAX);
    /** 视觉行缓存：逻辑行 key#定行宽#字号#度量纪元 → 换行产物。 */
    private final Map<String, List<RenderedLine>> linesCache = newLru(LINES_CACHE_MAX);

    /** 显式文档缓存按原文保存解析树，宽/字体/配色变化均不重复解析。 */
    private final Map<String, ContentEntry> contentCache = newLru(LOGICAL_CACHE_MAX);
    private final java.util.function.Function<String, MarkdownDocument> contentParser;

    ChatMarkdownPipeline() {
        this(MarkdownDocument::parse);
    }

    ChatMarkdownPipeline(java.util.function.Function<String, MarkdownDocument> contentParser) {
        this.contentParser = contentParser;
    }

    static final class RenderedContent {
        final List<PaintLeaf> leaves;
        final int width;
        final int height;

        RenderedContent(List<PaintLeaf> leaves, int width, int height) {
            this.leaves = Collections.unmodifiableList(new ArrayList<PaintLeaf>(leaves));
            this.width = width;
            this.height = height;
        }
    }

    /** L2 命令及同源度量叶盒；消费 helper 不认识 markdown 类型。 */
    static final class PaintLeaf {
        final club.heiqi.uilib.ui.scene.paint.PaintCommand command;
        final int width;
        final int height;
        final int top;

        PaintLeaf(club.heiqi.uilib.ui.scene.paint.PaintCommand command, int width, int height) {
            this(command, width, height, command.getTop());
        }

        PaintLeaf(club.heiqi.uilib.ui.scene.paint.PaintCommand command, int width, int height, int top) {
            this.command = command;
            this.width = width;
            this.height = height;
            this.top = top;
        }
    }

    private static final class ContentEntry {
        final MarkdownDocument document;
        final boolean tables;
        final boolean displayMath;
        int baseColor;
        int font;
        int epoch;
        int secondaryColor;
        int linkColor;
        MarkdownDocument.LayoutContent projection;
        TextLayoutService measurer;
        ChatMessageList.SegmentPostProcessor processor;
        // 同一消息仅保留 HUD 与展开 occurrence 最近两个正文宽。
        final Map<Integer, RenderedContent> rendered = newLru(2);

        ContentEntry(MarkdownDocument document) {
            this.document = document;
            MarkdownDocument.LayoutContent content = document.toLayoutContent(chatStyleTable(), new TextStyle());
            tables = !content.getTables().isEmpty();
            boolean math = false;
            for (MarkdownLayoutLine line : content.getLines()) {
                if (line.getKind() == MarkdownLayoutLine.Kind.MATH_DISPLAY) { math = true; break; }
                for (TextSegment segment : line.getSegments()) {
                    if (segment.isLatex() && segment.getLatexMathStyle()
                            == club.heiqi.uilib.font.latex.MathStyleOverride.DISPLAY) { math = true; break; }
                }
            }
            displayMath = math;
        }
    }

    private ContentEntry contentEntry(String source) {
        String text = source == null ? "" : source;
        ContentEntry entry = contentCache.get(text);
        if (entry == null) {
            entry = new ContentEntry(contentParser.apply(text));
            contentCache.put(text, entry);
        }
        return entry;
    }

    synchronized boolean hasTables(String source) {
        return contentEntry(source).tables;
    }

    synchronized boolean hasDisplayMath(String source) {
        return contentEntry(source).displayMath;
    }

    synchronized RenderedContent layoutContent(String source, int baseColor, int width, int font,
            ChatMessageList.SegmentPostProcessor processor) {
        FontService fonts = FontService.getInstance();
        return layoutContent(source, baseColor, width, font, processor,
                fonts.getTextLayoutService(), DefaultTextMeasureService.getInstance().getEpoch());
    }

    synchronized RenderedContent layoutContent(String source, int baseColor, int width, int font,
            ChatMessageList.SegmentPostProcessor processor, TextLayoutService measurer, int epoch) {
        ContentEntry entry = contentEntry(source);
        int available = Math.max(1, width);
        int secondaryColor = ChatMarkdownSettings.getTextSecondaryArgb();
        int linkColor = ChatMarkdownSettings.getLinkArgb();
        boolean sameProjection = entry.projection != null && entry.baseColor == baseColor && entry.font == font
                && entry.epoch == epoch && entry.secondaryColor == secondaryColor && entry.linkColor == linkColor
                && entry.processor == processor && entry.measurer == measurer;
        RenderedContent hit = sameProjection ? entry.rendered.get(available) : null;
        if (hit != null) return hit;
        if (!sameProjection) {
            TextStyle base = new TextStyle();
            base.setColor(baseColor);
            MarkdownDocument.LayoutContent raw = entry.document.toLayoutContent(chatStyleTable(), base);
            final int[] mappedLine = {0};
            entry.projection = raw.mapSegments(segments -> {
                int lineIndex = mappedLine[0]++;
                boolean displayBlock = lineIndex < raw.getLines().size()
                        && raw.getLines().get(lineIndex).getKind() == MarkdownLayoutLine.Kind.MATH_DISPLAY;
                List<TextSegment> processed = processor == null || segments.isEmpty() || displayBlock ? segments
                        : processor.postProcess(segments, font);
                return ChatUrlLinkifier.linkify(processed, ChatMarkdownSettings.getLinkArgb());
            });
            entry.rendered.clear();
            entry.baseColor = baseColor;
            entry.font = font;
            entry.epoch = epoch;
            entry.secondaryColor = secondaryColor;
            entry.linkColor = linkColor;
            entry.processor = processor;
            entry.measurer = measurer;
        }
        MarkdownPainter.ContentLayout plan = MarkdownPainter.layoutContent(entry.projection, measurer, available, font);
        List<PaintLeaf> leaves = new ArrayList<PaintLeaf>();
        int top = 0;
        int bottom = plan.getHeightPx();
        int right = plan.getWidthPx();
        for (club.heiqi.uilib.ui.scene.paint.PaintCommand command : plan.getCommands()) {
            int w = Math.max(1, command.getRight() - command.getLeft());
            int h = Math.max(1, command.getBottom() - command.getTop());
            if (command.getType() == club.heiqi.uilib.ui.scene.paint.PaintCommandType.SEGMENTS) {
                int size = command.getTextStyle().getFontSize();
                w = Math.max(1, MarkdownPainter.lineWidthPx(command.getSegments(), measurer, size));
                h = Math.max(1, MarkdownPainter.lineHeightPx(command.getSegments(), measurer, size));

            }
            top = Math.min(top, command.getTop());
            bottom = Math.max(bottom, command.getTop() + h);
            right = Math.max(right, command.getLeft() + w);
            leaves.add(new PaintLeaf(command, w, h));
        }
        if (top < 0) {
            List<PaintLeaf> shifted = new ArrayList<PaintLeaf>();
            for (PaintLeaf leaf : leaves) shifted.add(new PaintLeaf(leaf.command, leaf.width, leaf.height, leaf.top - top));
            leaves = shifted;
        }
        RenderedContent rendered = new RenderedContent(leaves, right, bottom - top);
        entry.rendered.put(available, rendered);
        return rendered;
    }

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
     * @param messageText   气泡消息本体（{@code ChatCardComposer.MessageLines.getDisplayText()}；
     *                    C7 起恒为 unformatted 源——原版 {@code chat.type.text} 的
     *                    {@code getFormatArgs()[1]} 或正则 rest——即最终喂进 markdown 的那个
     *                    字符串，与缓存 key 同源同值。非 vanilla 形带进来的 § 按定案当普通字符
     *                    原样显示，本层不解释）
     * @param baseColor     气泡正文基础色（ARGB）
     * @param maxWidthPx    定行宽（与行切分器同口径；{@code <= 0} = 只按行边界硬断）
     * @param fontSizePx    正文基准字号（UI px）
     * @param postProcessor 段流后处理（T8 LaTeX 行高约束；null = 关闭）
     * @param wrapOverride  视觉行换行注入（headless 测试用与行切分器同源度量的替身；
     *                      null = 生产路 {@link MarkdownPainter#wrapLayoutLines} +
     *                      {@code FontService} 度量——与 {@code uiLibMeasure}/{@code uiLibSegmentMeasurer}
     *                      三者同源，钳宽/换行/渲染一把尺）。注入时按逻辑行逐次调用
     *                      （maxWidthPx 已扣该行左偏移），产行继承该逻辑行身份
     * @return 不可变视觉行列表（空/null 文本 → 空表，消费方 {@code ChatMessageList} 按 0 行渲染；
     *         旧句「至少一行」与两代实现均不符，C7 就地更正并由锁钉住现状）
     */
    synchronized List<RenderedLine> layout(String messageText, int baseColor, int maxWidthPx,
            int fontSizePx, ChatMessageList.SegmentPostProcessor postProcessor,
            ChatMessageList.SegmentFlowWrapper wrapOverride) {
        // C7 划界（细账见规划 §二之八 C7）：两级缓存 key = **最终喂进 MarkdownDocument.parse
        // 的那个字符串**@基础色#配色代。转换器已退役 ⇒ 本方法入参 text 就是 parse 的输入，
        // 「一处用原文、一处用结构内容」的双轨在结构上不存在（key 与 parse 同源同值，
        // 单一真相）。baseColor 与配色代指纹同在 key 上 ⇒ 同 key ⇒ 同一语义输入。
        String text = messageText == null ? "" : messageText;
        List<MarkdownLayoutLine> logical = logicalCached(text, baseColor, postProcessor, fontSizePx);
        int epoch = DefaultTextMeasureService.getInstance().getEpoch();
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

    /**
     * 解析（{@link MarkdownDocument#parse(String)}）→ 链接化（换行前），带逻辑行缓存；
     * 后处理在入缓存前逐行施加。
     *
     * <p>C7：本层不再有 § → span 输入转换（C6b 方案甲随划界退役），入口回到 String 形
     * （{@code parse(text)} + {@code toLayoutLines(table, base)}）。{@code parseSpans} 保留在
     * L1 公共面（将来富文本 component 入口用），但 § 相关代码一律不得再经它——本层已无 §
     * 相关代码。缓存 key 恒等于此处喂进 {@code parse} 的 {@code text}（单轨，见
     * {@link #cacheKey}）。</p>
     */
    private List<MarkdownLayoutLine> logicalCached(String text, int baseColor,
            ChatMessageList.SegmentPostProcessor postProcessor, int fontSizePx) {
        // RC-06：后处理（LaTeX 行高约束）吃基准字号 ⇒ 字号必须并入逻辑行缓存 key；
        // 否则 150% 下同一 text 命中 100% 的旧逻辑行，公式缩放判定与渲染字号脱钩。
        String key = cacheKey(text, baseColor) + (postProcessor == null ? "" : "#p" + fontSizePx);
        List<MarkdownLayoutLine> hit = logicalCache.get(key);
        if (hit != null) {
            return hit;
        }
        TextStyle base = new TextStyle();
        base.setColor(baseColor);
        // 显式非表格消息已为通道判断解析过；玩家路未命中仍保持原解析入口。
        ContentEntry known = contentCache.get(text);
        MarkdownDocument document = known == null ? MarkdownDocument.parse(text) : known.document;
        List<MarkdownLayoutLine> logical = document.toLayoutLines(chatStyleTable(), base);
        List<MarkdownLayoutLine> processed = new ArrayList<MarkdownLayoutLine>(logical.size());
        for (int i = 0; i < logical.size(); i++) {
            MarkdownLayoutLine line = logical.get(i);
            List<TextSegment> segments = line.getSegments();
            if (postProcessor != null && !segments.isEmpty()) {
                segments = postProcessor.postProcess(segments, fontSizePx);
            }
            segments = ChatUrlLinkifier.linkify(segments, ChatMarkdownSettings.getLinkArgb());
            processed.add(line.withSegments(segments));
        }
        processed = Collections.unmodifiableList(processed);
        logicalCache.put(key, processed);
        return processed;
    }


    /** 测试工厂：消息本体 → 逻辑行（生产同路同缓存；chat3 markdown 入口的直读缝）。 */
    synchronized List<MarkdownLayoutLine> logicalForTest(String messageText, int baseColor) {
        return logicalCached(messageText == null ? "" : messageText, baseColor, null,
                ChatMarkdownSettings.getChatFontSizePx());
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
     * 配色代指纹（次级色/链接色变更 → 两级缓存整体失效重算）。
     *
     * <p>C7 第 8 条（单轨）：{@code text} 形参就是 {@code logicalCached} 里喂进
     * {@code MarkdownDocument.parse} 的那个字符串——装配侧（{@code ChatCardComposer}
     * 三条分支）与缓存侧共用同一个值，不留「一处用原文、一处用结构内容」的两把尺。</p>
     */
    private static String cacheKey(String text, int baseColor) {
        return text + '@' + Integer.toHexString(baseColor) + '#'
                + Integer.toHexString(ChatMarkdownSettings.getTextSecondaryArgb()) + '#'
                + Integer.toHexString(ChatMarkdownSettings.getLinkArgb());
    }

    private static <K, V> Map<K, V> newLru(final int max) {
        return new LinkedHashMap<K, V>(64, 0.75F, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
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