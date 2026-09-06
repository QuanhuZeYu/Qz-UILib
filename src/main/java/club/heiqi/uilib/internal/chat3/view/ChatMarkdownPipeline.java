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
import club.heiqi.uilib.font.layout.markdown.MarkdownSpan;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatCardComposer;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatUrlLinkifier;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;

/**
 * chat3 消息级 markdown 管道（M5 接线本体；规划《通用Markdown渲染器》§三 M5/§二 L3）。
 *
 * <p><b>C6b 方案甲落地（2026-09-07，取代 M7 时期的「预清洗 + 输出后置桥」乙′接线）</b>：
 * 顺序 = 门禁 B 路定义顺序的对应升级形：消息原文 → <b>§ → 样式锚点 span 流转换</b>
 * （{@link #toSpanStream}，MC 特有格式在集成层<b>进 markdown 之前</b>一次性消化：§ 码对经
 * {@code TextStyle.applyFormat} 逐码解释为该处生效样式，码本身不进文本；语义与 L0
 * {@code TextLayoutService.parseSegments} 同源，L1 对 § 零认知的宪法不变）→
 * {@link MarkdownDocument#parseSpans(java.util.List)} →
 * {@link MarkdownDocument#toLayoutLines(MarkdownStyleTable, TextStyle)}（逻辑行 +
 * kind/quoteLevel/行盒几何/块归属）→ {@link ChatUrlLinkifier#linkify}（<b>换行前</b>整条流
 * 链接化的逐行形态，理由同旧）→ {@link MarkdownPainter#wrapLayoutLines}（L2 换行，折行
 * 宽度按行扣除引用缩进）。乙′ 的两处结构性缺陷随输入侧转换根除：围栏内形似块标记的
 * § 行不再被误剥（围栏内容恒字面，样式锚点保留）；容器标记后的 § + 块标记
 * （{@code > §a- x}）由 L1 在纯文本上自然升格。出段样式定序 = 宿主显式色优先于块级色
 * （规划 §二之八 C6b 细账；对齐旧 § 桥「markdown 位先叠、§ 码后生效」的裁定）。
 * 产出的 {@link RenderedLine} 携带引用层级与 CODE/RULE 身份，{@link ChatMessageList} 据此
 * 用既有 SceneNode 能力（背景色节点/竖条/嵌套行）表达三项块级几何——<b>块模型与 L1/L2
 * 类型不外泄出本文件</b>（复生锁 G3 断言④口径不变），消费方面向 {@link RenderedLine}
 * 自有视图类型，可见 API 面零变化。</p>
 *
 * <p><b>每帧零解析（规划 §六 3）</b>：两级 LRU 沿用 {@code ChatLineLayouter} 既有布局缓存
 * 纪律——逻辑行缓存 key = <b>消息原文</b>@基础色#配色代（转换/解析/链接化与字体无关，
 * 配色变更即时失效；C6b：key 吃未转换原文，§ → span 转换只在未命中时做）；
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

    /** 逻辑行缓存：原文@基础色#配色代 → span 转换+解析+链接化后的逻辑行（含块身份；C6b：桥已退役）。 */
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
     *                    含 § 码对的原文直接进链路——C6b 方案甲：先经 {@link #toSpanStream}
     *                    转样式锚点 span 流再喂 {@code parseSpans}，§ 不进 markdown）
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
        // C6b 甲（归位宪法不变：L1 对 § 零认知，MC 特有格式只在本集成层消费——消费点从
        // 「清洗+后置桥」两处收拢为「输入转换」一处，细账见规划 §二之八 C6b）：两级缓存 key
        // 吃消息**原文**（displayText 未转换形态），§ → span 流转换只在缓存未命中时做。
        // 自洽论证：转换是 (原文, baseColor) 的纯函数（toSpanStream 无外部状态），baseColor 与
        // 配色代指纹（cacheKey 吃次级色/链接色现值）都在 key 上 ⇒ 同 key ⇒ 同一语义输入，
        // 结构上不存在「同 key 不同语义」；「§a- x」与「- x」这类转换后等值的**不同原文**
        // 分占条目——只回退去重效率，不会串味。乙′ 吃清洗后文本的旧口径随预清洗退役。
        String text = messageText == null ? "" : messageText;
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

    /**
     * 转换 → 解析（span 流入口）→ 链接化（换行前），带逻辑行缓存；后处理在入缓存前逐行施加。
     * C6b：key 吃原文（缓存未命中才做 § → span 转换）；旧输出侧 {@code bridgeSectionCodes}
     * 随输入侧转换退役——markdown 段流不再含可消费 § 码对（仅剩行尾孤立 § 字面，桥对它们
     * 本就无力，桥退役前的 no-op 实证见 {@code ChatMarkdownSectionSpanMigrationLockTest}）。
     */
    private List<MarkdownLayoutLine> logicalCached(String text, int baseColor,
            ChatMessageList.SegmentPostProcessor postProcessor) {
        String key = cacheKey(text, baseColor) + (postProcessor == null ? "" : "#p");
        List<MarkdownLayoutLine> hit = logicalCache.get(key);
        if (hit != null) {
            return hit;
        }
        TextStyle base = new TextStyle();
        base.setColor(baseColor);
        List<MarkdownLayoutLine> logical = MarkdownDocument.parseSpans(toSpanStream(text, base))
                .toLayoutLines(chatStyleTable(), base);
        List<MarkdownLayoutLine> processed = new ArrayList<MarkdownLayoutLine>(logical.size());
        for (int i = 0; i < logical.size(); i++) {
            MarkdownLayoutLine line = logical.get(i);
            List<TextSegment> segments = line.getSegments();
            if (postProcessor != null && !segments.isEmpty()) {
                segments = postProcessor.postProcess(segments, ChatMarkdownSettings.getChatFontSizePx());
            }
            segments = ChatUrlLinkifier.linkify(segments, ChatMarkdownSettings.getLinkArgb());
            processed.add(line.withSegments(segments));
        }
        processed = Collections.unmodifiableList(processed);
        logicalCache.put(key, processed);
        return processed;
    }

    /**
     * § → 样式锚点 {@link MarkdownSpan} 流转换器（C6b 方案甲落地形；chat3 气泡路唯一的
     * § 解释点，宪法②「§ 只能作为 chat3 集成层的输入转换存在」的实现处）。
     *
     * <p>扫描语义与旧输出侧桥 splitRunsOnFormatCodes（退役镜像见迁移锁
     * {@code ChatMarkdownSectionSpanMigrationLockTest.RetiredBPrime}）及 L0
     * {@code TextLayoutService.parseSegments} <b>同源</b>（不另造第二套 § 解释）：§ 与其后一
     * 字符构成码对、{@code TextStyle.applyFormat(code, baseColor)} 逐码消费，码对本身不进
     * span 文本；大小写同义；未知码对走 applyFormat 的 default 分支（= 重置，与 L0/原版同形，
     * 色码同时清先前 § 样式位亦是 MC 语义）。<b>行界不可跨</b>：紧邻 {@code \n}/{@code \r}
     * 的孤立 § 不消费、按字面进文本——旧桥按逐行段流作业、从来看不到跨行码对，而换行是 L1
     * 块检测的输入材料，此处吞行界等于伪造第二套切行。与旧桥的既裁差异：码效应沿<b>整条
     * 消息</b>累计（跨 markdown 段/跨软换行不重启）——色码在加粗/斜体等强调边界之后
     * 不再丢色（方案甲的设计意图，规划 §二之八 C6b 细账差异清单第 5 条）。</p>
     *
     * <p>起始样式经 {@code resetAll(baseColor)} 表达：caller 底色 {@code colorExplicit=false}。
     * 按 C6b 定序裁定（L1 resolve/applied 同一把尺），只有<b>显式着色</b>的 span 覆盖引用降色
     * 等块级色；未着色的底色 span 让位块级——与旧桥「§ 码改过色的段才变色、其余承块级色」
     * 逐位对齐（§r 重置后亦回落为非显式 ⇒ 引用内 {@code §c甲§r乙} 的乙仍吃引用色，同旧）。</p>
     *
     * @param text       消息原文（非 null；可含 § 码对与换行）
     * @param callerBase 气泡正文基样式（取色值与字体基状态；转换起点为其 resetAll 拷贝）
     * @return 样式锚点 span 流（无可见文本时为空表；直喂 {@link MarkdownDocument#parseSpans}）
     */
    static List<MarkdownSpan> toSpanStream(String text, TextStyle callerBase) {
        int baseColor = callerBase.getColor();
        TextStyle current = callerBase.copy();
        current.resetAll(baseColor);
        List<MarkdownSpan> out = new ArrayList<MarkdownSpan>();
        if (text.indexOf('\u00a7') < 0) {
            // 无 § 快路径：整条消息一个底色 span（「无 § 不复制」与旧桥/旧清洗的零拷贝纪律同款）
            if (!text.isEmpty()) {
                out.add(new MarkdownSpan(text, current));
            }
            return out;
        }
        StringBuilder buffer = new StringBuilder(text.length());
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()
                    && text.charAt(i + 1) != '\n' && text.charAt(i + 1) != '\r') {
                flushSpan(out, buffer, current);
                current = current.copy();
                current.applyFormat(Character.toLowerCase(text.charAt(i + 1)), baseColor);
                i += 2;
                continue;
            }
            buffer.append(c);
            i++;
        }
        flushSpan(out, buffer, current);
        return out;
    }

    /** 缓冲成段（空缓冲不成段——MarkdownSpan 拒空文本；连续码对之间恒走此空段路径）。 */
    private static void flushSpan(List<MarkdownSpan> out, StringBuilder buffer, TextStyle style) {
        if (buffer.length() == 0) {
            return;
        }
        out.add(new MarkdownSpan(buffer.toString(), style));
        buffer.setLength(0);
    }

    /** 测试工厂：消息原文 → 逻辑行（生产同路同缓存；C6b 迁移等价锁的甲侧读数）。 */
    synchronized List<MarkdownLayoutLine> logicalForTest(String messageText, int baseColor) {
        return logicalCached(messageText == null ? "" : messageText, baseColor, null);
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
