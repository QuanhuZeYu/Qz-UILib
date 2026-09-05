package club.heiqi.uilib.ui.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;

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
 * <p>M4-fix 在本类落了两处「段流编码识别」，都不扩公共面：
 * 入口先做 {@link #unifySwitchPointSpaces}（F5：§ 颜色切换点的尾随空格归后一段——chat3 的
 * 行切分器把 § 码排在 pending 空白之前，故其空格恒归后段；本层只在度量完全一致时搬，
 * 逐字符推进宽与总行宽不变），再在 {@link #splitLogicalLines} 认 L1 的块边界占位段
 * （F6：空文本段 = 源里被空行分开的块边界）并强制产一个空显示行，
 * {@link #wrapVisualLine} 因此不再吞中间的空白逻辑行。</p>
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
        List<List<Token>> logicalTokens =
                splitLogicalLines(unifySwitchPointSpaces(segments), measurer, baseFontSizePx);
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

    // ==================== 块身份行路（M7 方案乙） ====================

    /**
     * 块身份行换行：逻辑行 → 视觉行（身份字段透传，折行宽度扣除该行左偏移）。
     *
     * <p>复用既有 token 引擎（splitLogicalLines/materialize 一字不动；wrapVisualLine 扩为
     * 「首行/续行」双宽，两宽相等时与旧单宽行为逐位一致），每行把可用宽改为
     * {@code maxWidthPx - leftInsetPx}——引用续行、块内折断天然继承行身份。
     * 可见文本与段流路逐字等值（L1 {@code MarkdownLayoutLinesTest} 钉死），
     * 折行差异<b>只</b>应出现在带左偏移的引用行（用户裁定的有意差异，规划 §二之三 M7 注记）。</p>
     *
     * <p><b>M10b 列表续行对齐正文列（2026-09-05 裁定 2）→ M10d「做全」（同日追加裁定）</b>：
     * 触发器从「kind==LIST 的块内列」换成「{@code getListMarkerChain()} 非空的行」——项内
     * 后续段落/标题/引用/围栏/嵌套子项虽另起 blockId、不带标记段，同样携链，因此同样吃到
     * 正文列（旧实现的已知缺口就此闭合）。正文列 = 沿链<b>逐级</b> {@code ceil(单元素表
     * (链元素) 推进宽)} 之和——与首行量标记段用的是<b>同一个度量器</b>（{@code lineAdvance} →
     * {@code resolveAdvance}，无第二把尺子；逐级取整因父列是已落定的整 px 几何，本级标记从
     * 父列起笔）。旧实现里「祖先份额 = 标记段里的 2 空格前导 × 层数」这一文本代理已废
     * （headless 16px 基准：代理 14/29/43 vs 真值 14/28/42，漂移方向逐档不同，实测见
     * {@code MarkdownListContinuationLockTest} 三级列硬值锁）。分档规则：块首 LIST 行（标记行）
     * 的第一个视觉行 = 引用份额 + 祖先份额（本级 {@code seg0} 实测宽从文本里挣），其余一切
     * 视觉行 = 引用份额 + 全额列；可用宽随 inset 同步扣减（{@code withLeftInsetPx} 写回接缝，
     * L2 出图/页面/聊天三侧共读）。零段行（项内围栏空行/横线行）只平移不折行。本层是
     * 全仓唯一算正文列的地方；<b>无链行</b>（不在列表项内）路径一字不改。</p>
     */
    static List<MarkdownLayoutLine> layoutLines(List<MarkdownLayoutLine> logicalLines,
            TextLayoutService measurer, int maxWidthPx, int baseFontSizePx) {
        requireMeasurer(measurer);
        List<MarkdownLayoutLine> out = new ArrayList<MarkdownLayoutLine>();
        if (logicalLines == null || logicalLines.isEmpty()) {
            out.add(MarkdownLayoutLine.blank());
            return Collections.unmodifiableList(out);
        }
        // M10d 前置扫：按 blockId 记「该块首条 LIST 行的下标」——只有这一行的 seg0 是本级
        // 标记段，它的第一个视觉行不吃本级宽（标记从祖先列起笔，正文从标记之后起笔）。
        Map<Integer, Integer> listMarkerRow = new HashMap<Integer, Integer>();
        for (int i = 0; i < logicalLines.size(); i++) {
            MarkdownLayoutLine line = logicalLines.get(i);
            if (line.getKind() != MarkdownLayoutLine.Kind.LIST
                    || line.getBlockId() == MarkdownLayoutLine.NO_BLOCK
                    || listMarkerRow.containsKey(Integer.valueOf(line.getBlockId()))) {
                continue;
            }
            listMarkerRow.put(Integer.valueOf(line.getBlockId()), Integer.valueOf(i));
        }
        for (int i = 0; i < logicalLines.size(); i++) {
            MarkdownLayoutLine line = logicalLines.get(i);
            List<TextSegment> segments = line.getSegments();
            List<TextSegment> chain = line.getListMarkerChain();
            int column = listColumnPx(chain, measurer, baseFontSizePx);
            if (segments.isEmpty()) {
                // 空行/无线文本的分隔线行：一行即一显示行；M10d 项内零段行仍平移吃列
                // （围栏底色/横线矩形随行头缘平移，块内连续性由「同块同列」保证）。
                if (column > 0) {
                    out.add(line.withLeftInsetPx(line.getLeftInsetPx() + column));
                } else {
                    out.add(line);
                }
                continue;
            }
            Integer markerIdxKey = line.getKind() == MarkdownLayoutLine.Kind.LIST
                    ? listMarkerRow.get(Integer.valueOf(line.getBlockId())) : null;
            boolean isMarkerRow = markerIdxKey != null && markerIdxKey.intValue() == i;
            // 本级宽 = 标记段自身实测（= 链尾元素，文本与样式同源；非标记行为 0）。
            int ownColumn = isMarkerRow
                    ? (int) Math.ceil(lineAdvance(Collections.singletonList(segments.get(0)),
                            measurer, baseFontSizePx))
                    : 0;
            int inset = line.getLeftInsetPx();
            int restInset = inset + column;
            int firstInset = isMarkerRow ? restInset - ownColumn : restInset;
            if (chain.isEmpty() || (column == 0 && firstInset == inset)) {
                // 原路径（无列表归属行；column==0 只在圆点空串退化时出现）：一字不改
                List<List<Token>> tokenLines = splitLogicalLines(
                        unifySwitchPointSpaces(segments), measurer, baseFontSizePx);
                int availablePx = maxWidthPx <= 0
                        ? 0 : Math.max(1, maxWidthPx - inset);
                List<List<TextSegment>> visual = new ArrayList<List<TextSegment>>();
                for (int t = 0; t < tokenLines.size(); t++) {
                    wrapVisualLine(tokenLines.get(t), availablePx, visual, baseFontSizePx);
                }
                for (int v = 0; v < visual.size(); v++) {
                    out.add(copyWithSegments(line, visual.get(v)));
                }
                continue;
            }
            // 列表归属行悬挂列路径（M10d）：标记行首视觉行 = 祖先份额，其余视觉行 = 全额列
            List<List<Token>> tokenLines = splitLogicalLines(
                    unifySwitchPointSpaces(segments), measurer, baseFontSizePx);
            for (int t = 0; t < tokenLines.size(); t++) {
                List<List<TextSegment>> visual = new ArrayList<List<TextSegment>>();
                boolean firstKeepsInset = isMarkerRow && t == 0;
                int firstRowInset = firstKeepsInset ? firstInset : restInset;
                wrapVisualLine(tokenLines.get(t),
                        availableForWidth(maxWidthPx, firstRowInset),
                        availableForWidth(maxWidthPx, restInset),
                        visual, baseFontSizePx);
                for (int v = 0; v < visual.size(); v++) {
                    int rowInset = v == 0 ? firstRowInset : restInset;
                    MarkdownLayoutLine copy = copyWithSegments(line, visual.get(v));
                    out.add(rowInset == copy.getLeftInsetPx()
                            ? copy : copy.withLeftInsetPx(rowInset));
                }
            }
        }
        // M8 单一真相：块内统一内容宽在本层（唯一持度量服务处）算出并写入行对象——
        // L2 出图矩形、聊天面板、devtools 页三面对这一个数（规划 §二之七·续 第 9 条）。
        return Collections.unmodifiableList(unifyCodeBlockContentWidth(out, measurer, baseFontSizePx));
    }

    /**
     * 按 blockId 把 CODE 视觉行的「块内统一内容宽」写进行对象（M8 上收；三面对一份真相的产地）。
     *
     * <p>口径：<b>同 blockId 全部 CODE 视觉行自身文字宽（{@code ceil(段流推进宽)}）的最大值，
     * 下限 1</b>；非 CODE 行一律不写（保持 {@code 0 = 不适用}）。下限 1 是给「整块皆空行」的围栏
     * 仍留一条可辨识底色，且不破坏「块宽 &ge; 每行自身宽」这条不变量（空行自身宽为 0）。</p>
     *
     * <p>本方法是块宽的<b>唯一实现</b>：消费层（L3 与 devtools 页）只读
     * {@link MarkdownLayoutLine#getBlockContentWidthPx()}，不得各自再抄一份查表/取最大
     * ——那正是本轮要拆掉的装配侧第二套真相。</p>
     */
    private static List<MarkdownLayoutLine> unifyCodeBlockContentWidth(List<MarkdownLayoutLine> lines,
            TextLayoutService measurer, int baseFontSizePx) {
        Map<Integer, Integer> widestByBlock = new HashMap<Integer, Integer>();
        for (int i = 0; i < lines.size(); i++) {
            MarkdownLayoutLine line = lines.get(i);
            if (line.getKind() != MarkdownLayoutLine.Kind.CODE) {
                continue;
            }
            Integer key = Integer.valueOf(line.getBlockId());
            int own = (int) Math.ceil(lineAdvance(line.getSegments(), measurer, baseFontSizePx));
            Integer old = widestByBlock.get(key);
            if (old == null || own > old.intValue()) {
                widestByBlock.put(key, Integer.valueOf(own));
            }
        }
        if (widestByBlock.isEmpty()) {
            return lines; // 零围栏：一行都不必重建（非 CODE 行的 0 = 不适用天然成立）
        }
        List<MarkdownLayoutLine> out = new ArrayList<MarkdownLayoutLine>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            MarkdownLayoutLine line = lines.get(i);
            if (line.getKind() != MarkdownLayoutLine.Kind.CODE) {
                out.add(line);
                continue;
            }
            Integer widest = widestByBlock.get(Integer.valueOf(line.getBlockId()));
            out.add(line.withBlockContentWidthPx(Math.max(1, widest == null ? 0 : widest.intValue())));
        }
        return out;
    }

    /**
     * 块身份命令流：视觉行 → BACKGROUND（引用竖条/真横线/围栏底色）+ SEGMENTS + LINK_REGION。
     *
     * <p>三类几何全部落在既有 {@link PaintCommandType} 图元上（用户裁定：真横线 = 一条
     * 1px 高的 BACKGROUND，引用竖条、围栏底色同理）——零新增图元。底色/竖条/横线命令先于
     * 文本发出（软件回放按列表顺序绘制，几何在字下）。围栏块按连续同 blockId 合并为覆盖
     * 全部显示行的单矩形；引用竖条逐层逐行成段（行间 y 相邻 → 视觉连续）。</p>
     */
    static List<PaintCommand> blockCommands(List<MarkdownLayoutLine> visualLines,
            TextLayoutService measurer, int maxWidthPx, int baseFontSizePx) {
        requireMeasurer(measurer);
        List<PaintCommand> out = new ArrayList<PaintCommand>();
        if (visualLines == null || visualLines.isEmpty()) {
            return Collections.unmodifiableList(out);
        }
        int n = visualLines.size();
        int[] tops = new int[n];
        int[] heights = new int[n];
        int cursor = 0;
        double maxContentRight = 0.0D;
        for (int i = 0; i < n; i++) {
            MarkdownLayoutLine line = visualLines.get(i);
            heights[i] = lineHeightPx(line.getSegments(), measurer, baseFontSizePx);
            tops[i] = cursor;
            cursor += heights[i];
            double right = line.getLeftInsetPx()
                    + lineAdvance(line.getSegments(), measurer, baseFontSizePx);
            if (right > maxContentRight) {
                maxContentRight = right;
            }
        }
        int contentRight = maxWidthPx > 0
                ? maxWidthPx : Math.max(1, (int) Math.ceil(maxContentRight));

        // 1) 围栏底色：连续同 blockId 的 CODE 视觉行合并为单矩形
        int i = 0;
        while (i < n) {
            MarkdownLayoutLine line = visualLines.get(i);
            if (line.getKind() == MarkdownLayoutLine.Kind.CODE && line.getBackgroundArgb() != 0) {
                int j = i;
                while (j + 1 < n && visualLines.get(j + 1).getKind() == MarkdownLayoutLine.Kind.CODE
                        && visualLines.get(j + 1).getBlockId() == line.getBlockId()) {
                    j++;
                }
                // 宽恒取块内统一内容宽（M8 上收；同 blockId 各行的 getter 同值）。
                // 旧口径「铺满容器右缘」是与两路消费者都不一致的第四套数，故弃
                //（规划 §二之七·续 第 9 条；核心锁 MarkdownBlockContentWidthLockTest 钉死）。
                out.add(PaintCommand.background(line.getLeftInsetPx(), tops[i],
                        line.getLeftInsetPx() + Math.max(1, line.getBlockContentWidthPx()),
                        tops[j] + heights[j], line.getBackgroundArgb()));
                i = j + 1;
                continue;
            }
            i++;
        }
        // 2) 引用竖条 + 分隔线真横线
        for (int k = 0; k < n; k++) {
            MarkdownLayoutLine line = visualLines.get(k);
            int level = line.getQuoteLevel();
            if (level > 0 && line.getAccentArgb() != 0) {
                int step = line.getIndentStepPx();
                int barWidth = line.getBarWidthPx();
                for (int l = 0; l < level; l++) {
                    int left = l * step;
                    out.add(PaintCommand.background(left, tops[k],
                            Math.max(left + 1, left + barWidth), tops[k] + heights[k],
                            line.getAccentArgb()));
                }
            }
            if (line.getKind() == MarkdownLayoutLine.Kind.THEMATIC_BREAK
                    && line.getAccentArgb() != 0) {
                int thickness = Math.max(1, line.getRuleThicknessPx());
                int left = line.getLeftInsetPx();
                int top = tops[k] + Math.max(0, (heights[k] - thickness) / 2);
                out.add(PaintCommand.background(left, top,
                        Math.max(left + 1, contentRight), top + thickness, line.getAccentArgb()));
            }
        }
        // 3) 文本 + 命中区（左缘整体平移 leftInsetPx）
        for (int t = 0; t < n; t++) {
            MarkdownLayoutLine line = visualLines.get(t);
            List<TextSegment> segments = line.getSegments();
            if (segments.isEmpty()) {
                continue;
            }
            int left = line.getLeftInsetPx();
            out.add(PaintCommand.segments(segments, left, tops[t], Math.max(1, baseFontSizePx)));
            appendLinkRegions(out, segments, measurer, baseFontSizePx, tops[t], heights[t], left);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * 视觉行复制：身份与几何字段原样继承，仅换段流（materialize 产物）。M10d 起必须走
     * {@code withSegments}（类内拷贝法）而不是公共 10 参构造器——后者会把列表归属链与块
     * 内容宽一并丢掉，视觉行退化成「无列表身份」，续行/项内块的列就断了（旧实现靠
     * 「列已在 leftInsetPx 里」侥幸成立，链一上接缝就是隐性回归，此处是踩坑点，记死）。
     */
    private static MarkdownLayoutLine copyWithSegments(MarkdownLayoutLine source,
            List<TextSegment> segments) {
        return source.withSegments(segments);
    }

    /**
     * 沿链正文列（M10d，全仓唯一算列处）：Σ 逐级 {@code ceil(链元素推进宽)}。链元素自带
     * L1 产出该标记时的样式（引用斜体改变推进宽），故引用内列表与顶层列表各按各尺；
     * 「逐级取整」而非「双精度求和后一次取整」——父正文列是已渲染落定的整 px 几何，
     * 本级标记段从父列起笔，其自身宽度也按同一把尺（{@code lineAdvance}）单独取整。
     */
    private static int listColumnPx(List<TextSegment> chain, TextLayoutService measurer,
            int baseFontSizePx) {
        if (chain == null || chain.isEmpty()) {
            return 0;
        }
        int column = 0;
        for (int i = 0; i < chain.size(); i++) {
            column += (int) Math.ceil(lineAdvance(
                    Collections.singletonList(chain.get(i)), measurer, baseFontSizePx));
        }
        return column;
    }

    // ==================== 换行 ====================

    /**
     * § 切换点空格归属归一（M4-fix F5，采 chat3 口径：切换点的空格归<b>后</b>一段）。
     *
     * <p><b>相反切分产生在哪一环</b>：A 路是「逐显示行 parse」——
     * {@code ChatLineLayouter.splitFragments} 把空白先扣在 {@code pendingSpaces} 里，遇到 § 码对
     * 却<em>立即</em>并入行文本（{@code ChatLineLayouter.java:227-233} 的格式码分支排在
     * {@code :235-239} 的空白分支之前），于是源里的 {@code §c红色警告 §fplain} 到行文本上已成
     * {@code §c红色警告§f plain}，再交给 § 解析，空格天然落在<strong>后</strong>一段。
     * B 路的段流在进本层之前就已按 § 码切好，同一个空格留在<strong>前</strong>一段——
     * 差的是「布局期按显示行归一」这一环，不是语义。</p>
     *
     * <p><b>为什么落在 L2</b>：空格归属是排版事实（chat3 也是在行切分器里做的，见上），
     * 不是解析事实；放进 L1 的 {@code MarkdownInlineParser.parse(spans)} 会改掉 span 流既有契约
     * （既有测试 {@code shouldParseSpanStream} 钉死「前缀 」尾随空格归前段）。本方法只做
     * <b>度量中性</b>的切点搬移：字符全序一字不动，只是把上一段末尾的连续空格并进后一段头部，
     * 且要求两侧 {@code fontSizePx}/{@code FontType}/{@code italic} 完全相同、两侧都不是
     * code / link / latex 段——因此逐字符推进宽、总行宽、切行位置全部不变，变的只有段边界。</p>
     *
     * <p>不吞 L1 的块边界占位标记段（空文本段），也不改写调用方传入的段与样式实例。</p>
     *
     * @param segments 输入段流（可为 null）
     * @return 归一后的段流；无需归一时同引用返回原列表
     */
    static List<TextSegment> unifySwitchPointSpaces(List<TextSegment> segments) {
        if (segments == null || segments.size() < 2) {
            return segments;
        }
        int size = segments.size();
        List<TextSegment> out = null;
        int i = 0;
        while (i < size) {
            TextSegment current = segments.get(i);
            TextSegment next = i + 1 < size ? segments.get(i + 1) : null;
            if (!isSpaceNeutralPair(current, next)) {
                if (out != null) {
                    out.add(current);
                }
                i++;
                continue;
            }
            String text = current.getText();
            int keep = text.length();
            while (keep > 0 && text.charAt(keep - 1) == ' ') {
                keep--;
            }
            if (keep == text.length()) {
                if (out != null) {
                    out.add(current);
                }
                i++;
                continue;
            }
            if (out == null) {
                out = new ArrayList<TextSegment>(size + 2);
                for (int k = 0; k < i; k++) {
                    out.add(segments.get(k));
                }
            }
            if (keep > 0) {
                out.add(new TextSegment(text.substring(0, keep), current.getStyle()));
            }
            out.add(new TextSegment(text.substring(keep) + next.getText(), next.getStyle()));
            i += 2;
        }
        return out == null ? segments : out;
    }

    /**
     * 该相邻段对是否允许做「度量中性」的空格归一。
     *
     * <p>判据两半：<b>(1) 切换必须与 § 样式码同形</b>——两侧差异只剩颜色/下划线/删除线
     * （A 路的 § 色码切换正是这种切换）；<b>(2) 搬动不得改变任何推进宽度</b>——两侧
     * {@code FontType}、{@code fontSizePx}、{@code italic} 必须逐项相同，且两侧都不是 code /
     * link / latex 段。条件 (2) 是硬性安全阀：门禁的段宽容差是 {@code 0.0px}（位级等值），
     * 若把空格从 13px 段搬进 12px 的 code 段、或跨字体搬动，浮点求和顺序一变就会把已经
     * PASS 的条目判红（实测：P13 的「{@code • } + {@code 玩家列表行}」两段同为白色 NORMAL，
     * 一旦搬动即出现 1e-14 量位的浮点差 → 段宽差异）。</p>
     *
     * @param left  前一段（不可为 null）
     * @param right 后一段（可为 null）
     * @return true = 允许把 left 末尾空格并进 right 头部
     */
    private static boolean isSpaceNeutralPair(TextSegment left, TextSegment right) {
        if (left == null || right == null || left.isLatex() || right.isLatex()) {
            return false;
        }
        String leftText = left.getText();
        String rightText = right.getText();
        // 空文本段 = L1 的 F6 块边界占位标记段，不参与归一；右侧已是空格开头则无切点可归一
        if (leftText.isEmpty() || rightText.isEmpty() || rightText.charAt(0) == ' ') {
            return false;
        }
        TextStyle a = left.getStyle();
        TextStyle b = right.getStyle();
        if (a == null || b == null) {
            return false;
        }
        if (a.isCodeSpan() || b.isCodeSpan() || a.getLink() != null || b.getLink() != null) {
            return false;
        }
        // 宽度必须逐项同尺，搬动才可能位级无损
        if (a.getFontType() != b.getFontType() || a.getFontSizePx() != b.getFontSizePx()
                || a.isItalic() != b.isItalic()) {
            return false;
        }
        // 只在「§ 颜色码切换点」归一（F5 裁定的原场景）：两侧颜色必须真的不同。
        // 下划线/删除线/字重差异不触发——那些差异在 chat3 里由 §l/§m/§n 产生，本层不猜；
        // 且无差异时归一会改变浮点求和顺序，把已 PASS 的条目判成段宽差异（P13 实测）。
        return a.getColor() != b.getColor();
    }

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
            if (segment.getText().isEmpty()) {
                // F6（走 C1）：L1 在「吃掉过源空行的块边界」的 \n 段后紧跟一个空文本占位标记段。
                // 认它加一空行（与 chat3 一致：甲\n\n乙 = 3 显示行，中间一行零段）。
                lines.add(current);
                current = new ArrayList<Token>();
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

    /** 容器宽 → 扣除行左偏移后的可用宽（{@code maxWidthPx <= 0} = 不限宽，与旧语义同）。 */
    private static int availableForWidth(int maxWidthPx, int insetPx) {
        return maxWidthPx <= 0 ? 0 : Math.max(1, maxWidthPx - insetPx);
    }

    /** 单逻辑行 → 视觉行（词边界回退 + 无空格硬断，K3 语义），materialize 后追加到 out。 */
    private static void wrapVisualLine(List<Token> tokens, int maxWidthPx, List<List<TextSegment>> out,
            int baseFontSizePx) {
        wrapVisualLine(tokens, maxWidthPx, maxWidthPx, out, baseFontSizePx);
    }

    /**
     * 单逻辑行 → 视觉行，首行与续行分别限宽（M10b 列表悬挂缩进；两宽相等时与旧单宽逐位一致）。
     *
     * <p>判「首行」只看<b>本次调用</b>是否还未向 out 吐过行（起始尺寸比较），跨调用累计的
     * out 不影响判定；空逻辑行兜底产出的零段行同样计入吐行。除限宽数值外，切分算法
     * 一字未动——段流路 {@link #wrap} 恒走两宽相等路径，与门禁对拍行为逐位不变。</p>
     */
    private static void wrapVisualLine(List<Token> tokens, int firstMaxWidthPx, int restMaxWidthPx,
            List<List<TextSegment>> out, int baseFontSizePx) {
        int startSize = out.size();
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
            // 正在填的行是本次调用的首视觉行吗——是则用首行宽，否则用续行宽（两宽相等时恒等旧值）
            int maxWidthPx = out.size() == startSize ? firstMaxWidthPx : restMaxWidthPx;
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
        } else {
            // 空逻辑行必须留成一个空显示行（F6）：与 chat3 行切分器对 \n\n 的实测口径一致
            // ——中间空行产一段零段行，仅当整段流只有一个空逻辑行时也是单空行（既有行为不变）。
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
        appendLinkRegions(out, line, measurer, baseFontSizePx, top, height, 0);
    }

    /** 带左偏移的命中区累计（M7 块身份路：SEGMENTS 平移多少，命中区平移多少）。 */
    private static void appendLinkRegions(List<PaintCommand> out, List<TextSegment> line,
            TextLayoutService measurer, int baseFontSizePx, int top, int height, int leftOffset) {
        double x = (double) leftOffset;
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
