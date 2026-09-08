package club.heiqi.uilib.ui.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument.LayoutContent;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument.TableUnit;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;

/**
 * markdown L2 绘制层门面（规划《通用Markdown渲染器》§二 L2 / §五 D1）。
 *
 * <p><b>历史两条接缝保留，表格通过显式文档入口接入</b>：
 * <ul>
 *   <li><b>段流路（恒保留）</b>：{@code List<TextSegment>} 扁平接缝（{@link #wrapLines} /
 *       {@link #toPaintCommands}）——行为逐位不变，对拍门禁 {@code MarkdownChat3ParityTest}
 *       的 B 路定义与本层历史消费者继续走这条;</li>
 *   <li><b>块身份行路（M7 新增）</b>：{@code List<MarkdownLayoutLine>}（{@link #wrapLayoutLines} /
 *       {@link #toLayoutPaintCommands}）——行粒度块身份 + 行盒几何进接缝，本层据此产
 *       BACKGROUND 块级几何（引用每层竖条、真横线、围栏块底色）。<b>块模型本身仍不外泄</b>
 *       （{@code MarkdownBlock}/{@code MarkdownBlockParser} 恒 package-private）；可见文本
 *       与段流路逐字等值，几何一律经位置与图元表达。</li>
 *   <li><b>文档内容路</b>：{@link #layoutContent} 接收平行逻辑行/表格 units，
 *       按块锚合成同一绘制计划；只由已迁移的消费者调用，历史两路表格仍字面显示。</li>
 * </ul></p>
 *
 * <p><b>本层认得的两种段流编码</b>（M4-fix 落地，仍是零公共面变更——两者都只用既有的
 * {@code text}/{@code style} 通道）：
 * <ul>
 *   <li><b>块边界占位段</b>（F6，规划 §二之四 C1）：源里被空行分开的两块，L1 在换行段之后
 *       紧跟一个「文本为空串」的占位段；本层认它加一空行（与 chat3 对 {@code 甲\n\n乙}
 *       产 3 显示行一致）。空行不产 SEGMENTS 命令，只占一份行高。</li>
 *   <li><b>§ 切换点空格归属</b>（F5）：见 {@code MarkdownLineLayout#unifySwitchPointSpaces}
 *       ——只在两侧度量完全一致、差异仅为颜色的切换点把尾随空格并入后一段，逐字符推进宽
 *       与总行宽一字不变。</li>
 * </ul></p>
 *
 * <p><b>输出 = 仓内既有绘制抽象</b>：{@link PaintCommand} 流（每视觉行一条 SEGMENTS 命令 +
 * 链接段 LINK_REGION 命中区，与 {@code ScenePaintEngine} 同一契约），段流节点
 * {@code SceneNode.setSegments} 亦直接吃 {@link #wrapLines} 的产物（chat3 消息行同款范式）。
 * 本包零 {@code GL11} 直调（规划 §四 G1 由 MarkdownLayerGuardTest 锁死），
 * 度量恒走注入的 {@link TextLayoutService}（G4 度量同源，零自设字号/行高常量）。</p>
 *
 * <p><b>零每帧解析（规划 §六 3）</b>：解析/换行都只在文档到达或容器宽变化时做一次，
 * 缓存责任在消费层；本门面是纯函数（同输入 + 同度量代 → 同输出），不持任何状态。</p>
 *
 * <p>纯 JVM 无 Minecraft/AWT import（G2 锁）；{@code TextLayoutService} 在未注入字形表时
 * 自动退化为 AWT 直测口径，与既有场景测量链路一致。</p>
 */
public final class MarkdownPainter {

    private MarkdownPainter() {
    }

    /**
     * 已定位的统一绘制计划；消费层按内容、宽度、基础字号和度量纪元缓存。
     * 集合不可修改；段流遵循 PaintCommand 的只读约定，调用方不得修改其 TextStyle。
     */
    public static final class ContentLayout {
        private final List<PaintCommand> commands;
        private final int heightPx;
        private final int widthPx;

        private ContentLayout(List<PaintCommand> commands, int heightPx, int widthPx) {
            this.commands = Collections.unmodifiableList(new ArrayList<PaintCommand>(commands));
            this.heightPx = heightPx;
            this.widthPx = widthPx;
        }

        public List<PaintCommand> getCommands() { return commands; }
        public int getHeightPx() { return heightPx; }
        /** 实际内容右缘；不可拆公式/码点的最小宽超过容器时可大于 maxWidthPx。 */
        public int getWidthPx() { return widthPx; }
    }

    /**
     * 带表格的显式布局入口。先按逻辑行锚切普通行区间，再插入表格计划；
     * 同锚的表格按序定位，普通行仍由既有 MarkdownLineLayout 处理。
     * maxWidthPx 小于等于零表示不约束宽度；空文档的高度为零。
     * Page 与 headless 消费此同一计划，历史行路和段流路保持字面表格。
     */
    public static ContentLayout layoutContent(LayoutContent content, TextLayoutService measurer,
                                              int maxWidthPx, int baseFontSizePx) {
        if (content == null || measurer == null) {
            throw new IllegalArgumentException("content 与 measurer 不可为 null");
        }
        List<PaintCommand> out = new ArrayList<PaintCommand>();
        int before = 0;
        int y = 0;
        int width = 0;
        for (TableUnit unit : content.getTables()) {
            int end = unit.getBeforeLineIndex();
            MarkdownLayoutLine marker = end > before ? content.getLines().get(end - 1) : null;
            boolean inlineMarker = isDirectBlockMarker(marker, unit.getContext());
            ContentLayout run = layoutRun(content.getLines().subList(before, inlineMarker ? end - 1 : end),
                    measurer, maxWidthPx, baseFontSizePx);
            appendTranslated(out, run.commands, y);
            y += run.heightPx;
            width = Math.max(width, run.widthPx);
            MarkdownTableLayout.Result table = MarkdownTableLayout.layout(unit, measurer, maxWidthPx, baseFontSizePx);
            appendTranslated(out, table.commands, y);
            if (inlineMarker) {
                appendMarker(out, marker, measurer, maxWidthPx, baseFontSizePx,
                        y + unit.getBorderPx() + unit.getPaddingYPx());
            }
            y += table.height;
            width = Math.max(width, table.width);
            before = unit.getBeforeLineIndex();
        }
        ContentLayout tail = layoutRun(content.getLines().subList(before, content.getLines().size()),
                measurer, maxWidthPx, baseFontSizePx);
        appendTranslated(out, tail.commands, y);
        return new ContentLayout(out, y + tail.heightPx, Math.max(width, tail.widthPx));
    }

    private static void appendMarker(List<PaintCommand> out, MarkdownLayoutLine marker,
            TextLayoutService measurer, int maxWidthPx, int font, int y) {
        List<MarkdownLayoutLine> visual = MarkdownLineLayout.layoutLines(
                Collections.singletonList(marker), measurer, 0, font);
        List<PaintCommand> textOnly = new ArrayList<PaintCommand>();
        for (PaintCommand command : MarkdownLineLayout.blockCommands(visual, measurer, maxWidthPx, font)) {
            // 表格/数学块自身拥有引用装饰；marker 保留原列表列，只投放一次。
            if (command.getType() != club.heiqi.uilib.ui.scene.paint.PaintCommandType.BACKGROUND) {
                textOnly.add(command);
            }
        }
        appendTranslated(out, textOnly, y);
    }

    /** 首表格/数学列表项保留独立标记事件；只对直属、零正文标记行消除额外行高。 */
    private static boolean isDirectBlockMarker(MarkdownLayoutLine marker, MarkdownLayoutLine context) {
        if (marker == null || marker.getKind() != MarkdownLayoutLine.Kind.LIST
                || marker.getSegments().size() != 1 || marker.getQuoteLevel() != context.getQuoteLevel()
                || marker.getBlockId() + 1 != context.getBlockId()) {
            return false;
        }
        List<TextSegment> chain = context.getListMarkerChain();
        if (chain.isEmpty() || marker.getListMarkerChain().size() != chain.size()
                || marker.getSegments().get(0) != chain.get(chain.size() - 1)) {
            return false;
        }
        // 同一文档投影共享标记段身份；文字恰等于圆点的普通正文不能冒充直属标记。
        for (int i = 0; i < chain.size(); i++) {
            if (marker.getListMarkerChain().get(i) != chain.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static ContentLayout layoutRun(List<MarkdownLayoutLine> lines, TextLayoutService measurer,
                                           int maxWidthPx, int font) {
        if (lines.isEmpty()) {
            return new ContentLayout(Collections.<PaintCommand>emptyList(), 0, 0);
        }
        List<MarkdownLayoutLine> body = new ArrayList<MarkdownLayoutLine>();
        java.util.Map<Integer, MarkdownLayoutLine> markers = new java.util.HashMap<Integer, MarkdownLayoutLine>();
        for (int i = 0; i < lines.size(); i++) {
            MarkdownLayoutLine line = lines.get(i);
            MarkdownLayoutLine next = i + 1 < lines.size() ? lines.get(i + 1) : null;
            if (next != null && next.getKind() == MarkdownLayoutLine.Kind.MATH_DISPLAY
                    && isDirectBlockMarker(line, next)) {
                markers.put(next.getBlockId(), line);
            } else {
                body.add(line);
            }
        }
        List<MarkdownLayoutLine> visual = MarkdownLineLayout.layoutLines(body, measurer, maxWidthPx, font);
        int height = 0;
        int width = 0;
        MarkdownLineLayout.VisualLine[] measured = new MarkdownLineLayout.VisualLine[visual.size()];
        for (int i = 0; i < visual.size(); i++) {
            MarkdownLayoutLine line = visual.get(i);
            measured[i] = new MarkdownLineLayout.VisualLine(line.getSegments(), measurer, font);
            height += measured[i].height;
            int inkShift = line.getKind() == MarkdownLayoutLine.Kind.MATH_DISPLAY ? 0 : measured[i].leftOverhang;
            width = Math.max(width, line.getLeftInsetPx() + inkShift + measured[i].inkRight);
        }
        List<PaintCommand> commands = new ArrayList<PaintCommand>(
                MarkdownLineLayout.blockCommands(visual, measurer, maxWidthPx, font, measured));
        int y = 0;
        for (int i = 0; i < visual.size(); i++) {
            MarkdownLayoutLine marker = markers.remove(visual.get(i).getBlockId());
            if (marker != null) appendMarker(commands, marker, measurer, maxWidthPx, font, y);
            y += measured[i].height;
        }
        for (PaintCommand command : commands) {
            width = Math.max(width, command.getRight());
        }
        return new ContentLayout(commands, height, width);
    }

    private static void appendTranslated(List<PaintCommand> out, List<PaintCommand> commands, int y) {
        for (PaintCommand command : commands) {
            switch (command.getType()) {
                case BACKGROUND:
                    out.add(PaintCommand.background(command.getLeft(), command.getTop() + y,
                            command.getRight(), command.getBottom() + y, command.getColor()));
                    break;
                case SEGMENTS:
                    out.add(PaintCommand.segments(command.getSegments(), command.getLeft(), command.getTop() + y,
                            command.getTextStyle().getFontSize()));
                    break;
                case LINK_REGION:
                    out.add(PaintCommand.linkRegion(command.getLeft(), command.getTop() + y,
                            command.getRight(), command.getBottom() + y, command.getLinkUrl()));
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected markdown command: " + command.getType());
            }
        }
    }

    /**
     * 段流换行：把 markdown 段流按硬换行（{@code \n}）与容器宽切成视觉行。
     *
     * <p>切分语义承接 chat3 行切分器既有裁定（K3）：{@code \n} 硬断；空白是断行机会、
     * 行尾空白丢弃；普通散文超宽在最后一个词边界回退断行，无空格长串才字符级硬断；
     * 公式段是不可拆原子；行内尚无可见内容时强制放置，不产生零内容空行。</p>
     *
     * @param segments       markdown 段流（{@code MarkdownDocument.toSegments} 产物；null/空 → 单空行）
     * @param measurer       度量服务（不可为 null；推进宽度恒走其
     *                       {@code resolveAdvance/getSegmentWidth} 唯一原语）
     * @param maxWidthPx     内容盒最大宽度（UI 像素）；{@code <= 0} 视为不限宽（只按 {@code \n} 断行）
     * @param baseFontSizePx 基准字号（UI 像素，段未显式指定字号时使用）
     * @return 视觉行列表；每行是段流（不可变视图，行内不出现 {@code \n}）
     */
    public static List<List<TextSegment>> wrapLines(List<TextSegment> segments,
            TextLayoutService measurer, int maxWidthPx, int baseFontSizePx) {
        return MarkdownLineLayout.wrap(segments, measurer, maxWidthPx, baseFontSizePx);
    }

    /**
     * 段流 → {@link PaintCommand} 绘制流（规划 §二 L2「输出 PaintCommand 流」）。
     *
     * <p>先 {@link #wrapLines} 换行，再按行累计 y 游标：非空行产出一条 SEGMENTS 命令
     * （节点局部坐标，{@code left = 0}，{@code top} 为行框顶，与 {@code ScenePaintEngine}
     * 的 em-box 顶语义同口径）；带链接样式的段追加产出 LINK_REGION 命中区命令
     * （纯数据，回放器不渲染，供消费层做点击命中）。空行不产命令、只走行高游标。</p>
     *
     * @param segments       markdown 段流（null/空 → 空命令流）
     * @param measurer       度量服务（不可为 null）
     * @param maxWidthPx     内容盒最大宽度（UI 像素）；{@code <= 0} 不限宽
     * @param baseFontSizePx 基准字号（UI 像素）
     * @return 不可变命令流（总高 = 末命令 {@code getTop()} + 末行行高，或
     *         {@link #measureHeight}）
     */
    public static List<PaintCommand> toPaintCommands(List<TextSegment> segments,
            TextLayoutService measurer, int maxWidthPx, int baseFontSizePx) {
        return MarkdownLineLayout.toCommands(segments, measurer, maxWidthPx, baseFontSizePx);
    }

    /**
     * 单视觉行实测宽（UI 像素，上取整）。供消费层给段流节点钉
     * {@code setPreferredWidth}（chat3 消息行同款用法）。
     *
     * @param line           视觉行（{@link #wrapLines} 产物；行内不应含 {@code \n}）
     * @param measurer       度量服务（不可为 null）
     * @param baseFontSizePx 基准字号（UI 像素）
     * @return 行宽（{@code >= 0}）
     */
    public static int lineWidthPx(List<TextSegment> line, TextLayoutService measurer,
            int baseFontSizePx) {
        return MarkdownLineLayout.lineWidthPx(line, measurer, baseFontSizePx);
    }

    /**
     * 单视觉行行高（UI 像素）：取行内最大段有效字号的 ascent+descent+lineGap
     * （三段全部来自注入度量，零自设常量）。公式段按自身有效字号参与取大，
     * 与 chat3 现行口径一致；需要把超高公式缩进行盒的消费层可先调既有
     * {@code TextLayoutService.applyLatexLineHeightConstraint} 再进本层。
     *
     * @param line           视觉行（可为空行，空行按基准字号计）
     * @param measurer       度量服务（不可为 null）
     * @param baseFontSizePx 基准字号（UI 像素）
     * @return 行高（{@code >= 1}）
     */
    public static int lineHeightPx(List<TextSegment> line, TextLayoutService measurer,
            int baseFontSizePx) {
        return MarkdownLineLayout.lineHeightPx(line, measurer, baseFontSizePx);
    }

    /**
     * 整段绘制的总高（UI 像素）= 逐视觉行行高累计（与 {@link #toPaintCommands} 的 y 游标同源）。
     * 供消费层钉滚动范围，避免再走一遍命令流。
     *
     * @param segments       markdown 段流
     * @param measurer       度量服务（不可为 null）
     * @param maxWidthPx     内容盒最大宽度（UI 像素）
     * @param baseFontSizePx 基准字号（UI 像素）
     * @return 总高（{@code >= 0}）
     */
    public static int measureHeight(List<TextSegment> segments, TextLayoutService measurer,
            int maxWidthPx, int baseFontSizePx) {
        return MarkdownLineLayout.measureHeight(segments, measurer, maxWidthPx, baseFontSizePx);
    }

    /**
     * 块身份行换行（M7）：L1 {@code MarkdownDocument.toLayoutLines} 的逻辑行 → 视觉行。
     *
     * <p>切分语义与 {@link #wrapLines} 完全同源（同一 token 引擎），唯一差异：每行的折行
     * 宽度<b>扣除该行左偏移</b>（{@code maxWidthPx - line.getLeftInsetPx()}）——引用文字
     * 因此不溢出容器；引用的断点与 chat3 旧行为不同属用户裁定的有意差异（规划 §二之三 M7 注记 /
     * §二之五 登记表说明）。身份字段（kind/quoteLevel/blockId/几何/装饰色）逐视觉行透传，
     * 消费层据此表达块级几何；{@code getSegments()} 的可见文本与段流路逐字等值。</p>
     *
     * <p><b>M8 块内统一内容宽的产地（唯一实现）</b>：本方法是全仓唯一计算围栏「块内统一宽」的地方
     * ——按 {@code blockId} 聚合该 CODE 块全部视觉行的自身文字宽取最大，写进
     * {@link MarkdownLayoutLine#getBlockContentWidthPx()}。L2 出图的合并底色矩形、聊天面板
     * ({@code ChatMessageList})、devtools 演示页 ({@code MarkdownPage}) 三侧都只读这一个数
     * （规划 §二之七·续 第 9 条；恒等式「矩形宽 == getter」由
     * {@code MarkdownBlockContentWidthLockTest} 锁死）。非 CODE 行该字段恒 {@code 0 = 不适用}。</p>
     *
     * @param logicalLines   逻辑行序列（null/空 → 单空行）
     * @param measurer       度量服务（不可为 null）
     * @param maxWidthPx     内容盒最大宽度（UI 像素）；{@code <= 0} = 不限宽（只按行边界断行）
     * @param baseFontSizePx 基准字号（UI 像素）
     * @return 视觉行序列（不可变列表）
     */
    public static List<MarkdownLayoutLine> wrapLayoutLines(List<MarkdownLayoutLine> logicalLines,
            TextLayoutService measurer, int maxWidthPx, int baseFontSizePx) {
        return MarkdownLineLayout.layoutLines(logicalLines, measurer, maxWidthPx, baseFontSizePx);
    }

    /**
     * 块身份命令流（M7）：逻辑行 → {@link PaintCommand} 流，块级几何全部落在既有图元上
     * （用户裁定：真横线 = 一条 1px 高的 BACKGROUND；引用竖条、围栏块底色同理——零新造图元）。
     *
     * <p>先 {@link #wrapLayoutLines} 换行，再按序产出：围栏底色（连续同块行合并为覆盖全部
     * 显示行的单矩形，<b>其宽恒取该块的 {@code getBlockContentWidthPx()}——与两路消费者同数</b>，
     * M8 起不再铺至容器右缘）→ 引用竖条（逐层逐行，y 相邻成视觉连续柱）与分隔线真横线（
     * ruleThicknessPx 高、铺至内容右缘）→ 每文本行 SEGMENTS（{@code left = leftInsetPx}）
     * + LINK_REGION（同偏移平移）。几何数值恒取行上的样式表解析值（G4 度量同源，本包零
     * GL11 直调、零自设常量，由 MarkdownLayerGuardTest 锁死）。</p>
     *
     * @param logicalLines   L1 逻辑行序列（null/空 → 空命令流）
     * @param measurer       度量服务（不可为 null）
     * @param maxWidthPx     内容盒最大宽度（UI 像素；真横线铺至该右缘，折行扣宽亦以它为基准。
     *                       <b>M8 起围栏底色不再铺至本参数的右缘</b>，改用块内统一内容宽）
     * @param baseFontSizePx 基准字号（UI 像素）
     * @return 不可变命令流
     */
    public static List<PaintCommand> toLayoutPaintCommands(List<MarkdownLayoutLine> logicalLines,
            TextLayoutService measurer, int maxWidthPx, int baseFontSizePx) {
        List<MarkdownLayoutLine> visual = MarkdownLineLayout.layoutLines(
                logicalLines, measurer, maxWidthPx, baseFontSizePx);
        // 空输入换行产物 = 单空行：零段/零层级/零色 → blockCommands 天然零命令
        return MarkdownLineLayout.blockCommands(visual, measurer, maxWidthPx, baseFontSizePx);
    }
}
