package club.heiqi.uilib.internal.devtools.playground.pages;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;

/** 页面私有 L3：源与样式在构造时冻结；像素计划仅随宽、字号、度量服务或纪元变化重建。
 *
 * <p><b>外观归属（G16/MarkdownPage 同页实例）</b>：本类不引用任何主题/公共色板常量，只做
 * {@code PaintCommand} 搬运——{@link #nodes} 里 BACKGROUND 节点的底色与圆角恒等于 L2 从
 * {@code MarkdownStyleTable} 登记项（chat3 出货口径的表头 {@code 0x18FFFFFF}、边框/装饰
 * {@code 0x40FFFFFF}、围栏衬底 {@code 0x26FFFFFF} 等）解析出的<b>数据驱动色</b>，随被展示的
 * markdown 内容而定。按契约 §7.3「markdown/LaTeX 样本」不迁移清单刻意保留显式：主题切换既
 * 不改命令计划也不改搬运结果，由 {@code MarkdownPageTest} 反向钉住、{@code MarkdownPageContentTest}
 * 钉住「节点底色恒为命令色」的搬运合同。</p>
 */
final class MarkdownPageContent {
    private final MarkdownDocument.LayoutContent content;
    private TextLayoutService cachedMeasurer;
    private int cachedWidth;
    private int cachedFont;
    private int cachedEpoch;
    private MarkdownPainter.ContentLayout cached;

    MarkdownPageContent(String source, MarkdownStyleTable styles, TextStyle base) {
        // 每次挂载仅解析一次。修改源或样式需要创建新消费实例，旧计划不会跨文档复用。
        content = MarkdownDocument.parse(source).toLayoutContent(styles, base);
    }

    MarkdownPainter.ContentLayout layout(TextLayoutService measurer, int width, int font, int epoch) {
        int safeWidth = Math.max(1, width);
        if (cached != null && cachedMeasurer == measurer && cachedWidth == safeWidth
                && cachedFont == font && cachedEpoch == epoch) {
            // 命中跳过全 cell 度量、列宽分配、换行、行高、定位及 PaintCommand 构造。
            return cached;
        }
        MarkdownPainter.ContentLayout next = MarkdownPainter.layoutContent(content, measurer, safeWidth, font);
        cachedMeasurer = measurer;
        cachedWidth = safeWidth;
        cachedFont = font;
        cachedEpoch = epoch;
        cached = next;
        return next;
    }

    /**
     * @param declaredFontPx 声明层字号（设计基准，写进节点 {@code setFontSize}；进场景后由解析出口乘一次倍率）
     * @param layoutFontPx   L2 布局尺度字号（= 声明值 × 当前倍率，命令坐标与命令样式字号都在此尺度上）
     */
    static SceneNode create(SceneRuntime rt, String source, MarkdownStyleTable styles, TextStyle base,
            int declaredFontPx, int layoutFontPx, Supplier<TextLayoutService> measurers, IntSupplier epochs) {
        MarkdownPageContent cache = new MarkdownPageContent(source, styles, base);
        SceneNode body = SceneNode.column(0).setFillParentWidth(true).setHitTestable(false);
        Signal<List<SceneNode>> children = Signal.create(Collections.<SceneNode>emptyList());
        rt.forEach(body, children, node -> node, node -> node);
        int[] width = {0};
        int[] epoch = {epochs.getAsInt()};
        MarkdownPainter.ContentLayout[] published = {null};
        Runnable refresh = () -> {
            if (width[0] <= 0) return;
            TextLayoutService measurer = measurers.get();
            MarkdownPainter.ContentLayout plan = cache.layout(measurer, width[0], layoutFontPx, epoch[0]);
            if (published[0] == plan) return;
            published[0] = plan;
            body.setPreferredHeight(Math.max(1, plan.getHeightPx()));
            children.set(nodes(plan, measurer, declaredFontPx));
        };
        // 与 SceneTextAreaPrimitive 同源：最终布局盒发布后读取真实内容宽，两趟收敛。
        rt.bind(rt.layoutDoneSignal(), done -> {
            LayoutBox box = (LayoutBox) body.getCachedLayout();
            if (box == null) return;
            int available = Math.max(0, box.getWidth() - body.getPaddingLeft() - body.getPaddingRight());
            if (available == width[0]) return;
            width[0] = available;
            refresh.run();
        });
        // 帧订阅只比较整数纪元；稳态不解析、不度量、不构造命令、不写节点/子树。
        rt.bind(rt.__frameTimeNanos(), frame -> {
            int current = epochs.getAsInt();
            if (current == epoch[0]) return;
            epoch[0] = current;
            refresh.run();
        });
        return body;
    }

    /**
     * 仅搬运 L2 已定位命令。COLUMN 的 margin 表达相邻绘制原点差，可为负（背景与文字重叠）。
     * 父盒必须由 ContentLayout 钉高，不取末命令 bottom。段流命令只携带原点，
     * 使用同源度量补齐叶盒供 scene 可见性判断；不重新分配列宽或换行。
     * 仅计划变化时翻译一次，scroll/clip/paint 仍归既有 scene 管线。
     *
     * <p><b>字号契约（调用方必须遵守，否则几何与渲染分叉）</b>：L2 命令的坐标与样式字号都处于
     * <b>生效尺度</b>（= 设计值 × 用户倍率），故：</p>
     * <ul>
     *   <li>节点<b>声明层</b>只写设计基准 {@code declaredFontPx}。写命令字号会被解析出口再乘一次
     *       倍率（2×2 重复缩放）；无显式字号的段（正文）因此渲染成 {@code declared × 倍率}，恰好等于
     *       命令尺度，有显式字号的段（标题等）用段样式自身的绝对值。</li>
     *   <li>盒宽/盒高取<b>命令字号</b>（{@code MarkdownPainter.lineWidthPx/lineHeightPx}），与真正
     *       渲染的字形同尺度。段流节点无 text，叶高完全由这里的 {@code setPreferredHeight} 决定。</li>
     *   <li>调用方必须把 {@code styles.setDefaultFontSizePx(...)} 设成<b>生效字号</b>（见
     *       {@code MarkdownPage.layoutFontPx}），使 L2 段流、命令坐标、盒几何三处同源。</li>
     *   <li>{@code epochs} 必须<b>同时覆盖</b>度量纪元与字号代际：{@code SceneRuntime.textMeasureEpoch()}
     *       是字体运行时纪元，不含用户倍率。</li>
     *   <li><b>{@code layoutFontPx} 是 create 期捕获值</b>：组件内的纪元订阅只能作废缓存、换不掉这个
     *       实参，所以<b>倍率变化必须由宿主重建页面</b>（{@code TestPlaygroundHost} 在字号代际抬升时
     *       调 {@code refreshPage()}）。只订阅纪元而不重建，运行期改倍率会呈现「文字按新倍率放大、
     *       行框仍是旧尺度」—— F36 的同类分叉在真实运行期路径复发（独立审核指出）。</li>
     * </ul>
     *
     * <p><b>现场记录（2026-09-18 实测，缺陷已修）</b>：修前调用方传声明字号 {@code BASE_FONT_PX}，
     * 且 {@code epochs} 只订阅 {@code textMeasureEpoch}。实测 {@code --page=playground --page-index=8}
     * 的 commands bounds 在 fs=100/150/200 下为 1279x751 / 1309x818 / <b>1592</b>x885 —— fs=200 的宽
     * 已横向溢出 1280 视口；fs=0 时装饰（代码块衬底、引用竖条）仍按声明字号排布而文本塌陷为 0，
     * 卡片被拉长（bounds 885，与 fs=200 同高）。修后同一页几何随倍率整体缩放，默认 100% 逐像素不变。</p>
     */
    static List<SceneNode> nodes(MarkdownPainter.ContentLayout plan, TextLayoutService measurer,
            int declaredFontPx) {
        List<SceneNode> out = new ArrayList<SceneNode>();
        int cursor = 0;
        for (PaintCommand command : plan.getCommands()) {
            // 本页与既有 markdown 行同为非交互预览：链接样式保留，命中元数据不装配，
            // 不写 scene paint 引擎拥有的命中缓存。headless 另锁完整 L2 region 几何。
            if (command.getType() == club.heiqi.uilib.ui.scene.paint.PaintCommandType.LINK_REGION) continue;
            SceneNode node = new SceneNode().setHitTestable(false);
            int width = Math.max(1, command.getRight() - command.getLeft());
            int height = Math.max(1, command.getBottom() - command.getTop());
            switch (command.getType()) {
                case BACKGROUND:
                    // 保留显式（契约 §7.3 markdown 样本）：只搬运 L2 数据驱动色（样式表登记项解析进
                    // 命令），本类与主题色板零关联；主题切换不得改变此处输出（MarkdownPageTest 反向钉住）。
                    node.setBackgroundColor(command.getColor()).setCornerRadius(command.getCornerRadius());
                    break;
                case SEGMENTS:
                    node.setSegments(command.getSegments());
                    // 声明层写设计基准，绝不写命令字号：命令字号处于<b>生效尺度</b>（已含倍率），
                    // 写进声明层会被解析出口再乘一次（2×2 重复缩放 ⇒ 150%/200% 下行与行重叠，
                    // 见 FontSizeLimits#effectiveFontSizePx 的调用口径）。无显式字号的段（正文）
                    // 因此按「设计基准 × 倍率」渲染，恰好等于命令尺度；有显式字号的段（标题等）
                    // 用段样式自身的绝对值，与节点声明无关。
                    node.setFontSize(declaredFontPx);
                    node.setTextVerticalAlign(TextVerticalAlign.TOP);
                    // 几何取命令字号（生效尺度）：盒宽/盒高必须与真正渲染的字形同尺度。
                    int commandFont = command.getTextStyle().getFontSize();
                    width = Math.max(1, MarkdownPainter.lineWidthPx(command.getSegments(), measurer, commandFont));
                    height = Math.max(1, MarkdownPainter.lineHeightPx(command.getSegments(), measurer, commandFont));
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported Markdown page command: " + command.getType());
            }
            node.setPreferredWidth(width).setPreferredHeight(height);
            node.setMargin(command.getTop() - cursor, 0, 0, command.getLeft());
            cursor = command.getTop() + height;
            out.add(node);
        }
        return out;
    }
}
