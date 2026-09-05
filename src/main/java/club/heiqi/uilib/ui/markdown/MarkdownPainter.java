package club.heiqi.uilib.ui.markdown;

import java.util.List;

import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;

/**
 * markdown L2 绘制层门面（规划《通用Markdown渲染器》§二 L2 / §五 D1）。
 *
 * <p><b>两条接缝并存（M7 方案乙，2026-09-05 用户裁定重开裁定 B 的块几何部分）</b>：
 * <ul>
 *   <li><b>段流路（恒保留）</b>：{@code List<TextSegment>} 扁平接缝（{@link #wrapLines} /
 *       {@link #toPaintCommands}）——行为逐位不变，对拍门禁 {@code MarkdownChat3ParityTest}
 *       的 B 路定义与本层历史消费者继续走这条;</li>
 *   <li><b>块身份行路（M7 新增）</b>：{@code List<MarkdownLayoutLine>}（{@link #wrapLayoutLines} /
 *       {@link #toLayoutPaintCommands}）——行粒度块身份 + 行盒几何进接缝，本层据此产
 *       BACKGROUND 块级几何（引用每层竖条、真横线、围栏块底色）。<b>块模型本身仍不外泄</b>
 *       （{@code MarkdownBlock}/{@code MarkdownBlockParser} 恒 package-private）；可见文本
 *       与段流路逐字等值，几何一律经位置与图元表达。</li>
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
     * 显示行的单矩形）→ 引用竖条（逐层逐行，y 相邻成视觉连续柱）与分隔线真横线（
     * ruleThicknessPx 高、铺至内容右缘）→ 每文本行 SEGMENTS（{@code left = leftInsetPx}）
     * + LINK_REGION（同偏移平移）。几何数值恒取行上的样式表解析值（G4 度量同源，本包零
     * GL11 直调、零自设常量，由 MarkdownLayerGuardTest 锁死）。</p>
     *
     * @param logicalLines   L1 逻辑行序列（null/空 → 空命令流）
     * @param measurer       度量服务（不可为 null）
     * @param maxWidthPx     内容盒最大宽度（UI 像素；横线/底色铺至该右缘）
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
